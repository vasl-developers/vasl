package VASL.build.module.map;/*
 * Copyright (c) 2015 by irfjzb on 2/9/2015.
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Library General Public
 * License (LGPL) as published by the Free Software Foundation.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Library General Public License for more details.
 *
 * You should have received a copy of the GNU Library General Public
 * License along with this library; if not, copies are available
 * at http://www.opensource.org.
 */

import VASL.build.module.ASLMap;
import VASSAL.Info;
import VASSAL.build.*;
import VASSAL.build.module.*;
import VASSAL.build.module.documentation.HelpFile;
import VASSAL.build.module.metadata.AbstractMetaData;
import VASSAL.build.module.metadata.MetaDataFactory;
import VASSAL.build.module.metadata.SaveMetaData;
import VASSAL.command.Command;
import VASSAL.command.CommandEncoder;
import VASSAL.command.NullCommand;
import VASSAL.counters.GamePiece;
import VASSAL.counters.Stack;
import VASSAL.i18n.Resources;
import VASSAL.tools.WarningDialog;
import VASSAL.tools.version.VersionUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.File;
import java.util.ArrayList;
import java.util.Iterator;

/**
 * Converts a pre-6.2 saved game to 6.2+ format by shifting the pieces to align with the new map postion
 */
@SuppressWarnings("unused")
public class SavedGameConverter extends AbstractConfigurable implements CommandEncoder, GameComponent {

    private static final Logger logger = LoggerFactory.getLogger(SavedGameConverter.class);
    private static final String ATTRIBUTE_NAME = "SavedGameConverter";

    protected int updatedCount;
    protected int notFoundCount;

    final private static int SHIFT_AMOUNT = 200;

    // this component is not configurable
    @Override
    public Class[] getAllowableConfigureComponents() {
        return new Class[0];
    }

    @Override
    public Class<?>[] getAttributeTypes() {
        return new Class<?>[] {String.class};
    }

    @Override
    public String[] getAttributeNames() {
        return new String[] {ATTRIBUTE_NAME};
    }

    @Override
    public String[] getAttributeDescriptions() {
        return new String[] {ATTRIBUTE_NAME};
    }

    @Override
    public String getAttributeValueString(String key) {

        return ATTRIBUTE_NAME;
    }

    @Override
    public void setAttribute(String key, Object value) {
    }

    @Override
    public void addTo(Buildable parent) {

        if (parent instanceof ASLMap) {

            GameModule.getGameModule().addCommandEncoder(this);

            ASLMap map = (ASLMap) parent;

            JMenuItem menuItem = new JMenuItem("Convert pre-6.2 game...");

            menuItem.addActionListener(new ActionListener() {
                public void actionPerformed(ActionEvent evt) {

                    askToConvert();
                }
            });

            map.getPopupMenu().add(menuItem);

            // On-going game converter
            JMenuItem nextmenuItem = new JMenuItem("Update game...");

            nextmenuItem.addActionListener(new ActionListener() {
                public void actionPerformed(ActionEvent evt) {

                    askToUpdate();
                }
            });
            map.getPopupMenu().add(nextmenuItem);


        }
    }

    @Override
    public void removeFrom(Buildable parent) {

    }

    // no custom command is needed
    public String encode(Command c) {

        return null;
    }

    public Command decode(String s) {
        return null;
    }

    @Override
    public HelpFile getHelpFile() {
        return null;
    }

    public void setup(boolean gameStarting) {
    }

    @Override
    public Command getRestoreCommand() {
        return null;
    }

    /**
     * Displays the confirmation dialog and initiates the conversion if the user "yes"
     */
    public void askToConvert() {

        // show confirmation dialog
        int dialogResult = JOptionPane.showConfirmDialog (
                null,
                "Are you sure you want to convert this game to 6.2 format?",
                "Warning",
                JOptionPane.YES_NO_OPTION);

        if(dialogResult == JOptionPane.YES_OPTION){
            execute();
        }
    }
    /**
     * Checks for saveGame version older than current module version
     * Displays the confirmation dialog if true and initiates the conversion if the user "yes"
     * Displays no conversion message if false and exits
     */
    public void askToUpdate() {
        final String moduleVersion = GameModule.getGameModule().getGameVersion();
        String filename = GameModule.getGameModule().getGameFile();
        String filepath = GameModule.getGameModule().getGameState().getSavedGameDirectoryPreference().getValueString();
        File file = new File(filepath+"\\"+filename);
        if(file.getName()!="") {
            final AbstractMetaData metaData = MetaDataFactory.buildMetaData(file);
            if (!(metaData instanceof SaveMetaData)) {
                WarningDialog.show("GameState.invalid_save_file", file.getPath()); //NON-NLS
                return;
            }
            // Check if saveGame version matches the module version
            final SaveMetaData saveData = (SaveMetaData) metaData;
            String saveModuleVersion = "?";
            final GameModule g = GameModule.getGameModule();
            // Was the Module Data that created the save stored in the save? (Vassal 3.0+)
            if (saveData.getModuleData() != null) {
                saveModuleVersion = saveData.getModuleVersion();
                // For Module Version and just report in chat.
                if (!saveModuleVersion.equals(moduleVersion)) {
                    // show confirmation dialog
                    int dialogResult = JOptionPane.showConfirmDialog (
                            null,
                            "Are you sure you want to update this game to latest VASL version?",
                            "Warning",
                            JOptionPane.YES_NO_OPTION);

                    if(dialogResult == JOptionPane.YES_OPTION) {
                        doupdate();
                    }
                } else {
                    JOptionPane.showMessageDialog(null, "No update possible; game was saved with current version or higher",
                            "Updating Game . . . ", JOptionPane.WARNING_MESSAGE);
                }
            }

        }
    }
    /**
     * Execute the update
     */
    public void doupdate() {
        final GameModule theModule = GameModule.getGameModule();
        ASLGameRefresher gamerefresh = new ASLGameRefresher(theModule);
        gamerefresh.start();


        final Command command = new NullCommand();
        final Chatter chatter = theModule.getChatter();
        final Command msg = new Chatter.DisplayText(chatter, "----------");
        msg.append(new Chatter.DisplayText(chatter, "The game has been updated"));
        //msg.append(new Chatter.DisplayText(chatter, updatedCount + " counters were moved"));

        //if (notFoundCount > 0) {
        //    msg.append(new Chatter.DisplayText(chatter, notFoundCount + " counters were not found"));
        //}
        msg.append(new Chatter.DisplayText(chatter, "----------"));
        msg.execute();
        command.append(msg);
    }
    /**
     * Execute the conversion
     */
    public void execute() {

        final GameModule theModule = GameModule.getGameModule();
        updatedCount = 0;
        notFoundCount = 0;

        // Grab all pieces in the game
        final Command command = new NullCommand();
        final ArrayList<GamePiece> pieces = new ArrayList<GamePiece>();

        for (GamePiece piece : theModule.getGameState().getAllPieces()) {

            if (piece instanceof Stack) {
                for (Iterator<GamePiece> i = ((Stack) piece).getPiecesInVisibleOrderIterator(); i.hasNext();) {
                    final GamePiece p = i.next();
                    pieces.add(0, p);
                }
            }
            else if (piece.getParent() == null) {
                pieces.add(0, piece);
            }
        }

        // Generate the move commands
        for (GamePiece piece : pieces) {
            processGamePiece(piece, command);
        }

        final Chatter chatter = theModule.getChatter();
        final Command msg = new Chatter.DisplayText(chatter, "----------");
        msg.append(new Chatter.DisplayText(chatter, "The game has been converted to 6.2+ format"));
        msg.append(new Chatter.DisplayText(chatter, updatedCount + " counters were moved"));

        if (notFoundCount > 0) {
            msg.append(new Chatter.DisplayText(chatter, notFoundCount + " counters were not found"));
        }
        msg.append(new Chatter.DisplayText(chatter, "----------"));
        msg.execute();
        command.append(msg);

        // Send the update to other clients
        theModule.sendAndLog(command);
    }

    /**
     * Moves a single piece
     * @param piece the piece
     * @param command the parent command
     */
    private void processGamePiece(GamePiece piece, Command command) {

        final Map map = piece.getMap();
        if (map == null) {
            logger.error("Can't refresh piece " + piece.getName() + ": No Map");
            return;
        }

        // shift piece to compensate for the new map location
        final Point pos = piece.getPosition();
        pos.translate(SHIFT_AMOUNT, SHIFT_AMOUNT);
        final Command place = map.placeOrMerge(piece, pos);
        command.append(place);
        updatedCount++;

    }
}
