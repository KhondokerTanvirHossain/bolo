package app.bolo.goldenset;

import app.bolo.assessment.domain.SoundPattern;
import app.bolo.common.audio.Wav;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * A labelled set of recordings for the Gate 1 accuracy harness: each recording's audio, the text that was read, and
 * the words where the teacher heard a pattern swap. Loaded from {@code manifest.json} in the set's directory.
 */
public record GoldenSet(Path dir, String name, String source, String licence, List<Recording> recordings) {

    /** Azure's REST short-audio limit. */
    static final double MAX_SECONDS = 60;

    public record Recording(String id, Path audio, String reference, String labelledBy, Set<Label> labels) {
    }

    /** A teacher's flag: the reference word (by index in {@link Alignment#tokens}) and the pattern heard there. */
    public record Label(int refIndex, SoundPattern pattern) {
    }

    record IssueJson(String word, Integer nth, String pattern) {
    }

    record RecordingJson(String id, String audio, String reference, String labelledBy, List<IssueJson> issues) {
    }

    record ManifestJson(String name, String source, String licence, List<RecordingJson> recordings) {
    }

    private static final JsonMapper JSON = JsonMapper.builder().build();

    public static GoldenSet load(Path dir) {
        Path file = dir.resolve("manifest.json");
        ManifestJson m;
        try {
            m = JSON.readValue(Files.readString(file), ManifestJson.class);
        } catch (IOException | RuntimeException e) {
            throw new GoldenSetException("can't read " + file + ": " + e.getClass().getSimpleName());
        }
        if (m.recordings() == null || m.recordings().isEmpty()) throw new GoldenSetException(file + " has no recordings");
        List<Recording> out = new ArrayList<>();
        Set<String> ids = new HashSet<>();
        for (RecordingJson r : m.recordings()) {
            if (r.id() == null || r.id().isBlank()) throw new GoldenSetException("a recording has no id");
            if (!ids.add(r.id())) throw new GoldenSetException("recording id " + r.id() + " is used twice");
            if (r.reference() == null || r.reference().isBlank()) throw new GoldenSetException(r.id() + ": no reference text");
            Path audio = dir.resolve(r.audio() == null ? "" : r.audio());
            checkAudio(r.id(), audio);
            List<String> tokens = Alignment.tokens(r.reference());
            Set<Label> labels = new LinkedHashSet<>();
            for (IssueJson i : r.issues() == null ? List.<IssueJson>of() : r.issues()) labels.add(label(r.id(), tokens, i));
            out.add(new Recording(r.id(), audio, r.reference(), r.labelledBy(), Set.copyOf(labels)));
        }
        return new GoldenSet(dir, m.name(), m.source(), m.licence(), List.copyOf(out));
    }

    private static Label label(String id, List<String> tokens, IssueJson i) {
        SoundPattern pattern;
        try {
            pattern = SoundPattern.valueOf(String.valueOf(i.pattern()));
        } catch (IllegalArgumentException e) {
            throw new GoldenSetException(id + ": unknown pattern " + i.pattern());
        }
        String word = Alignment.norm(String.valueOf(i.word()));
        List<Integer> at = new ArrayList<>();
        for (int k = 0; k < tokens.size(); k++) if (tokens.get(k).equals(word)) at.add(k);
        if (at.isEmpty()) throw new GoldenSetException(id + ": \"" + i.word() + "\" is not in the reference text");
        if (i.nth() == null) {
            if (at.size() > 1) {
                throw new GoldenSetException(id + ": \"" + i.word() + "\" appears " + at.size() + " times; add \"nth\" (1 = first)");
            }
            return new Label(at.get(0), pattern);
        }
        if (i.nth() < 1 || i.nth() > at.size()) {
            throw new GoldenSetException(id + ": \"" + i.word() + "\" has no occurrence nth=" + i.nth() + " (it appears " + at.size() + " times)");
        }
        return new Label(at.get(i.nth() - 1), pattern);
    }

    private static void checkAudio(String id, Path audio) {
        String name = audio.getFileName() == null ? id : audio.getFileName().toString();
        byte[] wav;
        try {
            wav = Files.readAllBytes(audio);
        } catch (IOException e) {
            throw new GoldenSetException(id + ": can't read " + name);
        }
        if (wav.length < 44 || !"RIFF".equals(ascii(wav, 0)) || !"WAVE".equals(ascii(wav, 8))) {
            throw new GoldenSetException(id + ": " + name + " is not a WAV file (convert it: 16 kHz, mono, 16-bit)");
        }
        int i = 12;
        while (i + 8 <= wav.length) {
            int size = ByteBuffer.wrap(wav, i + 4, 4).order(ByteOrder.LITTLE_ENDIAN).getInt();
            if ("fmt ".equals(ascii(wav, i)) && i + 24 <= wav.length) {
                ByteBuffer b = ByteBuffer.wrap(wav, i + 8, 16).order(ByteOrder.LITTLE_ENDIAN);
                int format = b.getShort() & 0xffff, channels = b.getShort() & 0xffff, rate = b.getInt();
                b.getInt();
                b.getShort();
                int bits = b.getShort() & 0xffff;
                if (format != 1 || channels != 1 || rate != 16000 || bits != 16) {
                    throw new GoldenSetException(id + ": " + name + " must be 16 kHz, mono, 16-bit PCM (it is " + rate + " Hz, "
                            + channels + " channel(s), " + bits + "-bit)");
                }
                double seconds = Wav.pcm(wav).length / 32000.0;
                if (seconds > MAX_SECONDS) {
                    throw new GoldenSetException(id + ": " + name + " is " + Math.round(seconds) + " s; split it to at most 60 s");
                }
                return;
            }
            i += 8 + Math.max(0, size) + (size & 1);
        }
        throw new GoldenSetException(id + ": " + name + " has no format chunk");
    }

    private static String ascii(byte[] b, int at) {
        return new String(b, at, 4, StandardCharsets.US_ASCII);
    }
}
