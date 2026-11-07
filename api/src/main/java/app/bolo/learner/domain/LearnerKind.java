package app.bolo.learner.domain;

public enum LearnerKind {
    GUEST("guest"), ACCOUNT("account");

    private final String dbValue;

    LearnerKind(String dbValue) {
        this.dbValue = dbValue;
    }

    public String dbValue() {
        return dbValue;
    }

    public static LearnerKind fromDbValue(String value) {
        for (LearnerKind k : values()) if (k.dbValue.equals(value)) return k;
        throw new IllegalArgumentException("Unknown learner kind: " + value);
    }
}
