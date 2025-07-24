package com.fileshare.ui;

import com.fileshare.core.WiFiDirectService;
import com.fileshare.core.FileTransferService;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.stage.FileChooser;
import javafx.stage.DirectoryChooser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.net.Socket;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Main application window for WiFi Direct File Share
 * Provides UI for peer discovery, file selection, and transfer monitoring
 */
public class MainWindow {
    private static final Logger logger = LoggerFactory.getLogger(MainWindow.class);
    
    // UI Components
    private VBox root;
    private TabPane tabPane;
    private Tab sendTab;
    private Tab receiveTab;
    private Tab statusTab;
    
    // Service components
    private WiFiDirectService wifiDirectService;
    private FileTransferService fileTransferService;
    
    // UI Controls
    private ListView<WiFiDirectService.PeerDevice> peerListView;
    private ListView<File> fileListView;
    private ProgressBar transferProgressBar;
    private Label statusLabel;
    private Label speedLabel;
    private Button createGroupButton;
    private Button joinGroupButton;
    private Button selectFilesButton;
    private Button sendFilesButton;
    private Button selectFolderButton;
    private Label folderLabel;
    private ListView<String> incomingListView;
    private TextArea logArea;
    
    // State management
    private final ConcurrentHashMap<String, WiFiDirectService.PeerDevice> discoveredPeers = new ConcurrentHashMap<>();
    private final AtomicReference<File> downloadFolder = new AtomicReference<>();
    private Socket currentConnection;
    
    public MainWindow() {
        initializeServices();
        createUI();
        setupEventHandlers();
        startServices();
    }
    
    private void initializeServices() {
        wifiDirectService = new WiFiDirectService();
        fileTransferService = new FileTransferService();
        
        // Add listeners
        wifiDirectService.addDiscoveryListener(this::onPeerDiscovered);
        wifiDirectService.addConnectionListener(this::onConnectionEstablished);
        wifiDirectService.addConnectionStatusListener(this::onConnectionStatusChanged);
        fileTransferService.addProgressListener(this::onTransferProgress);
        fileTransferService.addCompletionListener(this::onTransferCompleted);
    }
    
    private void createUI() {
        root = new VBox(10);
        root.setPadding(new Insets(10));
        root.setAlignment(Pos.TOP_CENTER);
        
        // Create tab pane
        tabPane = new TabPane();
        
        // Create tabs
        createSendTab();
        createReceiveTab();
        createStatusTab();
        
        tabPane.getTabs().addAll(sendTab, receiveTab, statusTab);
        root.getChildren().add(tabPane);
        
        // Status bar
        createStatusBar();
    }
    
    private void createSendTab() {
        sendTab = new Tab("Send Files");
        sendTab.setClosable(false);
        
        VBox sendContent = new VBox(10);
        sendContent.setPadding(new Insets(10));
        
        // Group management section
        HBox groupControls = new HBox(10);
        groupControls.setAlignment(Pos.CENTER_LEFT);
        
        createGroupButton = new Button("Create Group");
        joinGroupButton = new Button("Join Group");
        
        groupControls.getChildren().addAll(createGroupButton, joinGroupButton);
        
        // Peer discovery section
        Label peerLabel = new Label("Available Peers:");
        peerListView = new ListView<>();
        peerListView.setPrefHeight(150);
        peerListView.setCellFactory(param -> new ListCell<WiFiDirectService.PeerDevice>() {
            @Override
            protected void updateItem(WiFiDirectService.PeerDevice item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                } else {
                    setText(String.format("%s (%s)", item.getName(), item.getAddress()));
                }
            }
        });
        
        // File selection section
        HBox fileControls = new HBox(10);
        fileControls.setAlignment(Pos.CENTER_LEFT);
        
        selectFilesButton = new Button("Select Files");
        sendFilesButton = new Button("Send Files");
        sendFilesButton.setDisable(true);
        
        fileControls.getChildren().addAll(selectFilesButton, sendFilesButton);
        
        Label fileLabel = new Label("Selected Files:");
        fileListView = new ListView<>();
        fileListView.setPrefHeight(200);
        
        // Transfer progress section
        Label progressLabel = new Label("Transfer Progress:");
        transferProgressBar = new ProgressBar(0);
        transferProgressBar.setPrefWidth(400);
        
        speedLabel = new Label("Speed: 0 MB/s");
        
        VBox progressBox = new VBox(5);
        progressBox.getChildren().addAll(progressLabel, transferProgressBar, speedLabel);
        
        // Add all sections to send content
        sendContent.getChildren().addAll(
            groupControls,
            peerLabel,
            peerListView,
            fileControls,
            fileLabel,
            fileListView,
            progressBox
        );
        
        sendTab.setContent(sendContent);
    }
    
    private void createReceiveTab() {
        receiveTab = new Tab("Receive Files");
        receiveTab.setClosable(false);
        
        VBox receiveContent = new VBox(10);
        receiveContent.setPadding(new Insets(10));
        
        // Folder selection
        HBox folderControls = new HBox(10);
        folderControls.setAlignment(Pos.CENTER_LEFT);
        
        selectFolderButton = new Button("Select Download Folder");
        folderLabel = new Label("No folder selected");
        
        folderControls.getChildren().addAll(selectFolderButton, folderLabel);
        
        // Incoming files list
        Label incomingLabel = new Label("Incoming Files:");
        incomingListView = new ListView<>();
        incomingListView.setPrefHeight(300);
        
        receiveContent.getChildren().addAll(folderControls, incomingLabel, incomingListView);
        receiveTab.setContent(receiveContent);
    }
    
    private void createStatusTab() {
        statusTab = new Tab("Status");
        statusTab.setClosable(false);
        
        VBox statusContent = new VBox(10);
        statusContent.setPadding(new Insets(10));
        
        // Connection status
        Label connectionLabel = new Label("Connection Status:");
        statusLabel = new Label("Disconnected");
        statusLabel.setStyle("-fx-text-fill: red;");
        
        // Transfer statistics
        Label statsLabel = new Label("Transfer Statistics:");
        Label statsInfo = new Label("No transfers yet");
        
        // Log area
        Label logLabel = new Label("Event Log:");
        logArea = new TextArea();
        logArea.setPrefHeight(150);
        logArea.setEditable(false);
        logArea.setWrapText(true);
        
        statusContent.getChildren().addAll(
            connectionLabel, statusLabel,
            statsLabel, statsInfo,
            logLabel, logArea
        );
        
        statusTab.setContent(statusContent);
    }
    
    private void createStatusBar() {
        HBox statusBar = new HBox(10);
        statusBar.setAlignment(Pos.CENTER_LEFT);
        statusBar.setPadding(new Insets(5));
        statusBar.setStyle("-fx-background-color: #f0f0f0;");
        
        Label statusBarLabel = new Label("Ready");
        statusBar.getChildren().add(statusBarLabel);
        
        root.getChildren().add(statusBar);
    }
    
    private void setupEventHandlers() {
        // Group management
        createGroupButton.setOnAction(e -> createGroup());
        joinGroupButton.setOnAction(e -> joinGroup());
        
        // File operations
        selectFilesButton.setOnAction(e -> selectFiles());
        sendFilesButton.setOnAction(e -> sendFiles());
        selectFolderButton.setOnAction(e -> selectDownloadFolder());
        
        // Peer selection
        peerListView.getSelectionModel().selectedItemProperty().addListener(
            (observable, oldValue, newValue) -> updateSendButtonState());
    }
    
    private void startServices() {
        try {
            wifiDirectService.start();
            updateStatus("Connected to WiFi Direct network", true);
        } catch (Exception e) {
            logger.error("Failed to start services", e);
            updateStatus("Failed to start services: " + e.getMessage(), false);
        }
    }
    
    private void createGroup() {
        try {
            wifiDirectService.createGroup();
            updateStatus("Created WiFi Direct group", true);
            logEvent("Group created. Waiting for peers to join...");
            createGroupButton.setDisable(true);
            joinGroupButton.setDisable(true);
        } catch (Exception e) {
            logger.error("Failed to create group", e);
            showError("Failed to create group", e.getMessage());
            logEvent("Error: Failed to create group: " + e.getMessage());
        }
    }
    
    private void joinGroup() {
        WiFiDirectService.PeerDevice selectedPeer = peerListView.getSelectionModel().getSelectedItem();
        if (selectedPeer == null) {
            showError("No peer selected", "Please select a peer to join their group.");
            logEvent("Error: No peer selected to join group.");
            return;
        }
        
        try {
            wifiDirectService.joinGroup(selectedPeer.getAddress());
            updateStatus("Joined WiFi Direct group", true);
            logEvent("Joined group at " + selectedPeer.getAddress() + ". Waiting for connection...");
            createGroupButton.setDisable(true);
            joinGroupButton.setDisable(true);
        } catch (Exception e) {
            logger.error("Failed to join group", e);
            showError("Failed to join group", e.getMessage());
            logEvent("Error: Failed to join group: " + e.getMessage());
        }
    }
    
    private void selectFiles() {
        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Select Files to Send");
        fileChooser.getExtensionFilters().add(
            new FileChooser.ExtensionFilter("All Files", "*.*"));
        
        List<File> selectedFiles = fileChooser.showOpenMultipleDialog(root.getScene().getWindow());
        if (selectedFiles != null && !selectedFiles.isEmpty()) {
            fileListView.getItems().clear();
            fileListView.getItems().addAll(selectedFiles);
            updateSendButtonState();
        }
    }
    
    private void sendFiles() {
        WiFiDirectService.PeerDevice selectedPeer = peerListView.getSelectionModel().getSelectedItem();
        if (selectedPeer == null) {
            showError("No peer selected", "Please select a peer to send files to.");
            return;
        }
        
        List<File> selectedFiles = fileListView.getItems();
        if (selectedFiles.isEmpty()) {
            showError("No files selected", "Please select files to send.");
            return;
        }
        
        // Perform real file transfer
        performFileTransfer(selectedFiles, selectedPeer);
    }
    
    private void performFileTransfer(List<File> files, WiFiDirectService.PeerDevice peer) {
        if (currentConnection == null || currentConnection.isClosed()) {
            showError("No Connection", "Please establish a connection first by creating or joining a group.");
            logEvent("Error: Attempted to send files without a connection.");
            return;
        }
        transferProgressBar.setProgress(0);
        speedLabel.setText("Preparing transfer...");
        logEvent("Starting file transfer to " + peer.getName() + " (" + peer.getAddress() + ")");
        CompletableFuture.runAsync(() -> {
            try {
                long totalSize = files.stream().mapToLong(File::length).sum();
                long transferred = 0;
                for (File file : files) {
                    if (Thread.currentThread().isInterrupted()) break;
                    try {
                        logEvent("Sending file: " + file.getName());
                        fileTransferService.sendFileOverSocket(file, currentConnection);
                        transferred += file.length();
                        double progress = (double) transferred / totalSize;
                        Platform.runLater(() -> {
                            transferProgressBar.setProgress(progress);
                            speedLabel.setText(String.format("Sent: %s (%.1f%%)", file.getName(), progress * 100));
                        });
                        logEvent("File sent: " + file.getName());
                    } catch (Exception e) {
                        Platform.runLater(() -> {
                            showError("Transfer Error", "Error sending " + file.getName() + ": " + e.getMessage());
                        });
                        logEvent("Error: Failed to send file " + file.getName() + ": " + e.getMessage());
                    }
                    Thread.sleep(100);
                }
                Platform.runLater(() -> {
                    transferProgressBar.setProgress(1.0);
                    speedLabel.setText("Transfer completed");
                    showInfo("Transfer Complete", "All files have been sent successfully.");
                });
                logEvent("All files sent successfully.");
            } catch (Exception e) {
                Platform.runLater(() -> {
                    showError("Transfer Error", "Error during file transfer: " + e.getMessage());
                });
                logEvent("Error: File transfer failed: " + e.getMessage());
            }
        });
    }
    
    private void selectDownloadFolder() {
        DirectoryChooser directoryChooser = new DirectoryChooser();
        directoryChooser.setTitle("Select Download Folder");
        
        File selectedFolder = directoryChooser.showDialog(root.getScene().getWindow());
        if (selectedFolder != null) {
            downloadFolder.set(selectedFolder);
            folderLabel.setText("Folder: " + selectedFolder.getAbsolutePath());
            logger.info("Download folder selected: {}", selectedFolder.getAbsolutePath());
            
            // Don't start file receiver immediately - wait for connection
            showInfo("Folder Selected", "Download folder set. Please join a group to start receiving files.");
        }
    }
    
    private void startFileReceiver() {
        File folder = downloadFolder.get();
        if (folder == null) {
            showError("No Folder", "Please select a download folder first.");
            logEvent("Error: No download folder selected.");
            return;
        }
        if (!wifiDirectService.isConnected()) {
            showError("No Connection", "Please establish a connection first by joining a group.");
            logEvent("Error: Attempted to receive files without a connection.");
            return;
        }
        CompletableFuture.runAsync(() -> {
            try {
                logger.info("Starting file receiver using handshake socket");
                logEvent("Listening for incoming files on handshake socket...");
                fileTransferService.receiveFileOverSocket(currentConnection, folder.getAbsolutePath());
                Platform.runLater(() -> {
                    incomingListView.getItems().add("File received successfully");
                    showInfo("File Received", "File received successfully!");
                });
                logEvent("File received successfully.");
            } catch (Exception e) {
                Platform.runLater(() -> {
                    showError("Receive Error", "Error receiving file: " + e.getMessage());
                });
                logEvent("Error: File receive failed: " + e.getMessage());
            }
        });
    }
    
    private void updateSendButtonState() {
        boolean hasPeer = peerListView.getSelectionModel().getSelectedItem() != null;
        boolean hasFiles = !fileListView.getItems().isEmpty();
        boolean hasConnection = currentConnection != null && !currentConnection.isClosed();
        sendFilesButton.setDisable(!hasPeer || !hasFiles || !hasConnection);
    }
    
    // Event handlers for service callbacks
    
    private void onPeerDiscovered(WiFiDirectService.PeerDevice peer) {
        Platform.runLater(() -> {
            // Check for duplicates
            if (!discoveredPeers.containsKey(peer.getAddress())) {
                discoveredPeers.put(peer.getAddress(), peer);
                peerListView.getItems().add(peer);
                logger.info("New peer discovered: {}", peer.getName());
                logEvent("Peer discovered: " + peer.getName() + " (" + peer.getAddress() + ")");
            }
        });
    }
    
    private void onConnectionEstablished(Socket socket) {
        Platform.runLater(() -> {
            currentConnection = socket;
            updateStatus("Connection established with " + socket.getInetAddress(), true);
            updateSendButtonState();
            logEvent("Connection established with peer: " + socket.getInetAddress());
            logEvent("Ready to send/receive files.");
        });
    }
    
    private void onConnectionStatusChanged(boolean connected, String peerAddress) {
        Platform.runLater(() -> {
            if (connected) {
                updateStatus("Connected to " + peerAddress, true);
                logEvent("Handshake complete. Connected to peer: " + peerAddress);
                // Start file receiver only when connection is confirmed
                if (downloadFolder.get() != null) {
                    logEvent("Receiver ready. Waiting for files...");
                    startFileReceiver();
                }
            } else {
                updateStatus("Disconnected", false);
                logEvent("Disconnected from peer.");
            }
        });
    }
    
    private void onTransferProgress(FileTransferService.TransferProgress progress) {
        Platform.runLater(() -> {
            transferProgressBar.setProgress(progress.getProgressPercentage() / 100.0);
            speedLabel.setText(String.format("Speed: %.2f MB/s", progress.getSpeed()));
            logEvent(String.format("Progress: %s %.1f%%, %.2f MB/s", progress.getFileName(), progress.getProgressPercentage(), progress.getSpeed()));
        });
    }
    
    private void onTransferCompleted(FileTransferService.TransferResult result) {
        Platform.runLater(() -> {
            if (result.isSuccess()) {
                showInfo("Transfer Complete", "File transfer completed successfully.");
                logEvent("Transfer completed successfully.");
            } else {
                showError("Transfer Failed", result.getErrorMessage());
                logEvent("Error: Transfer failed: " + result.getErrorMessage());
            }
        });
    }
    
    // Utility methods
    
    private void updateStatus(String message, boolean isConnected) {
        Platform.runLater(() -> {
            statusLabel.setText(message);
            statusLabel.setStyle(isConnected ? "-fx-text-fill: green;" : "-fx-text-fill: red;");
        });
    }
    
    private void showError(String title, String message) {
        Platform.runLater(() -> {
            Alert alert = new Alert(Alert.AlertType.ERROR);
            alert.setTitle(title);
            alert.setHeaderText(null);
            alert.setContentText(message);
            alert.showAndWait();
        });
    }
    
    private void showInfo(String title, String message) {
        Platform.runLater(() -> {
            Alert alert = new Alert(Alert.AlertType.INFORMATION);
            alert.setTitle(title);
            alert.setHeaderText(null);
            alert.setContentText(message);
            alert.showAndWait();
        });
    }
    
    private void logEvent(String message) {
        Platform.runLater(() -> {
            logArea.appendText("[" + java.time.LocalTime.now().withNano(0) + "] " + message + "\n");
        });
    }
    
    public VBox getRoot() {
        return root;
    }
} 