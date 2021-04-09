package VASL.build.module;

import VASL.environment.HeatHazeLevel;
import VASSAL.build.Buildable;
import VASSAL.build.GameModule;
import VASSAL.build.module.map.MapShader;
import VASSAL.build.module.properties.GlobalProperty;

import static VASL.environment.HeatHazeLevel.NONE;

public class ASLHeatHazeMapShader extends MapShader {
  private GlobalProperty globalHeatHazeLevel = new GlobalProperty();
  private HeatHazeLevel heatHazeLevel = NONE;

  public ASLHeatHazeMapShader() {
    super();
    globalHeatHazeLevel.setPropertyName("heat_haze_level");
    globalHeatHazeLevel.setAttribute("initialValue", heatHazeLevel.name());
    GameModule gm = GameModule.getGameModule();
    gm.addMutableProperty("heat_haze_level", globalHeatHazeLevel);
  }
  @Override
  public void addTo(Buildable buildable) {
    super.addTo(buildable);
  }

  @Override
  protected void toggleShading() {
    heatHazeLevel = heatHazeLevel.next();
    GameModule.getGameModule().getChatter().send(heatHazeLevel.toString() + " is in effect.");
    this.setShadingVisibility(setHeatHazeAndOpacity());
  }

  private boolean setHeatHazeAndOpacity() {
    switch (heatHazeLevel) {
      case NONE:
        opacity = 0;
        break;
      case HEAT_HAZE:
        opacity = 5;
        break;
      case INTENSE_HEAT_HAZE:
        opacity = 15;
        break;

    }
    // @todo - activate fog height

    globalHeatHazeLevel.setAttribute("initialValue", heatHazeLevel.name());
    buildComposite();
    return heatHazeLevel != NONE;
  }
}
