package VASL.build.module.shader;

import VASL.build.module.ASLNightMapShader;
import VASL.environment.Environment;
import VASSAL.build.GameModule;
import VASSAL.build.module.properties.MutableProperty;
import VASSAL.command.Command;

public class ActivateNightShaderCommand extends BaseShaderCommand {
    @Override
    protected void executeCommand() {
        GameModule gm = GameModule.getGameModule();

        ASLNightMapShader shaderObj = (ASLNightMapShader)super.getShader(ASLNightMapShader.class);
        if (shaderObj == null) return;

        shaderObj.setShadingVisibility(true);

        MutableProperty visibilityProperty = gm.getMutableProperty(Environment.NIGHT_VISIBILITY_PROPERTY);
        if (visibilityProperty == null) return;
        visibilityProperty.setPropertyValue(Boolean.TRUE.toString()).execute();

    }

    @Override
    protected Command myUndoCommand() {
        return new DeactivateNightShaderCommand();
    }
}
