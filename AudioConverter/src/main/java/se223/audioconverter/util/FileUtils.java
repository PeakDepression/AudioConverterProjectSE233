package se223.audioconverter.util;

import se223.audioconverter.model.OverwritePolicy;

import java.nio.file.*;

public final class FileUtils {
    private FileUtils() {}

    /** Decide final output path according to policy. Keeps original base name, swaps extension. */
    public static Path resolveOutput(Path input, Path outDir, String newExt, OverwritePolicy policy) {
        String name = input.getFileName().toString();
        int dot = name.lastIndexOf('.');
        String base = dot > 0 ? name.substring(0, dot) : name;

        Path candidate = outDir.resolve(base + "." + newExt.toLowerCase());

        switch (policy) {
            case OVERWRITE -> { return candidate; }
            case SKIP -> {
                return Files.exists(candidate) ? null : candidate;
            }
            case RENAME -> {
                if (!Files.exists(candidate)) return candidate;
                int i = 1;
                while (true) {
                    Path p = outDir.resolve(base + "(" + i + ")." + newExt.toLowerCase());
                    if (!Files.exists(p)) return p;
                    i++;
                }
            }
            default -> throw new IllegalArgumentException("Unknown policy: " + policy);
        }
    }
}