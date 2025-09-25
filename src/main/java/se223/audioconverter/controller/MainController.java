package se223.audioconverter.controller;

import javafx.application.Platform;
import javafx.beans.binding.Bindings;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.input.DragEvent;
import javafx.scene.input.Dragboard;
import javafx.scene.input.TransferMode;
import javafx.stage.DirectoryChooser;
import javafx.stage.FileChooser;
import javafx.stage.Stage;
import se223.audioconverter.core.ProgressCallback;
import se223.audioconverter.model.*;
import se223.audioconverter.service.ConversionService;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

public class MainController {

    // Left panel
    @FXML private ListView<String> fileListView;
    @FXML private Button convertButton;
    @FXML private ProgressBar progressBar;
    @FXML private Label statusLabel;

    // Right panel
    @FXML private MenuButton formatMenu;
    @FXML private ComboBox<Integer> sampleRateCombo;
    @FXML private RadioButton monoRadio;
    @FXML private RadioButton stereoRadio;

    // NEW: bitrate controls
    @FXML private Slider bitrateSlider;
    @FXML private Label bitrateValueLabel;

    // state
    private final ToggleGroup channelGroup = new ToggleGroup();
    private String selectedFormat = "MP3"; // default

    // singleton service
    private final ConversionService service = ConversionService.getInstance();

    // Track whether a conversion is running; used to disable UI via binding
    private final BooleanProperty converting = new SimpleBooleanProperty(false);

    private static final Set<String> ALLOWED =
            Set.of("mp3","wav","m4a","flac","aac","ogg","mp4","m4b");

    @FXML
    private void initialize() {
        // list setup
        fileListView.setCellFactory(lv -> new ListCell<>() {
            @Override protected void updateItem(String path, boolean empty) {
                super.updateItem(path, empty);
                setText(empty || path == null ? null : Path.of(path).getFileName().toString());
                setTooltip(empty || path == null ? null : new Tooltip(path));
            }
        });
        // multiple selection
        fileListView.getSelectionModel().setSelectionMode(SelectionMode.MULTIPLE);
        fileListView.setOnMouseClicked(e -> { if (e.getClickCount() == 2) handleOpenFile(); });

        // disable Convert when list empty OR while converting
        convertButton.disableProperty().bind(
                Bindings.or(Bindings.isEmpty(fileListView.getItems()), converting)
        );

        // sample rates
        sampleRateCombo.getItems().setAll(44_100, 48_000);
        sampleRateCombo.getSelectionModel().select(Integer.valueOf(44_100));

        // channels
        monoRadio.setToggleGroup(channelGroup);
        stereoRadio.setToggleGroup(channelGroup);
        stereoRadio.setSelected(true);

        // defaults
        formatMenu.setText(selectedFormat);
        progressBar.setProgress(0);
        if (statusLabel != null) statusLabel.setText("Ready");

        // --- Bitrate slider wiring ---
        if (bitrateSlider != null && bitrateValueLabel != null) {
            // initialise label
            bitrateValueLabel.setText(((int) bitrateSlider.getValue()) + " kbps");

            // update label on change
            bitrateSlider.valueProperty().addListener((obs, ov, nv) ->
                    bitrateValueLabel.setText(nv.intValue() + " kbps"));
        }

        // ensure slider enabled/disabled matches initial format
        updateBitrateEnabledByFormat();
    }

    // ---- File picking ----
    @FXML
    private void handleOpenFile() {
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Choose audio files");
        chooser.getExtensionFilters().add(
                new FileChooser.ExtensionFilter("Audio",
                        "*.mp3","*.wav","*.m4a","*.flac","*.aac","*.ogg","*.mp4")
        );
        Stage stage = (Stage) fileListView.getScene().getWindow();
        List<File> files = chooser.showOpenMultipleDialog(stage);
        if (files != null) {
            files.forEach(this::tryAddFile);
            updateStatusCount();
        }
    }

    @FXML private void handleDeleteSelected() {
        var selected = fileListView.getSelectionModel().getSelectedItems();
        fileListView.getItems().removeAll(List.copyOf(selected));
        updateStatusCount();
    }

    @FXML private void handleClearList() {
        fileListView.getItems().clear();
        progressBar.setProgress(0);
        if (statusLabel != null) statusLabel.setText("Ready");
    }

    @FXML private void handleClose() {
        ((Stage) fileListView.getScene().getWindow()).close();
    }

    @FXML private void handleAbout() {
        new Alert(Alert.AlertType.INFORMATION,
                "Audio Converter (UI demo)\nDrag files or use File → Open…").showAndWait();
    }

    // ---- Drag & drop on ListView ----
    @FXML private void handleDragOver(DragEvent e) {
        Dragboard db = e.getDragboard();
        if (db.hasFiles() && db.getFiles().stream().anyMatch(this::isAllowed)) {
            e.acceptTransferModes(TransferMode.COPY);
        }
        e.consume();
    }

    @FXML private void handleDragDropped(DragEvent e) {
        Dragboard db = e.getDragboard();
        boolean ok = false;
        if (db.hasFiles()) {
            db.getFiles().forEach(this::tryAddFile);
            ok = true;
            updateStatusCount();
        }
        e.setDropCompleted(ok);
        e.consume();
    }

    // ---- Audio Format menu handlers ----
    @FXML private void selectFormatMp3()  { setFormat("MP3"); }
    @FXML private void selectFormatWav()  { setFormat("WAV"); }
    @FXML private void selectFormatM4a()  { setFormat("M4A"); }
    @FXML private void selectFormatFlac() { setFormat("FLAC"); }

    private void setFormat(String fmt) {
        selectedFormat = fmt;
        formatMenu.setText(fmt);
        updateBitrateEnabledByFormat(); // toggle slider appropriately
    }

    /** Disable bitrate for formats where it’s not applicable. */
    private void updateBitrateEnabledByFormat() {
        if (bitrateSlider == null || bitrateValueLabel == null) return;

        boolean bitrateApplies = selectedFormat.equals("MP3") || selectedFormat.equals("M4A");
        bitrateSlider.setDisable(!bitrateApplies);
        bitrateValueLabel.setDisable(!bitrateApplies);

        if (!bitrateApplies) {
            bitrateValueLabel.setText("—");
        } else {
            bitrateValueLabel.setText(((int) bitrateSlider.getValue()) + " kbps");
        }
    }

    // ---- Convert using Mock service for now ----
    @FXML
    private void handleConvert() {
        if (fileListView.getItems().isEmpty()) {
            new Alert(Alert.AlertType.WARNING, "No files selected.").showAndWait();
            return;
        }

        // === Ask user where to save ===
        DirectoryChooser chooser = new DirectoryChooser();
        chooser.setTitle("Choose Output Folder");
        File chosenDir = chooser.showDialog(fileListView.getScene().getWindow());
        if (chosenDir == null) {
            // user cancelled
            return;
        }
        Path outDir = chosenDir.toPath();

        // Build settings (POJO)
        var settings = new ConversionSettings();
        settings.setFormat(AudioFormat.valueOf(selectedFormat));
        settings.setSampleRateHz(sampleRateCombo.getValue() == null ? 44_100 : sampleRateCombo.getValue());
        settings.setChannels(channelGroup.getSelectedToggle() == monoRadio ? Channels.MONO : Channels.STEREO);

        // Bitrate only for lossy formats (MP3/M4A); null for WAV/FLAC
        if (selectedFormat.equals("MP3") || selectedFormat.equals("M4A")) {
            settings.setBitrateKbps((int) bitrateSlider.getValue());
        } else {
            settings.setBitrateKbps(null);
        }

        // Build requests
        List<ConversionRequest> requests = fileListView.getItems().stream()
                .map(Path::of)
                .map(p -> new ConversionRequest(p, outDir, settings))
                .collect(Collectors.toList());

        // Progress callback
        ProgressCallback cb = (fileName, progress, index, total) -> Platform.runLater(() -> {
            progressBar.setProgress(progress);
            if (statusLabel != null) {
                int percent = (int) Math.round(progress * 100);
                statusLabel.setText("Processing " + index + "/" + total + " — " + percent + "%");
            }
        });

        // Fire it (service returns a CompletableFuture; errors surface in exceptionally)
        converting.set(true);
        progressBar.setProgress(0);
        if (statusLabel != null) statusLabel.setText("Starting…");

        service.convert(requests, cb)
                .thenAccept(results -> Platform.runLater(() -> {
                    progressBar.setProgress(1.0);
                    if (statusLabel != null) statusLabel.setText("Done: " + results.size() + " file(s)");
                    converting.set(false);
                }))
                .exceptionally(ex -> {
                    Platform.runLater(() -> {
                        new Alert(Alert.AlertType.ERROR, ex.getMessage()).showAndWait();
                        if (statusLabel != null) statusLabel.setText("Failed");
                        converting.set(false);
                    });
                    return null;
                });
    }


    // ---- Helpers ----
    private boolean isAllowed(File f) {
        String name = f.getName().toLowerCase();
        int dot = name.lastIndexOf('.');
        return dot > 0 && ALLOWED.contains(name.substring(dot + 1));
    }

    private void tryAddFile(File f) {
        if (isAllowed(f)) {
            String p = f.getAbsolutePath();
            if (!fileListView.getItems().contains(p)) fileListView.getItems().add(p);
        }
    }

    private void updateStatusCount() {
        if (statusLabel != null) {
            statusLabel.setText(fileListView.getItems().size() + " file(s) queued");
        }
    }
}
