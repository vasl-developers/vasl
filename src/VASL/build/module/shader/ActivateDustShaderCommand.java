package VASL.build.module.shader;

import VASL.build.module.ASLDTODustMapShader;
import VASL.environment.Environment;
import VASSAL.build.GameModule;
import VASSAL.build.module.properties.MutableProperty;
import VASSAL.command.Command;

public class ActivateDustShaderCommand extends BaseShaderCommand {
    @Override
    protected void executeCommand() {
        GameModule gm = GameModule.getGameModule();

        ASLDTODustMapShader shaderObj = (ASLDTODustMapShader)super.getShader(ASLDTODustMapShader.class);
        if (shaderObj == null) return;

        Environment env = new Environment();
        shaderObj.setAttribute("opacity", env.getOpacity(env.getCurrentDustLevel()));

        shaderObj.setShadingVisibility(true);

        MutableProperty visibilityProperty = gm.getMutableProperty(Environment.DUST_VISIBILITY_PROPERTY);
        if (visibilityProperty == null) return;
        visibilityProperty.setPropertyValue(Boolean.TRUE.toString()).execute();

    }

    @Override
    protected Command myUndoCommand() {
        return new DeactivateDustShaderCommand();
    }
}
