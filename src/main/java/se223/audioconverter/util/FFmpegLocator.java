// src/main/java/se223/audioconverter/util/FFmpegLocator.java
package se223.audioconverter.util;

import java.io.IOException;
import java.nio.file.*;
import java.util.Optional;

public final class FFmpegLocator {
    private FFmpegLocator() {}

    public static Optional<Path> findFfmpeg() { return findExeVerified("ffmpeg"); }
    public static Optional<Path> findFfprobe() { return findExeVerified("ffprobe"); }

    private static Optional<Path> findExeVerified(String base) {
        String exe = isWindows() ? base + ".exe" : base;

        // 1) FFMPEG_HOME
        String home = System.getenv("FFMPEG_HOME");
        if (home != null) {
            Path p = Paths.get(home, "bin", exe);
            if (Files.isExecutable(p)) return Optional.of(p);
            p = Paths.get(home, exe);
            if (Files.isExecutable(p)) return Optional.of(p);
        }

        // 2) Local project folder: ./ffmpeg/bin/ffmpeg(.exe)
        Path local = Paths.get("ffmpeg", "bin", exe);
        if (Files.isExecutable(local)) return Optional.of(local.toAbsolutePath());

        // 3) PATH: try to run "<exe> -version"
        if (canRun(exe)) {
            // Returning just the name lets ProcessBuilder resolve via PATH
            return Optional.of(Paths.get(exe));
        }

        return Optional.empty();
    }

    private static boolean canRun(String exeName) {
        try {
            Process p = new ProcessBuilder(exeName, "-version")
                    .redirectErrorStream(true)
                    .start();
            int exit = p.waitFor();
            return exit == 0;
        } catch (IOException | InterruptedException e) {
            return false;
        }
    }

    private static boolean isWindows() {
        return System.getProperty("os.name").toLowerCase().contains("win");
    }
}