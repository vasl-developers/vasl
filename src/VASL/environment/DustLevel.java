package VASL.environment;

public enum DustLevel {
    NONE ("No Dust"),
    LIGHT ("Light Dust (F11.71)"),
    MODERATE("Moderate Dust (F11.72)"),
    HEAVY("Heavy Dust (F11.73)"),
    VERY_HEAVY("Very Heavy Dust (F11.731)"),
    EXTREMELY_HEAVY("Extremely Heavy Dust (F11.732)") {
        @Override
        public DustLevel next() {
            return NONE;
        };
    };

    private String dustLevelDescription;

    private DustLevel(String s) {
        dustLevelDescription = s;
    }
    public String toString() {
        return this.dustLevelDescription;
    }

    public DustLevel next() {
        return values()[ordinal() + 1];
    }

    public boolean dustInEffect() {
        return this != NONE;
    }

    public boolean isLightDust() {
        return (this == LIGHT || this == HEAVY || this == VERY_HEAVY);
    }
}
