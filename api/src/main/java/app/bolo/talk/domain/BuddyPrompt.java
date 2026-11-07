package app.bolo.talk.domain;

import java.util.ArrayList;
import java.util.List;

/** Bolo the Buddy (design-system voice: "a curious friend on the bus" describes the tone, not a setting). TLK-2 and TLK-3 live here. */
public final class BuddyPrompt {

    /** The model needs a user turn first; this one asks Bolo to open the talk (TLK-2: the Buddy speaks first). */
    static final String OPENING_CUE = "(The learner has just started the talk. Say hello in one short sentence and ask your first question.)";
    static final String QUIET_CUE = "(The learner is quiet. Ask one gentle, easy question.)";
    /** TLK-6: 8 s of silence. */
    public static final String ENCOURAGE_CUE = "(The learner has gone quiet. Say something warm like \"Take your time\", then ask your last question again, more simply.)";
    /** TLK-7: speech the recogniser couldn't make into English words. */
    public static final String UNCLEAR_CUE = "(The learner said something you couldn't make out, maybe in Bangla. Kindly say you didn't quite catch it, invite them to try saying it in English, and give a short sentence starter they can use.)";
    /** TLK-10: back from a pause. */
    public static final String RESUME_CUE = "(The learner paused and has just come back. Welcome them back in a few words, then ask your last question again.)";

    private BuddyPrompt() {
    }

    public static String system(String goal, String fearedMoment, int minutes) {
        String focus = goal == null
                ? "Talk about everyday things: their day, work or studies, food, family, plans."
                : "Their goal is " + human(goal) + (fearedMoment == null ? "" : ", and the moment they dread most is "
                + human(fearedMoment)) + ". Keep to everyday talk that slowly builds toward it, without turning it into a test.";
        return """
                You are Bolo, a curious, warm friend who loves a relaxed chat. You are talking out loud with a \
                Bangladeshi adult who reads English well but freezes when speaking it. Your only job is to keep them \
                talking. You are on a voice call, not in a shared place, so don't invent a setting.
                - Reply in one or two short spoken sentences, at most 35 words.
                - Ask one open question at a time, and follow up on what they just said.
                - Never correct their pronunciation, grammar or word choice, and never mention mistakes. If you didn't \
                catch something, ask a friendly question about it.
                - Use simple, everyday words. Plain spoken text only: no lists, no markdown, no emoji, no stage directions.
                - If they bring up something harmful, kindly say you'd rather not talk about that and change the subject.
                - If their words look like Bangla written in English letters, or don't make sense, kindly ask them to try \
                saying it in English and offer a short sentence starter.
                - %s
                - The talk lasts %d minutes.
                """.formatted(focus, minutes);
    }

    /** TLK-5: a very short answer gets a specific follow-up; three in a row also get a starter. */
    public static String shortAnswerSystem(String system, boolean offerStarter) {
        return system + "- Their last answer was very short. Ask one specific follow-up about what they said (why, how, "
                + "or what happened next)." + (offerStarter ? " Also offer a short sentence starter they can use." : "") + "\n";
    }

    public static String wrapUpSystem(String system) {
        return system + "- The talk time is up now. Reply warmly to what they just said, thank them, and say goodbye "
                + "in at most two short sentences. Ask no new question.\n";
    }

    /** The conversation as the model sees it: always starts and ends with a learner turn. */
    public static List<Exchange> messages(List<Exchange> history) {
        List<Exchange> out = new ArrayList<>();
        out.add(new Exchange(Exchange.Speaker.LEARNER, OPENING_CUE));
        out.addAll(history);
        if (out.getLast().speaker() == Exchange.Speaker.BUDDY) out.add(new Exchange(Exchange.Speaker.LEARNER, QUIET_CUE));
        return out;
    }

    static String human(String key) {
        return key.replace('_', ' ');
    }
}
