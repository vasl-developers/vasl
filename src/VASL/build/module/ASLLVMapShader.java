package VASL.build.module;

import VASL.environment.FogIntensity;
import VASL.environment.LVLevel;
import VASSAL.build.Buildable;
import VASSAL.build.GameModule;
import VASSAL.build.module.map.MapShader;
import VASSAL.build.module.properties.GlobalProperty;
import VASSAL.configure.BooleanConfigurer;
import VASSAL.preferences.Prefs;

public class ASLLVMapShader extends MapShader {
  private GlobalProperty globalLVLevel = new GlobalProperty();
  private GlobalProperty globalFogLevel = new GlobalProperty();
  private GlobalProperty globalFogHeight = new GlobalProperty();
  private LVLevel lvLevel = LVLevel.NONE;
  private FogIntensity fogLevel = FogIntensity.LIGHT;
  private int fogHeight = -1;

  public ASLLVMapShader() {
    super();
    globalLVLevel.setPropertyName("lv_level");
    globalLVLevel.setAttribute("initialValue", lvLevel.name());
    GameModule gm = GameModule.getGameModule();
    gm.addMutableProperty("lv_level", globalLVLevel);
  }
  @Override
  public void addTo(Buildable buildable) {

    Prefs gameModulePrefs = GameModule.getGameModule().getPrefs();
    //String generalTabKey = Resources.getString("Prefs.general_tab");
    String generalTabKey = "VASL";
    String prefKey = "DisableFullColorStacks";

    BooleanConfigurer configurer = (BooleanConfigurer)gameModulePrefs.getOption(prefKey);
    if (configurer == null) {
      configurer = new BooleanConfigurer(prefKey, "Disable full color stacks (requires restart)", Boolean.FALSE);
      gameModulePrefs.addOption(generalTabKey, configurer);
    }


    super.addTo(buildable);
  }

  @Override
  protected void toggleShading() {
    lvLevel = lvLevel.next();
    GameModule.getGameModule().getChatter().send(lvLevel.toString() + " is in effect.");
    this.setShadingVisibility(setLVAndOpacity());
  }

  private boolean setLVAndOpacity() {
    switch (lvLevel) {
      case NONE:
        opacity = 0;
        break;
      default:
        opacity = 20;
        break;
    }
    // @todo - activate fog intensity and fog height
//    if(lvLevel == LVLevel.FOG) {
//
//    }


    globalLVLevel.setAttribute("initialValue", lvLevel.name());
    buildComposite();
    return lvLevel != LVLevel.NONE;
  }
}
