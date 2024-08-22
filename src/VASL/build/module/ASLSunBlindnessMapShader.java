package VASL.build.module;

import VASL.build.module.shader.ActivateSunBlindnessShaderCommand;
import VASL.build.module.shader.DeactivateSunBlindnessShaderCommand;
import VASL.environment.Environment;
import VASL.environment.LVLevel;
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
  protected void toggleShading() {

    this.boardClip=null;

    Environment env = new Environment();

    Object[] possibilities = LVLevel.values();
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

    // if we ever wanted to sync MapShader state between clients, this would need to happen
    //noinspection ConstantValue
    if (false) {
      gm.sendAndLog(command);
    }

    gm.getChatter().send(tempSunBlindnessLevel + " is in effect.");

  }

}
