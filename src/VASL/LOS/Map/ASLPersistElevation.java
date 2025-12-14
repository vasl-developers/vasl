package VASL.LOS.Map;

import VASL.LOS.LOS;
import VASSAL.build.GameModule;
import VASSAL.command.Command;

public class ASLPersistElevation {
    protected boolean persistVisible;
    //@Override
    public boolean setpersistelevprop(boolean gameStarting) {
        LOS los = new LOS();
        Command command;
        if (los.isPersistElevation()) {
            persistVisible = true;
            command = new ActivatePersistElevationCommand();
        } else {
            persistVisible = false;
            command = new DeactivatePersistElevationCommand();
        }
        command.execute();
        return persistVisible;
    }

    public Command getRestoreCommand() {
        return null;
    }


    public boolean togglePersistElevation() {

        Command command;
        if (persistVisible) {
            command = new DeactivatePersistElevationCommand();
        } else {
            command = new ActivatePersistElevationCommand();
        }
        persistVisible = !persistVisible;
        GameModule gm = GameModule.getGameModule();
        command.execute();
        gm.sendAndLog(command);

        gm.getChatter().send("Persist Board Elevation when adding overlays is" + (persistVisible ? " " : " not ") + "in effect." );
        return persistVisible;
    }

}
