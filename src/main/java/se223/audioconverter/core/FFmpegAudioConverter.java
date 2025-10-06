package se223.audioconverter.core;

import se223.audioconverter.exception.ConversionException;
import se223.audioconverter.model.*;
import se223.audioconverter.util.DebugLogger;
import se223.audioconverter.util.FFmpegLocator;
import se223.audioconverter.util.FileUtils;
import se223.audioconverter.util.Timecode;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.*;

public class FFmpegAudioConverter implements AudioConverter {

    private final ExecutorService pool;
    private final Path ffmpeg;
    private final Path ffprobe;

    public FFmpegAudioConverter(int parallelism, Path ffmpeg, Path ffprobe) {
        this.pool = Executors.newFixedThreadPool(parallelism);
        this.ffmpeg = ffmpeg;
        this.ffprobe = ffprobe;
    }

    /** Convenience creator: tries to locate binaries. */
    public static Optional<FFmpegAudioConverter> createDefault() {
        Path ff = FFmpegLocator.findFfmpeg().orElse(null);
        Path fp = FFmpegLocator.findFfprobe().orElse(null);
        if (ff == null || fp == null) return Optional.empty();
        int parallel = Math.max(2, Runtime.getRuntime().availableProcessors() / 2);
        return Optional.of(new FFmpegAudioConverter(parallel, ff, fp));
    }

    @Override
    public CompletableFuture<List<ConversionResult>> convertAll(
            List<ConversionRequest> requests, ProgressCallback progress) throws ConversionException {

        if (requests == null || requests.isEmpty())
            throw new ConversionException("No files to convert.");

        List<CompletableFuture<ConversionResult>> futures = new ArrayList<>();
        final int total = requests.size();

        for (int i = 0; i < total; i++) {
            final int idx = i;
            ConversionRequest req = requests.get(i);

            futures.add(CompletableFuture.supplyAsync(() -> runOne(req, idx, total, progress), pool));
        }

        return CompletableFuture
                .allOf(futures.toArray(new CompletableFuture[0]))
                .thenApply(v -> futures.stream().map(CompletableFuture::join).toList());
    }

    private ConversionResult runOne(
            ConversionRequest req, int idx, int total, ProgressCallback cb) {

        String ext = switch (req.settings().getFormat()) {
            case MP3 -> "mp3";
            case WAV -> "wav";
            case M4A -> "m4a";
            case FLAC -> "flac";
        };

        Path out = FileUtils.resolveOutput(
                req.input(), req.outputDir(), ext,
                Optional.ofNullable(req.settings().getOverwritePolicy()).orElse(OverwritePolicy.RENAME));

        if (out == null) { // SKIP
            return new ConversionResult(req.input(), null, true, "Skipped (exists)");
        }

        List<String> args = new ArrayList<>();
        args.add(ffmpeg.toString());
        args.add("-y"); // we'll still respect policy via chosen output path; -y avoids prompts
        args.add("-i");
        args.add(req.input().toString());

        // audio channel count
        int channels = (req.settings().getChannels() == Channels.MONO) ? 1 : 2;
        args.addAll(List.of("-ac", String.valueOf(channels)));

        // sample rate
        args.addAll(List.of("-ar", String.valueOf(req.settings().getSampleRateHz())));

        switch (req.settings().getFormat()) {
            case MP3 -> {
                args.addAll(List.of("-c:a", "libmp3lame"));
                Integer kbps = Optional.ofNullable(req.settings().getBitrateKbps()).orElse(192);
                args.addAll(List.of("-b:a", kbps + "k"));
            }
            case M4A -> {
                // write an AAC-in-mp4 container (.m4a)
                args.addAll(List.of("-c:a", "aac"));
                Integer kbps = Optional.ofNullable(req.settings().getBitrateKbps()).orElse(192);
                args.addAll(List.of("-b:a", kbps + "k"));
            }
            case WAV -> {
                args.addAll(List.of("-c:a", "pcm_s16le"));
            }
            case FLAC -> {
                args.addAll(List.of("-c:a", "flac"));
                // you can add -compression_level 5 if you want
            }
        }

        args.add(out.toString());

        ProcessBuilder pb = new ProcessBuilder(args);
        pb.redirectErrorStream(false); // we want stderr for progress
        DebugLogger.d("FFmpeg: " + String.join(" ", args));

        double duration = probeDurationSeconds(req.input());

        try {
            Process p = pb.start();

            // Read stderr for progress tokens
            BufferedReader err = new BufferedReader(new InputStreamReader(p.getErrorStream()));
            String line;
            while ((line = err.readLine()) != null) {
                // ffmpeg writes lines like: "time=00:00:12.34"
                int t = line.indexOf("time=");
                if (t >= 0) {
                    int end = line.indexOf(' ', t);
                    String token = (end > t) ? line.substring(t + 5, end) : line.substring(t + 5);
                    double sec = Timecode.parseSeconds(token);
                    if (sec >= 0 && duration > 0) {
                        double progress = Math.min(1.0, sec / duration);
                        cb.onProgress(req.input().getFileName().toString(), progress, idx + 1, total);
                    }
                }
            }

            int exit = p.waitFor();
            if (exit == 0) {
                cb.onProgress(req.input().getFileName().toString(), 1.0, idx + 1, total);
                return new ConversionResult(req.input(), out, true, "OK");
            } else {
                return new ConversionResult(req.input(), out, false, "ffmpeg failed (exit " + exit + ")");
            }
        } catch (Exception e) {
            return new ConversionResult(req.input(), out, false, "Error: " + e.getMessage());
        }
    }

    private double probeDurationSeconds(Path input) {
        try {
            List<String> cmd = List.of(
                    ffprobe.toString(),
                    "-v", "error",
                    "-show_entries", "format=duration",
                    "-of", "default=noprint_wrappers=1:nokey=1",
                    input.toString()
            );
            Process p = new ProcessBuilder(cmd).start();
            try (var r = new BufferedReader(new InputStreamReader(p.getInputStream()))) {
                String s = r.readLine();
                if (s != null) {
                    double d = Double.parseDouble(s.trim());
                    return (d > 0) ? d : -1;
                }
            }
        } catch (Exception ignored) { }
        return -1;
    }

    @Override public void close() { pool.shutdownNow(); }
}