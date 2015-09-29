package VASL.build.module.map;

import VASSAL.command.Command;

/**
 * Copyright (c) 2015 by David Sullivan
 * <p/>
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Library General Public
 * License (LGPL) as published by the Free Software Foundation.
 * <p/>
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Library General Public License for more details.
 * <p/>
 * You should have received a copy of the GNU Library General Public
 * License along with this library; if not, copies are available
 * at http://www.opensource.org.
 */

/**
 * Use this command to enable or disable double blind for the game
 */
public class EnableDoubleBlindCommand extends Command {
    private boolean enabledFlag;
    private DoubleBlindViewer doubleBlindViewer;

    public EnableDoubleBlindCommand(DoubleBlindViewer doubleBlindViewer, boolean enabled) {
        this.enabledFlag = enabled;
        this.doubleBlindViewer = doubleBlindViewer;
    }

    protected void executeCommand() {

        doubleBlindViewer.enableDB(enabledFlag);
    }

    protected Command myUndoCommand() {
        return null;
    }

    public int getValue() {
        return 0;
    }
}


