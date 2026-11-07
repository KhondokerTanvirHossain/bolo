package app.bolo.talk.application.port.in;

/** done · played(turn) · heard(turn, ms) · end */
public record ClientMessage(String type, Integer turn, Integer ms) {
}
