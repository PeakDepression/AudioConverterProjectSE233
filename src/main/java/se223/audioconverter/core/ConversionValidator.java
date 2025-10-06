package se223.audioconverter.core;

import se223.audioconverter.exception.ConversionException;
import se223.audioconverter.model.ConversionRequest;

import java.nio.file.Files;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class ConversionValidator {

    public static void validateRequests(List<ConversionRequest> requests) throws ConversionException {
        if (requests == null || requests.isEmpty())
            throw new ConversionException("No input files provided.");

        Set<String> seen = new HashSet<>();

        for (ConversionRequest req : requests) {
            if (!Files.exists(req.input()))
                throw new ConversionException("Input file missing: " + req.input());

            if (!Files.isReadable(req.input()))
                throw new ConversionException("Cannot read: " + req.input());

            if (!Files.isDirectory(req.outputDir()) && Files.exists(req.outputDir()) && !Files.isWritable(req.outputDir()))
                throw new ConversionException("Cannot write to output directory: " + req.outputDir());

            if (!seen.add(req.input().toAbsolutePath().toString()))
                throw new ConversionException("Duplicate file detected: " + req.input().getFileName());

            if (Files.exists(req.outputDir())) {
                if (!Files.isDirectory(req.outputDir()))
                    throw new ConversionException("Output path is not a directory: " + req.outputDir());
                if (!Files.isWritable(req.outputDir()))
                    throw new ConversionException("Cannot write to: " + req.outputDir());
            }
        }
    }
}
