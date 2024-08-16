package VASL.build.module;

import VASL.environment.LVLevel;
import VASSAL.build.GameModule;
import VASSAL.build.module.map.MapShader;
import VASSAL.build.module.properties.GlobalProperty;

public class ASLNightMapShader extends MapShader implements VisibilityQueryable{
  private final GlobalProperty globalNightLevel = new GlobalProperty();
    public ASLNightMapShader() {
      super();
      //shadingVisible = false;
      globalNightLevel.setPropertyName("night");
      globalNightLevel.setAttribute("initialValue", String.valueOf(shadingVisible));
      GameModule gm = GameModule.getGameModule();
      gm.addMutableProperty("night", globalNightLevel);
    }

  @Override
  protected void toggleShading() {
    this.boardClip=null;
    super.toggleShading();
    GameModule.getGameModule().getChatter().send("Night is " + (shadingVisible ? "" : "not ") + "in effect." );
    globalNightLevel.setAttribute("initialValue", String.valueOf(shadingVisible));
  }
  public boolean getShadingVisible (){
      return shadingVisible;
  }
  public String getShadingLevel(){
      return "";
  }

  @Override
  public void setStateFromSavedGame(Boolean v, String s) {
    this.boardClip=null;
    this.setShadingVisibility(v);
    GameModule.getGameModule().getChatter().send( "Night is " + (shadingVisible ? "" : "not ") + "in effect.");
  }
}
