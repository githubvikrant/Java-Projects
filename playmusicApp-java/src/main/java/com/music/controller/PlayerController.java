package com.music.controller;

import com.music.network.MasterServer;
import com.music.network.SlaveClient;
import javafx.application.Platform;
import javafx.scene.media.MediaPlayer;
import java.io.File;
import java.util.List;
import java.util.function.Consumer;

public class PlayerController {
    private MasterServer masterServer;
    private SlaveClient slaveClient;
    private MediaPlayer mediaPlayer;
    private boolean isMaster;
    private Consumer<String> logCallback;
    private Consumer<List<String>> deviceListCallback;
    private Consumer<File> slaveFileReceivedCallback;
    private Consumer<String> slaveStatusCallback;

    public PlayerController(boolean isMaster) {
        this.isMaster = isMaster;
        if (isMaster) {
            masterServer = new MasterServer(this);
        } else {
            slaveClient = new SlaveClient(this);
        }
    }

    // UI can register a log callback
    public void setLogCallback(Consumer<String> logCallback) {
        this.logCallback = logCallback;
    }
    public void setDeviceListCallback(Consumer<List<String>> deviceListCallback) {
        this.deviceListCallback = deviceListCallback;
    }
    public void setSlaveFileReceivedCallback(Consumer<File> cb) { this.slaveFileReceivedCallback = cb; }
    public void setSlaveStatusCallback(Consumer<String> cb) { this.slaveStatusCallback = cb; }

    public void log(String msg) {
        if (logCallback != null) Platform.runLater(() -> logCallback.accept(msg));
    }
    public void updateDeviceList(List<String> devices) {
        if (deviceListCallback != null) Platform.runLater(() -> deviceListCallback.accept(devices));
    }
    public void notifySlaveFileReceived(File file) {
        if (slaveFileReceivedCallback != null) Platform.runLater(() -> slaveFileReceivedCallback.accept(file));
    }
    public void notifySlaveStatus(String status) {
        if (slaveStatusCallback != null) Platform.runLater(() -> slaveStatusCallback.accept(status));
        if (logCallback != null) Platform.runLater(() -> logCallback.accept(status));
    }

    // Master methods
    public void startMaster(int port) {
        if (masterServer != null) masterServer.start(port);
    }
    public void sendFileToSlaves(File file) {
        if (masterServer != null) masterServer.sendFileToSlaves(file);
    }
    public void sendCommandToSlaves(String command, double position) {
        if (masterServer != null) masterServer.sendCommandToSlaves(command, position);
    }
    public List<String> getConnectedSlaves() {
        if (masterServer != null) return masterServer.getConnectedSlaves();
        return null;
    }

    // Slave methods
    public void connectToMaster(String ip, int port) {
        if (slaveClient != null) slaveClient.connect(ip, port);
    }
    public void disconnectFromMaster() {
        if (slaveClient != null) slaveClient.disconnect();
    }

    // MediaPlayer management
    public void setMediaPlayer(MediaPlayer player) {
        this.mediaPlayer = player;
    }
    public MediaPlayer getMediaPlayer() {
        return mediaPlayer;
    }

    public void stopMasterServer() {
        if (isMaster && masterServer != null) {
            masterServer.stop();
        }
    }
} 