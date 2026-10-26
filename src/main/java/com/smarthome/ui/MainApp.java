package com.smarthome.ui;

import javafx.application.Application;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.scene.layout.StackPane;
import javafx.stage.Stage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Main JavaFX application entry point for the Smart Home IoT Rule Engine.
 * Launches the desktop user interface stage.
 */
public class MainApp extends Application {

    private static final Logger logger = LoggerFactory.getLogger(MainApp.class);

    private static final String APPLICATION_TITLE = "Smart Home Rule Engine";
    private static final double DEFAULT_WINDOW_WIDTH = 900.0;
    private static final double DEFAULT_WINDOW_HEIGHT = 600.0;

    @Override
    public void start(Stage primaryStage) {
        logger.info("Initializing {} desktop UI stage", APPLICATION_TITLE);

        StackPane rootLayout = new StackPane();
        Label welcomeLabel = new Label("Smart Home Rule Engine initialized.");
        rootLayout.getChildren().add(welcomeLabel);

        Scene mainScene = new Scene(rootLayout, DEFAULT_WINDOW_WIDTH, DEFAULT_WINDOW_HEIGHT);

        primaryStage.setTitle(APPLICATION_TITLE);
        primaryStage.setScene(mainScene);
        primaryStage.show();

        logger.info("{} desktop stage successfully displayed", APPLICATION_TITLE);
    }

    /**
     * Desktop application entry point.
     *
     * @param args command-line arguments
     */
    public static void main(String[] args) {
        logger.info("Starting Smart Home IoT Rule Engine application launcher");
        launch(args);
    }
}
