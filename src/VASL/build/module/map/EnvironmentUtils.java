package VASL.build.module.map;

import VASSAL.build.GameModule;

public class EnvironmentUtils
{
    public static ASLDTODustMapShader.DustLevel getCurrentDustLevel ( final GameModule gm)
    {
        String value = gm.getMutableProperty("dust_level").getPropertyValue();
        return ASLDTODustMapShader.DustLevel.valueOf(value);
    }

    public static int getLightDust(final int roll) {
        return roll / 2;
    }

    public static int getModerateDust(final int roll) {
        return (roll / 2) + (roll % 2);
    }

}
