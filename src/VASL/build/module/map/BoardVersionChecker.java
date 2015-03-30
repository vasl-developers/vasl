/*
 * $Id$
 *
 * Copyright (c) 2000-2003 by Rodney Kinney
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
package VASL.build.module.map;

import VASL.build.module.map.boardPicker.ASLBoard;
import VASL.build.module.map.boardPicker.Overlay;
import VASSAL.build.AbstractBuildable;
import VASSAL.build.Buildable;
import VASSAL.build.GameModule;
import VASSAL.build.module.GameComponent;
import VASSAL.build.module.Map;
import VASSAL.build.module.ServerConnection;
import VASSAL.build.module.map.boardPicker.Board;
import VASSAL.command.Command;
import VASSAL.configure.StringConfigurer;
import VASSAL.tools.PropertiesEncoder;
import VASSAL.tools.io.IOUtils;

import javax.swing.*;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.io.*;
import java.net.URL;
import java.net.URLConnection;
import java.nio.channels.Channels;
import java.nio.channels.ReadableByteChannel;
import java.util.Enumeration;
import java.util.Properties;
import java.util.Vector;

/**
 * Copyright (c) 2003 by Rodney Kinney.  All rights reserved.
 * Date: Jun 11, 2003
 */
public class BoardVersionChecker extends AbstractBuildable implements GameComponent, PropertyChangeListener {

    private String boardVersionURL;
    private String overlayVersionURL;
    private String boardPageURL;
    private static String boardRepositoryURL;

    public static String BOARD_VERSION_PROPERTY_KEY = "boardVersions";

    private Map map;
    private Properties boardVersions;
    private Properties overlayVersions;

    private static final String BOARD_VERSION_URL = "boardVersionURL";
    private static final String OVERLAY_VERSION_URL = "overlayVersionURL";
    private static final String BOARD_PAGE_URL = "boardPageURL";
    private static final String BOARD_VERSIONS = BOARD_VERSION_PROPERTY_KEY;
    private static final String OVERLAY_VERSIONS = "overlayVersions";
    private static final String BOARD_REPOSITORY_URL = "boardRepositoryURL";

    public String[] getAttributeNames() {
        return new String[]{BOARD_VERSION_URL, OVERLAY_VERSION_URL, BOARD_PAGE_URL, BOARD_REPOSITORY_URL};
    }

    public String getAttributeValueString(String key) {
        if (BOARD_VERSION_URL.equals(key)) {
            return boardVersionURL;
        } else if (OVERLAY_VERSION_URL.equals(key)) {
            return overlayVersionURL;
        } else if (BOARD_PAGE_URL.equals(key)) {
            return boardPageURL;
        } else if (BOARD_REPOSITORY_URL.equals(key)) {
            return boardRepositoryURL;
        }
        return null;
    }

    public void setAttribute(String key, Object value) {
        if (BOARD_VERSION_URL.equals(key)) {
            boardVersionURL = (String) value;
        } else if (OVERLAY_VERSION_URL.equals(key)) {
            overlayVersionURL = (String) value;
        } else if (BOARD_PAGE_URL.equals(key)) {
            boardPageURL = (String) value;
        } else if (BOARD_REPOSITORY_URL.equals(key)) {
            boardRepositoryURL = (String) value;
        }
    }

    public void addTo(Buildable parent) {

        map = (Map) parent;
        GameModule.getGameModule().getGameState().addGameComponent(this);
        GameModule.getGameModule().getServer().addPropertyChangeListener(ServerConnection.CONNECTED, this);
        GameModule.getGameModule().getPrefs().addOption(null, new StringConfigurer(BOARD_VERSIONS, null));
        GameModule.getGameModule().getPrefs().addOption(null, new StringConfigurer(OVERLAY_VERSIONS, null));

        //TODO property change listener not firing - force update
        readVersionFiles();

        Properties p = readVersionList((String) GameModule.getGameModule().getPrefs().getValue(BOARD_VERSIONS));
        if (p != null) {
            boardVersions = p;
        }
        p = readVersionList((String) GameModule.getGameModule().getPrefs().getValue(OVERLAY_VERSIONS));
        if (p != null) {
            overlayVersions = p;
        }
    }

    public Command getRestoreCommand() {
        return null;
    }

    public void setup(boolean gameStarting) {

    }

/*
    public void setup(boolean gameStarting) {

        if (gameStarting) {

            if (boardVersions != null) {
                String info = "Using board(s): ";
                for (Board board : map.getBoards()) {
                    ASLBoard b = (ASLBoard) board;
                    info += b.getName() + "(v" + b.getVersion() + ") ";
                }
                GameModule.getGameModule().warn(info);
            }

            if (boardVersions != null) {
                String info = "Using board(s): ";
                Vector<String> obsolete = new Vector<String>();
                for (Board board : map.getBoards()) {
                    ASLBoard b = (ASLBoard) board;

                    String availableVersion = boardVersions.getProperty(b.getName(), b.getVersion());
                    if (!availableVersion.equals(b.getVersion())) {

                        // try to update board if out of date
                        GameModule.getGameModule().warn("Board " + b.getName() + " is out of date. Updating...");
                        if (!updateBoard(b.getName())) {
                            GameModule.getGameModule().warn("Update failed");
                            info += b.getName() + "(v" + b.getVersion() + ") ";
                            obsolete.addElement(b.getName());
                        }
                        else {
                            info += b.getName() + "(v" + availableVersion + ") ";
                            GameModule.getGameModule().warn("Update succeeded");
                        }
                    }
                    else {
                        info += b.getName() + "(v" + b.getVersion() + ") ";
                    }

                    String msg = null;
                    if (obsolete.size() == 1) {
                        String name = obsolete.firstElement();
                        msg = "Version " + boardVersions.getProperty(name) + " of board " + name + " is now available. Please go to \n" + boardPageURL;
                    } else if (obsolete.size() > 1) {
                        StringBuffer buff = new StringBuffer();
                        for (int i = 0, j = obsolete.size(); i < j; ++i) {
                            buff.append(obsolete.elementAt(i));
                            if (i < j - 2) {
                                buff.append(", ");
                            } else if (i < j - 1) {
                                buff.append(" and ");
                            }
                        }
                        msg = "New versions of boards " + buff + " are available. Please go to \n" + boardPageURL;
                    }
                    if (msg != null) {
                        final String message = msg;
                        Runnable runnable = new Runnable() {
                            public void run() {
                                JOptionPane.showMessageDialog(map.getView().getTopLevelAncestor(), message);
                            }
                        };
                        SwingUtilities.invokeLater(runnable);
                    }
                }
                GameModule.getGameModule().warn(info);

                if (overlayVersions != null) {
                    obsolete = new Vector<String>();
                    for (Board board : map.getBoards()) {
                        ASLBoard b = (ASLBoard) board;
                        for (Enumeration e2 = b.getOverlays(); e2.hasMoreElements(); ) {
                            Overlay o = (Overlay) e2.nextElement();
                            if (o.getClass().equals(Overlay.class)) { // Don't check for SSROverlays
                                String name = o.getFile().getName();
                                String availableVersion = overlayVersions.getProperty(name, o.getVersion());
                                if (!availableVersion.equals(o.getVersion())) {

                                    // try to update overlay if out of date
                                    GameModule.getGameModule().warn("Overlay " + name + " is out of date. Updating...");
                                    if (!updateOverlayFile(name)) {
                                        obsolete.addElement(b.getName());
                                        GameModule.getGameModule().warn("Overlay update failed");
                                    }
                                    else {
                                        GameModule.getGameModule().warn("Overlay update succeeded");
                                    }
                                }
                            }
                        }
                    }
                    String msg = null;
                    if (obsolete.size() == 1) {
                        String name = obsolete.firstElement();
                        msg = "Version " + overlayVersions.getProperty(name) + " of overlay " + name + " is now available.\n" + boardPageURL;
                    } else if (obsolete.size() > 1) {
                        StringBuffer buff = new StringBuffer();
                        for (int i = 0, j = obsolete.size(); i < j; ++i) {
                            buff.append(obsolete.elementAt(i));
                            if (i < j - 2) {
                                buff.append(", ");
                            } else if (i < j - 1) {
                                buff.append(" and ");
                            }
                        }
                        msg = "New versions of overlays " + buff + " are available.\n" + boardPageURL;
                    }
                    if (msg != null) {
                        final String message = msg;
                        Runnable runnable = new Runnable() {
                            public void run() {
                                JOptionPane.showMessageDialog(map.getView().getTopLevelAncestor(), message);
                            }
                        };
                        SwingUtilities.invokeLater(runnable);
                    }
                }
            }
        }
    }
*/


    private Properties readVersionList(String s) {
        Properties p = null;
        if (s != null
                && s.length() > 0) {
            try {
                p = new PropertiesEncoder(s).getProperties();
            } catch (IOException e) {
                // Fail silently if we can't contact the server
            }
        }
        return p;
    }

    public void propertyChange(PropertyChangeEvent evt) {
        if (Boolean.TRUE.equals(evt.getNewValue())) {

            readVersionFiles();
        }
    }

    /**
     * Reads the board and overlay versions using the URLs in the build file
     */
    private void readVersionFiles() {

        // Need to disable SNI to read from Github
        System.setProperty("jsse.enableSNIExtension", "false");

        try {
            URL base = new URL(boardVersionURL);
            URLConnection conn = base.openConnection();
            conn.setUseCaches(false);

            Properties p = new Properties();
            InputStream input = null;
            try {
                input = conn.getInputStream();
                p.load(input);

            }
            finally {
                IOUtils.closeQuietly(input);
            }

            boardVersions = p;
            GameModule.getGameModule().getPrefs().getOption(BOARD_VERSIONS).setValue(new PropertiesEncoder(p).getStringValue());
        } catch (IOException e) {
            // Fail silently if we can't contact the server
        }

        try {
            URL base = new URL(overlayVersionURL);
            URLConnection conn = base.openConnection();
            conn.setUseCaches(false);

            Properties p = new Properties();
            InputStream input = null;
            try {
                input = conn.getInputStream();
                p.load(input);
            } finally {
                IOUtils.closeQuietly(input);
            }

            overlayVersions = p;
            GameModule.getGameModule().getPrefs().getOption(OVERLAY_VERSIONS).setValue(new PropertiesEncoder(p).getStringValue());
        } catch (IOException e) {
            // Fail silently if we can't contact the server
        }
    }

    /**
     * Copies a board from the GitHub board repository to the board directory
     * @param boardName the board name (sans bd)
     * @return true if the board was successfully copied, otherwise false
     */
    public static boolean updateBoard(String boardName) {

        String qualifiedBoardName =
                GameModule.getGameModule().getPrefs().getStoredValue(ASLBoardPicker.BOARD_DIR) +
                        System.getProperty("file.separator", "\\") +
                        "bd" + boardName;
        String url = boardRepositoryURL + "/bd" + boardName;

        return getRepositoryFile(url, qualifiedBoardName);

    }

    /**
     * Copies an overlay file from the GitHub board repository to the overlay directory
     * @param overlayName the overlay name (sans ovr)
     * @return true if the overlay was successfully copied, otherwise false
     */
    public static boolean updateOverlayFile(String overlayName) {

        String qualifiedOverlayName =
                GameModule.getGameModule().getPrefs().getStoredValue(ASLBoardPicker.BOARD_DIR) +
                        System.getProperty("file.separator", "\\") +
                        "overlays" +
                        System.getProperty("file.separator", "\\") +
                        "ovr" + overlayName;
        String url = boardRepositoryURL + "/bd" + overlayName;

        return getRepositoryFile(url, qualifiedOverlayName);
    }

    /**
     * Copy a file from a website to local disk
     * Assumes URL is on github.com
     * NOTE - will overwrite existing file
     * @param url URL to the file on the website
     * @param fileName fully qualified file name
     * @return true if copy succeeded, otherwise false
     */
    private static boolean getRepositoryFile(String url, String fileName)  {

        // Need to disable SNI to read from Github
        System.setProperty("jsse.enableSNIExtension", "false");

        FileOutputStream outFile = null;
        InputStream in = null;
        try {

            URL website = new URL(url);
            URLConnection conn = website.openConnection();
            conn.setUseCaches(false);

            in = conn.getInputStream();
            ReadableByteChannel rbc = Channels.newChannel(in);
            outFile = new FileOutputStream(fileName);
            outFile.getChannel().transferFrom(rbc, 0, Long.MAX_VALUE);

            return true;

        } catch (IOException e) {
            // Fail silently on any error
            return false;
        }
        finally {
            IOUtils.closeQuietly(outFile);
            IOUtils.closeQuietly(in);
        }
    }
}
