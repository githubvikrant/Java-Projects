package com.music.ui;

import javafx.application.Platform;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.stage.FileChooser;
import javafx.stage.Stage;
import javafx.scene.media.Media;
import javafx.scene.media.MediaPlayer;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import com.music.controller.PlayerController;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.util.Collections;

public class PlayerUI {
    private MediaPlayer mediaPlayer;
    private boolean isMaster = false;
    private Stage mainStage;
    private PlayerController controller;
    // Networking stubs
    private int serverPort = 5000;
    private List<String> connectedSlaves = new ArrayList<>();
    private boolean isConnected = false;
    private File currentFile = null;
    private Label statusLabel = new Label("");
    private TextArea logArea = new TextArea();
    private ListView<String> deviceListView = new ListView<>();
    private double currentVolume = 0.7;

    public void start(Stage primaryStage) {
        this.mainStage = primaryStage;
        mainStage.setOnCloseRequest(e -> {
            if (controller != null) {
                if (isMaster) {
                    controller.stopMasterServer();
                } else {
                    controller.disconnectFromMaster();
                }
            }
            Platform.exit();
            System.exit(0);
        });
        showStartupScreen();
    }

    private String getLocalIp() {
        try {
            for (NetworkInterface ni : Collections.list(NetworkInterface.getNetworkInterfaces())) {
                if (!ni.isUp() || ni.isLoopback() || ni.isVirtual()) continue;
                for (InetAddress addr : Collections.list(ni.getInetAddresses())) {
                    if (!addr.isLoopbackAddress() && addr.getHostAddress().indexOf(':') < 0) {
                        return addr.getHostAddress();
                    }
                }
            }
        } catch (Exception e) {
            return "Unknown";
        }
        return "Unknown";
    }

    private void appendLog(String msg) {
        Platform.runLater(() -> logArea.appendText(msg + "\n"));
    }

    private void showStartupScreen() {
        Label instructions = new Label(
                "Welcome to PlayMusic!\n\n" +
                        "Please ensure all devices are on the same Wi-Fi network,\n" +
                        "or connect to the master device's Wi-Fi hotspot.\n\n" +
                        "If you have trouble connecting, check your Windows Firewall settings and ensure the app is allowed through the firewall.\n" +
                        "If auto-discovery fails, enter the IP and port manually.\n");
        Button continueBtn = new Button("Continue");
        continueBtn.setOnAction(e -> showRoleSelectionScreen());
        VBox layout = new VBox(20, instructions, continueBtn);
        layout.setAlignment(Pos.CENTER);
        mainStage.setScene(new Scene(layout, 400, 250));
        mainStage.setTitle("PlayMusic - Startup");
        mainStage.show();
    }

    private void showRoleSelectionScreen() {
        Label label = new Label("Choose your role:");
        Button masterBtn = new Button("Master");
        Button slaveBtn = new Button("Slave");
        Button backBtn = new Button("Back");

        masterBtn.setOnAction(e -> {
            isMaster = true;
            showMasterScreen();
        });
        slaveBtn.setOnAction(e -> {
            isMaster = false;
            showSlaveScreen();
        });
        backBtn.setOnAction(e -> showStartupScreen());

        VBox layout = new VBox(20, label, masterBtn, slaveBtn, backBtn);
        layout.setAlignment(Pos.CENTER);
        mainStage.setScene(new Scene(layout, 400, 250));
        mainStage.setTitle("PlayMusic - Select Role");
    }

    private void showMasterScreen() {
        controller = new PlayerController(true);
        controller.setLogCallback(this::appendLog);
        controller.setDeviceListCallback(devices -> {
            Platform.runLater(() -> deviceListView.getItems().setAll(devices));
        });
        controller.startMaster(serverPort);
        logArea.setEditable(false);
        logArea.setPrefHeight(120);
        logArea.setWrapText(true);
        deviceListView.setPrefHeight(80);
        Label titleLabel = new Label("PlayMusic - Master Mode");
        titleLabel.setStyle("-fx-font-size: 22px; -fx-font-weight: bold; -fx-text-fill: #2c3e50;");
        String localIp = getLocalIp();
        Label ipLabel = new Label("Your IP: " + localIp);
        Label portLabel = new Label("Port: " + serverPort);
        VBox networkBox = new VBox(5, ipLabel, portLabel, new Label("Connected Slaves:"), deviceListView);
        networkBox.setAlignment(Pos.CENTER_LEFT);
        Label fileLabel = new Label("No file selected.");
        fileLabel.setStyle("-fx-font-size: 14px; -fx-text-fill: #555;");
        Button selectFileBtn = new Button("🎵 Select MP3");
        selectFileBtn.setStyle("-fx-font-size: 14px; -fx-background-color: #3498db; -fx-text-fill: white;");
        selectFileBtn.setOnAction(e -> {
            FileChooser fileChooser = new FileChooser();
            fileChooser.getExtensionFilters().addAll(
                new FileChooser.ExtensionFilter("MP3 Files", "*.mp3"),
                new FileChooser.ExtensionFilter("MP4 Files", "*.mp4")
            );
            File file = fileChooser.showOpenDialog(mainStage);
            if (file != null) {
                fileLabel.setText("Selected: " + file.getName());
                currentFile = file;
                if (mediaPlayer != null) {
                    mediaPlayer.dispose();
                }
                try {
                    Media media = new Media(file.toURI().toString());
                    mediaPlayer = new MediaPlayer(media);
                    mediaPlayer.setVolume(currentVolume);
                    mediaPlayer.setOnError(() -> {
                        showErrorDialog("Playback Error", "Unsupported file format or playback error. Please use MP3 or MP4.");
                        statusLabel.setText("Playback error.");
                        appendLog("Playback error: " + media.getError());
                    });
                    controller.setMediaPlayer(mediaPlayer);
                    statusLabel.setText("Sending file to slaves...");
                    controller.sendFileToSlaves(file);
                    statusLabel.setText("File sent to slaves.");
                    controller.log("File sent: " + file.getName());
                } catch (Exception ex) {
                    showErrorDialog("File Error", "Could not load file: " + ex.getMessage());
                    statusLabel.setText("File load error.");
                    appendLog("File load error: " + ex.getMessage());
                }
            }
        });
        Button playBtn = new Button("▶ Play");
        Button pauseBtn = new Button("⏸ Pause");
        Button stopBtn = new Button("⏹ Stop");
        playBtn.setStyle("-fx-font-size: 14px; -fx-background-color: #27ae60; -fx-text-fill: white;");
        pauseBtn.setStyle("-fx-font-size: 14px; -fx-background-color: #f39c12; -fx-text-fill: white;");
        stopBtn.setStyle("-fx-font-size: 14px; -fx-background-color: #e74c3c; -fx-text-fill: white;");
        playBtn.setOnAction(e -> {
            if (mediaPlayer != null) {
                mediaPlayer.play();
                controller.sendCommandToSlaves("PLAY", mediaPlayer.getCurrentTime().toSeconds());
                statusLabel.setText("Play command sent to slaves.");
                controller.log("Play command sent");
            }
        });
        pauseBtn.setOnAction(e -> {
            if (mediaPlayer != null) {
                mediaPlayer.pause();
                controller.sendCommandToSlaves("PAUSE", mediaPlayer.getCurrentTime().toSeconds());
                statusLabel.setText("Pause command sent to slaves.");
                controller.log("Pause command sent");
            }
        });
        stopBtn.setOnAction(e -> {
            if (mediaPlayer != null) {
                mediaPlayer.stop();
                controller.sendCommandToSlaves("STOP", 0);
                statusLabel.setText("Stop command sent to slaves.");
                controller.log("Stop command sent");
            }
        });
        // Volume slider
        Label volumeLabel = new Label("Volume:");
        Slider volumeSlider = new Slider(0, 1, currentVolume);
        volumeSlider.setShowTickLabels(true);
        volumeSlider.setShowTickMarks(true);
        volumeSlider.setMajorTickUnit(0.25);
        volumeSlider.setMinorTickCount(4);
        volumeSlider.setBlockIncrement(0.01);
        volumeSlider.valueProperty().addListener((obs, oldVal, newVal) -> {
            currentVolume = newVal.doubleValue();
            if (mediaPlayer != null) {
                mediaPlayer.setVolume(currentVolume);
            }
            controller.sendCommandToSlaves("VOLUME", currentVolume);
            appendLog("Volume set to: " + String.format("%.2f", currentVolume));
        });
        HBox controls = new HBox(15, playBtn, pauseBtn, stopBtn, volumeLabel, volumeSlider);
        controls.setAlignment(Pos.CENTER);
        VBox musicBox = new VBox(10, fileLabel, selectFileBtn, controls);
        musicBox.setAlignment(Pos.CENTER);
        VBox centerBox = new VBox(20, titleLabel, networkBox, musicBox, statusLabel, new Label("Log:"), logArea);
        centerBox.setAlignment(Pos.CENTER);
        Button backBtn = new Button("← Back");
        backBtn.setStyle("-fx-font-size: 13px; -fx-background-color: #bdc3c7; -fx-text-fill: #2c3e50;");
        backBtn.setOnAction(e -> showRoleSelectionScreen());
        BorderPane root = new BorderPane();
        root.setCenter(centerBox);
        root.setBottom(backBtn);
        BorderPane.setAlignment(backBtn, Pos.CENTER);
        root.setStyle("-fx-background-color: linear-gradient(to bottom right, #f8fafc, #d6eaff); -fx-padding: 30px;");
        mainStage.setScene(new Scene(root, 540, 600));
        mainStage.setTitle("PlayMusic - Master");
    }

    private void showSlaveScreen() {
        controller = new PlayerController(false);
        controller.setLogCallback(this::appendLog);
        controller.setSlaveFileReceivedCallback(file -> {
            Platform.runLater(() -> {
                appendLog("Received file from master: " + file.getAbsolutePath());
                if (mediaPlayer != null) mediaPlayer.dispose();
                try {
                    Media media = new Media(file.toURI().toString());
                    mediaPlayer = new MediaPlayer(media);
                    mediaPlayer.setOnError(() -> {
                        showErrorDialog("Playback Error", "Unsupported file format or playback error. Please use MP3 or MP4.");
                        statusLabel.setText("Playback error.");
                        appendLog("Playback error: " + media.getError());
                    });
                    mediaPlayer.setOnReady(() -> appendLog("Slave: Media ready for playback."));
                    mediaPlayer.setOnPlaying(() -> appendLog("Slave: Playback started."));
                    mediaPlayer.setOnEndOfMedia(() -> appendLog("Slave: Playback finished."));
                    controller.setMediaPlayer(mediaPlayer);
                    statusLabel.setText("File received from master: " + file.getName());
                    appendLog("File received: " + file.getName());
                } catch (Exception ex) {
                    showErrorDialog("File Error", "Could not load file: " + ex.getMessage());
                    statusLabel.setText("File load error.");
                    appendLog("File load error: " + ex.getMessage());
                }
            });
        });
        controller.setSlaveStatusCallback(status -> {
            Platform.runLater(() -> {
                statusLabel.setText(status);
                appendLog("Status: " + status);
                if (status.startsWith("PLAY")) {
                    if (mediaPlayer != null) {
                        String[] parts = status.split(": ");
                        double pos = 0;
                        if (parts.length > 1) {
                            try { pos = Double.parseDouble(parts[1]); } catch (Exception ignored) {}
                        }
                        mediaPlayer.seek(javafx.util.Duration.seconds(pos));
                        mediaPlayer.play();
                        appendLog("Slave: Received PLAY command, seeking to " + pos + " and starting playback.");
                    } else {
                        showErrorDialog("Playback Error", "No media loaded to play.");
                        appendLog("Slave: PLAY command received but no media loaded.");
                    }
                } else if (status.startsWith("PAUSE")) {
                    if (mediaPlayer != null) {
                        mediaPlayer.pause();
                        appendLog("Slave: Received PAUSE command, pausing playback.");
                    }
                } else if (status.startsWith("STOP")) {
                    if (mediaPlayer != null) {
                        mediaPlayer.stop();
                        appendLog("Slave: Received STOP command, stopping playback.");
                    }
                } else if (status.startsWith("VOLUME")) {
                    if (mediaPlayer != null) {
                        String[] parts = status.split(": ");
                        double vol = 0.7;
                        if (parts.length > 1) {
                            try { vol = Double.parseDouble(parts[1]); } catch (Exception ignored) {}
                        }
                        mediaPlayer.setVolume(vol);
                        appendLog("Slave: Volume set to " + String.format("%.2f", vol));
                    }
                }
            });
        });
        logArea.setEditable(false);
        logArea.setPrefHeight(120);
        logArea.setWrapText(true);
        Label titleLabel = new Label("PlayMusic - Slave Mode");
        titleLabel.setStyle("-fx-font-size: 22px; -fx-font-weight: bold; -fx-text-fill: #2c3e50;");
        Label instructions = new Label(
                "How to connect to the Master:\n" +
                        "1. Ask the Master user for their IP address and port (shown on their screen).\n" +
                        "2. Enter the IP address and port below.\n" +
                        "3. Click 'Connect'.\n\n" +
                        "Once connected, this device will play music in sync with the Master.");
        instructions.setStyle("-fx-font-size: 13px; -fx-text-fill: #555;");
        Label ipLabel = new Label("Master IP Address:");
        TextField ipField = new TextField();
        ipField.setPromptText("e.g. 192.168.1.10");
        ipField.setMaxWidth(200);
        Label portLabel = new Label("Port:");
        TextField portField = new TextField(String.valueOf(serverPort));
        portField.setPromptText("e.g. 5000");
        portField.setMaxWidth(100);
        statusLabel.setText("Not connected.");
        Button connectBtn = new Button("Connect");
        Button disconnectBtn = new Button("Disconnect");
        disconnectBtn.setDisable(true);
        Label fileLabel = new Label("No file selected.");
        fileLabel.setStyle("-fx-font-size: 14px; -fx-text-fill: #555;");
        Button selectFileBtn = new Button("🎵 Select MP3");
        selectFileBtn.setStyle("-fx-font-size: 14px; -fx-background-color: #3498db; -fx-text-fill: white;");
        selectFileBtn.setDisable(true); // Slaves cannot select file
        connectBtn.setOnAction(e -> {
            String ip = ipField.getText();
            int port = Integer.parseInt(portField.getText());
            controller.connectToMaster(ip, port);
            statusLabel.setText("Connecting to master...");
            connectBtn.setDisable(true);
            disconnectBtn.setDisable(false);
            appendLog("Connecting to master at " + ip + ":" + port);
            // Show 'Connected' after a short delay to allow connection to establish
            new Thread(() -> {
                try {
                    Thread.sleep(1000); // Wait for connection attempt
                } catch (InterruptedException ignored) {}
                Platform.runLater(() -> {
                    if (!statusLabel.getText().startsWith("Connection failed")) {
                        statusLabel.setText("Connected to master");
                        appendLog("Connected to master at " + ip + ":" + port);
                    }
                });
            }).start();
        });
        disconnectBtn.setOnAction(e -> {
            controller.disconnectFromMaster();
            statusLabel.setText("Not connected.");
            connectBtn.setDisable(false);
            disconnectBtn.setDisable(true);
            appendLog("Disconnected from master");
        });
        Button backBtn = new Button("← Back");
        backBtn.setOnAction(e -> showRoleSelectionScreen());
        VBox layout = new VBox(15, titleLabel, instructions, ipLabel, ipField, portLabel, portField, selectFileBtn, fileLabel, connectBtn,
                disconnectBtn, statusLabel, new Label("Log:"), logArea, backBtn);
        layout.setAlignment(Pos.CENTER);
        layout.setStyle("-fx-background-color: linear-gradient(to bottom right, #f8fafc, #d6eaff); -fx-padding: 30px;");
        mainStage.setScene(new Scene(layout, 540, 700));
        mainStage.setTitle("PlayMusic - Slave");
    }

    private void showErrorDialog(String title, String message) {
        Platform.runLater(() -> {
            Alert alert = new Alert(Alert.AlertType.ERROR);
            alert.setTitle(title);
            alert.setHeaderText(null);
            alert.setContentText(message);
            alert.showAndWait();
        });
    }

    public PlayerController getController() {
        return controller;
    }
    public void stopMasterServer() {
        if (controller != null) {
            controller.stopMasterServer();
        }
    }
} 