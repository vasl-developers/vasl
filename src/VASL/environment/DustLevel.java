package VASL.environment;

import VASL.build.module.ASLDTODustMapShader;
import VASSAL.build.GameModule;

public enum DustLevel {
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
    public DustLevel getValueFromName (String dustName){  // can be deleted - did not use this approach.
        return DustLevel.NONE;
    }
    public static DustLevel getDustLevel(String s){
        switch (s) {
            case "NONE":
                return DustLevel.NONE;
            case "LIGHT":
                return DustLevel.LIGHT;
            case "MODERATE":
                return DustLevel.MODERATE;
            case "HEAVY":
                return DustLevel.HEAVY;
            case "VERY_HEAVY":
                return DustLevel.VERY_HEAVY;
            case "EXTREMELY_HEAVY":
                return DustLevel.EXTREMELY_HEAVY;
            case "SPECIAL":
                return DustLevel.SPECIAL;
            default:
                return DustLevel.NONE;

        }
    }
    public DustLevel next() {
        return values()[ordinal() + 1];
    }
}
