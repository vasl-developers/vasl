package VASL.build.module.shader;

import VASL.build.module.ASLDTODustMapShader;
import VASL.environment.Environment;
import VASSAL.build.GameModule;
import VASSAL.build.module.properties.MutableProperty;
import VASSAL.command.Command;

public class DeactivateDustShaderCommand extends BaseShaderCommand {
    @Override
    protected void executeCommand() {
        GameModule gm = GameModule.getGameModule();

        ASLDTODustMapShader shaderObj = (ASLDTODustMapShader)super.getShader(ASLDTODustMapShader.class);
        if (shaderObj == null) return;

        shaderObj.setShadingVisibility(false);

        MutableProperty visibilityProperty = gm.getMutableProperty(Environment.DUST_VISIBILITY_PROPERTY);
        if (visibilityProperty == null) return;
        visibilityProperty.setPropertyValue(Boolean.FALSE.toString()).execute();

    }

    @Override
    protected Command myUndoCommand() {
        return new ActivateDustShaderCommand();
    }
}
