package se223.audioconverter.util;

public final class DebugLogger {
    private static volatile boolean enabled = true;
    public static void setEnabled(boolean on) { enabled = on; }
    public static void d(String msg) { if (enabled) System.out.println("[DEBUG] " + msg); }
    public static void e(String msg, Throwable t) {
        System.err.println("[ERROR] " + msg);
        if (t != null) t.printStackTrace(System.err);
    }
}