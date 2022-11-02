package VASL.build.module;

import VASL.environment.SunBlindnessLevel;
import VASSAL.build.GameModule;
import VASSAL.build.module.map.MapShader;
import VASSAL.build.module.properties.GlobalProperty;

import javax.swing.*;

import static VASL.environment.SunBlindnessLevel.NONE;

public class ASLSunBlindnessMapShader extends MapShader {
  private final GlobalProperty globalSunBlindnessLevel = new GlobalProperty();
  private SunBlindnessLevel sunBlindnessLevel = NONE;
  public ASLSunBlindnessMapShader() {
    super();
    shadingVisible = false;
    globalSunBlindnessLevel.setPropertyName("sun_blindness");
    globalSunBlindnessLevel.setAttribute("initialValue",sunBlindnessLevel.name());
    GameModule gm = GameModule.getGameModule();
    gm.addMutableProperty("sun_blindness", globalSunBlindnessLevel);
  }

  @Override
  protected void toggleShading() {
    Object[] possibilities = SunBlindnessLevel.values();
    SunBlindnessLevel tempSunBlindnessLevel = (SunBlindnessLevel) JOptionPane.showInputDialog(
        getLaunchButton().getParent(),
        "Select Dust Type:",
        "Dust Type",
        JOptionPane.PLAIN_MESSAGE,
        getLaunchButton().getIcon(),
        possibilities,
        sunBlindnessLevel.toString());
    if(tempSunBlindnessLevel != null) {
      sunBlindnessLevel = tempSunBlindnessLevel;
    }
    GameModule.getGameModule().getChatter().send(sunBlindnessLevel.toString() + " is in effect.");
    this.boardClip=null;
    this.setShadingVisibility(setLVAndOpacity());
  }

  private boolean setLVAndOpacity() {
    switch (sunBlindnessLevel) {
      case NONE:
        opacity = 0;
        break;
      case EARLY_MORNING_SUN_BLINDNESS:
      case LATE_AFTERNOON_SUN_BLINDNESS:
        opacity = 10;
        break;
    }

    globalSunBlindnessLevel.setAttribute("initialValue", sunBlindnessLevel.name());
    buildComposite();
    return sunBlindnessLevel != SunBlindnessLevel.NONE;
  }
}
