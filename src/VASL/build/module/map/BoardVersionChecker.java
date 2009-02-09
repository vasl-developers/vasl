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

import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.net.URLConnection;
import java.util.Enumeration;
import java.util.Properties;
import java.util.Vector;

import javax.swing.JOptionPane;
import javax.swing.SwingUtilities;

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

/**
 * Copyright (c) 2003 by Rodney Kinney.  All rights reserved.
 * Date: Jun 11, 2003
 */
public class BoardVersionChecker extends AbstractBuildable implements GameComponent, PropertyChangeListener {
  private String boardVersionURL;
  private String overlayVersionURL;
  private Map map;
  private Properties boardVersions;
  private Properties overlayVersions;
  private static final String BOARD_VERSION_URL = "boardVersionURL";
  private static final String OVERLAY_VERSION_URL = "overlayVersionURL";
  private static final String BOARD_VERSIONS = "boardVersions";
  private static final String OVERLAY_VERSIONS = "overlayVersions";

  public String[] getAttributeNames() {
    return new String[]{BOARD_VERSION_URL, OVERLAY_VERSION_URL};
  }

  public String getAttributeValueString(String key) {
    if (BOARD_VERSION_URL.equals(key)) {
      return boardVersionURL;
    }
    else if (OVERLAY_VERSION_URL.equals(key)) {
      return overlayVersionURL;
    }
    return null;
  }

  public void setAttribute(String key, Object value) {
    if (BOARD_VERSION_URL.equals(key)) {
      boardVersionURL = (String) value;
    }
    else if (OVERLAY_VERSION_URL.equals(key)) {
      overlayVersionURL = (String) value;
    }
  }

  public void addTo(Buildable parent) {
    map = (Map) parent;
    GameModule.getGameModule().getGameState().addGameComponent(this);
    GameModule.getGameModule().getServer().addPropertyChangeListener(ServerConnection.CONNECTED, this);
    GameModule.getGameModule().getPrefs().addOption(null, new StringConfigurer(BOARD_VERSIONS, null));
    Properties p = readVersionList((String) GameModule.getGameModule().getPrefs().getValue(BOARD_VERSIONS));
    if (p != null) {
      boardVersions = p;
    }
    GameModule.getGameModule().getPrefs().addOption(null, new StringConfigurer(OVERLAY_VERSIONS, null));
    p = readVersionList((String) GameModule.getGameModule().getPrefs().getValue(OVERLAY_VERSIONS));
    if (p != null) {
      overlayVersions = p;
    }
  }

  public Command getRestoreCommand() {
    return null;
  }

  public void setup(boolean gameStarting) {
    if (gameStarting) {
      if (boardVersions != null) {
        Vector obsolete = new Vector();
        for (Board board : map.getBoards()) {
          ASLBoard b = (ASLBoard) board;
          String availableVersion = boardVersions.getProperty(b.getName(), b.getVersion());
          if (!availableVersion.equals(b.getVersion())) {
            obsolete.addElement(b.getName());
          }
        }
        String msg = null;
        if (obsolete.size() == 1) {
          String name = (String) obsolete.firstElement();
          msg = "Version " + boardVersions.getProperty(name) + " of board " + name + " is now available.\nhttp://www.vasl.org/boards.htm";
        }
        else if (obsolete.size() > 1) {
          StringBuffer buff = new StringBuffer();
          for (int i = 0,j = obsolete.size(); i < j; ++i) {
            buff.append((String) obsolete.elementAt(i));
            if (i < j - 2) {
              buff.append(", ");
            }
            else if (i < j - 1) {
              buff.append(" and ");
            }
          }
          msg = "New versions of boards " + buff + " are available.\nhttp://www.vasl.org/boards.htm";
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
      if (overlayVersions != null) {
        Vector obsolete = new Vector();
        for (Board board : map.getBoards()) {
          ASLBoard b = (ASLBoard) board;
          for (Enumeration e2 = b.getOverlays(); e2.hasMoreElements();) {
            Overlay o = (Overlay) e2.nextElement();
            if (o.getClass().equals(Overlay.class)) { // Don't check for SSROverlays
              String name = o.getFile().getName();
              String availableVersion = overlayVersions.getProperty(name, o.getVersion());
              if (!availableVersion.equals(o.getVersion())) {
                obsolete.addElement(name);
              }
            }
          }
        }
        String msg = null;
        if (obsolete.size() == 1) {
          String name = (String) obsolete.firstElement();
          msg = "Version " + overlayVersions.getProperty(name) + " of overlay " + name + " is now available.\nhttp://www.vasl.org/boards.htm";
        }
        else if (obsolete.size() > 1) {
          StringBuffer buff = new StringBuffer();
          for (int i = 0,j = obsolete.size(); i < j; ++i) {
            buff.append((String) obsolete.elementAt(i));
            if (i < j - 2) {
              buff.append(", ");
            }
            else if (i < j - 1) {
              buff.append(" and ");
            }
          }
          msg = "New versions of overlays " + buff + " are available.\nhttp://www.vasl.org/boards.htm";
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

  private Properties readVersionList(String s) {
    Properties p = null;
    if (s != null
        && s.length() > 0) {
      try {
        p = new PropertiesEncoder(s).getProperties();
      }
      catch (IOException e) {
        // Fail silently if we can't contact the server
      }
    }
    return p;
  }

  public void propertyChange(PropertyChangeEvent evt) {
    if (Boolean.TRUE.equals(evt.getNewValue())) {
      try {
        URL base = new URL(boardVersionURL);
        URLConnection conn = base.openConnection();
        conn.setUseCaches(false);
        InputStream input = conn.getInputStream();
        Properties p = new Properties();
        p.load(input);
        boardVersions = p;
        GameModule.getGameModule().getPrefs().getOption(BOARD_VERSIONS).setValue(new PropertiesEncoder(p).getStringValue());
        input.close();
      }
      catch (IOException e) {
        // Fail silently if we can't contact the server
      }
      try {
        URL base = new URL(overlayVersionURL);
        URLConnection conn = base.openConnection();
        conn.setUseCaches(false);
        InputStream input = conn.getInputStream();
        Properties p = new Properties();
        p.load(input);
        overlayVersions = p;
        GameModule.getGameModule().getPrefs().getOption(OVERLAY_VERSIONS).setValue(new PropertiesEncoder(p).getStringValue());
        input.close();
      }
      catch (IOException e) {
        // Fail silently if we can't contact the server
      }
    }
  }
}
