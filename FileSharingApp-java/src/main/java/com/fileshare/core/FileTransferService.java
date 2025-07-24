package com.fileshare.core;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;
import java.util.zip.CRC32;

/**
 * Complete file transfer service with progress reporting, error handling,
 * and concurrent transfer support.
 */
public class FileTransferService {
    private static final Logger logger = LoggerFactory.getLogger(FileTransferService.class);
    
    // Configuration constants
    private static final int DEFAULT_BUFFER_SIZE = 131072; // 128KB
    private static final int DEFAULT_PORT = 8889;
    private static final int MAX_CONCURRENT_TRANSFERS = 4;
    private static final long TRANSFER_TIMEOUT_MS = 300000; // 5 minutes
    
    // Service state
    private final ExecutorService executorService;
    private final ConcurrentHashMap<String, TransferProgress> activeTransfers;
    private final CopyOnWriteArrayList<TransferProgressListener> progressListeners;
    private final CopyOnWriteArrayList<TransferCompletionListener> completionListeners;
    
    // Transfer statistics
    private final AtomicLong totalBytesTransferred;
    private final AtomicLong totalFilesTransferred;
    
    public FileTransferService() {
        this.executorService = Executors.newFixedThreadPool(MAX_CONCURRENT_TRANSFERS);
        this.activeTransfers = new ConcurrentHashMap<>();
        this.progressListeners = new CopyOnWriteArrayList<>();
        this.completionListeners = new CopyOnWriteArrayList<>();
        this.totalBytesTransferred = new AtomicLong(0);
        this.totalFilesTransferred = new AtomicLong(0);
        
        logger.info("FileTransferService initialized");
    }
    
    /**
     * Send a file to a remote host with progress reporting.
     */
    public Future<TransferResult> sendFileAsync(File file, String host, int port) {
        return executorService.submit(() -> {
            String transferId = generateTransferId();
            TransferProgress progress = new TransferProgress(transferId, file.toPath(), TransferType.SEND);
            activeTransfers.put(transferId, progress);
            
            try {
                logger.info("Starting file send: {} -> {}:{}", file.getName(), host, port);
                
                sendFileWithProgress(file, host, port, progress);
                
                TransferResult result = new TransferResult(transferId, true, null, file.length());
                activeTransfers.remove(transferId);
                totalFilesTransferred.incrementAndGet();
                totalBytesTransferred.addAndGet(file.length());
                
                logger.info("File send completed: {} ({} bytes)", file.getName(), file.length());
                notifyCompletionListeners(result);
                
                return result;
                
            } catch (Exception e) {
                logger.error("File send failed: {}", file.getName(), e);
                TransferResult result = new TransferResult(transferId, false, e.getMessage(), 0);
                activeTransfers.remove(transferId);
                notifyCompletionListeners(result);
                throw new RuntimeException("File send failed", e);
            }
        });
    }
    
    /**
     * Receive a file on the given port with progress reporting.
     */
    public Future<TransferResult> receiveFileAsync(int port, String saveDirectory) {
        return executorService.submit(() -> {
            String transferId = generateTransferId();
            TransferProgress progress = new TransferProgress(transferId, null, TransferType.RECEIVE);
            activeTransfers.put(transferId, progress);
            
            try {
                logger.info("Starting file receive on port: {}", port);
                
                File receivedFile = receiveFileWithProgress(port, saveDirectory, progress);
                
                TransferResult result = new TransferResult(transferId, true, null, receivedFile.length());
                activeTransfers.remove(transferId);
                totalFilesTransferred.incrementAndGet();
                totalBytesTransferred.addAndGet(receivedFile.length());
                
                logger.info("File receive completed: {} ({} bytes)", receivedFile.getName(), receivedFile.length());
                notifyCompletionListeners(result);
                
                return result;
                
            } catch (Exception e) {
                logger.error("File receive failed on port: {}", port, e);
                TransferResult result = new TransferResult(transferId, false, e.getMessage(), 0);
                activeTransfers.remove(transferId);
                notifyCompletionListeners(result);
                throw new RuntimeException("File receive failed", e);
            }
        });
    }
    
    /**
     * Synchronous file send method.
     */
    public void sendFile(File file, String host, int port) throws IOException {
        TransferProgress progress = new TransferProgress(generateTransferId(), file.toPath(), TransferType.SEND);
        sendFileWithProgress(file, host, port, progress);
    }
    
    /**
     * Synchronous file receive method.
     */
    public File receiveFile(int port, String saveDirectory) throws IOException {
        TransferProgress progress = new TransferProgress(generateTransferId(), null, TransferType.RECEIVE);
        return receiveFileWithProgress(port, saveDirectory, progress);
    }
    
    /**
     * Send a file over an existing Socket with progress reporting.
     */
    public void sendFileOverSocket(File file, Socket socket) throws IOException {
        TransferProgress progress = new TransferProgress(generateTransferId(), file.toPath(), TransferType.SEND);
        if (!file.exists()) {
            throw new FileNotFoundException("File not found: " + file.getAbsolutePath());
        }
        socket.setSendBufferSize(DEFAULT_BUFFER_SIZE);
        try (FileInputStream fis = new FileInputStream(file);
             DataOutputStream dos = new DataOutputStream(socket.getOutputStream())) {
            socket.setSoTimeout((int) TRANSFER_TIMEOUT_MS);
            dos.writeUTF(file.getName());
            dos.writeLong(file.length());
            dos.writeLong(calculateFileChecksum(file.toPath()));
            byte[] buffer = new byte[DEFAULT_BUFFER_SIZE];
            long totalSent = 0;
            long fileSize = file.length();
            long startTime = System.currentTimeMillis();
            int bytesRead;
            long lastUpdate = System.currentTimeMillis();
            double lastPercent = 0;
            while ((bytesRead = fis.read(buffer)) != -1 && !progress.isCancelled()) {
                dos.write(buffer, 0, bytesRead);
                totalSent += bytesRead;
                double percent = (double) totalSent / fileSize * 100;
                long now = System.currentTimeMillis();
                if (percent - lastPercent >= 1.0 || now - lastUpdate >= 100) {
                    progress.setBytesTransferred(totalSent);
                    progress.setSpeed(calculateSpeed(totalSent, startTime));
                    progress.setProgressPercentage(percent);
                    notifyProgressListeners(progress);
                    lastUpdate = now;
                    lastPercent = percent;
                }
            }
            dos.flush();
            if (progress.isCancelled()) {
                throw new IOException("Transfer was cancelled");
            }
        }
    }

    /**
     * Receive a file over an existing Socket with progress reporting.
     */
    public File receiveFileOverSocket(Socket socket, String saveDirectory) throws IOException {
        TransferProgress progress = new TransferProgress(generateTransferId(), null, TransferType.RECEIVE);
        socket.setReceiveBufferSize(DEFAULT_BUFFER_SIZE);
        Path savePath = Paths.get(saveDirectory);
        if (!Files.exists(savePath)) {
            Files.createDirectories(savePath);
        }
        try (DataInputStream dis = new DataInputStream(socket.getInputStream())) {
            socket.setSoTimeout((int) TRANSFER_TIMEOUT_MS);
            String fileName = dis.readUTF();
            long fileSize = dis.readLong();
            long expectedChecksum = dis.readLong();
            File outFile = new File(saveDirectory, fileName);
            progress.setFilePath(outFile.toPath());
            try (FileOutputStream fos = new FileOutputStream(outFile)) {
                byte[] buffer = new byte[DEFAULT_BUFFER_SIZE];
                long totalReceived = 0;
                long startTime = System.currentTimeMillis();
                int bytesRead;
                long lastUpdate = System.currentTimeMillis();
                double lastPercent = 0;
                while (totalReceived < fileSize && !progress.isCancelled() &&
                       (bytesRead = dis.read(buffer, 0, (int)Math.min(buffer.length, fileSize - totalReceived))) != -1) {
                    fos.write(buffer, 0, bytesRead);
                    totalReceived += bytesRead;
                    double percent = (double) totalReceived / fileSize * 100;
                    long now = System.currentTimeMillis();
                    if (percent - lastPercent >= 1.0 || now - lastUpdate >= 100) {
                        progress.setBytesTransferred(totalReceived);
                        progress.setSpeed(calculateSpeed(totalReceived, startTime));
                        progress.setProgressPercentage(percent);
                        notifyProgressListeners(progress);
                        lastUpdate = now;
                        lastPercent = percent;
                    }
                }
                fos.flush();
                if (progress.isCancelled()) {
                    Files.deleteIfExists(outFile.toPath());
                    throw new IOException("Transfer was cancelled");
                }
                long actualChecksum = calculateFileChecksum(outFile.toPath());
                if (actualChecksum != expectedChecksum) {
                    Files.deleteIfExists(outFile.toPath());
                    throw new IOException("File integrity check failed");
                }
            }
            return outFile;
        }
    }
    
    /**
     * Get active transfer progress.
     */
    public TransferProgress getTransferProgress(String transferId) {
        return activeTransfers.get(transferId);
    }
    
    /**
     * Get all active transfers.
     */
    public ConcurrentHashMap<String, TransferProgress> getActiveTransfers() {
        return new ConcurrentHashMap<>(activeTransfers);
    }
    
    /**
     * Cancel an active transfer.
     */
    public boolean cancelTransfer(String transferId) {
        TransferProgress progress = activeTransfers.get(transferId);
        if (progress != null) {
            progress.setCancelled(true);
            activeTransfers.remove(transferId);
            logger.info("Transfer cancelled: {}", transferId);
            return true;
        }
        return false;
    }
    
    /**
     * Add progress listener.
     */
    public void addProgressListener(TransferProgressListener listener) {
        progressListeners.add(listener);
    }
    
    /**
     * Remove progress listener.
     */
    public void removeProgressListener(TransferProgressListener listener) {
        progressListeners.remove(listener);
    }
    
    /**
     * Add completion listener.
     */
    public void addCompletionListener(TransferCompletionListener listener) {
        completionListeners.add(listener);
    }
    
    /**
     * Remove completion listener.
     */
    public void removeCompletionListener(TransferCompletionListener listener) {
        completionListeners.remove(listener);
    }
    
    /**
     * Get transfer statistics.
     */
    public TransferStatistics getStatistics() {
        return new TransferStatistics(
            totalFilesTransferred.get(),
            totalBytesTransferred.get(),
            activeTransfers.size()
        );
    }
    
    /**
     * Shutdown the service and cleanup resources.
     */
    public void shutdown() {
        logger.info("Shutting down FileTransferService...");
        
        // Cancel all active transfers
        activeTransfers.keySet().forEach(this::cancelTransfer);
        
        // Shutdown executor service
        executorService.shutdown();
        try {
            if (!executorService.awaitTermination(5, TimeUnit.SECONDS)) {
                executorService.shutdownNow();
            }
        } catch (InterruptedException e) {
            executorService.shutdownNow();
            Thread.currentThread().interrupt();
        }
        
        logger.info("FileTransferService shutdown complete");
    }
    
    // Private helper methods
    
    private void sendFileWithProgress(File file, String host, int port, TransferProgress progress) throws IOException {
        if (!file.exists()) {
            throw new FileNotFoundException("File not found: " + file.getAbsolutePath());
        }
        
        try (Socket socket = new Socket(host, port);
             FileInputStream fis = new FileInputStream(file);
             DataOutputStream dos = new DataOutputStream(socket.getOutputStream())) {
            
            socket.setSoTimeout((int) TRANSFER_TIMEOUT_MS);
            
            // Send file metadata
            dos.writeUTF(file.getName());
            dos.writeLong(file.length());
            dos.writeLong(calculateFileChecksum(file.toPath()));
            
            // Send file content with progress
            byte[] buffer = new byte[DEFAULT_BUFFER_SIZE];
            long totalSent = 0;
            long fileSize = file.length();
            long startTime = System.currentTimeMillis();
            
            int bytesRead;
            while ((bytesRead = fis.read(buffer)) != -1 && !progress.isCancelled()) {
                dos.write(buffer, 0, bytesRead);
                totalSent += bytesRead;
                
                // Update progress
                progress.setBytesTransferred(totalSent);
                progress.setSpeed(calculateSpeed(totalSent, startTime));
                progress.setProgressPercentage((double) totalSent / fileSize * 100);
                
                notifyProgressListeners(progress);
            }
            
            dos.flush();
            
            if (progress.isCancelled()) {
                throw new IOException("Transfer was cancelled");
            }
        }
    }
    
    private File receiveFileWithProgress(int port, String saveDirectory, TransferProgress progress) throws IOException {
        // Ensure save directory exists
        Path savePath = Paths.get(saveDirectory);
        if (!Files.exists(savePath)) {
            Files.createDirectories(savePath);
        }
        
        try (ServerSocket serverSocket = new ServerSocket(port);
             Socket clientSocket = serverSocket.accept();
             DataInputStream dis = new DataInputStream(clientSocket.getInputStream())) {
            
            clientSocket.setSoTimeout((int) TRANSFER_TIMEOUT_MS);
            
            // Read file metadata
            String fileName = dis.readUTF();
            long fileSize = dis.readLong();
            long expectedChecksum = dis.readLong();
            
            File outFile = new File(saveDirectory, fileName);
            progress.setFilePath(outFile.toPath());
            
            // Receive file content with progress
            try (FileOutputStream fos = new FileOutputStream(outFile)) {
                byte[] buffer = new byte[DEFAULT_BUFFER_SIZE];
                long totalReceived = 0;
                long startTime = System.currentTimeMillis();
                
                int bytesRead;
                while (totalReceived < fileSize && !progress.isCancelled() && 
                       (bytesRead = dis.read(buffer, 0, (int)Math.min(buffer.length, fileSize - totalReceived))) != -1) {
                    fos.write(buffer, 0, bytesRead);
                    totalReceived += bytesRead;
                    
                    // Update progress
                    progress.setBytesTransferred(totalReceived);
                    progress.setSpeed(calculateSpeed(totalReceived, startTime));
                    progress.setProgressPercentage((double) totalReceived / fileSize * 100);
                    
                    notifyProgressListeners(progress);
                }
                
                fos.flush();
                
                if (progress.isCancelled()) {
                    Files.deleteIfExists(outFile.toPath());
                    throw new IOException("Transfer was cancelled");
                }
                
                // Verify file integrity
                long actualChecksum = calculateFileChecksum(outFile.toPath());
                if (actualChecksum != expectedChecksum) {
                    Files.deleteIfExists(outFile.toPath());
                    throw new IOException("File integrity check failed");
                }
            }
            
            return outFile;
        }
    }
    
    private long calculateFileChecksum(Path filePath) throws IOException {
        CRC32 crc32 = new CRC32();
        try (InputStream is = Files.newInputStream(filePath)) {
            byte[] buffer = new byte[DEFAULT_BUFFER_SIZE];
            int bytesRead;
            while ((bytesRead = is.read(buffer)) != -1) {
                crc32.update(buffer, 0, bytesRead);
            }
        }
        return crc32.getValue();
    }
    
    private double calculateSpeed(long bytesTransferred, long startTime) {
        long elapsed = System.currentTimeMillis() - startTime;
        if (elapsed == 0) return 0.0;
        return (bytesTransferred * 1000.0) / (elapsed * 1024.0 * 1024.0); // MB/s
    }
    
    private String generateTransferId() {
        return "transfer_" + System.currentTimeMillis() + "_" + Thread.currentThread().getId();
    }
    
    private void notifyProgressListeners(TransferProgress progress) {
        progressListeners.forEach(listener -> {
            try {
                listener.onProgressUpdated(progress);
            } catch (Exception e) {
                logger.error("Error in progress listener", e);
            }
        });
    }
    
    private void notifyCompletionListeners(TransferResult result) {
        completionListeners.forEach(listener -> {
            try {
                listener.onTransferCompleted(result);
            } catch (Exception e) {
                logger.error("Error in completion listener", e);
            }
        });
    }
    
    // Inner classes and interfaces
    
    public static class TransferProgress {
        private final String transferId;
        private Path filePath;
        private final TransferType type;
        private final long startTime;
        
        private long bytesTransferred;
        private double speed; // MB/s
        private double progressPercentage;
        private boolean cancelled;
        
        public TransferProgress(String transferId, Path filePath, TransferType type) {
            this.transferId = transferId;
            this.filePath = filePath;
            this.type = type;
            this.startTime = System.currentTimeMillis();
        }
        
        // Getters and setters
        public String getTransferId() { return transferId; }
        public Path getFilePath() { return filePath; }
        public TransferType getType() { return type; }
        public long getStartTime() { return startTime; }
        public long getBytesTransferred() { return bytesTransferred; }
        public double getSpeed() { return speed; }
        public double getProgressPercentage() { return progressPercentage; }
        public boolean isCancelled() { return cancelled; }
        
        public void setFilePath(Path filePath) { this.filePath = filePath; }
        public void setBytesTransferred(long bytesTransferred) { this.bytesTransferred = bytesTransferred; }
        public void setSpeed(double speed) { this.speed = speed; }
        public void setProgressPercentage(double progressPercentage) { this.progressPercentage = progressPercentage; }
        public void setCancelled(boolean cancelled) { this.cancelled = cancelled; }
        
        public String getFileName() {
            return filePath != null ? filePath.getFileName().toString() : "Unknown";
        }
        
        public long getElapsedTime() {
            return System.currentTimeMillis() - startTime;
        }
    }
    
    public static class TransferResult {
        private final String transferId;
        private final boolean success;
        private final String errorMessage;
        private final long bytesTransferred;
        
        public TransferResult(String transferId, boolean success, String errorMessage, long bytesTransferred) {
            this.transferId = transferId;
            this.success = success;
            this.errorMessage = errorMessage;
            this.bytesTransferred = bytesTransferred;
        }
        
        public String getTransferId() { return transferId; }
        public boolean isSuccess() { return success; }
        public String getErrorMessage() { return errorMessage; }
        public long getBytesTransferred() { return bytesTransferred; }
    }
    
    public static class TransferStatistics {
        private final long totalFilesTransferred;
        private final long totalBytesTransferred;
        private final int activeTransfers;
        
        public TransferStatistics(long totalFilesTransferred, long totalBytesTransferred, int activeTransfers) {
            this.totalFilesTransferred = totalFilesTransferred;
            this.totalBytesTransferred = totalBytesTransferred;
            this.activeTransfers = activeTransfers;
        }
        
        public long getTotalFilesTransferred() { return totalFilesTransferred; }
        public long getTotalBytesTransferred() { return totalBytesTransferred; }
        public int getActiveTransfers() { return activeTransfers; }
    }
    
    public enum TransferType {
        SEND, RECEIVE
    }
    
    public interface TransferProgressListener {
        void onProgressUpdated(TransferProgress progress);
    }
    
    public interface TransferCompletionListener {
        void onTransferCompleted(TransferResult result);
    }
} 