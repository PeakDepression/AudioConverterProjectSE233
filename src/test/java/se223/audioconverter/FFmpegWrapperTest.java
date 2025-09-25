package se223.audioconverter;

import net.bramp.ffmpeg.*;
import net.bramp.ffmpeg.builder.FFmpegBuilder;
import net.bramp.ffmpeg.job.FFmpegJob;
import net.bramp.ffmpeg.progress.Progress;
import net.bramp.ffmpeg.progress.ProgressListener;
import net.bramp.ffmpeg.probe.FFmpegFormat;
import net.bramp.ffmpeg.probe.FFmpegProbeResult;
import net.bramp.ffmpeg.probe.FFmpegStream;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

public class FFmpegWrapperTest {

    private static final String FFMPEG = "ffmpeg/ffmpeg.exe";
    private static final String FFPROBE = "ffmpeg/bin/ffprobe.exe";

    private static final String INPUT_FILE = "Input/input.mp4";       // your test input
    private static final String OUTPUT_DIR = "output";                // folder for results
    private static final String OUTPUT_FILE = OUTPUT_DIR + "/output.mp4";

    @Test
    public void testSimpleConversion() {
        assertDoesNotThrow(() -> {
            // Ensure output directory exists
            Files.createDirectories(Paths.get(OUTPUT_DIR));

            FFmpeg ffmpeg = new FFmpeg(FFMPEG);
            FFprobe ffprobe = new FFprobe(FFPROBE);

            FFmpegBuilder builder = new FFmpegBuilder()
                    .setInput(INPUT_FILE)
                    .overrideOutputFiles(true)
                    .addOutput(OUTPUT_FILE)
                    .setFormat("mp4")
                    .setVideoCodec("libx264")
                    .setVideoResolution(640, 480)
                    .setVideoFrameRate(24, 1)
                    .setAudioCodec("aac")
                    .setAudioChannels(1)
                    .setAudioSampleRate(48_000)
                    .done();

            FFmpegExecutor executor = new FFmpegExecutor(ffmpeg, ffprobe);
            FFmpegJob job = executor.createJob(builder);
            job.run();

            System.out.println("✅ Conversion finished!");
        });
    }

    @Test
    public void testProbeFile() throws IOException {
        FFprobe ffprobe = new FFprobe(FFPROBE);
        FFmpegProbeResult probeResult = ffprobe.probe(INPUT_FILE);

        FFmpegFormat format = probeResult.getFormat();
        System.out.printf("File: %s ; Format: %s ; Duration: %.3fs%n",
                format.filename,
                format.format_long_name,
                format.duration);

        FFmpegStream stream = probeResult.getStreams().get(0);
        System.out.printf("Codec: %s ; Width: %dpx ; Height: %dpx%n",
                stream.codec_long_name,
                stream.width,
                stream.height);
    }

    @Test
    public void testConversionWithProgress() throws IOException {
        // Ensure output directory exists
        Files.createDirectories(Paths.get(OUTPUT_DIR));

        FFmpeg ffmpeg = new FFmpeg(FFMPEG);
        FFprobe ffprobe = new FFprobe(FFPROBE);

        FFmpegProbeResult in = ffprobe.probe(INPUT_FILE);

        FFmpegBuilder builder = new FFmpegBuilder()
                .setInput(in)
                .overrideOutputFiles(true)
                .addOutput(OUTPUT_FILE)
                .done();

        FFmpegExecutor executor = new FFmpegExecutor(ffmpeg, ffprobe);
        final double duration_ns = in.getFormat().duration * TimeUnit.SECONDS.toNanos(1);

        FFmpegJob job = executor.createJob(builder, new ProgressListener() {
            @Override
            public void progress(Progress progress) {
                double percentage = progress.out_time_ns / duration_ns;
                System.out.printf("[%.0f%%] frame:%d time:%s fps:%.0f speed:%.2fx%n",
                        percentage * 100,
                        progress.frame,
                        FFmpegUtils.toTimecode(progress.out_time_ns, TimeUnit.NANOSECONDS),
                        progress.fps.doubleValue(),
                        progress.speed);
            }
        });

        job.run();
    }
}
