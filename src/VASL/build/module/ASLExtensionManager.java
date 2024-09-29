package VASL.build.module;

import VASSAL.build.Buildable;
import VASSAL.build.GameModule;
import VASSAL.build.module.ExtensionsLoader;
import VASSAL.i18n.Resources;

import java.io.File;

public class ASLExtensionManager extends VASSAL.build.module.ExtensionsLoader {

    public ASLExtensionManager(){
        super();
    }

    @Override
    protected void addExtensions() {
        for (final File ext : globalExtMgr.getActiveExtensions()) {
            if (!addExtension(ext)) {
                globalExtMgr.setActive(ext, false);
            }
        }
        for (final File ext : extMgr.getActiveExtensions()) {
            if (!addExtension(ext)) {
                GameModule.getGameModule().warn(Resources.getString("ExtensionsLoader.deactivating_extension", ext.getName()));
                extMgr.setActive(ext, false);
            }
        }
        String msg = "override is working";
        GameModule.getGameModule().warn(msg);
    }
    public void updateExtensions(){
        addExtensions();
    }
}
