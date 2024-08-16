package VASL.build.module;

import VASL.environment.LVLevel;
import VASSAL.build.Buildable;
import VASSAL.build.GameModule;
import VASSAL.build.module.map.MapShader;
import VASSAL.build.module.properties.GlobalProperty;

import javax.swing.*;

import static VASL.environment.SunBlindnessLevel.NONE;

public class ASLLVMapShader extends MapShader implements  VisibilityQueryable {
  private final GlobalProperty globalLVLevel = new GlobalProperty();
  private LVLevel lvLevel = LVLevel.NONE;

  public ASLLVMapShader() {
    super();
    globalLVLevel.setPropertyName("lv_level");
    globalLVLevel.setAttribute("initialValue", lvLevel.name());
    GameModule gm = GameModule.getGameModule();
    gm.addMutableProperty("lv_level", globalLVLevel);
  }
  @Override
  public void addTo(Buildable buildable) {
    super.addTo(buildable);
  }

  @Override
  protected void toggleShading() {
    Object[] possibilities = LVLevel.values();
    LVLevel tempLvLevel = (LVLevel) JOptionPane.showInputDialog(
        getLaunchButton().getParent(),
        "Select LV type:",
        "LV Type",
        JOptionPane.PLAIN_MESSAGE,
        getLaunchButton().getIcon(),
        possibilities,
        lvLevel.toString());
    if(tempLvLevel != null) {
      lvLevel = tempLvLevel;
    }
    GameModule.getGameModule().getChatter().send(lvLevel.toString() + " is in effect.");
    this.boardClip=null;
    this.setShadingVisibility(setLVAndOpacity());
  }

  private boolean setLVAndOpacity() {
    if (lvLevel == LVLevel.NONE) {
      opacity = 0;
    } else {
      opacity = 20;
    }

    globalLVLevel.setAttribute("initialValue", lvLevel.name());
    buildComposite();
    return lvLevel != LVLevel.NONE;
  }

  @Override
  public boolean getShadingVisible() {
    return (lvLevel == LVLevel.NONE ? false : true);
  }
  public String getShadingLevel(){
    return lvLevel.name();
  }

  @Override
  public void setStateFromSavedGame(Boolean v, String s) {
    lvLevel = LVLevel.getLVLevel(s);
    GameModule.getGameModule().getChatter().send(lvLevel.toString() + " is in effect.");
    this.boardClip=null;
    this.setShadingVisibility(setLVAndOpacity());
  }
}
