package se223.audioconverter.model;

public class ConversionSettings {
    private AudioFormat format;
    private Integer bitrateKbps;     // null if not applicable (e.g. WAV/FLAC)
    private int sampleRateHz;        // 44100, 48000…
    private Channels channels;       // MONO/STEREO

    // ctor/getters/setters/builder (keep it boring & clear)
    // Add a small validator if you fancy (e.g. bitrate null for WAV/FLAC)

    public AudioFormat getFormat() {
        return format;
    }

    public void setFormat(AudioFormat format) {
        this.format = format;
    }

    public Integer getBitrateKbps() {
        return bitrateKbps;
    }

    public void setBitrateKbps(Integer bitrateKbps) {
        this.bitrateKbps = bitrateKbps;
    }

    public int getSampleRateHz() {
        return sampleRateHz;
    }

    public void setSampleRateHz(int sampleRateHz) {
        this.sampleRateHz = sampleRateHz;
    }

    public Channels getChannels() {
        return channels;
    }

    public void setChannels(Channels channels) {
        this.channels = channels;
    }
}

