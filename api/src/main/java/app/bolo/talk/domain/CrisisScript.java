package app.bolo.talk.domain;

import java.util.List;

/**
 * TLK-14's approved crisis response: fixed content, never generated (Architecture §4.2). Spoken in Bolo's voice and
 * shown as a help card; the practice stops. Numbers verified 2026-11-08 (findahelpline.com, kaanpeteroi.org) and
 * PENDING FOUNDER APPROVAL before any pilot learner uses Bolo.
 */
public final class CrisisScript {

    public record Help(String name, String number, String hours) {
    }

    public static final String TITLE = "You're not alone";

    public static final List<String> LINES = List.of(
            "I'm really glad you told me.",
            "What you're feeling matters, and you don't have to carry it alone.",
            "Please talk to someone who can help right now.",
            "In Bangladesh you can call Kaan Pete Roi, from three in the afternoon to three at night, or 999 if you are in danger.");

    public static final List<Help> HELP = List.of(
            new Help("Kaan Pete Roi (emotional support)", "09612-119911", "3 PM – 3 AM, every day"),
            new Help("National emergency", "999", "Any time"));

    private CrisisScript() {
    }
}
