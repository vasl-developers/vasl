package VASL.build.module.shader;

import VASL.build.module.ASLHeatHazeMapShader;
import VASL.environment.Environment;
import VASSAL.build.GameModule;
import VASSAL.build.module.properties.MutableProperty;
import VASSAL.command.Command;

public class DeactivateHeatHazeShaderCommand extends BaseShaderCommand {
    @Override
    protected void executeCommand() {
        GameModule gm = GameModule.getGameModule();

        ASLHeatHazeMapShader shaderObj = (ASLHeatHazeMapShader)super.getShader(ASLHeatHazeMapShader.class);
        if (shaderObj == null) return;

        shaderObj.setShadingVisibility(false);

        MutableProperty visibilityProperty = gm.getMutableProperty(Environment.HEAT_HAZE_VISIBILITY_PROPERTY);
        if (visibilityProperty == null) return;
        visibilityProperty.setPropertyValue(Boolean.FALSE.toString()).execute();

    }

    @Override
    protected Command myUndoCommand() {
        return new ActivateHeatHazeShaderCommand();
    }
}
