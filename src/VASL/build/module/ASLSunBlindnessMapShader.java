package VASL.build.module;

import VASL.build.module.shader.*;
import VASL.environment.Environment;
import VASL.environment.SunBlindnessLevel;
import VASSAL.build.GameModule;
import VASSAL.build.module.map.MapShader;
import VASSAL.command.Command;

import javax.swing.*;


public class ASLSunBlindnessMapShader extends MapShader {

  public ASLSunBlindnessMapShader() {
    super();
    opacity = 10;
  }

  @Override
  public void setup(boolean gameStarting) {
    super.setup(gameStarting);
    Environment env = new Environment();
    Command command;
    if (env.isSunBlindness()) {
      command = new ActivateSunBlindnessShaderCommand();
    } else {
      command = new DeactivateSunBlindnessShaderCommand();
    }
    command.execute();
  }

  public Command getRestoreCommand() {
//    Environment env = new Environment();
//    if (env.isSunBlindness()) {
//      return new ActivateSunBlindnessShaderCommand();
//    }
//    return new DeactivateSunBlindnessShaderCommand();
    return null;
  }

  @Override
  protected void toggleShading() {

    this.boardClip=null;

    Environment env = new Environment();

    Object[] possibilities = SunBlindnessLevel.values();
    SunBlindnessLevel tempSunBlindnessLevel = (SunBlindnessLevel) JOptionPane.showInputDialog(
            getLaunchButton().getParent(),
            "Select Sun Blindness type:",
            "Sun Blind Type",
            JOptionPane.PLAIN_MESSAGE,
            getLaunchButton().getIcon(),
            possibilities,
            env.getCurrentSunBlindnessLevel().toString());

    if (tempSunBlindnessLevel == null) return;

    GameModule gm = GameModule.getGameModule();

    Command command;
    if (tempSunBlindnessLevel == SunBlindnessLevel.NONE) {
      command = new DeactivateSunBlindnessShaderCommand();
    } else {
      command = new ActivateSunBlindnessShaderCommand();
    }

    command.execute();
    gm.sendAndLog(command);

    gm.getChatter().send(tempSunBlindnessLevel + " is in effect.");

  }

}
