package app.bolo.talk.domain;

import java.util.List;
import java.util.Locale;

/**
 * A fast keyword screen (TLK-14). A match makes the relay wait for the safety classifier before Bolo speaks; without
 * one, the classifier still runs in parallel and can stop Bolo mid-reply. Includes common Bangla transliterations.
 */
public final class CrisisScreen {

    static final List<String> PHRASES = List.of("kill myself", "killing myself", "suicide", "suicidal", "want to die",
            "wanna die", "end my life", "take my life", "hurt myself", "hurting myself", "self harm", "self-harm",
            "no reason to live", "better off dead", "morte chai", "mora chai", "atmohotta", "attohotta", "bachte chai na");

    private CrisisScreen() {
    }

    public static boolean mayBeCrisis(String text) {
        String t = text == null ? "" : text.toLowerCase(Locale.ROOT);
        return PHRASES.stream().anyMatch(t::contains);
    }
}
