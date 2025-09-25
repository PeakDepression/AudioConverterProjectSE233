package se223.audioconverter;

import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.stage.Stage;
import se223.audioconverter.service.ConversionService;

public class Launcher extends Application {

    @Override
    public void start(Stage stage) throws Exception {
        FXMLLoader fxmlLoader = new FXMLLoader(
                Launcher.class.getResource("/se223/audioconverter/MainView.fxml")
        );
        Scene scene = new Scene(fxmlLoader.load(), 600, 400);
        stage.setTitle("Audio Converter");
        stage.setScene(scene);
        stage.show();
    }

    @Override
    public void stop() {
        // ensure any background executors are shut down
        ConversionService.getInstance().close();
    }

    public static void main(String[] args) {
        launch();
    }
}
