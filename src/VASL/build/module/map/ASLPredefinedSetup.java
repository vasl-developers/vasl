package VASL.build.module.map;

import VASL.build.module.ASLDiceBot;
import VASL.build.module.ASLMap;
import VASSAL.build.GameModule;
import VASSAL.build.IllegalBuildException;
import VASSAL.build.module.GameComponent;
import VASSAL.build.module.GameRefresher;
import VASSAL.build.module.GameState;
import VASSAL.build.module.PredefinedSetup;
import VASSAL.command.Command;
import VASSAL.tools.ArchiveWriter;
import VASSAL.tools.io.ZipArchive;

import java.awt.*;
import java.awt.event.ActionListener;
import java.io.File;
import java.io.IOException;
import java.util.HashSet;
import java.util.Set;

public class ASLPredefinedSetup extends PredefinedSetup {

    private final Set<String> refresherOptions = new HashSet();
    ArchiveWriter aw = new ArchiveWriter(this.extensionName, ".vmdx");
    private String extensionName;
    public ASLPredefinedSetup(){
        super();

    }
    @Override
    public int refreshWithStatus(Set<String> options) throws IOException, IllegalBuildException {
        if (!options.isEmpty()) {
            refresherOptions.clear();
            refresherOptions.addAll(options);
        }

        this.fileName = fileName;
        GameModule mod = GameModule.getGameModule();
        GameState gs = mod.getGameState();
        ASLGameUpdater aslGameUpdater = new ASLGameUpdater();
        if (aslGameUpdater == null){
            return 0;
        }
        //GameRefresher gameRefresher = new GameRefresher(mod);
        //gameRefresher.log("----------");
        String var10001 = this.getAttributeValueString("name");
        aslGameUpdater.log("Updating Predefined Setup: " + var10001 + " (" + this.fileName + ")");
        gs.closeGame();
        gs.setupRefresh();
        gs.loadGameInForeground(this.fileName, this.getSavedGameContents());
        mod.getPlayerWindow().setCursor(Cursor.getPredefinedCursor(3));
        aslGameUpdater.doupdate("6.6.7");
        //gameRefresher.execute(this.refresherOptions, (Command)null);
        File tmpFile = File.createTempFile("vassal", (String)null);
        ZipArchive tmpZip = new ZipArchive(tmpFile);
        gs.saveGameRefresh(tmpZip);
        gs.updateDone();
        //ArchiveWriter aw = mod.getArchiveWriter();
        if (aw == null){
            aw = new ArchiveWriter(this.extensionName, ".vmdx");
        }
        aw.removeFile(this.fileName);
        aw.addFile(tmpZip.getFile().getPath(), this.fileName);
        gs.closeGame();
        mod.getPlayerWindow().setCursor(Cursor.getPredefinedCursor(3));
        return 0; //aslGameUpdater.warnings();
    }

    public void setfileName (String passfilename){
        this.fileName = passfilename;
    }
    public void setExtensionName (String passextensionname){
        this.extensionName = passextensionname;
        aw = new ArchiveWriter(this.extensionName, ".vmdx");
    }
}
