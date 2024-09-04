package VASL.build.module.shader;

import VASL.build.module.ASLFogMapShader;
import VASL.environment.Environment;
import VASSAL.build.GameModule;
import VASSAL.build.module.properties.MutableProperty;
import VASSAL.command.Command;

public class DeactivateFogShaderCommand extends BaseShaderCommand {
    @Override
    protected void executeCommand() {
        GameModule gm = GameModule.getGameModule();

        ASLFogMapShader shaderObj = (ASLFogMapShader)super.getShader(ASLFogMapShader.class);
        if (shaderObj == null) return;

        shaderObj.setShadingVisibility(false);

        MutableProperty visibilityProperty = gm.getMutableProperty(Environment.FOG_VISIBILITY_PROPERTY);
        if (visibilityProperty == null) return;
        visibilityProperty.setPropertyValue(Boolean.FALSE.toString()).execute();

    }

    @Override
    protected Command myUndoCommand() {
        return new ActivateFogShaderCommand();
    }
}
