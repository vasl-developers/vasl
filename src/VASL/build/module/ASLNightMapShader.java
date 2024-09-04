package VASL.build.module;

import VASL.environment.Environment;
import VASSAL.build.GameModule;
import VASSAL.build.module.map.MapShader;
import VASSAL.command.Command;
import VASL.build.module.shader.*;

public class ASLNightMapShader extends MapShader {

  public ASLNightMapShader() {
    super();
  }

  @Override
  public void setup(boolean gameStarting) {
    super.setup(gameStarting);
    Environment env = new Environment();
    Command command;
    if (env.isNight()) {
      command = new ActivateNightShaderCommand();
    } else {
      command = new DeactivateNightShaderCommand();
    }
    command.execute();
  }

  public Command getRestoreCommand() {
//    Environment env = new Environment();
//    if (env.isNight()) {
//      return new ActivateNightShaderCommand();
//    }
//    return new DeactivateNightShaderCommand();
    return null;
  }

  @Override
  protected void toggleShading() {
    this.boardClip=null;

    Command command;
    if (shadingVisible) {
      command = new DeactivateNightShaderCommand();
    } else {
      command = new ActivateNightShaderCommand();
    }

    GameModule gm = GameModule.getGameModule();
    command.execute();
    gm.sendAndLog(command);

    gm.getChatter().send("Night is" + (shadingVisible ? " " : " not ") + "in effect." );

  }

}
