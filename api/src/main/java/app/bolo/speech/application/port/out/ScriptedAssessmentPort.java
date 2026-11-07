package app.bolo.speech.application.port.out;

import app.bolo.speech.application.port.in.PronunciationResult;

public interface ScriptedAssessmentPort {

    String vendor();

    PronunciationResult assess(byte[] wav16k, String reference);
}
