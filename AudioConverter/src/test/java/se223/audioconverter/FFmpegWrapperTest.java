package se223.audioconverter;

import net.bramp.ffmpeg.*;
import net.bramp.ffmpeg.builder.FFmpegBuilder;
import net.bramp.ffmpeg.job.FFmpegJob;
import net.bramp.ffmpeg.progress.Progress;
import net.bramp.ffmpeg.progress.ProgressListener;
import net.bramp.ffmpeg.probe.FFmpegFormat;
import net.bramp.ffmpeg.probe.FFmpegProbeResult;
import net.bramp.ffmpeg.probe.FFmpegStream;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import se223.audioconverter.util.FFmpegLocator;

import java.io.IOException;
import java.nio.file.*;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

/**
 * FFmpeg integration tests that auto-skip if ffmpeg/ffprobe or the test input are not available.
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class FFmpegWrapperTest {

    private static Path FFMPEG;
    private static Path FFPROBE;

    // Prefer a small input you place under project root (or test resources)
    private static final String DEFAULT_INPUT = "Input/input.mp4";

    @TempDir
    Path tempDir; // JUnit creates and cleans this for us

    @BeforeAll
    static void resolveTools() {
        Optional<Path> ff = FFmpegLocator.findFfmpeg();
        Optional<Path> fp = FFmpegLocator.findFfprobe();

        Assumptions.assumeTrue(ff.isPresent() && fp.isPresent(),
                "FFmpeg/ffprobe not available on PATH or in expected locations; skipping tests.");

        FFMPEG = ff.get();
        FFPROBE = fp.get();
    }

    private static Path resolveInput() {
        // 1) Try project-root relative
        Path p1 = Paths.get(DEFAULT_INPUT);
        if (Files.isRegularFile(p1)) return p1;

        // 2) Try test resources (src/test/resources/Input/input.mp4)
        try {
            var url = FFmpegWrapperTest.class.getResource("/Input/input.mp4");
            if (url != null) {
                Path p = Paths.get(url.toURI());
                if (Files.isRegularFile(p)) return p;
            }
        } catch (Exception ignored) {}

        return null;
    }

    @Test
    @Order(1)
    void testSimpleConversion() {
        Path input = resolveInput();
        Assumptions.assumeTrue(input != null && Files.isRegularFile(input),
                "Test input file not found at " + DEFAULT_INPUT + " or in test resources; skipping.");

        Path outFile = tempDir.resolve("output.mp4");

        assertDoesNotThrow(() -> {
            FFmpeg ffmpeg = new FFmpeg(FFMPEG.toString());
            FFprobe ffprobe = new FFprobe(FFPROBE.toString());

            FFmpegBuilder builder = new FFmpegBuilder()
                    .setInput(input.toString())
                    .overrideOutputFiles(true)
                    .addOutput(outFile.toString())
                    .setFormat("mp4")
                    .setVideoCodec("libx264")
                    .setVideoResolution(640, 480)
                    .setVideoFrameRate(24, 1)
                    .setAudioCodec("aac")
                    .setAudioChannels(1)
                    .setAudioSampleRate(48_000)
                    .done();

            new FFmpegExecutor(ffmpeg, ffprobe).createJob(builder).run();

            Assertions.assertTrue(Files.exists(outFile),
                    "Expected output not created: " + outFile);
        });
    }

    @Test
    @Order(2)
    void testProbeFile() throws IOException {
        Path input = resolveInput();
        Assumptions.assumeTrue(input != null && Files.isRegularFile(input),
                "Test input file not found; skipping.");

        FFprobe ffprobe = new FFprobe(FFPROBE.toString());
        FFmpegProbeResult probeResult = ffprobe.probe(input.toString());

        FFmpegFormat format = probeResult.getFormat();
        System.out.printf("File: %s ; Format: %s ; Duration: %.3fs%n",
                format.filename, format.format_long_name, format.duration);

        // Guard: some files may have only audio or only video
        if (!probeResult.getStreams().isEmpty()) {
            FFmpegStream stream = probeResult.getStreams().get(0);
            System.out.printf("Codec: %s ; Width: %dpx ; Height: %dpx%n",
                    stream.codec_long_name, stream.width, stream.height);
        }
    }

    @Test
    @Order(3)
    void testConversionWithProgress() throws IOException {
        Path input = resolveInput();
        Assumptions.assumeTrue(input != null && Files.isRegularFile(input),
                "Test input file not found; skipping.");

        Path outFile = tempDir.resolve("progress_output.mp4");

        FFmpeg ffmpeg = new FFmpeg(FFMPEG.toString());
        FFprobe ffprobe = new FFprobe(FFPROBE.toString());

        FFmpegProbeResult in = ffprobe.probe(input.toString());
        final double duration_ns = Math.max(1, in.getFormat().duration * TimeUnit.SECONDS.toNanos(1));

        FFmpegBuilder builder = new FFmpegBuilder()
                .setInput(in)
                .overrideOutputFiles(true)
                .addOutput(outFile.toString())
                .done();

        FFmpegJob job = new FFmpegExecutor(ffmpeg, ffprobe).createJob(builder, new ProgressListener() {
            @Override public void progress(Progress progress) {
                double pct = Math.min(1.0, progress.out_time_ns / duration_ns);
                System.out.printf("[%.0f%%] frame:%d time:%s fps:%.0f speed:%.2fx%n",
                        pct * 100,
                        progress.frame,
                        FFmpegUtils.toTimecode(progress.out_time_ns, TimeUnit.NANOSECONDS),
                        progress.fps.doubleValue(),
                        progress.speed);
            }
        });

        job.run();
        Assertions.assertTrue(Files.exists(outFile), "Expected output not created: " + outFile);
    }
}