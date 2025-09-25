package se223.audioconverter.core;

import se223.audioconverter.exception.ConversionException;
import se223.audioconverter.model.ConversionRequest;
import se223.audioconverter.model.ConversionResult;

import java.util.List;
import java.util.concurrent.CompletableFuture;

public interface AudioConverter extends AutoCloseable {

    CompletableFuture<List<ConversionResult>> convertAll(
            List<ConversionRequest> requests,
            ProgressCallback progress) throws ConversionException;

    /** Default no-op; concrete converters can override to release resources. */
    @Override
    default void close() {
        // no-op
    }
}