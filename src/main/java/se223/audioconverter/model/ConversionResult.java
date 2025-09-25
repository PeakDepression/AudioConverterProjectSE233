package se223.audioconverter.model;

import java.nio.file.Path;

public class ConversionResult {
    private final Path input;
    private final Path output;
    private final boolean success;
    private final String message;

    public ConversionResult(Path input, Path output, boolean success, String message) {
        this.input = input; this.output = output; this.success = success; this.message = message;
    }

    public String getMessage() {
        return message;
    }

    public Path getInput() {
        return input;
    }

    public Path getOutput() {
        return output;
    }

    public boolean isSuccess() {
        return success;
    }
// getters…
}

