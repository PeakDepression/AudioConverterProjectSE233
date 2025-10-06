package se223.audioconverter.util;

public final class Timecode {
    private Timecode() {}
    /** Parses ffmpeg "time=HH:MM:SS.xx" to seconds. Returns -1 if invalid. */
    public static double parseSeconds(String hhmmss) {
        try {
            String[] parts = hhmmss.trim().split(":");
            if (parts.length != 3) return -1;
            double h = Double.parseDouble(parts[0]);
            double m = Double.parseDouble(parts[1]);
            double s = Double.parseDouble(parts[2]);
            return h * 3600 + m * 60 + s;
        } catch (Exception e) {
            return -1;
        }
    }
}
