package app.bolo.memory.domain;

/** One short line Bolo remembers about the learner (About you, Your interests). The id lets the learner remove it. */
public record Line(String id, String text) {
}
