package VASL.environment;

import VASL.build.module.ASLDTODustMapShader;
import VASSAL.build.GameModule;
import VASSAL.configure.BooleanConfigurer;
import VASSAL.configure.StringEnumConfigurer;

import static VASL.build.module.ASLDTODustMapShader.SPECIAL_DUST_DIVIDE_BY;
import static VASL.build.module.ASLDTODustMapShader.SPECIAL_DUST_ROUNDING;
import static VASL.environment.DustLevel.*;

public class Environment
{
    private BooleanConfigurer specialDustActiveConfigurer = null;
    private StringEnumConfigurer specialDustDivisorConfigurer = null;
    private StringEnumConfigurer specialDustRoundingConfigurer = null;
    private boolean specialDustActive = false;
    private int divisor;
    private boolean FRU;
    public Environment(){

    }

    public DustLevel getCurrentDustLevel()
    {
        String value = GameModule.getGameModule().getMutableProperty("dust_level").getPropertyValue();
        return DustLevel.valueOf(value);
    }

    public LVLevel getCurrentLVLevel()
    {
        String value = GameModule.getGameModule().getMutableProperty("lv_level").getPropertyValue();
        return LVLevel.valueOf(value);
    }


    public FogLevel getCurrentFogLevel()
    {
        String value = GameModule.getGameModule().getMutableProperty("fog_level").getPropertyValue();
        return FogLevel.valueOf(value);
    }

    public HeatHazeLevel getCurrentHeatHazeLevel()
    {
        String value = GameModule.getGameModule().getMutableProperty("heat_haze_level").getPropertyValue();
        return HeatHazeLevel.valueOf(value);
    }

    public SunBlindnessLevel getCurrentSunBlindnessLevel()
    {
        String value = GameModule.getGameModule().getMutableProperty("sun_blindness").getPropertyValue();
        return SunBlindnessLevel.valueOf(value);
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
        return (dustLevel == LIGHT || dustLevel == HEAVY || dustLevel == VERY_HEAVY);
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
        specialDustDivisorConfigurer = (StringEnumConfigurer)GameModule.getGameModule().getPrefs().getOption(SPECIAL_DUST_DIVIDE_BY);
        divisor = Integer.parseInt(specialDustDivisorConfigurer.getValueString());
        specialDustDivisorConfigurer.addPropertyChangeListener( listener -> divisor = Integer.parseInt((String)listener.getNewValue()));
        specialDustRoundingConfigurer = (StringEnumConfigurer)GameModule.getGameModule().getPrefs().getOption(SPECIAL_DUST_ROUNDING);
        FRU = (specialDustRoundingConfigurer.getValueString().equals("up"));
        specialDustRoundingConfigurer.addPropertyChangeListener(listener -> FRU = (listener.getNewValue().equals("up")));
    }
    public boolean isNight() {
        String value = GameModule.getGameModule().getMutableProperty("night").getPropertyValue();
        return Boolean.parseBoolean(value);
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
