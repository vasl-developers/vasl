package VASL.build.module.map;

import VASSAL.build.Buildable;
import VASSAL.build.GameModule;
import VASSAL.build.module.PredefinedSetup;
import VASSAL.tools.menu.ChildProxy;
import VASSAL.tools.menu.MenuManager;

public class ASLPredefinedSetup2 extends PredefinedSetup {

    @Override
    public void addTo(Buildable parent) {
        if (parent instanceof GameModule) {
            MenuManager.getInstance().addToSection("PredefinedSetup", getMenuInUse());
        }
        else if (parent instanceof ASLPredefinedSetup2) {
            ASLPredefinedSetup2 setup = (ASLPredefinedSetup2) parent;
            setup.menu.add(getMenuInUse());
        }
        // The three lines below have been intentionally removed:
        // MenuManager.getInstance().removeAction("GameState.new_game");
        // GameModule.getGameModule().getGameState().addGameComponent(this);
        // GameModule.getGameModule().getWizardSupport().addPredefinedSetup(this);
    }

    private ChildProxy<?> getMenuInUse() {
        return isMenu ? menu : menuItem;
    }
}