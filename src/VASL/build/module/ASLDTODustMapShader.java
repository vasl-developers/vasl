package VASL.build.module;

import VASL.build.module.shader.ActivateDustShaderCommand;
import VASL.build.module.shader.DeactivateDustShaderCommand;
import VASL.environment.DustLevel;
import VASL.environment.Environment;
import VASSAL.build.Buildable;
import VASSAL.build.GameModule;
import VASSAL.build.module.map.MapShader;
import VASSAL.build.module.properties.MutableProperty;
import VASSAL.command.Command;
import VASSAL.configure.BooleanConfigurer;
import VASSAL.configure.StringConfigurer;
import VASSAL.configure.StringEnumConfigurer;
import VASSAL.preferences.Prefs;

import javax.swing.*;

public class ASLDTODustMapShader extends MapShader {
    public final static String ENVIRONMENT = "Environment";
    public final static String SPECIAL_DUST_SETTING = "UseSpecialDustSetting";
    public final static String SPECIAL_DUST_NAME = "SpecialDustName";
    public final static String SPECIAL_DUST_DIVIDE_BY = "SpecialDustDivideBy";
    public final static String SPECIAL_DUST_ROUNDING = "SpecialDustRounding";

    private BooleanConfigurer useSpecialDustSetting;

    public ASLDTODustMapShader() {
        super();
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

                if (specialDust) {
                    GameModule gm = GameModule.getGameModule();
                    MutableProperty levelProperty = gm.getMutableProperty(Environment.DUST_LEVEL_PROPERTY);
                    levelProperty.setPropertyValue(DustLevel.SPECIAL.name()).execute();
                    Command command = new ActivateDustShaderCommand();
                    command.execute();
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

        this.boardClip=null;

        Environment env = new Environment();

        Object[] possibilities = DustLevel.values();
        DustLevel tempDustLevel = (DustLevel) JOptionPane.showInputDialog(
            getLaunchButton().getParent(),
            "Select Dust Type:",
            "Dust Type",
            JOptionPane.PLAIN_MESSAGE,
            getLaunchButton().getIcon(),
            possibilities,
            env.getCurrentDustLevel().toString());

        if (tempDustLevel == null) return;

        GameModule gm = GameModule.getGameModule();
        MutableProperty levelProperty = gm.getMutableProperty(Environment.DUST_LEVEL_PROPERTY);
        if (levelProperty == null) return;
        levelProperty.setPropertyValue(tempDustLevel.name()).execute();

        Command visibilityCommand;
        if (tempDustLevel == DustLevel.NONE) {
            visibilityCommand = new DeactivateDustShaderCommand();
        } else {
            visibilityCommand = new ActivateDustShaderCommand();
        }

        visibilityCommand.execute();

        // if we ever wanted to sync MapShader state between clients, this would need to happen
        //noinspection ConstantValue
        if (false) {
            gm.sendAndLog(visibilityCommand);
        }

        gm.getChatter().send(tempDustLevel + " is in effect.");

        useSpecialDustSetting.setValue(tempDustLevel == DustLevel.SPECIAL);

    }


}
