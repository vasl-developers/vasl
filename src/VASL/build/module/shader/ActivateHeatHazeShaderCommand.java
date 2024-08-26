package VASL.build.module.shader;

import VASL.build.module.ASLHeatHazeMapShader;
import VASL.environment.Environment;
import VASSAL.build.GameModule;
import VASSAL.build.module.properties.MutableProperty;
import VASSAL.command.Command;

public class ActivateHeatHazeShaderCommand extends BaseShaderCommand {
    @Override
    protected void executeCommand() {
        GameModule gm = GameModule.getGameModule();

        ASLHeatHazeMapShader shaderObj = (ASLHeatHazeMapShader)super.getShader(ASLHeatHazeMapShader.class);
        if (shaderObj == null) return;

        Environment env = new Environment();
        shaderObj.setAttribute("opacity", env.getOpacity(env.getCurrentHeatHazeLevel()));

        shaderObj.setShadingVisibility(true);

        MutableProperty visibilityProperty = gm.getMutableProperty(Environment.HEAT_HAZE_VISIBILITY_PROPERTY);
        if (visibilityProperty == null) return;
        visibilityProperty.setPropertyValue(Boolean.TRUE.toString()).execute();

    }

    @Override
    protected Command myUndoCommand() {
        return new DeactivateHeatHazeShaderCommand();
    }
}
