module se223.audioconverter {
    requires javafx.controls;
    requires javafx.fxml;
    requires java.prefs;

    // allow FXML to access controller classes via reflection
    opens se223.audioconverter.controller to javafx.fxml;
    opens se223.audioconverter to javafx.graphics;

    // make your main package available if needed elsewhere
    exports se223.audioconverter;
    exports se223.audioconverter.controller;
}
