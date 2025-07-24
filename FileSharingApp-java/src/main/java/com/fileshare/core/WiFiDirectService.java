package com.fileshare.core;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.*;
import java.util.*;
import java.util.concurrent.*;

/**
 * WiFi Direct Service for peer discovery and connection management.
 * Handles device discovery, connection establishment, and network management.
 */
public class WiFiDirectService {
    private static final Logger logger = LoggerFactory.getLogger(WiFiDirectService.class);
    
    // Network configuration
    private static final int DISCOVERY_PORT = 8888;
    private static final int TRANSFER_PORT = 8889;
    private static final String SERVICE_NAME = "WiFiDirectFileShare";
    private static final String MULTICAST_GROUP = "230.0.0.1";
    
    // Service state
    private boolean isRunning = false;
    private boolean isGroupOwner = false;
    private String deviceName;
    private String deviceAddress;
    private final Map<String, PeerDevice> discoveredPeers = new ConcurrentHashMap<>();
    private final ExecutorService executorService = Executors.newCachedThreadPool();
    
    // Network components
    private MulticastSocket discoverySocket;
    private ServerSocket transferServer;
    private DatagramSocket broadcastSocket;
    private Socket groupConnection;
    
    // Event listeners
    private final List<PeerDiscoveryListener> discoveryListeners = new CopyOnWriteArrayList<>();
    private final List<ConnectionListener> connectionListeners = new CopyOnWriteArrayList<>();
    private final List<ConnectionStatusListener> statusListeners = new CopyOnWriteArrayList<>();
    
    public WiFiDirectService() {
        this.deviceName = System.getProperty("user.name", "Unknown");
        this.deviceAddress = getLocalAddress();
    }
    
    /**
     * Start the WiFi Direct service.
     */
    public void start() {
        if (isRunning) {
            logger.warn("Service is already running");
            return;
        }
        
        try {
            logger.info("Starting WiFi Direct service...");
            isRunning = true;
            
            // Start discovery service
            startDiscoveryService();
            
            // Start transfer server
            startTransferServer();
            
            // Start peer discovery
            startPeerDiscovery();
            
            logger.info("WiFi Direct service started successfully");
            
        } catch (Exception e) {
            logger.error("Failed to start WiFi Direct service", e);
            stop();
            throw new RuntimeException("Failed to start WiFi Direct service", e);
        }
    }
    
    /**
     * Stop the WiFi Direct service.
     */
    public void stop() {
        if (!isRunning) {
            return;
        }
        
        logger.info("Stopping WiFi Direct service...");
        isRunning = false;
        
        // Close network resources
        closeResources();
        
        // Shutdown executor
        executorService.shutdown();
        
        logger.info("WiFi Direct service stopped");
    }
    
    /**
     * Create a WiFi Direct group (become group owner).
     */
    public void createGroup() {
        if (!isRunning) {
            throw new IllegalStateException("Service must be started before creating group");
        }
        
        try {
            logger.info("Creating WiFi Direct group...");
            isGroupOwner = true;
            
            // Broadcast group creation
            broadcastGroupCreation();
            
            logger.info("WiFi Direct group created successfully");
            
        } catch (Exception e) {
            logger.error("Failed to create WiFi Direct group", e);
            isGroupOwner = false;
            throw new RuntimeException("Failed to create WiFi Direct group", e);
        }
    }
    
    /**
     * Join an existing WiFi Direct group.
     */
    public void joinGroup(String groupOwnerAddress) {
        if (!isRunning) {
            throw new IllegalStateException("Service must be started before joining group");
        }
        
        try {
            logger.info("Joining WiFi Direct group at {}", groupOwnerAddress);
            isGroupOwner = false;
            
            // Connect to group owner
            connectToGroupOwner(groupOwnerAddress);
            
            logger.info("Successfully joined WiFi Direct group");
            
        } catch (Exception e) {
            logger.error("Failed to join WiFi Direct group", e);
            throw new RuntimeException("Failed to join WiFi Direct group", e);
        }
    }
    
    /**
     * Get list of discovered peers.
     */
    public List<PeerDevice> getDiscoveredPeers() {
        return new ArrayList<>(discoveredPeers.values());
    }
    
    /**
     * Get current connection status.
     */
    public boolean isConnected() {
        return groupConnection != null && !groupConnection.isClosed();
    }
    
    /**
     * Get the current connection socket.
     */
    public Socket getConnection() {
        return groupConnection;
    }
    
    /**
     * Add peer discovery listener.
     */
    public void addDiscoveryListener(PeerDiscoveryListener listener) {
        discoveryListeners.add(listener);
    }
    
    /**
     * Add connection listener.
     */
    public void addConnectionListener(ConnectionListener listener) {
        connectionListeners.add(listener);
    }
    
    /**
     * Add connection status listener.
     */
    public void addConnectionStatusListener(ConnectionStatusListener listener) {
        statusListeners.add(listener);
    }
    
    // Private helper methods
    
    private void startDiscoveryService() throws IOException {
        // Create multicast socket for service discovery
        discoverySocket = new MulticastSocket(DISCOVERY_PORT);
        InetAddress group = InetAddress.getByName(MULTICAST_GROUP);
        discoverySocket.joinGroup(group);
        
        // Start discovery listener
        executorService.submit(() -> {
            byte[] buffer = new byte[1024];
            DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
            
            while (isRunning) {
                try {
                    discoverySocket.receive(packet);
                    handleDiscoveryMessage(packet);
                } catch (IOException e) {
                    if (isRunning) {
                        logger.error("Error in discovery service", e);
                    }
                }
            }
        });
    }
    
    private void startTransferServer() throws IOException {
        transferServer = new ServerSocket(TRANSFER_PORT);
        logger.info("[HANDSHAKE] Server listening on port {}", TRANSFER_PORT);
        statusListeners.forEach(listener -> listener.onConnectionStatusChanged(false, "Listening on port " + TRANSFER_PORT));
        executorService.submit(() -> {
            while (isRunning) {
                try {
                    Socket clientSocket = transferServer.accept();
                    logger.info("[HANDSHAKE] Accepted connection from {}", clientSocket.getInetAddress());
                    statusListeners.forEach(listener -> listener.onConnectionStatusChanged(true, clientSocket.getInetAddress().toString()));
                    handleIncomingConnection(clientSocket);
                } catch (IOException e) {
                    if (isRunning) {
                        logger.error("Error in transfer server", e);
                    }
                }
            }
        });
    }
    
    private void startPeerDiscovery() {
        executorService.submit(() -> {
            while (isRunning) {
                try {
                    broadcastDiscoveryMessage();
                    Thread.sleep(5000); // Broadcast every 5 seconds
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                } catch (Exception e) {
                    logger.error("Error broadcasting discovery message", e);
                }
            }
        });
    }
    
    private void broadcastDiscoveryMessage() throws IOException {
        if (broadcastSocket == null) {
            broadcastSocket = new DatagramSocket();
            broadcastSocket.setBroadcast(true);
        }
        
        String message = String.format("DISCOVER:%s:%s:%s", 
            SERVICE_NAME, deviceName, deviceAddress);
        
        byte[] data = message.getBytes();
        DatagramPacket packet = new DatagramPacket(data, data.length,
            InetAddress.getByName("255.255.255.255"), DISCOVERY_PORT);
        
        broadcastSocket.send(packet);
    }
    
    private void handleDiscoveryMessage(DatagramPacket packet) {
        String message = new String(packet.getData(), 0, packet.getLength());
        String[] parts = message.split(":");
        
        if (parts.length >= 4 && "DISCOVER".equals(parts[0])) {
            String serviceName = parts[1];
            String peerName = parts[2];
            String peerAddress = parts[3];
            
            if (SERVICE_NAME.equals(serviceName) && !deviceAddress.equals(peerAddress)) {
                // Check if this is a new peer or updated information
                PeerDevice existingPeer = discoveredPeers.get(peerAddress);
                if (existingPeer == null || !existingPeer.getName().equals(peerName)) {
                    PeerDevice peer = new PeerDevice(peerName, peerAddress);
                    discoveredPeers.put(peerAddress, peer);
                    
                    // Notify listeners only for new peers
                    if (existingPeer == null) {
                        discoveryListeners.forEach(listener -> 
                            listener.onPeerDiscovered(peer));
                    }
                }
            }
        } else if (parts.length >= 3 && "GROUP_CREATED".equals(parts[0])) {
            String groupOwnerName = parts[1];
            String groupOwnerAddress = parts[2];
            
            if (!deviceAddress.equals(groupOwnerAddress)) {
                PeerDevice groupOwner = new PeerDevice(groupOwnerName, groupOwnerAddress);
                discoveredPeers.put(groupOwnerAddress, groupOwner);
                
                discoveryListeners.forEach(listener -> 
                    listener.onPeerDiscovered(groupOwner));
            }
        }
    }
    
    private void handleIncomingConnection(Socket socket) {
        logger.info("Incoming connection from {}", socket.getInetAddress());
        statusListeners.forEach(listener -> listener.onConnectionStatusChanged(true, socket.getInetAddress().toString()));
        // Handle the connection in a separate thread
        executorService.submit(() -> {
            try {
                // Read the join message
                byte[] buffer = new byte[1024];
                int bytesRead = socket.getInputStream().read(buffer);
                if (bytesRead > 0) {
                    String message = new String(buffer, 0, bytesRead);
                    String[] parts = message.split(":");
                    if (parts.length >= 3 && "JOIN".equals(parts[0])) {
                        String peerName = parts[1];
                        String peerAddress = parts[2];
                        // Store the connection
                        groupConnection = socket;
                        // Notify listeners
                        connectionListeners.forEach(listener -> 
                            listener.onConnectionEstablished(socket));
                        logger.info("Peer {} joined the group", peerName);
                        statusListeners.forEach(listener -> listener.onConnectionStatusChanged(true, peerAddress));
                    }
                }
            } catch (Exception e) {
                logger.error("Error handling incoming connection", e);
                statusListeners.forEach(listener -> listener.onConnectionStatusChanged(false, "Error: " + e.getMessage()));
            }
        });
    }
    
    private void broadcastGroupCreation() throws IOException {
        String message = String.format("GROUP_CREATED:%s:%s", deviceName, deviceAddress);
        broadcastMessage(message);
    }
    
    private void connectToGroupOwner(String groupOwnerAddress) throws IOException {
        try {
            logger.info("[HANDSHAKE] Connecting to group owner at {}:{}", groupOwnerAddress, TRANSFER_PORT);
            statusListeners.forEach(listener -> listener.onConnectionStatusChanged(false, "Connecting to group owner at " + groupOwnerAddress));
            groupConnection = new Socket(groupOwnerAddress, TRANSFER_PORT);
            logger.info("[HANDSHAKE] Connected to group owner at {}", groupOwnerAddress);
            statusListeners.forEach(listener -> listener.onConnectionStatusChanged(true, groupOwnerAddress));
            // Send join message
            String message = String.format("JOIN:%s:%s", deviceName, deviceAddress);
            groupConnection.getOutputStream().write(message.getBytes());
            groupConnection.getOutputStream().flush();
            // Notify listeners
            connectionListeners.forEach(listener -> 
                listener.onConnectionEstablished(groupConnection));
        } catch (IOException e) {
            logger.error("Failed to connect to group owner", e);
            if (groupConnection != null) {
                groupConnection.close();
                groupConnection = null;
            }
            statusListeners.forEach(listener -> listener.onConnectionStatusChanged(false, "Failed to connect: " + e.getMessage()));
            throw e;
        }
    }
    
    private void broadcastMessage(String message) throws IOException {
        if (broadcastSocket == null) {
            broadcastSocket = new DatagramSocket();
            broadcastSocket.setBroadcast(true);
        }
        
        byte[] data = message.getBytes();
        DatagramPacket packet = new DatagramPacket(data, data.length,
            InetAddress.getByName("255.255.255.255"), DISCOVERY_PORT);
        
        broadcastSocket.send(packet);
    }
    
    private String getLocalAddress() {
        try {
            return InetAddress.getLocalHost().getHostAddress();
        } catch (UnknownHostException e) {
            return "127.0.0.1";
        }
    }
    
    private void closeResources() {
        try {
            if (discoverySocket != null) {
                discoverySocket.close();
            }
            if (transferServer != null) {
                transferServer.close();
            }
            if (broadcastSocket != null) {
                broadcastSocket.close();
            }
            if (groupConnection != null) {
                groupConnection.close();
            }
        } catch (IOException e) {
            logger.error("Error closing resources", e);
        }
    }
    
    // Inner classes for data structures and listeners
    
    public static class PeerDevice {
        private final String name;
        private final String address;
        private final long discoveryTime;
        
        public PeerDevice(String name, String address) {
            this.name = name;
            this.address = address;
            this.discoveryTime = System.currentTimeMillis();
        }
        
        public String getName() { return name; }
        public String getAddress() { return address; }
        public long getDiscoveryTime() { return discoveryTime; }
        
        @Override
        public String toString() {
            return String.format("PeerDevice{name='%s', address='%s'}", name, address);
        }
        
        @Override
        public boolean equals(Object obj) {
            if (this == obj) return true;
            if (obj == null || getClass() != obj.getClass()) return false;
            PeerDevice that = (PeerDevice) obj;
            return Objects.equals(address, that.address);
        }
        
        @Override
        public int hashCode() {
            return Objects.hash(address);
        }
    }
    
    public interface PeerDiscoveryListener {
        void onPeerDiscovered(PeerDevice peer);
    }
    
    public interface ConnectionListener {
        void onConnectionEstablished(Socket socket);
    }
    
    public interface ConnectionStatusListener {
        void onConnectionStatusChanged(boolean connected, String peerAddress);
    }
} 