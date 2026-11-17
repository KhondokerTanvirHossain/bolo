package app.bolo.speech.application.port.in;

/** A turn recognised in Bangla (bn-IN): the text in Bangla script and the recogniser's 0-1 confidence. */
public record HeardBangla(String text, double confidence) {
}
