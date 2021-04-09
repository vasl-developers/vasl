package VASL.build.module;

import VASL.environment.Environment;
import VASSAL.build.GameModule;
import VASSAL.build.module.map.MapShader;
import VASSAL.build.module.properties.GlobalProperty;

public class ASLNightMapShader extends MapShader{
  private GlobalProperty globalNightLevel = new GlobalProperty();
    public ASLNightMapShader() {
      super();
      shadingVisible = false;
      globalNightLevel.setPropertyName("night");
      globalNightLevel.setAttribute("initialValue", String.valueOf(shadingVisible));
      GameModule gm = GameModule.getGameModule();
      gm.addMutableProperty("night", globalNightLevel);
    }

  @Override
  protected void toggleShading() {
    super.toggleShading();
    GameModule.getGameModule().getChatter().send("Night is " + (shadingVisible ? "" : "not ") + "in effect." );
    globalNightLevel.setAttribute("initialValue", String.valueOf(shadingVisible));
  }
}
