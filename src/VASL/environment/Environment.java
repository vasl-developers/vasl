package VASL.environment;

import VASL.build.module.ASLDTODustMapShader;
import VASSAL.build.GameModule;
import VASSAL.build.module.properties.MutableProperty;
import VASSAL.configure.BooleanConfigurer;
import VASSAL.configure.StringEnumConfigurer;

import static VASL.build.module.ASLDTODustMapShader.SPECIAL_DUST_DIVIDE_BY;
import static VASL.build.module.ASLDTODustMapShader.SPECIAL_DUST_ROUNDING;

public class Environment
{

    public static final String NIGHT_VISIBILITY_PROPERTY = "NightShaderVisibility";
    public static final String FOG_VISIBILITY_PROPERTY = "FogShaderVisibility";
    public static final String FOG_LEVEL_PROPERTY = "FogShaderLevel";
    public static final String DUST_VISIBILITY_PROPERTY = "DustShaderVisibility";
    public static final String DUST_LEVEL_PROPERTY = "DustShaderLevel";
    public static final String LOW_VIS_VISIBILITY_PROPERTY = "LowVisShaderVisibility";
    public static final String LOW_VIS_LEVEL_PROPERTY = "LowVisShaderLevel";

    public static final String HEAT_HAZE_VISIBILITY_PROPERTY = "HeatHazeShaderVisibility";
    public static final String HEAT_HAZE_LEVEL_PROPERTY = "HeatHazeShaderLevel";
    public static final String SUN_BLIND_VISIBILITY_PROPERTY = "SunBlindShaderVisibility";
    public static final String SUN_BLIND_LEVEL_PROPERTY = "SunBlindShaderLevel";

    private BooleanConfigurer specialDustActiveConfigurer = null;
    private StringEnumConfigurer specialDustDivisorConfigurer = null;
    private StringEnumConfigurer specialDustRoundingConfigurer = null;
    private boolean specialDustActive = false;
    private int divisor;
    private boolean FRU;

    public Environment(){

    }

    public String tryGetMutableProperty(String propertyName, String valueIfMissing) {
        final String ERROR_NO_PROPERTY = "buildFile does not contain expected GlobalProperty " + propertyName;
        GameModule gm = GameModule.getGameModule();
        MutableProperty propertyObj = gm.getMutableProperty(propertyName);
        if (propertyObj == null) {
            gm.warn(ERROR_NO_PROPERTY);
            return valueIfMissing;
        }
        return propertyObj.getPropertyValue();
    }

    public DustLevel getCurrentDustLevel()
    {
        return DustLevel.valueOf(tryGetMutableProperty(DUST_LEVEL_PROPERTY, DustLevel.NONE.toString()));
    }

    public LVLevel getCurrentLVLevel()
    {
        return LVLevel.valueOf(tryGetMutableProperty(LOW_VIS_LEVEL_PROPERTY, LVLevel.NONE.toString()));
    }

    public FogLevel getCurrentFogLevel()
    {
        return FogLevel.valueOf(tryGetMutableProperty(FOG_LEVEL_PROPERTY, FogLevel.NONE.toString()));
    }

    public int getOpacity(HeatHazeLevel heatHazeLevel) {
        switch (heatHazeLevel) {
            case HEAT_HAZE:
                return 5;
            case INTENSE_HEAT_HAZE:
                return 15;
            default:
                return 0;
        }
    }

    public int getOpacity(FogLevel fogLevel) {
        switch (fogLevel) {
            case LIGHT_FOGM1:
            case LIGHT_FOGL0:
            case LIGHT_FOGL1:
            case LIGHT_FOGL2:
            case LIGHT_FOGL3:
            case LIGHT_FOGL4:
                return 35;
            case MODERATE_FOGM1:
            case MODERATE_FOGL0:
            case MODERATE_FOGL1:
            case MODERATE_FOGL2:
            case MODERATE_FOGL3:
            case MODERATE_FOGL4:
                return 40;
            case HEAVY_FOGM1:
            case HEAVY_FOGL0:
            case HEAVY_FOGL1:
            case HEAVY_FOGL2:
            case HEAVY_FOGL3:
            case HEAVY_FOGL4:
                return 45;
            default:
                return 0;
        }
    }

    public int getOpacity(DustLevel dustLevel) {
        switch (dustLevel) {
            case LIGHT:
                return 5;
            case MODERATE:
            case SPECIAL:
                return 10;
            case HEAVY:
                return 15;
            case VERY_HEAVY:
                return 20;
            case EXTREMELY_HEAVY:
                return 25;
            default:
                return 0;
        }
    }

    public HeatHazeLevel getCurrentHeatHazeLevel()
    {
        return HeatHazeLevel.valueOf(tryGetMutableProperty(HEAT_HAZE_LEVEL_PROPERTY, HeatHazeLevel.NONE.toString()));
    }

    public SunBlindnessLevel getCurrentSunBlindnessLevel()
    {
        return SunBlindnessLevel.valueOf(tryGetMutableProperty(SUN_BLIND_LEVEL_PROPERTY, SunBlindnessLevel.NONE.toString()));
    }


    public boolean dustInEffect() {
        return getCurrentDustLevel() != DustLevel.NONE || isSpecialDust();
    }

    public boolean isSpecialDust() {
        if(specialDustActiveConfigurer == null)
        {
            lazyInitialiseDustStateListener();
        }
        return specialDustActive;
    }

    private void lazyInitialiseDustStateListener() {
        specialDustActiveConfigurer = (BooleanConfigurer) GameModule.getGameModule().getPrefs().getOption(ASLDTODustMapShader.SPECIAL_DUST_SETTING);
        specialDustActive = specialDustActiveConfigurer.booleanValue();
        specialDustActiveConfigurer.addPropertyChangeListener( listener -> specialDustActive = (Boolean)listener.getNewValue());
    }

    public boolean isLightDust() {
        DustLevel dustLevel = getCurrentDustLevel();
        return (dustLevel == DustLevel.LIGHT || dustLevel == DustLevel.HEAVY || dustLevel == DustLevel.VERY_HEAVY);
    }

    public static int getLightDust(final int roll) {
        return roll / 2;
    }

    public static int getModerateDust(final int roll) {
        return (roll / 2) + (roll % 2);
    }

    public int getSpecialDust(final int roll) {
        if( specialDustRoundingConfigurer == null && specialDustDivisorConfigurer == null) {
            lazyInitialiseDivisorAndRoundingState();
        }
        if(FRU) {
            return (roll / divisor) + (roll % divisor);
        }
        return roll / divisor;
    }

    private void lazyInitialiseDivisorAndRoundingState(){
        GameModule gm = GameModule.getGameModule();
        specialDustDivisorConfigurer = (StringEnumConfigurer)gm.getPrefs().getOption(SPECIAL_DUST_DIVIDE_BY);
        divisor = Integer.parseInt(specialDustDivisorConfigurer.getValueString());
        specialDustDivisorConfigurer.addPropertyChangeListener( listener -> divisor = Integer.parseInt((String)listener.getNewValue()));
        specialDustRoundingConfigurer = (StringEnumConfigurer)gm.getPrefs().getOption(SPECIAL_DUST_ROUNDING);
        FRU = (specialDustRoundingConfigurer.getValueString().equals("up"));
        specialDustRoundingConfigurer.addPropertyChangeListener(listener -> FRU = (listener.getNewValue().equals("up")));
    }

    public boolean isDust() {
        return Boolean.parseBoolean(tryGetMutableProperty(DUST_VISIBILITY_PROPERTY, Boolean.FALSE.toString()));
    }

    public boolean isNight() {
        return Boolean.parseBoolean(tryGetMutableProperty(NIGHT_VISIBILITY_PROPERTY, Boolean.FALSE.toString()));
    }

    public boolean isLV() {
        return getCurrentLVLevel() != LVLevel.NONE && getCurrentLVLevel() != LVLevel.SHADE_ONLY;
    }

    public boolean isFog() {
        return getCurrentFogLevel() != FogLevel.NONE;
    }

    public boolean isHeatHaze() {
        return getCurrentHeatHazeLevel() != HeatHazeLevel.NONE;
    }

    public boolean isSunBlindness() {
        return getCurrentSunBlindnessLevel() != SunBlindnessLevel.NONE;
    }
}
