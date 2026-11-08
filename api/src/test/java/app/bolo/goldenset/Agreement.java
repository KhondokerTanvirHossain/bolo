package app.bolo.goldenset;

import app.bolo.assessment.domain.SoundFlags;
import app.bolo.assessment.domain.SoundPattern;
import app.bolo.assessment.domain.SpokenSound;
import app.bolo.assessment.domain.SpokenWord;
import app.bolo.speech.application.port.in.PronunciationResult;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * How Bolo's sound flags agree with the teacher's labels, per pattern (NFR-10). Chances to agree are the
 * (reference word, pattern) pairs where the word carries the pattern's sound, plus any word the teacher labelled.
 */
public final class Agreement {

    static final double MIN_PRECISION = 0.80;
    static final double MIN_RECALL = 0.60;
    static final int MIN_PATTERNS_FOR_GATE = 4;
    /** A recording whose words line up with less than this share of the reference text isn't measured. */
    static final double MIN_ALIGNED = 0.5;

    public record Outcome(GoldenSet.Recording rec, PronunciationResult result) {
    }

    public enum Verdict { PASS, FAIL, NOT_ENOUGH_DATA }

    public enum GateVerdict { PASS, FAIL, NOT_DECIDED }

    public record PatternStats(int tp, int fp, int fn, int tn, Double precision, Double recall, double falseFlagRate, Verdict verdict) {
    }

    public record Disagreement(String recordingId, String word, SoundPattern pattern, boolean teacher, boolean bolo, Double soundScore) {
    }

    public record Skipped(String recordingId, String reason) {
    }

    /** A teacher label on a word where the vendor heard none of that pattern's sounds (kept in the counts, listed for review). */
    public record OffSound(String recordingId, String word, SoundPattern pattern) {
    }

    private record Key(int refIndex, SoundPattern pattern) {
    }

    private final Map<SoundPattern, PatternStats> byPattern;
    private final List<Disagreement> disagreements;
    private final List<Skipped> skipped;
    private final List<OffSound> offSound;
    private final GateVerdict gate;
    private final double rawAgreement;
    private final int chances;

    private Agreement(Map<SoundPattern, PatternStats> byPattern, List<Disagreement> disagreements, List<Skipped> skipped,
                      List<OffSound> offSound, GateVerdict gate, double rawAgreement, int chances) {
        this.byPattern = byPattern;
        this.disagreements = disagreements;
        this.skipped = skipped;
        this.offSound = offSound;
        this.gate = gate;
        this.rawAgreement = rawAgreement;
        this.chances = chances;
    }

    public static Agreement of(List<Outcome> outcomes, double soundBelow, double wordBelow, int minPositives) {
        Map<SoundPattern, int[]> counts = new EnumMap<>(SoundPattern.class);
        for (SoundPattern p : SoundPattern.values()) counts.put(p, new int[4]);
        List<Disagreement> disagreements = new ArrayList<>();
        List<Skipped> skipped = new ArrayList<>();
        List<OffSound> offSound = new ArrayList<>();
        for (Outcome o : outcomes) {
            String id = o.rec().id();
            if (o.result() == null || !o.result().heard() || o.result().words().isEmpty()) {
                skipped.add(new Skipped(id, "nothing heard"));
                continue;
            }
            List<SpokenWord> words = o.result().words().stream().map(w -> new SpokenWord(w.word(), w.startMs(), w.endMs(), w.accuracy(),
                    w.errorType(), w.phonemes().stream().map(p -> new SpokenSound(p.symbol(), p.accuracy())).toList())).toList();
            List<String> tokens = Alignment.tokens(o.rec().reference());
            Map<Integer, Integer> align = Alignment.align(words, tokens);
            // Azure returns unspoken reference words as Omissions, which always line up; only words actually said count here.
            long said = align.keySet().stream().filter(pos -> !"Omission".equals(words.get(pos).errorType())).count();
            if (tokens.isEmpty() || said < MIN_ALIGNED * tokens.size()) {
                skipped.add(new Skipped(id, "words didn't match the reference: " + said + " of " + tokens.size() + " said and lined up"));
                continue;
            }
            Map<Key, Double> chancesHere = new HashMap<>();
            align.forEach((pos, ref) -> {
                SpokenWord w = words.get(pos);
                for (SoundPattern p : SoundFlags.patternsIn(w)) chancesHere.put(new Key(ref, p), weakest(w, p));
            });
            Map<Key, Double> flagged = new HashMap<>();
            // Measure what a learner is told: one sound per word plus the extra vowel (as practice shows it).
            for (SoundFlags.Flag f : SoundFlags.shown(SoundFlags.flags(words, soundBelow, wordBelow))) {
                Integer ref = align.get(f.position());
                if (ref != null) flagged.putIfAbsent(new Key(ref, f.pattern()), f.soundScore());
            }
            Set<Key> teacher = new LinkedHashSet<>();
            o.rec().labels().forEach(l -> teacher.add(new Key(l.refIndex(), l.pattern())));
            Set<Key> universe = new LinkedHashSet<>(chancesHere.keySet());
            universe.addAll(flagged.keySet());
            for (Key k : teacher) {
                if (!chancesHere.containsKey(k)) offSound.add(new OffSound(id, tokens.get(k.refIndex()), k.pattern()));
                universe.add(k);
            }
            for (Key k : universe) {
                boolean t = teacher.contains(k), b = flagged.containsKey(k);
                int[] c = counts.get(k.pattern());
                c[t ? (b ? 0 : 2) : (b ? 1 : 3)]++;
                if (t != b) {
                    disagreements.add(new Disagreement(id, tokens.get(k.refIndex()), k.pattern(), t, b,
                            b ? flagged.get(k) : chancesHere.get(k)));
                }
            }
        }
        Map<SoundPattern, PatternStats> stats = new EnumMap<>(SoundPattern.class);
        int agree = 0, total = 0;
        for (var e : counts.entrySet()) {
            int tp = e.getValue()[0], fp = e.getValue()[1], fn = e.getValue()[2], tn = e.getValue()[3];
            Double precision = tp + fp == 0 ? null : (double) tp / (tp + fp);
            Double recall = tp + fn == 0 ? null : (double) tp / (tp + fn);
            double falseRate = fp + tn == 0 ? 0 : (double) fp / (fp + tn);
            Verdict v = tp + fn < minPositives ? Verdict.NOT_ENOUGH_DATA
                    : precision != null && precision >= MIN_PRECISION && recall >= MIN_RECALL ? Verdict.PASS : Verdict.FAIL;
            stats.put(e.getKey(), new PatternStats(tp, fp, fn, tn, precision, recall, falseRate, v));
            agree += tp + tn;
            total += tp + fp + fn + tn;
        }
        List<PatternStats> judged = stats.values().stream().filter(s -> s.verdict() != Verdict.NOT_ENOUGH_DATA).toList();
        GateVerdict gate = judged.stream().anyMatch(s -> s.verdict() == Verdict.FAIL) ? GateVerdict.FAIL
                : judged.size() >= MIN_PATTERNS_FOR_GATE ? GateVerdict.PASS : GateVerdict.NOT_DECIDED;
        return new Agreement(stats, List.copyOf(disagreements), List.copyOf(skipped), List.copyOf(offSound), gate,
                total == 0 ? Double.NaN : (double) agree / total, total);
    }

    private static Double weakest(SpokenWord w, SoundPattern p) {
        return w.phonemes().stream().filter(s -> SoundPattern.of(s.symbol()).filter(p::equals).isPresent())
                .map(SpokenSound::accuracy).min(Double::compare).orElse(null);
    }

    public Map<SoundPattern, PatternStats> byPattern() {
        return byPattern;
    }

    public List<Disagreement> disagreements() {
        return disagreements;
    }

    public List<Skipped> skipped() {
        return skipped;
    }

    public List<OffSound> offSound() {
        return offSound;
    }

    public GateVerdict gate() {
        return gate;
    }

    /** (agreements) / (chances) over every pattern — NFR-10's plain figure; NaN when there were no chances. */
    public double rawAgreement() {
        return rawAgreement;
    }

    public int chances() {
        return chances;
    }
}
