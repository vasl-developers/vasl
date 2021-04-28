package VASL.build.module;

import VASL.environment.DustLevel;
import VASSAL.build.Buildable;
import VASSAL.build.GameModule;
import VASSAL.build.module.map.MapShader;
import VASSAL.build.module.properties.GlobalProperty;
import VASSAL.configure.BooleanConfigurer;
import VASSAL.configure.StringConfigurer;
import VASSAL.configure.StringEnumConfigurer;
import VASSAL.preferences.Prefs;

import static VASL.environment.DustLevel.*;

public class ASLDTODustMapShader extends MapShader {
    public final static String ENVIRONMENT = "Environment";
    public final static String SPECIAL_DUST_SETTING = "UseSpecialDustSetting";
    public final static String SPECIAL_DUST_NAME = "SpecialDustName";
    public final static String SPECIAL_DUST_DIVIDE_BY = "SpecialDustDivideBy";
    public final static String SPECIAL_DUST_ROUNDING = "SpecialDustRounding";

    private DustLevel dustLevel = NONE;
    private BooleanConfigurer useSpecialDustSetting;
    private final GlobalProperty globalDustLevel = new GlobalProperty();

    public ASLDTODustMapShader() {
        super();
        globalDustLevel.setPropertyName("dust_level");
        globalDustLevel.setAttribute("initialValue", dustLevel.name());
        GameModule gm = GameModule.getGameModule();
        gm.addMutableProperty("dust_level", globalDustLevel);
    }

    @Override
    public void addTo(Buildable b) {
        super.addTo(b);
        Prefs modulePreferences = GameModule.getGameModule().getPrefs();

        if(modulePreferences.getValue(SPECIAL_DUST_SETTING) == null) {
            useSpecialDustSetting = new BooleanConfigurer(SPECIAL_DUST_SETTING, "Use Special Dust Setting", false);
            modulePreferences.addOption(ENVIRONMENT, useSpecialDustSetting);
            useSpecialDustSetting.addPropertyChangeListener( listener -> {
                boolean specialDust = (Boolean)listener.getNewValue();
                if( specialDust) {
                    dustLevel = SPECIAL;
                } else {
                    dustLevel = NONE;
                }
                setShadingVisibility(setDustAndOpacity());
                if(GameModule.getGameModule().getChatter()!= null) {
                    GameModule.getGameModule().getChatter().send( (specialDust ? "Special dust is in effect" : "No Dust is in effect"));
                }

            });
        }
        if(modulePreferences.getValue(SPECIAL_DUST_NAME) == null) {
            StringConfigurer dustName = new StringConfigurer(SPECIAL_DUST_NAME, "Dust Type Name (For chatter)", "Special Dust");
            modulePreferences.addOption(ENVIRONMENT, dustName);
        }
        if(modulePreferences.getValue(SPECIAL_DUST_DIVIDE_BY) == null) {
            String [] divideValues = {"1","2","3","4","5","6"};
            StringEnumConfigurer divideBy = new StringEnumConfigurer(SPECIAL_DUST_DIVIDE_BY, "Divide roll by", divideValues);
            modulePreferences.addOption(ENVIRONMENT, divideBy);
        }
        if(modulePreferences.getValue(SPECIAL_DUST_ROUNDING) == null) {
            String [] roundingValues = {"up","down"};
            StringEnumConfigurer rounding = new StringEnumConfigurer(SPECIAL_DUST_ROUNDING, "Fractions rounded", roundingValues);
            modulePreferences.addOption(ENVIRONMENT, rounding);
        }
        useSpecialDustSetting.fireUpdate();
    }


    @Override
    protected void toggleShading() {
        dustLevel = dustLevel.next();
        if(GameModule.getGameModule().getChatter()!= null) {
            GameModule.getGameModule().getChatter().send(dustLevel.toString() + " is in effect.");
        }
        if(dustLevel == SPECIAL) {
            useSpecialDustSetting.setValue(true);
        } else {
            useSpecialDustSetting.setValue(false);
        }

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
            case SPECIAL: {
                opacity = 10;
                break;
            }
        }
        globalDustLevel.setAttribute("initialValue", dustLevel.name());
        buildComposite();
        return dustLevel != NONE;
    }

}
