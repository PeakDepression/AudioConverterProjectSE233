package se223.audioconverter.util;

import javafx.application.Platform;
import javafx.scene.control.Alert;

public final class ExceptionHandler {
    public static void showError(String message, Throwable t) {
        Platform.runLater(() -> new Alert(Alert.AlertType.ERROR, message).showAndWait());
        DebugLogger.e(message, t);
    }
}