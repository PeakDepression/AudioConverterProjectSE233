package se223.audioconverter.service;

import se223.audioconverter.core.*;
import se223.audioconverter.exception.ConversionException;
import se223.audioconverter.model.ConversionRequest;
import se223.audioconverter.model.ConversionResult;

import java.util.List;
import java.util.concurrent.CompletableFuture;

public class ConversionService {

    private static final ConversionService INSTANCE = new ConversionService();
    public static ConversionService getInstance() { return INSTANCE; }

    private final AudioConverter converter;

    // 🔹 Add these two fields:
    private final boolean usingFFmpeg;
    private final String ffmpegInfo;

    private ConversionService() {
        // Try real FFmpeg first, fall back to mock
        var maybe = FFmpegAudioConverter.createDefault();
        if (maybe.isPresent()) {
            this.converter = maybe.get();
            this.usingFFmpeg = true;
            this.ffmpegInfo = "FFmpeg located successfully (PATH / FFMPEG_HOME / ffmpeg/bin)";
        } else {
            this.converter = new MockAudioConverter(Math.max(2, Runtime.getRuntime().availableProcessors() / 2));
            this.usingFFmpeg = false;
            this.ffmpegInfo = "FFmpeg not found; running in mock simulation mode.";
        }
    }

    // No 'throws' here: we wrap any sync failure into a failed future.
    public CompletableFuture<List<ConversionResult>> convert(
            List<ConversionRequest> requests, ProgressCallback progress) {

        try {
            ConversionValidator.validateRequests(requests);
            return converter.convertAll(requests, progress);
        } catch (ConversionException e) {
            return CompletableFuture.failedFuture(e);
        }
    }

    /** Close background resources (thread pools, etc.) */
    public void close() {
        converter.close();
    }

    public AudioConverter getConverter() { return converter; }

    // 🔹 Add these two methods:
    public boolean isUsingFFmpeg() { return usingFFmpeg; }
    public String getFfmpegInfo() { return ffmpegInfo; }
}