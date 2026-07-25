package VASL.build.module.shader;

import VASL.build.module.ASLNightMapShader;
import VASL.build.module.ScenInfo;
import VASL.environment.Environment;
import VASSAL.build.GameModule;
import VASSAL.build.module.properties.MutableProperty;
import VASSAL.command.Command;

import java.awt.*;
import java.awt.event.KeyEvent;

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

        //linking to ScenInfo variables for los purposes
        Robot robot = null;
        try {
            robot = new Robot();
        } catch (AWTException e) {
            throw new RuntimeException(e);
        }
        // Simulate a key press
        robot.keyPress(KeyEvent.VK_F7);
    }

    @Override
    protected Command myUndoCommand() {
        return new DeactivateNightShaderCommand();
    }
}
