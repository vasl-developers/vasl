package VASL.build.module;

import VASSAL.build.GameModule;
import VASSAL.build.module.GameComponent;
import VASSAL.build.module.map.MapShader;
import VASSAL.command.Command;

public class ASLNightMapShader extends MapShader implements GameComponent {

  public interface CommandConfig {}

  public ASLNightMapShader() {
    super();
  }

  @Override
  protected void toggleShading() {
    this.boardClip=null;

    Command command;
    if (shadingVisible) {
      command = new ASLNightMapShaderExtensions.DectivateCommand();
    } else {
      command = new ASLNightMapShaderExtensions.ActivateCommand();
    }

    GameModule gm = GameModule.getGameModule();
    command.execute();
    gm.sendAndLog(command);
    gm.getChatter().send("Night is" + (shadingVisible ? " " : " not ") + "in effect." );

  }

}
