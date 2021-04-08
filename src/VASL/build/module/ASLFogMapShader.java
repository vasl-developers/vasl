package VASL.build.module;

import VASL.environment.FogLevel;
import VASSAL.build.Buildable;
import VASSAL.build.GameModule;
import VASSAL.build.module.map.MapShader;
import VASSAL.build.module.properties.GlobalProperty;

import static VASL.environment.FogLevel.NONE;

public class ASLFogMapShader extends MapShader {
  private GlobalProperty globalFogLevel = new GlobalProperty();
  private FogLevel fogLevel = NONE;

  public ASLFogMapShader() {
    super();
    globalFogLevel.setPropertyName("fog_level");
    globalFogLevel.setAttribute("initialValue", fogLevel.name());
    GameModule gm = GameModule.getGameModule();
    gm.addMutableProperty("fog_level", globalFogLevel);
  }
  @Override
  public void addTo(Buildable buildable) {
    super.addTo(buildable);
  }

  @Override
  protected void toggleShading() {
    fogLevel = fogLevel.next();
    GameModule.getGameModule().getChatter().send(fogLevel.toString() + " is in effect.");
    this.setShadingVisibility(setFogLevelAndOpacity());
  }

  private boolean setFogLevelAndOpacity() {
    switch (fogLevel) {
      case NONE:
        opacity = 0;
        break;
      case LIGHT_FOGM1:
      case LIGHT_FOGL0:
      case LIGHT_FOGL1:
      case LIGHT_FOGL2:
      case LIGHT_FOGL3:
      case LIGHT_FOGL4:
        opacity = 10;
        break;
      case MODERATE_FOGM1:
      case MODERATE_FOGL0:
      case MODERATE_FOGL1:
      case MODERATE_FOGL2:
      case MODERATE_FOGL3:
      case MODERATE_FOGL4:
        opacity = 15;
        break;
      case HEAVY_FOGM1:
      case HEAVY_FOGL0:
      case HEAVY_FOGL1:
      case HEAVY_FOGL2:
      case HEAVY_FOGL3:
      case HEAVY_FOGL4:
        opacity = 20;
        break;
    }

    globalFogLevel.setAttribute("initialValue", fogLevel.name());
    buildComposite();
    return fogLevel != NONE;
  }
}
