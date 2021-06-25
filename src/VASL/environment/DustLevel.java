package VASL.environment;

import VASL.build.module.ASLDTODustMapShader;
import VASSAL.build.GameModule;

public enum     DustLevel {
    NONE ("No Dust"),
    LIGHT ("Light Dust (F11.71)"),
    MODERATE("Moderate Dust (F11.72)"),
    HEAVY("Heavy Dust (F11.73)"),
    VERY_HEAVY("Very Heavy Dust (F11.731)"),
    EXTREMELY_HEAVY("Extremely Heavy Dust (F11.732)"),
    SPECIAL("Special Dust (Preferences->Environment)")
    {
        @Override
        public DustLevel next() {
            return NONE;
        };
    };

    private String dustLevelDescription;

    DustLevel(String s) {
        dustLevelDescription = s;
    }

    public String toString() {
        if(values()[ordinal()] == SPECIAL){
            return (String) GameModule.getGameModule().getPrefs().getValue(ASLDTODustMapShader.SPECIAL_DUST_NAME);
        }
        return this.dustLevelDescription;
    }

    public DustLevel next() {
        return values()[ordinal() + 1];
    }
}
