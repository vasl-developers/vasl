package VASL.build.module.shader;

import VASL.build.module.ASLLVMapShader;
import VASL.environment.Environment;
import VASSAL.build.GameModule;
import VASSAL.build.module.properties.MutableProperty;
import VASSAL.command.Command;

public class ActivateLowVisibilityShaderCommand extends BaseShaderCommand {
    @Override
    protected void executeCommand() {
        GameModule gm = GameModule.getGameModule();

        ASLLVMapShader shaderObj = (ASLLVMapShader)super.getShader(ASLLVMapShader.class);
        if (shaderObj == null) return;

        shaderObj.setShadingVisibility(true);

        MutableProperty visibilityProperty = gm.getMutableProperty(Environment.LOW_VIS_VISIBILITY_PROPERTY);
        if (visibilityProperty == null) return;
        visibilityProperty.setPropertyValue(Boolean.TRUE.toString()).execute();

    }

    @Override
    protected Command myUndoCommand() {
        return new DeactivateLowVisibilityShaderCommand();
    }
}
