package app.bolo.goldenset;

import app.bolo.speech.adapter.out.azure.AzureScriptedAssessment;
import app.bolo.speech.application.port.in.PronunciationResult;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.io.PrintStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

/**
 * The Gate 1 accuracy harness: scores every recording of a golden set, compares Bolo's flags with the teacher's and
 * writes {@code report.md}. Each vendor result is saved under {@code cache/azure/}, so rescoring costs nothing.
 * <p>
 * {@code ./gradlew goldenSet -Pset=<dir> [-Pmode=offline|refresh]}, or {@code scripts/golden-set.sh <dir>}, which
 * reads the Azure key from the box into this process only. Exit: 0 gate passed or not decided, 1 failed, 2 error.
 */
public final class GoldenSetRunner {

    public enum Mode { CACHE_THEN_VENDOR, OFFLINE, REFRESH }

    static final double DEFAULT_SOUND_BELOW = 60;
    static final double WORD_BELOW = 75;
    static final int MIN_POSITIVES = 5;
    static final List<Double> SWEEP = List.of(40.0, 50.0, 60.0, 70.0);

    private static final JsonMapper JSON = JsonMapper.builder().build();

    private GoldenSetRunner() {
    }

    public static void main(String[] args) {
        if (args.length < 1) {
            System.err.println("usage: GoldenSetRunner <set dir> [--offline|--refresh]");
            System.exit(2);
        }
        Mode mode = args.length > 1 && args[1].equals("--offline") ? Mode.OFFLINE
                : args.length > 1 && args[1].equals("--refresh") ? Mode.REFRESH : Mode.CACHE_THEN_VENDOR;
        String key = System.getenv("BOLO_AZURE_SPEECH_KEY");
        String region = System.getenv().getOrDefault("BOLO_AZURE_SPEECH_REGION", "");
        if (mode != Mode.OFFLINE && (key == null || key.isBlank())) {
            System.err.println("set BOLO_AZURE_SPEECH_KEY (scripts/golden-set.sh does it) or use --offline");
            System.exit(2);
        }
        var azure = mode == Mode.OFFLINE ? null : new AzureScriptedAssessment(key,
                "https://" + (region.isBlank() ? "centralindia" : region) + ".stt.speech.microsoft.com", Duration.ofSeconds(30));
        System.exit(run(Path.of(args[0]), mode, rec -> {
            try {
                return azure.assess(Files.readAllBytes(rec.audio()), rec.reference());
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        }, System.out));
    }

    public static int run(Path setDir, Mode mode, Function<GoldenSet.Recording, PronunciationResult> vendor, PrintStream out) {
        GoldenSet set;
        try {
            set = GoldenSet.load(setDir);
        } catch (GoldenSetException e) {
            out.println("Golden set error: " + e.getMessage());
            return 2;
        }
        Path cache = setDir.resolve("cache").resolve("azure");
        List<Agreement.Outcome> outcomes = new ArrayList<>();
        for (GoldenSet.Recording rec : set.recordings()) {
            Path file = cache.resolve(rec.id() + ".json");
            PronunciationResult result;
            if (mode != Mode.REFRESH && Files.exists(file)) {
                try {
                    result = JSON.readValue(Files.readString(file), PronunciationResult.class);
                } catch (IOException | RuntimeException e) {
                    out.println(rec.id() + ": the saved result " + file.getFileName() + " is unreadable; delete it and run again");
                    return 2;
                }
            } else if (mode == Mode.OFFLINE) {
                out.println(rec.id() + ": no saved result (run once without --offline)");
                return 2;
            } else {
                try {
                    result = vendor.apply(rec);
                } catch (RuntimeException e) {
                    // Only the type: a vendor message may carry request details.
                    out.println(rec.id() + ": the vendor call failed (" + e.getClass().getSimpleName() + "); saved results are kept, run again to resume");
                    return 2;
                }
                try {
                    Files.createDirectories(cache);
                    Files.writeString(file, JSON.writeValueAsString(result), StandardCharsets.UTF_8);
                } catch (IOException e) {
                    out.println(rec.id() + ": can't save the result (" + e.getClass().getSimpleName() + ")");
                    return 2;
                }
                out.println(rec.id() + ": scored");
            }
            outcomes.add(new Agreement.Outcome(rec, result));
        }
        Agreement main = Agreement.of(outcomes, DEFAULT_SOUND_BELOW, WORD_BELOW, MIN_POSITIVES);
        Map<Double, Agreement> sweep = new LinkedHashMap<>();
        for (double t : SWEEP) sweep.put(t, Agreement.of(outcomes, t, WORD_BELOW, MIN_POSITIVES));
        String md = Report.markdown(set, main, sweep);
        try {
            Files.writeString(setDir.resolve("report.md"), md, StandardCharsets.UTF_8);
        } catch (IOException e) {
            out.println("can't write report.md (" + e.getClass().getSimpleName() + ")");
            return 2;
        }
        out.println(md);
        return main.gate() == Agreement.GateVerdict.FAIL ? 1 : 0;
    }
}
