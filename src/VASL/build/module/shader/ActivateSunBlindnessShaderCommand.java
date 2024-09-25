package VASL.build.module.shader;

import VASL.build.module.ASLSunBlindnessMapShader;
import VASL.environment.Environment;
import VASSAL.build.GameModule;
import VASSAL.build.module.properties.MutableProperty;
import VASSAL.command.Command;

public class ActivateSunBlindnessShaderCommand extends BaseShaderCommand {
    @Override
    protected void executeCommand() {
        GameModule gm = GameModule.getGameModule();

        ASLSunBlindnessMapShader shaderObj = (ASLSunBlindnessMapShader)super.getShader(ASLSunBlindnessMapShader.class);
        if (shaderObj == null) return;

        shaderObj.setShadingVisibility(true);

        MutableProperty visibilityProperty = gm.getMutableProperty(Environment.SUN_BLIND_VISIBILITY_PROPERTY);
        if (visibilityProperty == null) return;
        visibilityProperty.setPropertyValue(Boolean.TRUE.toString()).execute();

    }

    @Override
    protected Command myUndoCommand() {
        return new DeactivateSunBlindnessShaderCommand();
    }
}