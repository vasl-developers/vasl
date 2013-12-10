/*
 * $Id: VASLThread.java 781 2005-11-07 06:25:46Z rodneykinney $
 *
 * Copyright (c) 2001 David Sullivan
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

import CASL.Map.*;
import CASL.Scenario.Scenario;
import VASL.build.module.map.boardPicker.ASLBoard;
import VASL.counters.ASLProperties;
import VASL.counters.TextInfo;
import VASSAL.build.Buildable;
import VASSAL.build.GameModule;
import VASSAL.build.module.GameComponent;
import VASSAL.build.module.map.boardPicker.Board;
import VASSAL.build.module.map.boardPicker.board.HexGrid;
import VASSAL.command.Command;
import VASSAL.configure.BooleanConfigurer;
import VASSAL.configure.ColorConfigurer;
import VASSAL.counters.Decorator;
import VASSAL.counters.GamePiece;
import VASSAL.counters.PieceIterator;
import VASSAL.tools.io.IOUtils;
import org.jdesktop.swingworker.SwingWorker;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.swing.*;
import java.awt.*;
import java.awt.event.KeyEvent;
import java.awt.event.KeyListener;
import java.awt.event.MouseEvent;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.StringTokenizer;
import java.util.concurrent.ExecutionException;

/**
 * Extends the LOS thread to take advantage of CASL's LOS logic and report
 */
public class CASLThread
    extends ASLThread
    implements KeyListener, GameComponent {

  private static final Logger logger =
    LoggerFactory.getLogger(CASLThread.class);

  public static final String ENABLED = "LosCheckEnabled";
  // status flag

  private int status = LOADING;
  private static final int LOADING = 1; // Initialization Thread still running
  private static final int LOADED = 2; // CASL map successfully loaded
  private static final int ERROR = 3; // Error loading CASL map
  private static final int DISABLED = 4; // Permanently disabled, due to incompatible Java version
  // board stuff
  private GameMap CASLMap;
  private int mapWidth = 0;		// VASL map dimensions (i.e. number of rows and columns of boards)
  private int mapHeight = 0;

  // LOS stuff
  protected LOSResult result;
  private String resultsString;
  private boolean useAuxSourceLOSPoint;
  private boolean useAuxTargetLOSPoint;
  private Location source;
  private Location target;
  private Scenario scenario;
  private ASLBoard upperLeftBoard;

  // LOS colors
  private Color LOSColor;
  private Color hindranceColor;
  private Color blockedColor;

  // Displayed in top left corner of map while loading
  private Image loadingStatus = null;

  // Thread to initialize CASL map in background;
//  private Thread initThread;
  private SwingWorker<String, Void> initThread;

  public CASLThread() {
    // ensure correct Java version
    if (System.getProperty("java.version").startsWith("1.1")) {
      System.err.println("LOS checking turned off:  Java version " + System.getProperty("java.version"));
      status = DISABLED;
      freeResources();
    }

      // disable CASL LOS
      status = DISABLED;
  }

  /** Invoked when the user hits the "LOS" button */
  protected void launch() {
    if (!isPreferenceEnabled()) {
      super.launch();
        super.threadColor = (Color) GameModule.getGameModule().getPrefs().getValue("threadColor");
    }
    else {
      switch (status) {
        case LOADING:
          if (initThread != null && !visible) {
            try {
              initThread.get();
            }
            catch (InterruptedException e) {
            }
            catch (ExecutionException e) {
// FIXME: this is likely an actual problem, should not just log it!
              logger.error("", e);
            }
            launchTruLOS();
          }
          break;
        case LOADED:
          if (!visible) {
            launchTruLOS();
          }
          break;
        default:
          super.launch();
      }
    }
  }

  /** register mouseListener that calculates true LOS */
  private void launchTruLOS() {
    super.launch();
    LOSColor = (Color) GameModule.getGameModule().getPrefs().getValue("threadColor");
    hindranceColor = (Color) GameModule.getGameModule().getPrefs().getValue("hindranceThreadColor");
    blockedColor = (Color) GameModule.getGameModule().getPrefs().getValue("blockedThreadColor");
    map.getView().requestFocus();
  }

  /** Initialize the CASL Map.  Return null if successful, otherwise return a String describing the error preventing load */
  protected String initCaslMap() {
    if (DISABLED == status) {
      return null;
    }
    // hide while loading
    visible = false;

    // if there are any unexpected exceptions, turn off LOS checking
    try {
      // get the board list
      List<ASLBoard> boardList = new ArrayList<ASLBoard>();
      for (Board b : map.getBoards()) {
          boardList.add((ASLBoard)b);
      }

      // determine the VASL map dimensions
      for (ASLBoard b : boardList) {
        mapWidth = Math.max(b.relativePosition().x, mapWidth);
        mapHeight = Math.max(b.relativePosition().y, mapHeight);
      }
      mapWidth++;
      mapHeight++;
      // create the necessary LOS variables
      result = new LOSResult();
      scenario = new Scenario();
      resultsString = "";

      // create the map
      //CASLMap = new GameMap(mapWidth * 32 + 1, mapHeight * 10);
      //CASLMap = createCASLMap(mapWidth * 32 + 1, mapHeight * 10);
      if (boardList.size() > 0) {
        CASLMap = createCASLMap(mapWidth * (int) Math.round(boardList.get(0).getUncroppedSize().getWidth() / 56.25) + 1,
            mapHeight * (int) Math.round(boardList.get(0).getUncroppedSize().getHeight() / 64.5));
      }



      // load the CASL maps
      boolean mapFound = false;
      for (ASLBoard b : boardList) {

        String boardName = b.getName().startsWith("r") ? b.getName().substring(1) : b.getName();

        // set the upper left board
        if (b.relativePosition().x == 0 && b.relativePosition().y == 0) {

          upperLeftBoard = b;
        }

        // load the map files
        GameMap newCASLMap;
        if (b.getBoardArchive() == null) {
          newCASLMap = null;
        }
        else {
          InputStream in = null;
          try {
            in = b.getBoardArchive().getInputStream("bd" + boardName + ".map");
            newCASLMap = CASL.Map.Map.readMap(in);
          }
          catch (IOException e) {
            freeResources();
            return "@LOS engine disabled... Could not read bd" + boardName + ".map";
          }
          finally {
            IOUtils.closeQuietly(in);
          }
        }

        if (newCASLMap == null) {
          freeResources();
          return "LOS engine disabled... Board " + boardName + " does not support LOS checking";
        }

        else {
          mapFound = true;
          applyTerrainChanges(b, newCASLMap);
          // reverse if necessary
          if (b.isReversed()) {
            newCASLMap.flip();
          }

          // add to map
          if (!CASLMap.insertGEOMap(newCASLMap, CASLMap.getHex(b.relativePosition().x * ((int) (Math.round(b.getUncroppedSize().getWidth() / 56.25))), b.relativePosition().y * ((int) (Math.round(b.getUncroppedSize().getHeight() / 64.5)))))) {
            System.err.println("LOS engine disabled... Error building map");
            newCASLMap = null;
            freeResources();
            return "LOS engine disabled... Error building map";
          }

          // clean up to try to reuse the same memory
          newCASLMap = null;
          System.gc();
        }
      }

      // found no boards?
      if (!mapFound) {
        System.err.println("LOS engine disabled... No board found");
        freeResources();
        return "LOS engine disabled... No board found";
      }
    }
        // give up with any exception
    catch (Exception e) {
      freeResources();
      e.printStackTrace();
      return "LOS engine disabled... " + e.getMessage();
    }
    return null;
  }

  protected GameMap createCASLMap(int w, int h) {
    return new GameMap(w, h);
  }

  public void addTo(Buildable buildable) {
      super.addTo(buildable);
      if (status != DISABLED) {
          // add the key listener
          map.getView().addKeyListener(this);
          // add additional thread colors
          final BooleanConfigurer enable = new BooleanConfigurer(ENABLED, "Enable LOS checking", Boolean.TRUE);
          final JCheckBox enableBox = findBox(enable.getControls());
          final ColorConfigurer hindrance = new ColorConfigurer("hindranceThreadColor", "Hindrance Thread Color", Color.red);
          final ColorConfigurer blocked = new ColorConfigurer("blockedThreadColor", "Blocked Thread Color", Color.blue);
          final BooleanConfigurer verbose = new BooleanConfigurer("verboseLOS", "Verbose LOS mode");
          GameModule.getGameModule().getPrefs().addOption(getAttributeValueString("label"), enable);
          GameModule.getGameModule().getPrefs().addOption(getAttributeValueString("label"), hindrance);
          GameModule.getGameModule().getPrefs().addOption(getAttributeValueString("label"), blocked);
          GameModule.getGameModule().getPrefs().addOption(getAttributeValueString("label"), verbose);
          java.awt.event.ItemListener l = new java.awt.event.ItemListener() {
              public void itemStateChanged(java.awt.event.ItemEvent evt) {
                  enableAll(hindrance.getControls(), enableBox.isSelected());
                  enableAll(blocked.getControls(), enableBox.isSelected());
                  enableAll(verbose.getControls(), enableBox.isSelected());
              }
          };
          enableBox.addItemListener(l);
          enableAll(hindrance.getControls(), Boolean.TRUE.equals(enable.getValue()));
          enableAll(blocked.getControls(), Boolean.TRUE.equals(enable.getValue()));
          enableAll(verbose.getControls(), Boolean.TRUE.equals(enable.getValue()));
          // hook for game opening/closing
          GameModule.getGameModule().getGameState().addGameComponent(this);
      }

      // customize VASL thread
      else {

          final ColorConfigurer threadColor = new ColorConfigurer("threadColor", "Thread Color", Color.red);
          GameModule.getGameModule().getPrefs().addOption("LOS", threadColor);

      }
  }

  protected JCheckBox findBox(Component c) {
    JCheckBox val = null;
    if (c instanceof JCheckBox) {
      val = (JCheckBox) c;
    }
    if (c instanceof Container) {
      if (c instanceof Container) {
        for (int i = 0; i < ((Container) c).getComponentCount(); ++i) {
          val = findBox(((Container) c).getComponent(i));
          if (val != null) {
            break;
          }
        }
      }
    }
    return val;
  }

  private void enableAll(Component c, boolean enable) {
    c.setEnabled(enable);
    if (c instanceof Container) {
      for (int i = 0; i < ((Container) c).getComponentCount(); ++i) {
        enableAll(((Container) c).getComponent(i), enable);
      }
    }
  }

  public void mousePressed(MouseEvent e) {
    super.mousePressed(e);
    if (!isEnabled()) {
      return;
    }
    result.setClear();
    // get the map point
    Point p = mapMouseToCASLCoordinates(e.getPoint());
    if (p == null || !CASLMap.onMap(p.x, p.y)) return;
    // get the nearest location
    source = CASLMap.gridToHex(p.x, p.y).nearestLocation(p.x, p.y);
    useAuxSourceLOSPoint = useAuxLOSPoint(source, p.x, p.y);
    // if Ctrl click, use upper location
    if (e.isControlDown()) {
      while (source.getUpLocation() != null) {
        source = source.getUpLocation();
      }
    }
    // make the souce and the target the same
    target = source;
    useAuxTargetLOSPoint = useAuxSourceLOSPoint;
    // update the scenario
    resetScenario();
  }

  public void mouseReleased(MouseEvent e) {
      source = null;
      target = null;
      super.mouseReleased(e);
  }

  public void mouseDragged(MouseEvent e) {
    if (source != null && isEnabled()) {
    // get the map point, ensure the point is on the CASL map
    Point p = mapMouseToCASLCoordinates(map.mapCoordinates(e.getPoint()));
    if (p == null || !CASLMap.onMap(p.x, p.y)) return;
    Location newLocation = CASLMap.gridToHex(p.x, p.y).nearestLocation(p.x, p.y);
    boolean useAuxNewLOSPoint = useAuxLOSPoint(newLocation, p.x, p.y);
    // are we really in a new location?
    if (target == newLocation && useAuxTargetLOSPoint == useAuxNewLOSPoint) {
      return;
    }
    target = newLocation;
    useAuxTargetLOSPoint = useAuxNewLOSPoint;
    // if Ctrl click, use upper location
    if (e.isControlDown()) {
      while (target.getUpLocation() != null) {
        target = target.getUpLocation();
      }
    }
    doLOS();
    }
    super.mouseDragged(e);
  }

  private boolean isEnabled() {
    return visible
        && status == LOADED
        && isPreferenceEnabled();
  }

  private boolean isPreferenceEnabled() {
    return Boolean.TRUE.equals(GameModule.getGameModule().getPrefs().getValue(ENABLED));
  }

  public void draw(Graphics g, VASSAL.build.module.Map m) {
    if (!isPreferenceEnabled()) {
      super.draw(g, m);
    }
    else if (LOADING == status) {
      if (loadingStatus == null) {
        JLabel l = new JLabel("Loading LOS data ...");
        l.setSize(l.getPreferredSize());
        l.setFont(new Font("Dialog", 0, 11));
        l.setForeground(Color.black);
        Color bg = new Color(200, 200, 255);
        l.setBackground(bg);
        loadingStatus = map.getView().createImage(l.getWidth(), l.getHeight());
        Graphics gg = loadingStatus.getGraphics();
        gg.setColor(bg);
        gg.fillRect(0, 0, l.getWidth(), l.getHeight());
        l.paint(gg);
      }
      g.drawImage(loadingStatus, map.getView().getVisibleRect().x, map.getView().getVisibleRect().y, map.getView());
    }
    else if (visible && status == LOADED) {
      lastAnchor = map.componentCoordinates(anchor);
      lastArrow = map.componentCoordinates(arrow);
      if (source != null) {
        // source LOS point
        Point sourceLOSPoint;
        if (useAuxSourceLOSPoint) {
          sourceLOSPoint = new Point(source.getAuxLOSPoint());
        }
        else {
          sourceLOSPoint = new Point(source.getLOSPoint());
        }
        sourceLOSPoint = mapCASLPointToScreen(sourceLOSPoint);
        // target LOS point
        Point targetLOSPoint;
        if (useAuxTargetLOSPoint) {
          targetLOSPoint = new Point(target.getAuxLOSPoint());
        }
        else {
          targetLOSPoint = new Point(target.getLOSPoint());
        }
        targetLOSPoint = mapCASLPointToScreen(targetLOSPoint);
        // transform the blocked-at point
        Point b = null;
        if (result.isBlocked()) {
          b = new Point(result.getBlockedAtPoint());
          b = mapCASLPointToScreen(b);
        }
        // transform the hindrance point
        Point h = null;
        if (result.hasHindrance()) {
          h = new Point(result.firstHindranceAt());
          h = mapCASLPointToScreen(h);
        }
        // draw the LOS thread
        if (result.isBlocked()) {
          if (result.hasHindrance()) {
            g.setColor(LOSColor);
            g.drawLine(
                sourceLOSPoint.x,
                sourceLOSPoint.y,
                h.x,
                h.y);
            g.setColor(hindranceColor);
            g.drawLine(
                h.x,
                h.y,
                b.x,
                b.y);
            g.setColor(blockedColor);
            g.drawLine(
                b.x,
                b.y,
                targetLOSPoint.x,
                targetLOSPoint.y);
          }
          else {
            g.setColor(LOSColor);
            g.drawLine(
                sourceLOSPoint.x,
                sourceLOSPoint.y,
                b.x,
                b.y);
            g.setColor(blockedColor);
            g.drawLine(
                b.x,
                b.y,
                targetLOSPoint.x,
                targetLOSPoint.y);
          }
        }
        else if (result.hasHindrance()) {
          g.setColor(LOSColor);
          g.drawLine(
              sourceLOSPoint.x,
              sourceLOSPoint.y,
              h.x,
              h.y);
          g.setColor(hindranceColor);
          g.drawLine(
              h.x,
              h.y,
              targetLOSPoint.x,
              targetLOSPoint.y);
        }
        else {
          g.setColor(LOSColor);
          g.drawLine(
              sourceLOSPoint.x,
              sourceLOSPoint.y,
              targetLOSPoint.x,
              targetLOSPoint.y);
        }
        // use the draw range property to turn all text on/off
        if (drawRange) {
          // determine if the text should be above or below the location
          boolean shiftSourceText = sourceLOSPoint.y > targetLOSPoint.y;
          int shift = g.getFontMetrics().getHeight();
          // draw the source elevation
          switch (source.getBaseHeight() + source.getHex().getBaseHeight()) {
            case -1:
            case -2:
              g.setColor(Color.red);
              break;
            case 0:
              g.setColor(Color.gray);
              break;
            case 1:
              g.setColor(Color.darkGray);
              break;
            case 2:
              g.setColor(Color.black);
              break;
            default:
              g.setColor(Color.white);
          }
          g.setFont(RANGE_FONT);
          if (isVerbose()) {
            lastRangeRect = drawString(g,
                       sourceLOSPoint.x - 20,
                       sourceLOSPoint.y + (shiftSourceText ? shift : 0) - g.getFontMetrics().getDescent(),
                       source.getName() + "  (Level " + (source.getBaseHeight() + source.getHex().getBaseHeight() + ")"));
          }
          else if (source.getBaseHeight() != 0) {
            lastRangeRect = drawString(g,
                       sourceLOSPoint.x - 20,
                       sourceLOSPoint.y + (shiftSourceText ? shift : 0) - g.getFontMetrics().getDescent(),
                       "Level " + (source.getBaseHeight() + source.getHex().getBaseHeight()));
          }
          // draw the target elevation
          switch (target.getBaseHeight() + target.getHex().getBaseHeight()) {
            case -1:
            case -2:
              g.setColor(Color.red);
              break;
            case 0:
              g.setColor(Color.gray);
              break;
            case 1:
              g.setColor(Color.darkGray);
              break;
            case 2:
              g.setColor(Color.black);
              break;
            default:
              g.setColor(Color.white);
          }
          if (isVerbose()) {
            lastRangeRect.add(drawString(g,
                       targetLOSPoint.x - 20,
                       targetLOSPoint.y + (shiftSourceText ? 0 : shift) - g.getFontMetrics().getDescent(),
                       target.getName() + "  (Level " + (target.getBaseHeight() + target.getHex().getBaseHeight() + ")")));
          }
          else if (target.getBaseHeight() != 0) {
            lastRangeRect.add(drawString(g,
                       targetLOSPoint.x - 20,
                       targetLOSPoint.y + (shiftSourceText ? 0 : shift) - g.getFontMetrics().getDescent(),
                       "Level " + (target.getBaseHeight() + target.getHex().getBaseHeight())));
          }
          // draw the results string
          g.setColor(Color.black);
          if (shiftSourceText) {
            lastRangeRect.add(drawString(g, targetLOSPoint.x - 20, targetLOSPoint.y - shift, resultsString));
          }
          else {
            lastRangeRect.add(drawString(g, targetLOSPoint.x - 20, targetLOSPoint.y + shift * 2 - 2, resultsString));
          }
        }
      }
    }
    else {
      super.draw(g, m);
    }
  }

  private boolean isVerbose() {
    return Boolean.TRUE.equals(GameModule.getGameModule().getPrefs().getValue("verboseLOS"));
  }

  /******************************
   Keyboard methods
   ******************************/
  public void keyTyped(KeyEvent e) {
  }

  public void keyReleased(KeyEvent e) {
  }

  public void keyPressed(KeyEvent e) {
    if (!isEnabled()) {
      return;
    }
    int code = e.getKeyCode();
    String modifiers = KeyEvent.getKeyModifiersText(e.getModifiers());
    // move up
    if (code == KeyEvent.VK_KP_UP || code == KeyEvent.VK_UP) {
      // move the source up
      if (modifiers.equals("Ctrl") && source != null) {
        if (source.getUpLocation() != null) {
          source = source.getUpLocation();
          doLOS();
          map.repaint();
        }
      }
      // move the target up
      else if (modifiers.equals("") && target != null) {
        if (target.getUpLocation() != null) {
          target = target.getUpLocation();
          doLOS();
          map.repaint();
        }
      }
    }
    // move down
    else if (code == KeyEvent.VK_KP_DOWN || code == KeyEvent.VK_DOWN) {
      // move the source down
      if (modifiers.equals("Ctrl") && source != null) {
        if (source.getDownLocation() != null) {
          source = source.getDownLocation();
          doLOS();
          map.repaint();
        }
      }
      // move the target down
      else if (modifiers.equals("") && target != null) {
        if (target.getDownLocation() != null) {
          target = target.getDownLocation();
          doLOS();
          map.repaint();
        }
      }
    }
  }

  /******************************
   Private methods
   ******************************/
  private boolean useAuxLOSPoint(Location l, int x, int y) {
    Point LOSPoint = l.getLOSPoint();
    Point AuxLOSPoint = l.getAuxLOSPoint();
    // use the closest LOS point
    if (Point.distance(x, y, LOSPoint.x, LOSPoint.y) > Point.distance(x, y, AuxLOSPoint.x, AuxLOSPoint.y)) {
      return true;
    }
    return false;
  }

    private void resetScenario() {
        // remove all of the old smoke, vehicles
        CASLMap.removeAllSmoke();
        scenario = new Scenario();
        if (!map.isPiecesVisible() && Boolean.TRUE.equals(GameModule.getGameModule().getPrefs().getValue(HindranceKeeper.DRAW_HINDRANCES))) {
            // get all of the game pieces
            GamePiece[] p = map.getPieces();
            // add each of the pieces to the scenario
            for (int i = 0; i < p.length; ++i) {
                if (p[i] instanceof VASSAL.counters.Stack) {
                    for (PieceIterator pi = new PieceIterator(((VASSAL.counters.Stack) p[i]).getPiecesIterator()); pi.hasMoreElements();) {
                        loadPiece(pi.nextPiece());
                    }
                }
                else {
                    loadPiece(p[i]);
                }
            }
        }
    }

    private void loadPiece(GamePiece piece) {
        // determine what hex the piece is in
        Point p = map.mapCoordinates(new Point(piece.getPosition()));
        p.x *= map.getZoom();
        p.y *= map.getZoom();
        p.translate(-map.getEdgeBuffer().width, -map.getEdgeBuffer().height);

        if (!CASLMap.onMap(p.x, p.y)) return;
        Hex h = CASLMap.gridToHex(p.x, p.y);

        // add the piece to the scenario/map
        if ((piece.getProperty(ASLProperties.HINDRANCE) != null && !Boolean.TRUE.equals(piece.getProperty(VASSAL.counters.Properties.INVISIBLE_TO_ME)))) {
            String name = piece.getName().trim();
            // smoke
            if (name.equals("White +3 Smoke")) {
                CASLMap.addSmoke(new Smoke(Smoke.SMOKE, h.getCenterLocation()));
            }
            else if (name.equals("White +2 Smoke")) {
                CASLMap.addSmoke(new Smoke(Smoke.SMOKE, h.getCenterLocation(), true));
            }
            else if (name.equals("Gray +2 Smoke")) {
                CASLMap.addSmoke(new Smoke(Smoke.SMOKE, h.getCenterLocation(), true));
            }
            else if (name.equals("White +2 WP")) {
                CASLMap.addSmoke(new Smoke(Smoke.WHITE_PHOSPHORUS, h.getCenterLocation()));
            }
            else if (name.equals("White +1 WP")) {
                CASLMap.addSmoke(new Smoke(Smoke.WHITE_PHOSPHORUS, h.getCenterLocation(), true));
            }
            else if (name.equals("Smoke grenade +2")) {
                CASLMap.addSmoke(new Smoke(Smoke.SMOKE_GRENADES, h.getCenterLocation()));
            }
            else if (name.equals("Gray +1 WP") || name.equals("WP grenade +1")) {
                CASLMap.addSmoke(new Smoke(Smoke.WHITE_PHOSPHORUS_SMOKE_GRENADES, h.getCenterLocation()));
            }
            else if (name.equals("Blaze")) {
                CASLMap.addSmoke(new Smoke(Smoke.SMOKE, h.getCenterLocation()));
            }
            else if (name.equals("Blazing Building") || name.equals("1-level Blaze") || name.equals("2-level Blaze") || name.equals("3-level Blaze") || name.equals("4-level Blaze")) {
                CASLMap.addSmoke(new Smoke(Smoke.SMOKE, h.getCenterLocation()));
            }
            else if (name.equals("Wreck")) {
                scenario.addUnit(new CASL.Unit.Vehicle(h.getCenterLocation()), Scenario.ALLIES);
            }
            // vehicle hindrances
            else if (Decorator.getDecorator(piece, TextInfo.class) != null) {
                scenario.addUnit(new CASL.Unit.Vehicle(h.getCenterLocation()), Scenario.ALLIES);
            }
            else if (name.equals("Stone Rubble")) {
                CASLMap.setGridTerrain(h.getHexBorder(), CASLMap.getTerrain(Terrain.STONE_RUBBLE));
                CASLMap.setHexTerrain(h.getHexBorder(), CASLMap.getTerrain(Terrain.STONE_RUBBLE));
            }
            else if (name.equals("Wood Rubble")) {
                CASLMap.setGridTerrain(h.getHexBorder(), CASLMap.getTerrain(Terrain.WOODEN_RUBBLE));
                CASLMap.setHexTerrain(h.getHexBorder(), CASLMap.getTerrain(Terrain.WOODEN_RUBBLE));
            }
            // TODO: Palm Debris
            else
                System.out.println("LOS WARNING: hindrance not handled for counter ["+name+"] at hex " + h.getName());
        }
    }

    public void setup(boolean flag) {
        // game closing - close LOS and free resources
        if (!flag) {
            CASLMap = null;
            visible = false;
            freeResources();
            initThread = null;
        }
        // game opening - start a new Thread to load CASL Map
        else if (isPreferenceEnabled()) {
            if (initThread == null
                    && status != DISABLED) {
                status = LOADING;
                initThread = new SwingWorker<String,Void>() {
                    @Override
                    protected String doInBackground() throws Exception {
                        return initCaslMap();
                    }
                    protected void done() {
                        String error = null;
                        try {
                            error = get();
                        }
                        catch (InterruptedException e) {
                            e.printStackTrace();
                        }
                        catch (ExecutionException e) {
                            e.printStackTrace();
                        }
                        if (error != null) {
                            if(error.charAt(0) != '@')
                                GameModule.getGameModule().warn(error);
                            status = ERROR;
                        }
                        else {
                            status = LOADED;
                        }
                        map.repaint();
                    }
                };
                initThread.execute();
            }
        }
        else {
            super.setup(flag);
        }
    }

    public Command getRestoreCommand() {
        return null;
    }

    private Point mapCASLPointToScreen(Point p) {
        Point temp = map.componentCoordinates(new Point(p));
        double scale = upperLeftBoard == null ? 1.0 : upperLeftBoard.getMagnification() * ((HexGrid)upperLeftBoard.getGrid()).getHexSize()/ASLBoard.DEFAULT_HEX_HEIGHT;
        if (upperLeftBoard != null) {
            temp.x = (int)Math.round(temp.x*scale);
            temp.y = (int)Math.round(temp.y*scale);
        }
        temp.translate((int) (map.getEdgeBuffer().width * map.getZoom()), (int) (map.getEdgeBuffer().height * map.getZoom()));
        // adjust for board cropping
        if (upperLeftBoard != null) {
            int deltaX = 0, deltaY = 0;
            Rectangle crop = upperLeftBoard.getCropBounds();
            if (upperLeftBoard.isReversed()) {
                double hexScale = ((HexGrid)upperLeftBoard.getGrid()).getHexSize()/ASLBoard.DEFAULT_HEX_HEIGHT;
                if (crop.width >= 0) {
//          deltaX = (int)Math.round((upperLeftBoard.getUncroppedSize().width - crop.x - crop.width)*hexScale);
                    deltaX = upperLeftBoard.getUncroppedSize().width - crop.x - crop.width;
                }
                if (crop.height >= 0) {
//          deltaY = (int)Math.round((upperLeftBoard.getUncroppedSize().height - crop.y - crop.height)*hexScale);
                    deltaY = upperLeftBoard.getUncroppedSize().height - crop.y - crop.height;
                }
            }
            else {
                deltaX = crop.x;
                deltaY = crop.y;
            }
            temp.translate((int) Math.round(-deltaX * map.getZoom()*upperLeftBoard.getMagnification()),
                    (int) Math.round(-deltaY * map.getZoom()*upperLeftBoard.getMagnification()));
        }
        return temp;
    }

    private Point mapMouseToCASLCoordinates(Point p) {
        ASLBoard b = (ASLBoard) map.findBoard(p);
        // ensure we are on a board
        if (b == null) return null;
        // Now we need to adjust for cropping of the boards to the left and
        // above the target board
        for (Board board : map.getBoards()) {
            ASLBoard b2 = (ASLBoard) board;
            double scale = b2.getMagnification() * ((HexGrid)b2.getGrid()).getHexSize()/ASLBoard.DEFAULT_HEX_HEIGHT;
            if (b2.relativePosition().y == b.relativePosition().y
                    && b2.relativePosition().x < b.relativePosition().x) {
                p.translate((int)Math.round(scale*b2.getUncroppedSize().width) - b2.bounds().width, 0);
            }
            else if (b2.relativePosition().x == b.relativePosition().x
                    && b2.relativePosition().y < b.relativePosition().y) {
                p.translate(0, (int)Math.round(scale*b2.getUncroppedSize().height) - b2.bounds().height);
            }
        }
        // remove edge buffer
        p.translate(-map.getEdgeBuffer().width, -map.getEdgeBuffer().height);
        p = b.localCoordinates(p);
        double gridScale = ((HexGrid)b.getGrid()).getHexSize() / ASLBoard.DEFAULT_HEX_HEIGHT;
        p.x = (int)Math.round(p.x / gridScale);
        p.y = (int)Math.round(p.y / gridScale);
        if (b.isReversed()) {
            p.x = (int)Math.round(b.getUncroppedSize().width/gridScale)-p.x;
            p.y = (int)Math.round(b.getUncroppedSize().height/gridScale)-p.y;
        }
        return p;
    }

    private void applyTerrainChanges(ASLBoard b, GameMap map) {
        StringTokenizer st = new StringTokenizer(b.getState(), "\t");
        String s = null;
        while (st.hasMoreTokens()) {

            s = st.nextToken();
//		System.out.println("token: " + s);

            // convert the terrain
            if (s.equals("AllPaved")) {

            }
            else if (s.equals("AllDirt")) {

            }
            else if (s.equals("NoDirt")) {

            }
            else if (s.equals("NoStairwells")) {

            }
            else if (s.equals("RowhouseBarsToBuildings")) {

            }
            else if (s.equals("RowhouseBarsToOpenGround")) {

                map.changeAllTerrain(map.getTerrain(Terrain.ROWHOUSE_WALL), map.getTerrain(Terrain.OPEN_GROUND));
                map.changeAllTerrain(map.getTerrain(Terrain.ROWHOUSE_WALL_1_LEVEL), map.getTerrain(Terrain.OPEN_GROUND));
                map.changeAllTerrain(map.getTerrain(Terrain.ROWHOUSE_WALL_2_LEVEL), map.getTerrain(Terrain.OPEN_GROUND));
                map.changeAllTerrain(map.getTerrain(Terrain.ROWHOUSE_WALL_3_LEVEL), map.getTerrain(Terrain.OPEN_GROUND));
                map.changeAllTerrain(map.getTerrain(Terrain.ROWHOUSE_WALL_4_LEVEL), map.getTerrain(Terrain.OPEN_GROUND));
            }
            else if (s.equals("AllStone")) {

            }
            else if (s.equals("AllWood")) {

            }
            else if (s.equals("BrushToOpenGround")) {

                map.changeAllTerrain(map.getTerrain(Terrain.BRUSH), map.getTerrain(Terrain.OPEN_GROUND));
            }
            else if (s.equals("SwampToMarsh")) {

            }
            else if (s.equals("MarshToOpenGround")) {

                map.changeAllTerrain(map.getTerrain(Terrain.MARSH), map.getTerrain(Terrain.OPEN_GROUND));
            }
            else if (s.equals("GulliesToStreams")) {

            }
            else if (s.equals("Flooded")) {

                Hex h = null;
                Terrain dt;
                boolean isStream = false;

                // set ground level to level 0 for water and streams
                for (int col = 0; col < map.getWidth(); col++) {
                    for (int row = 0; row < map.getHeight() + (col % 2); row++) {

                        h = map.getHex(col, row);

                        // is it a stream hex?
                        dt = h.getCenterLocation().getDepressionTerrain();
                        if ((dt != null && (dt.getType() == Terrain.SHALLOW_STREAM || dt.getType() == Terrain.DRY_STREAM))) {

                            isStream = true;
                        }
                        else
                            for (int x = 0; x < 6; x++) {

                                dt = h.getHexsideLocation(x).getDepressionTerrain();
                                if ((dt != null && (dt.getType() == Terrain.SHALLOW_STREAM || dt.getType() == Terrain.DRY_STREAM))) {

                                    isStream = true;
                                }
                            }

                        if (isStream || h.getCenterLocation().getTerrain().getType() == Terrain.WATER) {

                            map.changeAllGroundLevel(-1, 0, h.getHexBorder());
                        }
                    }
                }

                // marsh to water
                map.changeAllTerrain(map.getTerrain(Terrain.MARSH), map.getTerrain(Terrain.WATER));
            }
            else if (s.equals("DryStreams")) {

            }
            else if (s.equals("OrchardOutOfSeason")) {

                map.changeAllTerrain(map.getTerrain(Terrain.ORCHARD), map.getTerrain(Terrain.ORCHARD_OUT_OF_SEASON));
            }
            else if (s.equals("NoGrain")) {

                map.changeAllTerrain(map.getTerrain(Terrain.GRAIN), map.getTerrain(Terrain.OPEN_GROUND));
            }
            else if (s.equals("NoSunkElevRoads")) {


            }
            else if (s.equals("NoRoads")) {


            }
            else if (s.equals("Level4ToLevel3")) {

                map.changeAllGroundLevel(4, 3);
            }
            else if (s.equals("Level4ToLevel2")) {

                map.changeAllGroundLevel(4, 2);
            }
            else if (s.equals("Level3ToLevel2")) {

                map.changeAllGroundLevel(3, 2);
            }
            else if (s.equals("Level4ToLevel1")) {

                map.changeAllGroundLevel(4, 1);
            }
            else if (s.equals("Level3ToLevel1")) {

                map.changeAllGroundLevel(3, 1);
            }
            else if (s.equals("Level2ToLevel1")) {

                map.changeAllGroundLevel(2, 1);
            }
            else if (s.equals("Level4ToLevel0")) {

                map.changeAllGroundLevel(4, 0);
            }
            else if (s.equals("Level3ToLevel0")) {

                map.changeAllGroundLevel(3, 0);
            }
            else if (s.equals("Level2ToLevel0")) {

                map.changeAllGroundLevel(2, 0);
            }
            else if (s.equals("Level1ToLevel0")) {

                map.changeAllGroundLevel(1, 0);
            }
            else if (s.equals("Level_1ToLevel0")) {

                map.changeAllGroundLevel(-1, 0);
            }
            else if (s.equals("HedgesOnly")) {

                map.changeAllTerrain(map.getTerrain(Terrain.WALL), map.getTerrain(Terrain.HEDGE));
            }
            else if (s.equals("WallsOnly")) {

                map.changeAllTerrain(map.getTerrain(Terrain.HEDGE), map.getTerrain(Terrain.WALL));
            }
            else if (s.equals("WallsToBocage")) {

                map.changeAllTerrain(map.getTerrain(Terrain.WALL), map.getTerrain(Terrain.BOCAGE));
            }
            else if (s.equals("HedgesToBocage")) {

                map.changeAllTerrain(map.getTerrain(Terrain.HEDGE), map.getTerrain(Terrain.BOCAGE));
            }
            else if (s.equals("Bocage")) {

                map.changeAllTerrain(map.getTerrain(Terrain.WALL), map.getTerrain(Terrain.BOCAGE));
                map.changeAllTerrain(map.getTerrain(Terrain.HEDGE), map.getTerrain(Terrain.BOCAGE));
            }
            else if (s.equals("PlowedFields")) {

                map.changeAllTerrain(map.getTerrain(Terrain.GRAIN), map.getTerrain(Terrain.PLOWED_FIELD));
            }
            else if (s.equals("RiverToValley")) {

            }
            else if (s.equals("PondToValley")) {

            }
            else if (s.equals("IrrigatedPaddies")) {

            }
            else if (s.equals("InSeasonPaddies")) {

            }
            else if (s.equals("HedgeToCactus")) {

            }
            else if (s.equals("WallToCactus")) {

            }
            else if (s.equals("OrchardsToOliveGroves")) {

            }
            else if (s.equals("OrchardsToShellholes")) {

                map.changeAllTerrain(map.getTerrain(Terrain.ORCHARD), map.getTerrain(Terrain.SHELL_HOLES));
            }
            else if (s.equals("OrchardsToCrags")) {

                map.changeAllTerrain(map.getTerrain(Terrain.ORCHARD), map.getTerrain(Terrain.CRAGS));
            }
            else if (s.equals("CragsToShellholes")) {

                map.changeAllTerrain(map.getTerrain(Terrain.CRAGS), map.getTerrain(Terrain.SHELL_HOLES));
            }
            else if (s.equals("CragsToOrchards") || s.equals("CragsToPalmTrees")) {

                map.changeAllTerrain(map.getTerrain(Terrain.CRAGS), map.getTerrain(Terrain.ORCHARD));
            }
            else if (s.equals("DTOtoETO")) {

            }
            else if (s.equals("Winter")) {

            }
            else if (s.equals("Mud")) {

            }
            else if (s.equals("ETOtoDTO")) {

            }
            else if (s.equals("WoodsToVineyard")) {

            }
            else if (s.equals("BrushToVineyard")) {

            }
            else if (s.equals("GrainToVineyard")) {

            }
            else if (s.equals("MarshToVineyard")) {

            }
            else if (s.equals("Level_1ToVineyard")) {

            }
            else if (s.equals("Level1ToVineyard")) {

            }
            else if (s.equals("Level2ToVineyard")) {

            }
            else if (s.equals("Level3ToVineyard")) {

            }
            else if (s.equals("Level4ToVineyard")) {

            }
            else if (s.equals("WoodsToBrush")) {

                map.changeAllTerrain(map.getTerrain(Terrain.WOODS), map.getTerrain(Terrain.BRUSH));
            }
            else if (s.equals("GrainToBrush")) {

                map.changeAllTerrain(map.getTerrain(Terrain.GRAIN), map.getTerrain(Terrain.BRUSH));
            }
            else if (s.equals("MarshToBrush")) {

                map.changeAllTerrain(map.getTerrain(Terrain.MARSH), map.getTerrain(Terrain.BRUSH));
            }
            else if (s.equals("Level_1ToBrush")) {

                changeHillToTerrain(map, -1, map.getTerrain(Terrain.BRUSH));
            }
            else if (s.equals("Level1ToBrush")) {

                changeHillToTerrain(map, 1, map.getTerrain(Terrain.BRUSH));
            }
            else if (s.equals("Level2ToBrush")) {

                changeHillToTerrain(map, 2, map.getTerrain(Terrain.BRUSH));
            }
            else if (s.equals("Level3ToBrush")) {

                changeHillToTerrain(map, 3, map.getTerrain(Terrain.BRUSH));
            }
            else if (s.equals("Level4ToBrush")) {

                changeHillToTerrain(map, 4, map.getTerrain(Terrain.BRUSH));
            }
            else if (s.equals("BrushToWoods")) {

                map.changeAllTerrain(map.getTerrain(Terrain.BRUSH), map.getTerrain(Terrain.WOODS));
            }
            else if (s.equals("GrainToWoods")) {

                map.changeAllTerrain(map.getTerrain(Terrain.GRAIN), map.getTerrain(Terrain.WOODS));
            }
            else if (s.equals("MarshToWoods")) {

                map.changeAllTerrain(map.getTerrain(Terrain.MARSH), map.getTerrain(Terrain.WOODS));
            }
            else if (s.equals("Level_1ToWoods")) {

                changeHillToTerrain(map, -1, map.getTerrain(Terrain.WOODS));
            }
            else if (s.equals("Level1ToWoods")) {

                changeHillToTerrain(map, 1, map.getTerrain(Terrain.WOODS));
            }
            else if (s.equals("Level2ToWoods")) {

                changeHillToTerrain(map, 2, map.getTerrain(Terrain.WOODS));
            }
            else if (s.equals("Level3ToWoods")) {

                changeHillToTerrain(map, 3, map.getTerrain(Terrain.WOODS));
            }
            else if (s.equals("Level4ToWoods")) {

                changeHillToTerrain(map, 4, map.getTerrain(Terrain.WOODS));
            }
            else if (s.equals("WoodsToMarsh")) {

                map.changeAllTerrain(map.getTerrain(Terrain.WOODS), map.getTerrain(Terrain.MARSH));
            }
            else if (s.equals("BrushToMarsh")) {

                map.changeAllTerrain(map.getTerrain(Terrain.BRUSH), map.getTerrain(Terrain.MARSH));
            }
            else if (s.equals("GrainToMarsh")) {

                map.changeAllTerrain(map.getTerrain(Terrain.GRAIN), map.getTerrain(Terrain.MARSH));
            }
            else if (s.equals("Level_1ToMarsh")) {

                changeHillToTerrain(map, -1, map.getTerrain(Terrain.MARSH));
            }
            else if (s.equals("Level1ToMarsh")) {

                changeHillToTerrain(map, 1, map.getTerrain(Terrain.MARSH));
            }
            else if (s.equals("Level2ToMarsh")) {

                changeHillToTerrain(map, 2, map.getTerrain(Terrain.MARSH));
            }
            else if (s.equals("Level3ToMarsh")) {

                changeHillToTerrain(map, 3, map.getTerrain(Terrain.MARSH));
            }
            else if (s.equals("Level4ToMarsh")) {

                changeHillToTerrain(map, 4, map.getTerrain(Terrain.MARSH));
            }
            else if (s.equals("WoodsToGrain")) {

                map.changeAllTerrain(map.getTerrain(Terrain.WOODS), map.getTerrain(Terrain.GRAIN));
            }
            else if (s.equals("BrushToGrain")) {

                map.changeAllTerrain(map.getTerrain(Terrain.BRUSH), map.getTerrain(Terrain.GRAIN));
            }
            else if (s.equals("MarshToGrain")) {

                map.changeAllTerrain(map.getTerrain(Terrain.MARSH), map.getTerrain(Terrain.GRAIN));
            }
            else if (s.equals("Level_1ToGrain")) {

                changeHillToTerrain(map, -1, map.getTerrain(Terrain.GRAIN));
            }
            else if (s.equals("Level1ToGrain")) {

                changeHillToTerrain(map, 1, map.getTerrain(Terrain.GRAIN));
            }
            else if (s.equals("Level2ToGrain")) {

                changeHillToTerrain(map, 2, map.getTerrain(Terrain.GRAIN));
            }
            else if (s.equals("Level3ToGrain")) {

                changeHillToTerrain(map, 3, map.getTerrain(Terrain.GRAIN));
            }
            else if (s.equals("Level4ToGrain")) {

                changeHillToTerrain(map, 4, map.getTerrain(Terrain.GRAIN));
            }
            else if (s.equals("NoCliffs")) {

                map.changeAllTerrain(map.getTerrain(Terrain.CLIFF), map.getTerrain(Terrain.OPEN_GROUND));
            }
            else if (s.equals("Level1ToBrushLevel2ToBrush")) {

                changeHillToTerrain(map, 1, map.getTerrain(Terrain.BRUSH));
                changeHillToTerrain(map, 2, map.getTerrain(Terrain.BRUSH));
            }

            // PTO SSR changes
            else if (s.equals("RoadsToPaths") || s.equals("NoRoads") || s.equals("NoWoodsRoads")) {

                Hex h = null;
                // convert forest-road hexes
                for (int col = 0; col < map.getWidth(); col++) {
                    for (int row = 0; row < map.getHeight() + (col % 2); row++) {

                        h = map.getHex(col, row);
                        h.getCenterLocation().getTerrain();

                        boolean roadHexside = false;
                        boolean woodsHex = false;

                        for (int x = 0; x < 6; x++) {

                            if (h.getHexsideLocation(x).getTerrain().isRoadTerrain()) {
                                roadHexside = true;
                            }
                            if (h.getHexsideLocation(x).getTerrain().isWoodsTerrain()) {
                                woodsHex = true;
                            }

                        }

                        if (roadHexside && woodsHex) {

                            // first we have to map the road to ocean if near woods...
                            int x = h.getHexBorder().getBounds().x;
                            int y = h.getHexBorder().getBounds().y;
                            for (int i = x; i < x + h.getHexBorder().getBounds().width; i++) {
                                for (int j = y; j < y + h.getHexBorder().getBounds().height; j++) {

                                    if (map.getGridTerrain(i, j).isRoadTerrain() && isNearWoods(i, j, map)) {

                                        map.setGridTerrain(new Rectangle(i, j, 1, 1), map.getTerrain(Terrain.OCEAN));
                                    }
                                }
                            }

                            // then change the water to woods
                            map.changeAllTerrain(map.getTerrain(Terrain.OCEAN), map.getTerrain(Terrain.WOODS), h.getHexBorder());


                            // reset the hex terrain
                            map.setHexTerrain(h.getHexBorder(), map.getTerrain(Terrain.WOODS));
                        }
                    }
                }
            }
            else if (s.equals("Bamboo")) {

                Hex h, h2 = null;
                Terrain t;
                boolean woodsAdjacent = false;

                // set all brush to bamboo
                for (int col = 0; col < map.getWidth(); col++) {
                    for (int row = 0; row < map.getHeight() + (col % 2); row++) {

                        h = map.getHex(col, row);
                        t = h.getCenterLocation().getTerrain();

                        if (t.getType() == Terrain.BRUSH) {

                            map.setGridTerrain(h.getHexBorder(), map.getTerrain(Terrain.BAMBOO));
                            map.setHexTerrain(h.getHexBorder(), map.getTerrain(Terrain.BAMBOO));
                        }

                        // assume we need to also change marsh to swamp here
                        else if (t.getType() == Terrain.MARSH) {

                            // are we adjacent to a woods hex?
                            for (int x = 0; x < 6; x++) {

                                h2 = map.getAdjacentHex(h, x);
                                if (h2 != null && (h2.getCenterLocation().getTerrain().getType() == Terrain.WOODS ||
                                        h2.getCenterLocation().getTerrain().getType() == Terrain.LIGHT_JUNGLE ||
                                        h2.getCenterLocation().getTerrain().getType() == Terrain.DENSE_JUNGLE)) {

                                    woodsAdjacent = true;
                                }
                            }

                            // change marsh to swamp
                            if (woodsAdjacent) {

                                map.changeAllTerrain(map.getTerrain(Terrain.MARSH), map.getTerrain(Terrain.SWAMP), h.getHexBorder());
                            }
                        }
                    }
                }

                // pick up any stray brush
                map.changeAllTerrain(map.getTerrain(Terrain.BRUSH), map.getTerrain(Terrain.BAMBOO));

                // assume we need to also change to woods to jungle here
                map.changeAllTerrain(map.getTerrain(Terrain.WOODS), map.getTerrain(Terrain.LIGHT_JUNGLE));

                map.changeAllTerrain(map.getTerrain(Terrain.WOODS), map.getTerrain(Terrain.LIGHT_JUNGLE));


            }
            else if (s.equals("PalmTrees")) {

            }
            else if (s.equals("DenseJungle")) {

                Hex h = null;
                Terrain t;

                // set all woods to dense jungle
                for (int col = 0; col < map.getWidth(); col++) {
                    for (int row = 0; row < map.getHeight() + (col % 2); row++) {

                        h = map.getHex(col, row);
                        t = h.getCenterLocation().getTerrain();

                        if (t.getType() == Terrain.WOODS || t.getType() == Terrain.LIGHT_JUNGLE) {

                            map.setGridTerrain(h.getHexBorder(), map.getTerrain(Terrain.DENSE_JUNGLE));
                            map.setHexTerrain(h.getHexBorder(), map.getTerrain(Terrain.DENSE_JUNGLE));
                        }
                    }
                }
            }
            else if (s.equals("NoBridge")) {

                // remove all bridges
                for (int col = 0; col < map.getWidth(); col++) {
                    for (int row = 0; row < map.getHeight() + (col % 2); row++) {

                        map.getHex(col, row).removeBridge();
                    }
                }
            }
            else if (s.equals("BridgeToFord")) {

                // remove all bridges
                for (int col = 0; col < map.getWidth(); col++) {
                    for (int row = 0; row < map.getHeight() + (col % 2); row++) {

                        map.getHex(col, row).removeBridge();
                    }
                }
            }
            else if (s.equals("")) {

            }
        }

//	map.writeMap("C:\\CASL\\Maps\\a.map");
    }

    private void changeHillToTerrain(GameMap map, int level, Terrain t) {

        for (int x = 0; x < map.getImageWidth(); x++) {
            for (int y = 0; y < map.getImageHeight(); y++) {

                if (map.getGridGroundLevel(x, y) == level) {

                    // set the ground level to zero
                    map.setGridGroundLevel(new Rectangle(x, y, 0, 0), null, 0);
                    map.gridToHex(x, y).setBaseHeight(0);

                    // change open ground to the terrain
                    if (map.getGridTerrain(x, y).getType() == Terrain.OPEN_GROUND) {

                        Rectangle r = new Rectangle(x, y, 1, 1);
                        map.setGridTerrain(r, t);
                        map.setHexTerrain(r, t);
                    }
                }
            }
        }

    }

    private boolean isNearWoods(int x, int y, GameMap map) {

        int max = 8;

        for (int i = x - max; i <= x + max; i++) {
            for (int j = y - max; j <= y + max; j++) {

                if (map.onMap(i, j) &&
                        map.getGridTerrain(i, j).isWoodsTerrain() &&
                        Point.distance((double) x, (double) y, (double) i, (double) j) <= max) {

                    return true;
                }
            }
        }

        return false;
    }

  private void freeResources() {
    // release all resource
    CASLMap = null;
    result = null;
    source = null;
    target = null;
    scenario = null;
    System.gc();
  }

  private Rectangle drawString(Graphics g, int x, int y, String s) {
    int border = 1;
    // paint the background
    g.setColor(Color.black);
    Rectangle region = new Rectangle(
        x - border,
        y - border - g.getFontMetrics().getHeight() + g.getFontMetrics().getDescent(),
        g.getFontMetrics().stringWidth(s) + border * 2,
        g.getFontMetrics().getHeight() + border * 2);
    g.fillRect(region.x, region.y, region.width, region.height);

    // draw the string
    g.setColor(Color.white);
    g.drawString(s, x, y);
    return region;
  }

  private void doLOS() {
	if(source == null)
	{
		System.err.println("LOS failed: no source hex");
		return;
	}
    // do the LOS
    CASLMap.LOS(source, useAuxSourceLOSPoint, target, useAuxTargetLOSPoint, result, scenario);
    // set the result string
    resultsString =
        "Range: " + result.getRange();
    lastRange = String.valueOf(result.getRange());
    if (isVerbose()) {
      if (result.isBlocked()) {
        resultsString += "  Blocked in " + CASLMap.gridToHex(result.getBlockedAtPoint().x, result.getBlockedAtPoint().y).getName() +
            " ( " + result.getReason() + ")";
      }
      else {
        resultsString += (result.getHindrance() > 0 ? ("  Hindrances: " + result.getHindrance()) : "");
      }
    }
  }
}

