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
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;
import java.util.prefs.Preferences;

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

    @FXML private ComboBox<OverwritePolicy> overwriteCombo;

    @FXML private Label concurrencyLabel;

    // How the slider should be interpreted for the current format
    private enum SliderMode { BITRATE_KBPS, SAMPLE_RATE_KHZ, DISABLED }
    private SliderMode sliderMode = SliderMode.BITRATE_KBPS; // default for MP3

    // state
    private final ToggleGroup channelGroup = new ToggleGroup();
    private String selectedFormat = "MP3"; // default

    // singleton service
    private final ConversionService service = ConversionService.getInstance();

    // Track whether a conversion is running; used to disable UI via binding
    private final BooleanProperty converting = new SimpleBooleanProperty(false);

    //only warn once per app run
    private static final AtomicBoolean FF_WARN_SHOWN = new AtomicBoolean(false);

    private static final Set<String> ALLOWED =
            Set.of("mp3","wav","m4a","flac","aac","ogg","mp4","m4b");

    private final Preferences prefs = Preferences.userNodeForPackage(MainController.class);
    private static final String KEY_LAST_OUT = "lastOutputDir";

    @FXML
    private void initialize() {
        if (!service.isUsingFFmpeg() && FF_WARN_SHOWN.compareAndSet(false, true)) {
            Alert a = new Alert(Alert.AlertType.WARNING);
            a.setTitle("FFmpeg not found");
            a.setHeaderText("FFmpeg/ffprobe not detected");
            a.setContentText(
                    """
                    Conversions will run in MOCK mode (no real audio processing).
                    
                    To enable real conversion, do one of the following:
                    • Put ffmpeg.exe and ffprobe.exe in ./ffmpeg/bin next to the app
                    • OR add FFmpeg to your PATH
                    • OR set the FFMPEG_HOME environment variable to your FFmpeg folder
                    
                    (You only see this once per run.)
                    """);
            a.show();
        }

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

        int workers = Math.max(2, Runtime.getRuntime().availableProcessors()/2);
        if (concurrencyLabel != null) concurrencyLabel.setText("Workers: " + workers);

        overwriteCombo.getItems().setAll(OverwritePolicy.values());
        overwriteCombo.getSelectionModel().select(OverwritePolicy.RENAME);

        // sample rates
        sampleRateCombo.getItems().setAll(8000, 11025, 16000, 22050, 32000, 44100, 48000, 88200, 96000);
        sampleRateCombo.getSelectionModel().select(Integer.valueOf(8000));

        // channels
        monoRadio.setToggleGroup(channelGroup);
        stereoRadio.setToggleGroup(channelGroup);
        stereoRadio.setSelected(true);

        // defaults
        formatMenu.setText(selectedFormat);
        progressBar.setProgress(0);
        if (statusLabel != null) statusLabel.setText("Ready");

        // --- Bitrate slider wiring ---
        // --- Slider label wiring (format-aware) ---
        if (bitrateSlider != null && bitrateValueLabel != null) {
            bitrateSlider.valueProperty().addListener((obs, ov, nv) -> {
                switch (sliderMode) {
                    case BITRATE_KBPS -> bitrateValueLabel.setText(String.format("%d kbps", nv.intValue()));
                    case SAMPLE_RATE_KHZ -> bitrateValueLabel.setText(String.format("%.1f kHz", nv.doubleValue()));
                    default -> bitrateValueLabel.setText("—");
                }
            });
        }

        // ensure slider enabled/disabled matches initial format
        configureSliderForFormat();
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
            files.forEach(this::tryAddFileOrFolder);
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
            db.getFiles().forEach(this::tryAddFileOrFolder);
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
        configureSliderForFormat(); // toggle slider appropriately
    }

    /** Disable bitrate for formats where it’s not applicable.
     Configure the slider based on the selected format. */
    private void configureSliderForFormat() {
        if (bitrateSlider == null || bitrateValueLabel == null) return;

        switch (selectedFormat) {
            case "MP3", "M4A" -> {
                sliderMode = SliderMode.BITRATE_KBPS;
                bitrateSlider.setDisable(false);
                // sensible bitrate range
                bitrateSlider.setMin(64);
                bitrateSlider.setMax(320);
                bitrateSlider.setMajorTickUnit(64);
                bitrateSlider.setMinorTickCount(3);
                bitrateSlider.setBlockIncrement(32);
                if (bitrateSlider.getValue() < 64 || bitrateSlider.getValue() > 320) {
                    bitrateSlider.setValue(192);
                }
                bitrateValueLabel.setDisable(false);
                bitrateValueLabel.setText(((int) bitrateSlider.getValue()) + " kbps");

                // Sample-rate ComboBox is relevant for these formats
                sampleRateCombo.setDisable(false);
            }
            case "WAV" -> {
                sliderMode = SliderMode.SAMPLE_RATE_KHZ;
                bitrateSlider.setDisable(false);

                // WAV: Only 4 fixed increments — 20, 44.1, 48, and 96 kHz
                bitrateSlider.setMin(20);
                bitrateSlider.setMax(96);
                bitrateSlider.setMajorTickUnit(24);
                bitrateSlider.setMinorTickCount(0);
                bitrateSlider.setBlockIncrement(1);
                bitrateSlider.setSnapToTicks(true);

                // Snap to one of the fixed presets
                double v = bitrateSlider.getValue();
                double[] presets = {20.0, 44.1, 48.0, 96.0};
                double nearest = presets[0];
                for (double p : presets) {
                    if (Math.abs(v - p) < Math.abs(v - nearest)) nearest = p;
                }
                bitrateSlider.setValue(nearest);

                // Label formatting with one decimal point (if needed)
                bitrateValueLabel.setDisable(false);
                bitrateValueLabel.setText(String.format("%.1f kHz", bitrateSlider.getValue()));

                // While WAV is selected, slider controls SR, disable ComboBox
                sampleRateCombo.setDisable(true);
            }
            case "FLAC" -> {
                // keep simple for now: slider unused; SR combo still applies
                sliderMode = SliderMode.DISABLED;
                bitrateSlider.setDisable(true);
                bitrateValueLabel.setDisable(true);
                bitrateValueLabel.setText("—");
                sampleRateCombo.setDisable(false);
            }
            default -> {
                sliderMode = SliderMode.DISABLED;
                bitrateSlider.setDisable(true);
                bitrateValueLabel.setDisable(true);
                bitrateValueLabel.setText("—");
                sampleRateCombo.setDisable(false);
            }
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
        String last = prefs.get(KEY_LAST_OUT, null);
        if (last != null) {
            File lastDir = new File(last);
            if (lastDir.isDirectory()) chooser.setInitialDirectory(lastDir);
        }
        File chosenDir = chooser.showDialog(fileListView.getScene().getWindow());
        if (chosenDir == null) return;
        prefs.put(KEY_LAST_OUT, chosenDir.getAbsolutePath());
        Path outDir = chosenDir.toPath();

        // Build settings (POJO)
        var settings = new ConversionSettings();
        settings.setFormat(AudioFormat.valueOf(selectedFormat));
        settings.setChannels(channelGroup.getSelectedToggle() == monoRadio ? Channels.MONO : Channels.STEREO);
        settings.setOverwritePolicy(overwriteCombo.getValue());

        // ✅ sample-rate logic
        if ("WAV".equals(selectedFormat)) {
            // Slider drives sample rate for WAV
            int srHz = nearestSampleRateHz(bitrateSlider.getValue());
            settings.setSampleRateHz(srHz);
        } else {
            // ComboBox drives SR for lossy/FLAC
            if ("WAV".equals(selectedFormat)) {
                // WAV: derive sample rate from slider (kHz → Hz)
                int srHz = nearestSampleRateHz(bitrateSlider.getValue());
                settings.setSampleRateHz(srHz);
            } else {
                // Other formats: use the ComboBox value or default to 44.1 kHz
                settings.setSampleRateHz(sampleRateCombo.getValue() == null ? 44_100 : sampleRateCombo.getValue());
            }
        }

        // Bitrate for lossy only
        if ("MP3".equals(selectedFormat) || "M4A".equals(selectedFormat)) {
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
            // overall progress across all files
            double overall = ((index - 1) + progress) / total;
            progressBar.setProgress(overall);

            if (statusLabel != null) {
                int percent = (int) Math.round(overall * 100);
                statusLabel.setText("Processing " + index + "/" + total + " — " + percent + "% (" + fileName + ")");
            }
        });

        // Fire it (service returns a CompletableFuture; errors surface in exceptionally)
        converting.set(true);
        progressBar.setProgress(0);
        if (statusLabel != null) statusLabel.setText("Starting…");

        service.convert(requests, cb)
                .thenAccept(results -> Platform.runLater(() -> {
                    progressBar.setProgress(1.0);
                    long ok = results.stream().filter(ConversionResult::isSuccess).count();
                    long fail = results.size() - ok;
                    if (statusLabel != null) statusLabel.setText("Done: " + ok + " ok, " + fail + " failed");

                    String msg = results.stream()
                            .map(r -> (r.isSuccess() ? "✅ " : "❌ ")
                                    + r.getInput().getFileName() + (r.getOutput()!=null? " → " + r.getOutput().getFileName() : "")
                                    + (r.getMessage()!=null? " (" + r.getMessage() + ")" : ""))
                            .reduce("", (a,b) -> a + b + "\n");

                    new Alert(Alert.AlertType.INFORMATION, msg).showAndWait();
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

    private int nearestSampleRateHz(double sliderValKHz) {
        // Convert from kHz to Hz, rounding to nearest standard rate
        int hz = (int) Math.round(sliderValKHz * 1000);
        // snap to common values
        int[] standardRates = {8000, 11025, 16000, 22050, 32000, 44100, 48000, 88200, 96000};
        int nearest = standardRates[0];
        int diff = Math.abs(hz - nearest);
        for (int sr : standardRates) {
            int d = Math.abs(hz - sr);
            if (d < diff) { diff = d; nearest = sr; }
        }
        return nearest;
    }

    private void tryAddFileOrFolder(File f) {
        if (f.isDirectory()) {
            try {
                Files.walk(f.toPath(), 1)
                        .filter(p -> Files.isRegularFile(p))
                        .map(Path::toFile)
                        .forEach(this::tryAddFile);
            } catch (Exception ignored) {}
        } else {
            tryAddFile(f);
        }
    }
}
