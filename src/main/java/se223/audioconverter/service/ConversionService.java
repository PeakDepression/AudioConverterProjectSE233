package se223.audioconverter.service;

import se223.audioconverter.core.AudioConverter;
import se223.audioconverter.core.MockAudioConverter;
import se223.audioconverter.core.ProgressCallback;
import se223.audioconverter.exception.ConversionException;
import se223.audioconverter.model.ConversionRequest;
import se223.audioconverter.model.ConversionResult;

import java.util.List;
import java.util.concurrent.CompletableFuture;

public class ConversionService {

    private static final ConversionService INSTANCE = new ConversionService();
    public static ConversionService getInstance() { return INSTANCE; }

    private final AudioConverter converter;

    private ConversionService() {
        this.converter =
                new MockAudioConverter(Math.max(2, Runtime.getRuntime().availableProcessors() / 2));
    }

    // ⬇️ No 'throws' here anymore
    public CompletableFuture<List<ConversionResult>> convert(
            List<ConversionRequest> requests, ProgressCallback progress) {
        try {
            return converter.convertAll(requests, progress); // may throw synchronously
        } catch (ConversionException e) {
            return CompletableFuture.failedFuture(e);        // Java 9+, you're on JDK 21
        }
    }

    public void close() {
        if (converter instanceof MockAudioConverter mock) {
            mock.close();
        }
    }

    public AudioConverter getConverter() {
        return converter;
    }
}