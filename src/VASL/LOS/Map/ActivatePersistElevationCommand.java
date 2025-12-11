package VASL.LOS.Map;

import VASL.LOS.LOS;
import VASSAL.build.GameModule;
import VASSAL.build.module.properties.MutableProperty;
import VASSAL.command.Command;

public class ActivatePersistElevationCommand extends BaseSettingPrefCommand {
    @Override
    protected void executeCommand() {
        GameModule gm = GameModule.getGameModule();

        MutableProperty prefProperty = gm.getMutableProperty(LOS.PERSIST_ELEVATION_PROPERTY);
        if (prefProperty == null) return;
        // this line changes the value of the Global Property
        prefProperty.setPropertyValue(Boolean.TRUE.toString()).execute();

    }

    @Override
    protected Command myUndoCommand() {
        return new DeactivatePersistElevationCommand();
    }
}
