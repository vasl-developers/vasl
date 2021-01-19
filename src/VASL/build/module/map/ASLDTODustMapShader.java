package VASL.build.module.map;

import VASSAL.build.GameModule;
import VASSAL.build.module.GameState;
import VASSAL.build.module.map.MapShader;
import VASSAL.build.module.properties.GlobalProperty;

public class ASLDTODustMapShader extends MapShader {
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

    private DustLevel dustLevel = DustLevel.NONE;
    private GlobalProperty globalDustLevel = new GlobalProperty();
    public ASLDTODustMapShader() {
        super();
        globalDustLevel.setPropertyName("dust_level");
        globalDustLevel.setAttribute("initialValue", dustLevel.name());
        GameModule gm = GameModule.getGameModule();
        gm.addMutableProperty("dust_level", globalDustLevel);
    }

    @Override
    protected void toggleShading() {
        dustLevel = dustLevel.next();
        GameModule.getGameModule().getChatter().send(dustLevel.toString() + " is in effect." );

        this.setShadingVisibility(setDustAndOpacity());
    }

    private boolean setDustAndOpacity() {
        switch (dustLevel) {
            case NONE:
                opacity = 0;
                break;
            case LIGHT:
                opacity = 5;
                break;
            case MODERATE:
                opacity = 10;
                break;
            case HEAVY:
                opacity = 15;
                break;
            case VERY_HEAVY:
                opacity = 20;
                break;
            case EXTREMELY_HEAVY:
                opacity = 25;
                break;
        }
        globalDustLevel.setAttribute("initialValue", dustLevel.name());
        buildComposite();
        return dustLevel != DustLevel.NONE;
    }

}
