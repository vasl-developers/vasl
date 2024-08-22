package VASL.build.module.shader;

import VASL.build.module.ASLMap;
import VASSAL.build.GameModule;
import VASSAL.build.module.map.MapShader;
import VASSAL.command.Command;

import java.util.Iterator;

public abstract class BaseShaderCommand extends Command implements ShaderCommandConfig{

    final String ERROR_NO_SHADER = "buildFile does not contain expected MapShader ";

    public <T extends MapShader> MapShader getShader(Class<T> shader) {
        GameModule gm = GameModule.getGameModule();
        Iterator<ASLMap> mapIterator = gm.getComponentsOf(ASLMap.class).iterator();
        if (mapIterator.hasNext()) {
            ASLMap map = mapIterator.next();
            Iterator<T> shaderIterator = map.getComponentsOf(shader).iterator();
            if (!shaderIterator.hasNext()) {
                gm.warn(ERROR_NO_SHADER + shader.getName());
                return null;
            }
            return shaderIterator.next();
        }
        return null;
    }
}
