package VASL.build.module;

import VASL.environment.DustLevel;
import VASSAL.build.GameModule;
import VASSAL.build.module.map.MapShader;
import VASSAL.build.module.properties.GlobalProperty;

public class ASLDTODustMapShader extends MapShader {
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
