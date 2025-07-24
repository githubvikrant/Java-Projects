package com.music.network;

import com.music.controller.PlayerController;
import java.io.*;
import java.net.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.nio.file.Files;
import java.net.InetSocketAddress;

public class MasterServer {
    private ServerSocket serverSocket;
    private List<Socket> slaveSockets = new ArrayList<>();
    private PlayerController controller;
    private boolean running = false;
    private Map<Socket, Boolean> slaveReady = new ConcurrentHashMap<>();
    private Map<Socket, Long> slaveLatencyMs = new ConcurrentHashMap<>();
    private int actualPort = -1;

    public MasterServer(PlayerController controller) {
        this.controller = controller;
    }

    public void start(int port) {
        Thread acceptThread = new Thread(() -> {
            int tryPort = port;
            while (true) {
                try {
                    ServerSocket ss = new ServerSocket();
                    ss.setReuseAddress(true);
                    ss.bind(new InetSocketAddress(tryPort));
                    serverSocket = ss;
                    actualPort = tryPort;
                    controller.log("Master: Listening on port " + actualPort);
                    break;
                } catch (IOException e) {
                    controller.log("Port " + tryPort + " is busy, trying next port...");
                    tryPort++;
                    if (tryPort > port + 20) {
                        controller.log("Server error: No available ports found in range.");
                        return;
                    }
                }
            }
            running = true;
            while (running) {
                try {
                    controller.log("Master: Waiting for slave connection...");
                    Socket slave = serverSocket.accept();
                    controller.log("Master: New slave connection attempt from " + slave.getInetAddress().getHostAddress());
                    synchronized (slaveSockets) {
                        slaveSockets.add(slave);
                        slaveReady.put(slave, false);
                        controller.log("Slave connected: " + slave.getInetAddress().getHostAddress());
                        controller.updateDeviceList(getConnectedSlaves());
                        controller.log("Current connected slaves: " + getConnectedSlaves());
                        if (getConnectedSlaves().isEmpty()) {
                            controller.log("Warning: Device list is empty after adding a slave. Possible bug.");
                        }
                    }
                    // Start a single thread to handle READY and disconnect
                    Thread slaveThread = new Thread(() -> handleSlaveMessages(slave));
                    slaveThread.setDaemon(true);
                    slaveThread.start();
                } catch (IOException e) {
                    controller.log("Master: Error accepting slave connection: " + e.getMessage());
                    // Continue accepting new connections unless serverSocket is closed
                    if (serverSocket == null || serverSocket.isClosed()) {
                        controller.log("Master: Server socket closed, stopping accept loop.");
                        break;
                    }
                }
            }
            closeServerSocket();
        });
        acceptThread.setDaemon(true);
        acceptThread.start();
    }

    public void stop() {
        running = false;
        // Close all slave sockets
        synchronized (slaveSockets) {
            for (Socket s : slaveSockets) {
                try {
                    s.close();
                } catch (IOException e) {
                    controller.log("Error closing slave socket: " + e.getMessage());
                }
            }
            slaveSockets.clear();
        }
        closeServerSocket();
    }

    private void handleSlaveMessages(Socket slave) {
        try {
            InputStream in = slave.getInputStream();
            DataInputStream dis = new DataInputStream(in);
            OutputStream out = slave.getOutputStream();
            DataOutputStream dos = new DataOutputStream(out);
            while (slave.isConnected() && !slave.isClosed()) {
                String msg;
                try {
                    msg = dis.readUTF();
                } catch (EOFException eof) {
                    controller.log("Master: Slave disconnected (EOF): " + slave.getInetAddress().getHostAddress());
                    break;
                }
                controller.log("Master: Message from slave " + slave.getInetAddress().getHostAddress() + ": " + msg);
                if (msg.equals("READY")) {
                    slaveReady.put(slave, true);
                    controller.log("Master: Received READY from slave: " + slave.getInetAddress().getHostAddress());
                    // Send PING and measure latency
                    try {
                        long pingStart = System.currentTimeMillis();
                        dos.writeUTF("PING");
                        dos.flush();
                        controller.log("Master: Sent PING to slave: " + slave.getInetAddress().getHostAddress());
                        String pong = dis.readUTF();
                        if (pong.equals("PONG")) {
                            long latency = System.currentTimeMillis() - pingStart;
                            slaveLatencyMs.put(slave, latency);
                            controller.log("Master: Received PONG from slave: " + slave.getInetAddress().getHostAddress() + ", latency: " + latency + " ms");
                        }
                    } catch (IOException ex) {
                        controller.log("Master: Latency measurement failed for slave: " + slave.getInetAddress().getHostAddress() + ", " + ex.getMessage());
                    }
                }
            }
        } catch (IOException ignored) {}
        synchronized (slaveSockets) {
            slaveSockets.remove(slave);
            slaveReady.remove(slave);
            slaveLatencyMs.remove(slave);
        }
        controller.log("Slave disconnected: " + slave.getInetAddress().getHostAddress());
        controller.updateDeviceList(getConnectedSlaves());
    }

    public void sendFileToSlaves(File file) {
        try {
            byte[] fileBytes = Files.readAllBytes(file.toPath());
            controller.log("Master: Sending file to slaves: " + file.getName() + ", size: " + fileBytes.length + " bytes");
            synchronized (slaveSockets) {
                Iterator<Socket> it = slaveSockets.iterator();
                while (it.hasNext()) {
                    Socket slave = it.next();
                    try {
                        OutputStream out = slave.getOutputStream();
                        DataOutputStream dos = new DataOutputStream(out);
                        dos.writeUTF("FILE");
                        dos.writeUTF(file.getName());
                        dos.writeLong(fileBytes.length);
                        dos.write(fileBytes);
                        dos.flush();
                        controller.log("Master: File sent to slave: " + slave.getInetAddress().getHostAddress());
                        slaveReady.put(slave, false); // Reset READY status
                    } catch (IOException ex) {
                        controller.log("File transfer failed to slave: " + slave.getInetAddress().getHostAddress() + ", removing.");
                        it.remove();
                        slaveReady.remove(slave);
                        controller.updateDeviceList(getConnectedSlaves());
                    }
                }
            }
        } catch (IOException e) {
            controller.log("File read/send error: " + e.getMessage());
        }
    }

    public void sendCommandToSlaves(String command, double position) {
        controller.log("Master: Preparing to send command: " + command + ", position: " + position);
        // Only send PLAY if all slaves are READY
        if (command.equals("PLAY")) {
            boolean allReady;
            synchronized (slaveSockets) {
                allReady = !slaveSockets.isEmpty() && slaveReady.values().stream().allMatch(Boolean::booleanValue);
            }
            if (!allReady) {
                controller.log("Master: Not all slaves are READY. PLAY command will not be sent.");
                return;
            }
            // Find max latency
            long maxLatency = 0;
            synchronized (slaveSockets) {
                for (Socket s : slaveSockets) {
                    Long lat = slaveLatencyMs.get(s);
                    if (lat != null && lat > maxLatency) maxLatency = lat;
                }
            }
            controller.log("Master: Delaying playback by max slave latency: " + maxLatency + " ms");
            // Send PLAY to slaves first
            controller.log("Master: Sending command to slaves: " + command + ", position: " + position);
            synchronized (slaveSockets) {
                Iterator<Socket> it = slaveSockets.iterator();
                while (it.hasNext()) {
                    Socket slave = it.next();
                    try {
                        OutputStream out = slave.getOutputStream();
                        DataOutputStream dos = new DataOutputStream(out);
                        dos.writeUTF("CMD");
                        dos.writeUTF(command);
                        dos.writeDouble(position);
                        dos.flush();
                        controller.log("Master: Command sent to slave: " + slave.getInetAddress().getHostAddress());
                    } catch (IOException ex) {
                        controller.log("Command send failed to slave: " + slave.getInetAddress().getHostAddress() + ", removing.");
                        it.remove();
                        slaveReady.remove(slave);
                        slaveLatencyMs.remove(slave);
                        controller.updateDeviceList(getConnectedSlaves());
                    }
                }
            }
            // Delay master playback
            try {
                Thread.sleep(maxLatency);
            } catch (InterruptedException ignored) {}
        } else if (command.equals("VOLUME")) {
            controller.log("Master: Sending VOLUME command to slaves.");
            synchronized (slaveSockets) {
                Iterator<Socket> it = slaveSockets.iterator();
                while (it.hasNext()) {
                    Socket slave = it.next();
                    try {
                        OutputStream out = slave.getOutputStream();
                        DataOutputStream dos = new DataOutputStream(out);
                        dos.writeUTF("VOLUME");
                        dos.writeDouble(position); // Use position as volume for VOLUME command
                        dos.flush();
                        controller.log("Master: VOLUME command sent to slave: " + slave.getInetAddress().getHostAddress());
                    } catch (IOException ex) {
                        controller.log("VOLUME send failed to slave: " + slave.getInetAddress().getHostAddress() + ", removing.");
                        it.remove();
                        slaveReady.remove(slave);
                        slaveLatencyMs.remove(slave);
                        controller.updateDeviceList(getConnectedSlaves());
                    }
                }
            }
            return;
        }
        if (!command.equals("PLAY")) {
            controller.log("Master: Sending command to slaves: " + command + ", position: " + position);
            synchronized (slaveSockets) {
                Iterator<Socket> it = slaveSockets.iterator();
                while (it.hasNext()) {
                    Socket slave = it.next();
                    try {
                        OutputStream out = slave.getOutputStream();
                        DataOutputStream dos = new DataOutputStream(out);
                        dos.writeUTF("CMD");
                        dos.writeUTF(command);
                        dos.writeDouble(position);
                        dos.flush();
                        controller.log("Master: Command sent to slave: " + slave.getInetAddress().getHostAddress());
                    } catch (IOException ex) {
                        controller.log("Command send failed to slave: " + slave.getInetAddress().getHostAddress() + ", removing.");
                        it.remove();
                        slaveReady.remove(slave);
                        slaveLatencyMs.remove(slave);
                        controller.updateDeviceList(getConnectedSlaves());
                    }
                }
            }
        }
    }

    public List<String> getConnectedSlaves() {
        synchronized (slaveSockets) {
            List<String> ips = new ArrayList<>();
            for (Socket s : slaveSockets) {
                ips.add(s.getInetAddress().getHostAddress());
            }
            return ips;
        }
    }

    private void closeServerSocket() {
        try {
            if (serverSocket != null && !serverSocket.isClosed()) {
                serverSocket.close();
            }
        } catch (IOException e) {
            controller.log("Error closing server socket: " + e.getMessage());
        }
    }
} 