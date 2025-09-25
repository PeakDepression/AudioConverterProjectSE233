package se223.audioconverter.core;
public interface ProgressCallback {
    // 0.0..1.0 per-file progress; totalIndex starts at 1
    void onProgress(String fileName, double progress, int totalIndex, int totalCount);
}
