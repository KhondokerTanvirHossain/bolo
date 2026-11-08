package app.bolo.goldenset;

import app.bolo.speech.application.port.in.PronunciationResult;
import app.bolo.speech.application.port.in.SpeechUnavailableException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

class GoldenSetRunnerTest {

    @TempDir Path dir;
    final ByteArrayOutputStream out = new ByteArrayOutputStream();
    final AtomicInteger calls = new AtomicInteger();

    @BeforeEach
    void copyDemo() throws Exception {
        Path demo = Path.of("src/test/resources/goldenset/demo");
        try (Stream<Path> files = Files.walk(demo)) {
            for (Path p : files.toList()) {
                Path to = dir.resolve(demo.relativize(p).toString());
                if (Files.isDirectory(p)) Files.createDirectories(to);
                else Files.copy(p, to);
            }
        }
    }

    Function<GoldenSet.Recording, PronunciationResult> vendor(PronunciationResult r) {
        return rec -> {
            calls.incrementAndGet();
            return r;
        };
    }

    int run(GoldenSetRunner.Mode mode, Function<GoldenSet.Recording, PronunciationResult> vendor) {
        return GoldenSetRunner.run(dir, mode, vendor, new PrintStream(out, true, StandardCharsets.UTF_8));
    }

    String printed() {
        return out.toString(StandardCharsets.UTF_8);
    }

    @Test
    void offlineRunWritesTheReportFromTheCache() throws Exception {
        assertThat(run(GoldenSetRunner.Mode.OFFLINE, vendor(PronunciationResult.nothingHeard()))).isZero();
        assertThat(calls).hasValue(0);
        String report = Files.readString(dir.resolve("report.md"));
        assertThat(report).contains("Gate 1: NOT DECIDED");
        assertThat(printed()).contains("Gate 1: NOT DECIDED");
    }

    @Test
    void offlineWithAMissingCacheNamesTheRecording() throws Exception {
        Files.delete(dir.resolve("cache/azure/school.json"));
        assertThat(run(GoldenSetRunner.Mode.OFFLINE, vendor(PronunciationResult.nothingHeard()))).isEqualTo(2);
        assertThat(calls).hasValue(0);
        assertThat(printed()).contains("school");
    }

    @Test
    void aCorruptCacheIsAnErrorNotAGuess() throws Exception {
        Files.writeString(dir.resolve("cache/azure/vans.json"), "{not json");
        assertThat(run(GoldenSetRunner.Mode.CACHE_THEN_VENDOR, vendor(PronunciationResult.nothingHeard()))).isEqualTo(2);
        assertThat(calls).hasValue(0);
        assertThat(printed()).contains("vans");
    }

    @Test
    void aVendorResultIsCachedAndReused() throws Exception {
        deleteCache();
        assertThat(run(GoldenSetRunner.Mode.CACHE_THEN_VENDOR, vendor(PronunciationResult.nothingHeard()))).isZero();
        assertThat(calls).hasValue(2);
        assertThat(dir.resolve("cache/azure/vans.json")).exists();
        assertThat(run(GoldenSetRunner.Mode.CACHE_THEN_VENDOR, vendor(PronunciationResult.nothingHeard()))).isZero();
        assertThat(calls).hasValue(2);
        assertThat(Files.readString(dir.resolve("report.md"))).contains("nothing heard");
    }

    @Test
    void refreshCallsTheVendorAgain() {
        assertThat(run(GoldenSetRunner.Mode.REFRESH, vendor(PronunciationResult.nothingHeard()))).isZero();
        assertThat(calls).hasValue(2);
    }

    @Test
    void theKeyNeverReachesTheReportOrTheCache() throws Exception {
        deleteCache();
        int code = run(GoldenSetRunner.Mode.CACHE_THEN_VENDOR, rec -> {
            throw new SpeechUnavailableException("key=SECRETKEY");
        });
        assertThat(code).isEqualTo(2);
        assertThat(printed()).doesNotContain("SECRETKEY").contains("SpeechUnavailableException");
        try (Stream<Path> files = Files.walk(dir)) {
            for (Path p : files.filter(Files::isRegularFile).toList()) {
                assertThat(new String(Files.readAllBytes(p), StandardCharsets.ISO_8859_1)).as(p.toString()).doesNotContain("SECRETKEY");
            }
        }
    }

    void deleteCache() throws Exception {
        Files.delete(dir.resolve("cache/azure/vans.json"));
        Files.delete(dir.resolve("cache/azure/school.json"));
    }
}
