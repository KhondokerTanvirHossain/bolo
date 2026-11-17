package app.bolo.common.text;

/** Founder 2026-11-18: text written in Bangla script (U+0980-U+09FF), as the Bangla recogniser writes a Bangla turn. */
public final class Bangla {

    private Bangla() {
    }

    /** At least half the words carry Bangla letters. */
    public static boolean is(String text) {
        if (text == null || text.isBlank()) return false;
        String[] words = text.strip().split("\\s+");
        long bangla = java.util.Arrays.stream(words).filter(w -> w.codePoints().anyMatch(c -> c >= 0x0980 && c <= 0x09FF)).count();
        return bangla * 2 >= words.length;
    }
}
