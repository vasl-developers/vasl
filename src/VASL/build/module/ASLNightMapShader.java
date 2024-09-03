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

  public Command getRestoreCommand() {
    Environment env = new Environment();
    if (env.isNight()) {
      return new ActivateNightShaderCommand();
    }
    return new DeactivateNightShaderCommand();
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
