package com.music.network;

import com.music.controller.PlayerController;
import java.io.*;
import java.net.*;

public class SlaveClient {
    private Socket socket;
    private PlayerController controller;
    private boolean running = false;

    public SlaveClient(PlayerController controller) {
        this.controller = controller;
        // Add shutdown hook to ensure disconnect on JVM exit
        Runtime.getRuntime().addShutdownHook(new Thread(() -> disconnect()));
    }

    public void connect(String ip, int port) {
        Thread t = new Thread(() -> {
            try {
                socket = new Socket(ip, port);
                running = true;
                listenToMaster();
            } catch (IOException e) {
                controller.notifySlaveStatus("Connection failed: " + e.getMessage() + ". Check firewall or network.");
                controller.log("Connection failed: " + e.getMessage());
            }
        });
        t.setDaemon(true);
        t.start();
    }

    private void listenToMaster() {
        try {
            InputStream in = socket.getInputStream();
            DataInputStream dis = new DataInputStream(in);
            OutputStream out = socket.getOutputStream();
            DataOutputStream dos = new DataOutputStream(out);
            while (running) {
                String type = dis.readUTF();
                if (type.equals("FILE")) {
                    String fileName = dis.readUTF();
                    long fileLen = dis.readLong();
                    File outFile = new File(System.getProperty("java.io.tmpdir"), fileName);
                    try (FileOutputStream fos = new FileOutputStream(outFile)) {
                        byte[] buffer = new byte[4096];
                        long totalRead = 0;
                        while (totalRead < fileLen) {
                            int toRead = (int)Math.min(buffer.length, fileLen - totalRead);
                            int read = dis.read(buffer, 0, toRead);
                            if (read == -1) break;
                            fos.write(buffer, 0, read);
                            totalRead += read;
                        }
                        controller.log("Slave: File received from master: " + fileName + ", size: " + fileLen + " bytes");
                    } catch (IOException ex) {
                        controller.notifySlaveStatus("File save failed: " + ex.getMessage());
                        controller.log("File save failed: " + ex.getMessage());
                        continue;
                    }
                    controller.notifySlaveFileReceived(outFile);
                    // Send READY message to master
                    try {
                        dos.writeUTF("READY");
                        dos.flush();
                        controller.log("Slave: Sent READY to master after loading file.");
                    } catch (IOException ex) {
                        controller.log("Slave: Failed to send READY to master: " + ex.getMessage());
                    }
                } else if (type.equals("CMD")) {
                    String command = dis.readUTF();
                    double position = dis.readDouble();
                    controller.log("Slave: Command received from master: " + command + ", position: " + position);
                    controller.notifySlaveStatus(command + ": " + position);
                } else if (type.equals("PING")) {
                    // Reply with PONG immediately
                    try {
                        dos.writeUTF("PONG");
                        dos.flush();
                        controller.log("Slave: Received PING, sent PONG to master.");
                    } catch (IOException ex) {
                        controller.log("Slave: Failed to send PONG: " + ex.getMessage());
                    }
                } else if (type.equals("VOLUME")) {
                    double volume = dis.readDouble();
                    controller.log("Slave: Received VOLUME command: " + volume);
                    controller.notifySlaveStatus("VOLUME: " + volume);
                }
            }
        } catch (IOException e) {
            controller.notifySlaveStatus("Disconnected from master or network error: " + e.getMessage());
            controller.log("Disconnected from master or network error: " + e.getMessage());
            disconnect();
        }
    }

    public void disconnect() {
        running = false;
        try {
            if (socket != null && !socket.isClosed()) {
                socket.close();
            }
        } catch (IOException e) {
            controller.log("Error closing socket: " + e.getMessage());
        }
        controller.notifySlaveStatus("Not connected.");
    }
} 