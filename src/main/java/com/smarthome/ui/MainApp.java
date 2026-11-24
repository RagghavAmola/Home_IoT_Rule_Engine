package com.smarthome.ui;

import com.smarthome.config.AppBootstrap;
import javafx.application.Application;
import javafx.scene.Scene;
import javafx.stage.Stage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Main JavaFX application entry point for the Smart Home IoT Rule Engine.
 * Launches the desktop user interface stage with manual dependency injection via {@link AppBootstrap}.
 */
public class MainApp extends Application {

    private static final Logger logger = LoggerFactory.getLogger(MainApp.class);

    private static final String APPLICATION_TITLE = "Smart Home IoT Rule Engine";
    private static final double DEFAULT_WINDOW_WIDTH = 1100.0;
    private static final double DEFAULT_WINDOW_HEIGHT = 740.0;

    private AppBootstrap bootstrap;

    @Override
    public void init() {
        logger.info("Initializing application bootstrap container...");
        this.bootstrap = AppBootstrap.start();
    }

    @Override
    public void start(Stage primaryStage) {
        logger.info("Starting {} desktop UI stage", APPLICATION_TITLE);

        MainPresenter presenter = new MainPresenter(
                bootstrap.getDeviceControlService(),
                bootstrap.getRuleValidationService(),
                bootstrap.getRuleMonitorService()
        );

        MainView mainView = new MainView(presenter);
        Scene mainScene = new Scene(mainView, DEFAULT_WINDOW_WIDTH, DEFAULT_WINDOW_HEIGHT);

        // Load modern stylesheet
        var cssResource = getClass().getResource("/styles/app.css");
        if (cssResource != null) {
            mainScene.getStylesheets().add(cssResource.toExternalForm());
        }

        primaryStage.setTitle(APPLICATION_TITLE);
        primaryStage.setScene(mainScene);
        primaryStage.setMinWidth(920);
        primaryStage.setMinHeight(620);
        primaryStage.show();

        logger.info("{} desktop stage successfully displayed", APPLICATION_TITLE);
    }

    @Override
    public void stop() {
        logger.info("Application stopping: terminating background monitors and executors...");
        if (bootstrap != null) {
            bootstrap.shutdown();
        }
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
