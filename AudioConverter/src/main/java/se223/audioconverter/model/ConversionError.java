package se223.audioconverter.model;

public record ConversionError(String fileName, String message, Throwable cause) {
    @Override
    public String toString() {
        return "Error in " + fileName + ": " + message;
    }
}