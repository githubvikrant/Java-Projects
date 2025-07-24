package com.music;

import com.music.ui.PlayerUI;
import javafx.application.Application;
import javafx.stage.Stage;

public class Main extends Application {
    @Override
    public void start(Stage primaryStage) {
        PlayerUI playerUI = new PlayerUI();
        playerUI.start(primaryStage);
        // Add shutdown hook for cleanup
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            if (playerUI.getController() != null) {
                playerUI.getController().stopMasterServer();
            }
        }));
    }

    public static void main(String[] args) {
        launch(args);
    }
} 