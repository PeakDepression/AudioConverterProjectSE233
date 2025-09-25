package se223.audioconverter.core;

import se223.audioconverter.exception.ConversionException;
import se223.audioconverter.model.ConversionRequest;
import se223.audioconverter.model.ConversionResult;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;

public class MockAudioConverter implements AudioConverter {

    private final ExecutorService pool;

    public MockAudioConverter(int parallelism) {
        this.pool = Executors.newFixedThreadPool(parallelism);
    }

    @Override
    public CompletableFuture<List<ConversionResult>> convertAll(
            List<ConversionRequest> requests,
            ProgressCallback progress) throws ConversionException {

        if (requests == null || requests.isEmpty()) {
            throw new ConversionException("No files to convert.");
        }

        List<CompletableFuture<ConversionResult>> futures = new ArrayList<>();
        final int total = requests.size();

        for (int i = 0; i < total; i++) {
            final int idx = i;
            var req = requests.get(i);

            CompletableFuture<ConversionResult> f = CompletableFuture.supplyAsync(() -> {
                String name = req.input().getFileName().toString();
                // simulate work in 20 steps
                for (int step = 1; step <= 20; step++) {
                    try { Thread.sleep(60); } catch (InterruptedException ignored) {}
                    double p = step / 20.0;
                    progress.onProgress(name, p, idx + 1, total);
                }
                Path out = req.outputDir().resolve(name + ".mock");
                return new ConversionResult(req.input(), out, true, "Mock OK");
            }, pool);

            futures.add(f);
        }

        return CompletableFuture
                .allOf(futures.toArray(new CompletableFuture[0]))
                .thenApply(v -> futures.stream().map(CompletableFuture::join).toList());
    }

    @Override
    public void close() {
        pool.shutdownNow();
    }
}
