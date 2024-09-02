package VASL.build.module;

import VASSAL.build.GameModule;
import VASSAL.build.module.map.MapShader;
import VASSAL.command.Command;
import VASL.build.module.shader.*;

public class ASLNightMapShader extends MapShader {

  public ASLNightMapShader() {
    super();
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
