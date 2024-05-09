package VASL.build.module.map;

import VASSAL.command.Command;

/**
 * Use this command to enable or disable HIP fortification reveal for the game
 */
public class EnableHIPFortCommand extends Command {
    private boolean enabledFlag;
    private HIPFortification hipFortViewer;

    public EnableHIPFortCommand (HIPFortification hipfortviewer, boolean enabled) {
        this.enabledFlag = enabled;
        this.hipFortViewer = hipfortviewer;
    }

    protected void executeCommand() {

       // hipFortViewer.enableFortHIP(enabledFlag);  DISABLED initially to force use of preference/restart
    }

    protected Command myUndoCommand() {
        return null;
    }

    public int getValue() {
        return 0;
    }
}
