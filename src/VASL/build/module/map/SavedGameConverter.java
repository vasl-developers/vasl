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
import VASSAL.tools.WarningDialog;
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
        // incorporated into Game Update functionality so no need for separate menu option
        /*if (parent instanceof ASLMap) {

            GameModule.getGameModule().addCommandEncoder(this);

            ASLMap map = (ASLMap) parent;

            JMenuItem menuItem = new JMenuItem("Convert pre-6.2 game...");

            menuItem.addActionListener(new ActionListener() {
                public void actionPerformed(ActionEvent evt) {

                    askToConvert();
                }
            });

            map.getPopupMenu().add(menuItem);
        }*/
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

    // incorporated into Game Update functionality so no need for separate menu option
    /**
     * Displays the confirmation dialog and initiates the conversion if the user "yes"
     */
   /* public void askToConvert() {

        // show confirmation dialog
        int dialogResult = JOptionPane.showConfirmDialog (
                null,
                "Are you sure you want to convert this game to 6.2 format?",
                "Warning",
                JOptionPane.YES_NO_OPTION);

        if(dialogResult == JOptionPane.YES_OPTION){
            execute();
        }
    }*/

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
