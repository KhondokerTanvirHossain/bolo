package app.bolo.talk.domain;

/** One spoken line in a talk. */
public record Exchange(Speaker speaker, String text) {

    public enum Speaker { BUDDY, LEARNER }
}
