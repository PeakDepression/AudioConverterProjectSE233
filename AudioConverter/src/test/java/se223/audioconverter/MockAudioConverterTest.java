package se223.audioconverter;

import org.junit.jupiter.api.Test;
import se223.audioconverter.core.MockAudioConverter;
import se223.audioconverter.exception.ConversionException;
import se223.audioconverter.model.*;

import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

public class MockAudioConverterTest {

    @Test
    void runsConcurrentlyAndReportsProgress() throws Exception {
        var settings = new ConversionSettings();
        settings.setFormat(AudioFormat.MP3);
        settings.setBitrateKbps(192);
        settings.setSampleRateHz(44100);
        settings.setChannels(Channels.STEREO);

        var reqs = List.of(
                new ConversionRequest(Path.of("a.wav"), Path.of("target/out"), settings),
                new ConversionRequest(Path.of("b.wav"), Path.of("target/out"), settings),
                new ConversionRequest(Path.of("c.wav"), Path.of("target/out"), settings)
        );

        var conv = new MockAudioConverter(3);
        AtomicInteger ticks = new AtomicInteger();

        var results = conv.convertAll(reqs, (file, p, i, n) -> ticks.incrementAndGet()).get();

        assertEquals(3, results.size());
        assertTrue(ticks.get() >= 3 * 10); // received a fair few progress ticks
        assertTrue(results.stream().allMatch(ConversionResult::isSuccess));
        conv.close();
    }

    @Test
    void rejectsEmptyList() {
        assertThrows(ConversionException.class, () ->
                new MockAudioConverter(2).convertAll(List.of(), (f,p,i,n) -> {}).get()
        );
    }
}
