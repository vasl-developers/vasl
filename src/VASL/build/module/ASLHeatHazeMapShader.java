package VASL.build.module;

import VASL.build.module.shader.*;
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
  public void setup(boolean gameStarting) {
    super.setup(gameStarting);
    Environment env = new Environment();
    Command command;
    if (env.isHeatHaze()) {
      command = new ActivateHeatHazeShaderCommand();
    } else {
      command = new DeactivateHeatHazeShaderCommand();
    }
    command.execute();
  }

  public Command getRestoreCommand() {
//    Environment env = new Environment();
//    if (env.isHeatHaze()) {
//      return new ActivateHeatHazeShaderCommand();
//    }
//    return new DeactivateHeatHazeShaderCommand();
    return null;
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
    Command setPropertyCommand = levelProperty.setPropertyValue(tempHeatHazeLevel.name());
    setPropertyCommand.execute();
    gm.sendAndLog(setPropertyCommand);

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
