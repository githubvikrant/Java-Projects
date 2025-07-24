package com.fileshare;

import com.fileshare.ui.MainWindow;
import javafx.application.Application;
import javafx.scene.Scene;
import javafx.stage.Stage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Main extends Application {
    private static final Logger logger = LoggerFactory.getLogger(Main.class);
    
    @Override
    public void start(Stage primaryStage) {
        try {
            logger.info("Starting WiFi Direct File Share application...");
            
            // Create main window with proper UI
            MainWindow mainWindow = new MainWindow();
            Scene scene = new Scene(mainWindow.getRoot(), 1000, 700);
            
            // Configure primary stage
            primaryStage.setTitle("WiFi Direct File Share");
            primaryStage.setScene(scene);
            primaryStage.setMinWidth(800);
            primaryStage.setMinHeight(600);
            primaryStage.show();
            
            logger.info("Application started successfully");
            
        } catch (Exception e) {
            logger.error("Failed to start application", e);
            System.exit(1);
        }
    }
    
    @Override
    public void stop() {
        logger.info("Shutting down WiFi Direct File Share application...");
        System.exit(0);
    }
    
    public static void main(String[] args) {
        logger.info("Launching WiFi Direct File Share...");
        launch(args);
    }
} 