package VASL.environment;

import VASSAL.build.GameModule;

public class EnvironmentUtils
{
    public static DustLevel getCurrentDustLevel (final GameModule gm)
    {
        String value = gm.getMutableProperty("dust_level").getPropertyValue();
        return DustLevel.valueOf(value);
    }

    public static int getLightDust(final int roll) {
        return roll / 2;
    }

    public static int getModerateDust(final int roll) {
        return (roll / 2) + (roll % 2);
    }
}
