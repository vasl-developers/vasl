package VASL.LOS.Map;

import VASL.build.module.ASLMap;
import VASL.build.module.shader.ShaderCommandConfig;
import VASSAL.build.GameModule;
import VASSAL.build.module.map.MapShader;
import VASSAL.command.Command;

import java.util.Iterator;

public abstract class BaseSettingPrefCommand extends Command implements LOSCommandConfig {
    final String ERROR_NO_SETTING_PREF = "buildFile does not contain expected LOS Setting or Preference ";
}
