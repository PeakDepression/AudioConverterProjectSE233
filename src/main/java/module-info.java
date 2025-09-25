module se223.audioconverter {
    requires javafx.controls;
    requires javafx.fxml;

    // allow FXML to access controller classes via reflection
    opens se223.audioconverter.controller to javafx.fxml;

    // make your main package available if needed elsewhere
    exports se223.audioconverter;
}
