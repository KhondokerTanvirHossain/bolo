package app.bolo.goldenset;

import app.bolo.assessment.domain.SoundFlags;
import app.bolo.assessment.domain.SoundPattern;

import java.util.Map;

/** The Gate 1 report in Markdown: the verdict, a row per sound, the threshold sweep and every disagreement. */
public final class Report {

    private Report() {
    }

    public static String markdown(GoldenSet set, Agreement a, Map<Double, Agreement> sweep) {
        StringBuilder md = new StringBuilder();
        int measured = set.recordings().size() - a.skipped().size();
        md.append("# Gate 1 accuracy report: ").append(set.name()).append("\n\n");
        md.append("Source: ").append(set.source()).append(" · Licence: ").append(set.licence()).append("  \n");
        md.append("Recordings: ").append(set.recordings().size()).append(" (measured ").append(measured)
                .append(", skipped ").append(a.skipped().size()).append(")  \n");
        md.append("Rule: a sound under ").append(num(SoundFlags.PRACTICE_SOUND_BELOW)).append(" inside a word under ")
                .append(num(SoundFlags.WORD_BELOW)).append(" (practice and Try it); the extra vowel by insertion.  \n");
        md.append("Bar per sound: at least ").append(pct(Agreement.MIN_PRECISION)).append(" of Bolo's flags right and at least ")
                .append(pct(Agreement.MIN_RECALL)).append(" of the teacher's issues caught, judged with 5 or more teacher issues. ")
                .append("The gate passes when at least ").append(Agreement.MIN_PATTERNS_FOR_GATE).append(" sounds pass and none fail.\n\n");

        md.append("## Gate 1: ").append(a.gate().name().replace('_', ' ')).append("\n\n");
        md.append(switch (a.gate()) {
            case PASS -> "Every sound with enough data meets the bar.";
            case FAIL -> "At least one sound with enough data is below the bar.";
            case NOT_DECIDED -> "Not enough teacher-marked issues yet to judge " + Agreement.MIN_PATTERNS_FOR_GATE + " sounds.";
        }).append("\n\n");

        md.append("| Sound | Teacher issues | Bolo flags | Right | Wrong flags | Missed | Flags right | Issues caught | False flags on clear words | Verdict |\n");
        md.append("|---|---|---|---|---|---|---|---|---|---|\n");
        for (SoundPattern p : SoundPattern.values()) {
            var s = a.byPattern().get(p);
            md.append("| ").append(p.title()).append(" | ").append(s.tp() + s.fn()).append(" | ").append(s.tp() + s.fp())
                    .append(" | ").append(s.tp()).append(" | ").append(s.fp()).append(" | ").append(s.fn())
                    .append(" | ").append(pct(s.precision())).append(" | ").append(pct(s.recall()))
                    .append(" | ").append(s.fp() + s.tn() == 0 ? "–" : pct(s.falseFlagRate()) + " of " + (s.fp() + s.tn()))
                    .append(" | ").append(s.verdict().name().replace('_', ' ')).append(" |\n");
        }
        md.append("\nPlain agreement, clear words included (not the gate): ").append(Double.isNaN(a.rawAgreement()) ? "–" : pct(a.rawAgreement()))
                .append(" over ").append(a.chances()).append(" word–sound chances. Most words have no issue, so this stays high even when Bolo misses issues; use the verdicts above.\n\n");

        md.append("## Threshold sweep (flags right / issues caught)\n\n| Sound below |");
        for (SoundPattern p : SoundPattern.values()) md.append(' ').append(p.title()).append(" |");
        md.append("\n|---|").append("---|".repeat(SoundPattern.values().length)).append('\n');
        sweep.forEach((t, ag) -> {
            md.append("| ").append(num(t)).append(" |");
            for (SoundPattern p : SoundPattern.values()) {
                var s = ag.byPattern().get(p);
                md.append(' ').append(pct(s.precision())).append(" / ").append(pct(s.recall())).append(" |");
            }
            md.append('\n');
        });

        md.append("\n## Disagreements (for the teacher's review)\n\n");
        if (a.disagreements().isEmpty()) md.append("None.\n");
        else {
            md.append("| Recording | Word | Sound | Teacher | Bolo | Sound score |\n|---|---|---|---|---|---|\n");
            for (var d : a.disagreements()) {
                md.append("| ").append(d.recordingId()).append(" | ").append(d.word()).append(" | ").append(d.pattern().title())
                        .append(" | ").append(d.teacher() ? "issue" : "fine").append(" | ").append(d.bolo() ? "flagged" : "not flagged")
                        .append(" | ").append(d.soundScore() == null ? "–" : num(d.soundScore())).append(" |\n");
            }
        }
        if (!a.skipped().isEmpty()) {
            md.append("\n## Skipped\n\n");
            a.skipped().forEach(s -> md.append("- ").append(s.recordingId()).append(": ").append(s.reason()).append('\n'));
        }
        if (!a.offSound().isEmpty()) {
            md.append("\n## Teacher labels on words where Azure heard none of that sound\n\n");
            a.offSound().forEach(o -> md.append("- ").append(o.recordingId()).append(": ").append(o.word()).append(" (")
                    .append(o.pattern().title()).append(")\n"));
        }
        md.append("\n> Measured on scripted reading (the speaker reads known text), the way practice and Try it score. ")
                .append("The Coach scores free talk with unscripted recognition, which can score differently; that path needs its own check.\n");
        return md.toString();
    }

    private static String pct(Double v) {
        return v == null ? "–" : Math.round(v * 100) + "%";
    }

    private static String num(double v) {
        return v == Math.rint(v) ? String.valueOf((long) v) : String.valueOf(v);
    }
}
