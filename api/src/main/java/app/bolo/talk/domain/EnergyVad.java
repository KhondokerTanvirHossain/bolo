package app.bolo.talk.domain;

/**
 * A frame is speech when its RMS level stands {@code marginDb} above the room's noise floor and above
 * {@code speechFloorDb}. The floor drops at once to any quieter frame and creeps up 0.1 dB per frame (5 dB/s), so it
 * settles on a steady fan or hiss within seconds while the dips between words keep pulling it back down.
 */
public class EnergyVad {

    private static final double RISE_PER_FRAME_DB = 0.1;

    private final double speechFloorDb;
    private final double marginDb;
    private double noiseFloorDb = -60;

    public EnergyVad() {
        this(-50, 12);
    }

    public EnergyVad(double speechFloorDb, double marginDb) {
        this.speechFloorDb = speechFloorDb;
        this.marginDb = marginDb;
    }

    public boolean isSpeech(short[] frame) {
        double level = levelDb(frame);
        boolean speech = level > Math.max(speechFloorDb, noiseFloorDb + marginDb);
        noiseFloorDb = Math.min(noiseFloorDb + RISE_PER_FRAME_DB, level);
        return speech;
    }

    static double levelDb(short[] frame) {
        if (frame.length == 0) return -120;
        double sum = 0;
        for (short s : frame) sum += (double) s * s;
        double rms = Math.sqrt(sum / frame.length) / 32768.0;
        return rms <= 0 ? -120 : 20 * Math.log10(rms);
    }
}
