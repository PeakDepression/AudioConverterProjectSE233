package se223.audioconverter.model;

import java.nio.file.Path;

public class ConversionRequest {
    private final Path input;
    private final Path outputDir;
    private final ConversionSettings settings;

    public ConversionRequest(Path input, Path outputDir, ConversionSettings settings) {
        this.input = input; this.outputDir = outputDir; this.settings = settings;
    }
    public Path input() { return input; }
    public Path outputDir() { return outputDir; }
    public ConversionSettings settings() { return settings; }
}

