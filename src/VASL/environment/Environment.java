package VASL.environment;

import VASSAL.build.GameModule;

import static VASL.environment.DustLevel.*;

public class Environment
{
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

    public boolean dustInEffect() {
        return getCurrentDustLevel() != DustLevel.NONE;
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

    public boolean isNight() {
        String value = GameModule.getGameModule().getMutableProperty("night").getPropertyValue();
        return Boolean.valueOf(value);
    }

    public boolean isLV() {
        if (getCurrentLVLevel() == LVLevel.NONE || getCurrentLVLevel() == LVLevel.SHADE_ONLY) {
          return false;
        }
        return true;
    }
// @todo mist, etc.
//    public boolean isMist() {
//        return getCurrentLVLevel() == LVLevel.MIST;
//    }
//
//    public boolean isFog() {
//        return getCurrentLVLevel() == LVLevel.FOG;
//    }
//
//    public boolean isHeatHaze() {
//        return (getCurrentLVLevel() == LVLevel.HEAT_HAZE || getCurrentLVLevel() == LVLevel.INTENSE_HEAT_HAZE);
//    }
//
//    public boolean isSunBlindness() {
//        return (getCurrentLVLevel() == LVLevel.EARLY_MORNING_SUN_BLINDNESS || getCurrentLVLevel() == LVLevel.LATE_AFTERNOON_SUN_BLINDNESS);
//    }
}
