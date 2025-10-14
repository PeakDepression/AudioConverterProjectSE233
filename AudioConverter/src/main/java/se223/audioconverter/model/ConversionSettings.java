package se223.audioconverter.model;

public class ConversionSettings {
    private AudioFormat format;
    private Integer bitrateKbps;       // null if not applicable (MP3/M4A only)
    private int sampleRateHz;          // e.g. 44100, 48000, or user-selected for WAV
    private Channels channels;         // MONO/STEREO
    private OverwritePolicy overwritePolicy = OverwritePolicy.RENAME;

    // === Getters and Setters ===
    public AudioFormat getFormat() { return format; }
    public void setFormat(AudioFormat format) { this.format = format; }

    public Integer getBitrateKbps() { return bitrateKbps; }
    public void setBitrateKbps(Integer bitrateKbps) { this.bitrateKbps = bitrateKbps; }

    public int getSampleRateHz() { return sampleRateHz; }
    public void setSampleRateHz(int sampleRateHz) { this.sampleRateHz = sampleRateHz; }

    public Channels getChannels() { return channels; }
    public void setChannels(Channels channels) { this.channels = channels; }

    public OverwritePolicy getOverwritePolicy() { return overwritePolicy; }
    public void setOverwritePolicy(OverwritePolicy overwritePolicy) { this.overwritePolicy = overwritePolicy; }

}