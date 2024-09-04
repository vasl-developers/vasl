package VASL.build.module;

import VASL.build.module.shader.*;
import VASL.environment.Environment;
import VASL.environment.LVLevel;
import VASSAL.build.GameModule;
import VASSAL.build.module.map.MapShader;
import VASSAL.build.module.properties.MutableProperty;
import VASSAL.command.Command;

import javax.swing.*;

public class ASLLVMapShader extends MapShader {

  public ASLLVMapShader() {
    super();
    opacity = 20;
  }

  @Override
  public void setup(boolean gameStarting) {
    super.setup(gameStarting);
    Environment env = new Environment();
    Command command;
    if (env.isLV()) {
      command = new ActivateLowVisibilityShaderCommand();
    } else {
      command = new DeactivateLowVisibilityShaderCommand();
    }
    command.execute();
  }

  public Command getRestoreCommand() {
//    Environment env = new Environment();
//    if (env.isLV()) {
//      return new ActivateLowVisibilityShaderCommand();
//    }
//    return new DeactivateLowVisibilityShaderCommand();
    return null;
  }

  @Override
  protected void toggleShading() {

    this.boardClip=null;

    Environment env = new Environment();

    Object[] possibilities = LVLevel.values();
    LVLevel tempLvLevel = (LVLevel) JOptionPane.showInputDialog(
        getLaunchButton().getParent(),
        "Select LV type:",
        "LV Type",
        JOptionPane.PLAIN_MESSAGE,
        getLaunchButton().getIcon(),
        possibilities,
        env.getCurrentLVLevel().toString());

    if (tempLvLevel == null) return;

    // since Commands don't accept parameters outside of encode/decode,
    // inject the opacity into the game before we turn on/off visibility

    GameModule gm = GameModule.getGameModule();
    MutableProperty levelProperty = gm.getMutableProperty(Environment.LOW_VIS_LEVEL_PROPERTY);
    if (levelProperty == null) return;
    Command setPropertyCommand = levelProperty.setPropertyValue(tempLvLevel.name());
    setPropertyCommand.execute();
    gm.sendAndLog(setPropertyCommand);

    Command command;
    if (tempLvLevel == LVLevel.NONE) {
      command = new DeactivateLowVisibilityShaderCommand();
    } else {
      command = new ActivateLowVisibilityShaderCommand();
    }

    command.execute();
    gm.sendAndLog(command);

    gm.getChatter().send(tempLvLevel + " is in effect.");

  }

}
