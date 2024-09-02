package VASL.build.module;

import VASL.build.module.shader.ActivateHeatHazeShaderCommand;
import VASL.build.module.shader.DeactivateHeatHazeShaderCommand;
import VASL.environment.Environment;
import VASL.environment.HeatHazeLevel;
import VASSAL.build.GameModule;
import VASSAL.build.module.map.MapShader;
import VASSAL.build.module.properties.MutableProperty;
import VASSAL.command.Command;

import javax.swing.*;


public class ASLHeatHazeMapShader extends MapShader {

  public ASLHeatHazeMapShader() {
    super();
  }

  @Override
  protected void toggleShading() {

    this.boardClip=null;

    Environment env = new Environment();

    Object[] possibilities = HeatHazeLevel.values();
    HeatHazeLevel tempHeatHazeLevel = (HeatHazeLevel) JOptionPane.showInputDialog(
        getLaunchButton().getParent(),
        "Select Dust Type:",
        "Dust Type",
        JOptionPane.PLAIN_MESSAGE,
        getLaunchButton().getIcon(),
        possibilities,
        env.getCurrentHeatHazeLevel().toString());

    if (tempHeatHazeLevel == null) return;

    GameModule gm = GameModule.getGameModule();
    MutableProperty levelProperty = gm.getMutableProperty(Environment.HEAT_HAZE_LEVEL_PROPERTY);
    if (levelProperty == null) return;
    levelProperty.setPropertyValue(tempHeatHazeLevel.name()).execute();

    Command visibilityCommand;
    if (tempHeatHazeLevel == HeatHazeLevel.NONE) {
      visibilityCommand = new DeactivateHeatHazeShaderCommand();
    } else {
      visibilityCommand = new ActivateHeatHazeShaderCommand();
    }

    visibilityCommand.execute();
    gm.sendAndLog(visibilityCommand);

    gm.getChatter().send(tempHeatHazeLevel + " is in effect.");

  }

}
