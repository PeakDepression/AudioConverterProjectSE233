package se223.audioconverter.model;

public enum OverwritePolicy {
    OVERWRITE,     // replace existing file
    SKIP,          // skip existing
    RENAME         // create new name like "file(1).mp3"
}
