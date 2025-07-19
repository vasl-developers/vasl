/*
 * $Id: ASLMap.java 8530 2012-12-26 04:37:04Z uckelman $
 *
 * Copyright (c) 2013 by Brent Easton
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

package VASL.build.module;

import VASL.LOS.LOSDataEditor;
import VASL.LOS.Map.Hex;
import VASL.LOS.Map.Location;
import VASL.LOS.Map.Terrain;
import VASL.LOS.counters.CounterMetadataFile;
import VASL.build.module.map.ASLPieceMover;
import VASL.build.module.map.ASLStackMetrics;
import VASL.build.module.map.boardArchive.BoardMetadata;
import VASL.build.module.map.boardArchive.SharedBoardMetadata;
import VASL.build.module.map.boardPicker.ASLBoard;
import VASL.build.module.map.boardPicker.BoardException;
import VASL.build.module.map.boardPicker.Overlay;
import VASL.build.module.map.boardPicker.VASLBoard;
import VASSAL.build.Buildable;
import VASSAL.build.GameModule;
import VASSAL.build.module.GameComponent;
import VASSAL.build.module.Map;
import VASSAL.build.module.PieceWindow;
import VASSAL.build.module.map.PieceMover;
import VASSAL.build.module.map.boardPicker.Board;
import VASSAL.build.widget.ListWidget;
import VASSAL.build.widget.PanelWidget;
import VASSAL.build.widget.PieceSlot;
import VASSAL.configure.*;
import VASSAL.counters.GamePiece;
import VASSAL.counters.Properties;
import VASSAL.counters.Stack;
import VASSAL.tools.DataArchive;
import VASSAL.tools.ErrorDialog;
import VASSAL.tools.imageop.Op;
import org.jdom2.JDOMException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Element;

import javax.swing.*;
import java.awt.*;
import java.awt.dnd.*;
import java.awt.event.*;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.util.*;

import static VASSAL.build.GameModule.getGameModule;
import static java.lang.Math.cos;

public class ASLMap extends Map {
    private JPopupMenu mainpopup = null;
    private VASL.LOS.Map.Map VASLMap;
    private static final String sharedBoardMetadataFileName = "boardData/SharedBoardMetadata.xml"; // name of the shared board metadata file
    private static SharedBoardMetadata sharedBoardMetadata = null;
    private boolean legacyMode;                     // true if unable to create a VASL map or LOS data is missing
    // counter metadata
    private static CounterMetadataFile counterMetadata = null;
    // used to log errors in the VASSAL error log
    private static final Logger logger = LoggerFactory.getLogger(ASLMap.class);
    private ShowMapLevel showmaplevel = ShowMapLevel.ShowAll;
    // background color preference
    private static final String preferenceTabName = "VASL";

    //JY - independent zoom factors for the boards and pieces
    //Intended to be activated by a separate extension, but needs to be coded in the main module
    protected ASLStackMetrics ASLmetrics;
    private static double bZoom; //Additional zoom factor for the boards only
    private static double oldbZoom;
    private ArrayList<String> pieceslotgpidlist = new ArrayList<>(); //List of pieces that scale with the board (mostly overlays)
    public static final String SCALEWITHBOARDZOOM = "ScaleWithBoardZoom"; //Property name for any counters that should scale with the board zoom level
    public static final String SCALEWITHBOARDMAG = "ScaleWithBoardMag"; //Property name for any counters that should also scale with the board magnification (not the same as the zoom)
    public ArrayList<String> dxAvailBoards = new ArrayList<>(); //List of all available deluxe boards


  public ASLMap() {
    super();
    setbZoom(1.0D);

    try {
        readMetadata();
    }
    catch (JDOMException e) {
        // give up if there's any problem reading the shared metadata file
        ErrorDialog.bug(e);
    }
    mainpopup = new JPopupMenu();
    // creation of the toolbar button that opens the popup menu
    JButton lMenu = new JButton();

    try     {
        lMenu.setIcon(new ImageIcon(Op.load("QC/menu.png").getImage(null)));
    }
    catch (Exception ex) 
    {
        ex.printStackTrace();
    }
    
    lMenu.setMargin(new Insets(0, 0, 0, 0));
    lMenu.setAlignmentY(0.0F);
    lMenu.addActionListener(new ActionListener() {
        public void actionPerformed(ActionEvent evt)  {
            if (evt.getSource() instanceof JButton) {
                mainpopup.show((JButton) evt.getSource(), 0, 0);
            }
        }
    });

    // add the first element to the popupp menu
    JMenuItem selectitem = new JMenuItem("Select");
    selectitem.setBackground(new Color(255,255,255));
    mainpopup.add(selectitem);
    mainpopup.addSeparator();
    // add the menu button to the toolbar
    getToolBar().add(lMenu);
    getToolBar().addSeparator();

    // background color preference
    final ColorConfigurer backgroundcolor = new ColorConfigurer("backcolor", "Set Color of space around Map (requires VASL restart)", Color.white);
    getGameModule().getPrefs().addOption(preferenceTabName, backgroundcolor);

}
    @Override
    public void addTo(Buildable b) {
        super.addTo(b);

        // This code is added to fix an issue with Linux users when using seperate windows, the drag
        // gesture is not reinitializing the counter image when entering the main map from the VASL
        // counters window. The issue is that the main map uses the ASLPiecemover and the counter window
        // uses the older Piecemover.
        if (System.getProperty("os.name").contains("nux")){
            DropTargetListener aslPieceListener = ASLPieceMover.DragHandler.makeDropTarget(this.theMap, 2, this);
            DropTargetListener pieceListener = PieceMover.DragHandler.makeDropTarget(this.theMap, 2, this);

            // Define the combined listener
            DropTargetListener combinedListener = new DropTargetListener() {
                @Override
                public void dragEnter(DropTargetDragEvent dtde) {
                    pieceListener.dragEnter(dtde);
                }

                @Override
                public void dragOver(DropTargetDragEvent dtde) {
                    aslPieceListener.dragOver(dtde);
                }

                @Override
                public void dropActionChanged(DropTargetDragEvent dtde) {
                    aslPieceListener.dropActionChanged(dtde);
                }

                @Override
                public void dragExit(DropTargetEvent dte) {
                    aslPieceListener.dragExit(dte);
                }

                @Override
                public void drop(DropTargetDropEvent dtde) {
                    aslPieceListener.drop(dtde);
                }
            };

            // Set the DropTarget with the combined listener
            this.theMap.setDropTarget(new DropTarget(this.theMap, DnDConstants.ACTION_MOVE, combinedListener, true));
        } else {
            this.theMap.setDropTarget(ASLPieceMover.DragHandler.makeDropTarget(this.theMap, 2, this));
        }
    }

  /*
   *  Work-around for VASL board being 1 pixel too large causing double stacks to form along board edges.
   *  Any snap to a board top or left edge half hex, bump it 1 pixel up or left on to the next board.
   *  
   *  */
  public Point snapTo(Point p) {

    final Point pSnapTo = super.snapTo(p);
    Point pShiftedXY, pShiftedY, pShiftedX;
    
    pShiftedXY = new Point (pSnapTo);

    pShiftedXY.x -= 3;
    pShiftedXY.y -= 3; // move the snap point 3 pixel up and left: if the map changes, the snapTo could return a different point, otherwise nothing changes
    pShiftedXY = super.snapTo(pShiftedXY);
    
    if (findBoard(pShiftedXY) != null) { //  Return to the snapTo point if moved off the top border or the left border
        return pShiftedXY;
    }
    pShiftedY = new Point (pSnapTo);
    
    pShiftedY.y -= 3; // move the snap point 3 pixel up: if the map changes, the snapTo could return a different point, otherwise nothing changes
    pShiftedY = super.snapTo(pShiftedY);
    
    if (findBoard(pShiftedY) == null) { // moved off the top border, return to the snapTo point
        pShiftedY.y = pSnapTo.y;
    }
    pShiftedX = new Point (pShiftedY);
    
    pShiftedX.x -= 3; // move the snap point 3 pixel left: if the map changes, the snapTo could return a different point, otherwise nothing changes
    pShiftedX = super.snapTo(pShiftedX);
    
    if (findBoard(pShiftedX) == null) { // moved off the left border
        return pShiftedY;
    }
    return pShiftedX;
    
  }
  // return the popup menu
  public JPopupMenu getPopupMenu() {
      return mainpopup;
  }

    @Override
    public synchronized void setBoards(Collection<Board> c) {
        final GameModule mod = getGameModule();
        for (Board boardc: c) {
            VASLBoard testboardexists = (VASLBoard) boardc;
            if (testboardexists.getVASLBoardArchive() == null) {
                mod.getChatter().send("Board missing. Auto-synching of boards requires board directory in board picker matches the board directory set in preferences. Close this game and start new game");
                return;
            }
        }
        super.setBoards(c);
        String info = "Using board(s): ";
        for (Board board : boards) {
            ASLBoard b = (ASLBoard) board;
            info += b.getName() + "(v" + b.getVersion() + ") ";
        }
        mod.warn(info);
        buildVASLMap();

        //JY
        findOverlays();
        createDeluxeBoardsList();
        //JY

        // Add OBObserver location
        if (VASLMap!=null){
            for (GameComponent gc: mod.getGameState().getGameComponents()) {
                //String classname = gc.getClass().getName();
                if (gc.getClass().getName() =="VASL.build.module.OBA" ) {
                    OBA oba = (OBA) gc;
                    oba.checkforOBO();
                }
            }
        }
    }

    /**
     * read the shared board metadata
     */
    private void readMetadata() throws JDOMException {

        final DataArchive archive = getGameModule().getDataArchive();
        // shared board metadata
        try (InputStream inputStream =  archive.getInputStream(sharedBoardMetadataFileName)) {
            sharedBoardMetadata = new SharedBoardMetadata();
            sharedBoardMetadata.parseSharedBoardMetadataFile(inputStream);

        // give up on any errors
        }
        catch (IOException e) {
            sharedBoardMetadata = null;
            throw new JDOMException("Cannot read the shared metadata file", e);
        }
        catch (JDOMException e) {
            sharedBoardMetadata = null;
            throw new JDOMException("Cannot read the shared metadata file", e);
        }
        catch (NullPointerException e) {
            sharedBoardMetadata = null;
            throw new JDOMException("Cannot read the shared metadata file", e);
        }
    }

    /**
     * Builds the VASL map
     * a VASL map is required by the VASL LOS engine; if map does not support VASL LOS checking then no need for VASL Map
     * the VASL map is first created as an empty shell of the correct size in pixels and with a hex grid
     * that has the required number of hexes and also the proper hex configuration along the left and right map edges
     * this grid will always have hex A1 as the top left hex with whatever hex configuration (full/half height and width)
     * is required by cropping
     * hexgrid configuration changes required by cropping are NOT handled yet
     * after the empty VASL map is created addBoardsToMap is called to populate the VASL map with board data and to handle flipping
     */
    protected void buildVASLMap() {
        // setup for map window
        final GameModule mod = getGameModule();
        // set background color from preference
        super.bgColor = (Color) mod.getPrefs().getValue("backcolor");
        // set Player Window Always on Top from preference
        final Boolean alwaysontop = Boolean.TRUE.equals(mod.getPrefs().getValue("PWAlwaysOnTop"));
        mod.getPlayerWindow().setAlwaysOnTop(alwaysontop);
        repaint();

        // set variables
        legacyMode = false;  // if legacyMode then no VASL LOS checking
        boolean nullBoards = false; // are null boards being used?
        LinkedList<VASLBoard> vaslboards = new LinkedList<VASLBoard>(); // list of boards
        String gridconfigWidth = "";
        String fliphexconfig="";
        boolean iscropping = false;
        double hexheight = 0.0; //hex height in pixels
        double hexwidth = 0.0;  //hex width in pixels
        int indexOfCol1 = 0;  //numerical index of map columns (A, B, C, . . .) zero-based
        int indexOfCol2 = 0;
        // populate board list
        try {
            // see if there are any legacy boards in the board set
            // and determine the size of the map in pixels
            final Rectangle mapBoundary = new Rectangle(0, 0);
            for (Board b : boards) {
                final VASLBoard board = (VASLBoard) b;
                // ignore null boards
                if (!"NUL".equals(b.getName()) && !"NULV".equals(b.getName())) {
                    if (board.isLegacyBoard()) {
                        throw new Exception("VASL LOS disabled - Board " + board.getName() + " does not support LOS checking. VASSAL los active - safe to continue play");
                    }
                    mapBoundary.add(b.bounds());
                    vaslboards.add(board);
                    // make sure the hex geometry of all boards is the same
                    if (hexheight != 0.0 && Math.round(board.getHexHeight()) != Math.round(hexheight) || hexwidth != 0.0 && Math.round(board.getHexWidth()) != Math.round(hexwidth)) {
                        throw new Exception("VASL LOS disabled: Map configuration contains multiple hex sizes. VASSAL los active - safe to continue play");
                    }
                    hexheight = board.getHexHeight();
                    hexwidth = board.getHexWidth();
                } else {
                    nullBoards = true;
                }
            }
            // handle non-standard boards separately.
            // there are only 3 but they complexify the crop/flip options enormously so pull out
            for (VASLBoard board : vaslboards) {
                if (board.getName().equals("RBv3") || board.getName().equals("RO") || board.getName().equals("DaE")) {
                    buildVASLMapforNonStandardBoards(vaslboards, mod);
                    return;
                }
            }
            // all boards past this point have either standard geo 33 x 10 or a/b 17 x 20 configurations
            // with half-hexes on left and right sides and 10/11 or 20/21 hex row configurations
            // no other configurations will work and should be added to the non standard list above
            // the code below will support all possible width and height crops with or without flipping
            // see vasl repo on github Wiki tab for list of all crop and flip configurations

            // this is a hack to fix problem with board geometry. Standard geo hexes cannot have a width greater than 56.25 or they will exceed the board size of 1800 pixels
            // even if they are actually 56.3125 in size
            // ToDo need to edit BoardMetaData.xml to change hexHeight to 56.25 - this is a hack for incorrect BoardMetaData - need to correct Board files
            if (hexwidth == 56.3125) {hexwidth = 56.25;}
            // remove the edge buffer from the map boundary size
            mapBoundary.width -= edgeBuffer.width;
            mapBoundary.height -= edgeBuffer.height;

            // create the VASL map object with the correct size and underlying hex grid configuration
            // variables to pass cropping values
            gridconfigWidth = "HalfHexWidth"; //A1 default value before cropping/flipping adjustment
            String toplefthexheight = "LeftHexFullHeight"; // holds height of top left hex after crop; start with default value
            String toprighthexheight = "RightHexFullHeight"; // holds height of top right hex after crop; start with default value
            String toplefthexwidth = "LeftHexHalfWidth"; // holds with width of the top left hex after crop; start with default value
            VASLBoard b = vaslboards.get(0); // this will always be the top left board and drives the configuration of the left side of the map
            indexOfCol2 = b.getWidth() - 1 ; //default value
            if (b.isCropped()) {
                //set Width value
                iscropping = true;
                if (b.nearestFullRow) {  //value set in ASLBoard.getState() or ASLBoard.crop()
                    // if both left and right edges of this board are cropped, cropgridconfig will equal "FullHexWidth"
                    gridconfigWidth = "FullHexWidth"; // set as default when nearestFullRow is selected
                    toplefthexwidth = "FullHexWidth";
                    // left edge is not cropped to full hex; half width value whether cropped or not
                    if (b.getCropBounds().getX() == 0) {
                        gridconfigWidth = "FullHexWidthLeftHalf";
                        toplefthexwidth = "HalfHexWidth";
                    }
                    // right edge is not cropped to full hex; half width value whether cropped or not
                    if (b.getCropBounds().getMaxX() == b.getUncroppedSize().getWidth()) {
                        gridconfigWidth = "FullHexWidthRightHalf";
                    }
                } // no need to handle if nearestFullRow is false - simply use default value as all geo and a/b boards are initially halfwidth left and right
                // set hex height value for top row of crop
                //retieve crop values
                boolean isbboard = b.getA1CenterX() == -901 ? true : false;  // "b" board test
                String column_names = "abcdefghijklmnopqrstuvwxyz"; //""ABCDEFGHIJKLMNOPQRSTUVWXYZ";
                indexOfCol1 = b.getRow1().length() != 0 ? column_names.indexOf(b.getRow1().charAt(0)) : 0;
                indexOfCol2 = b.getRow2().length() != 0 ? (b.getRow2().length() == 2 ? column_names.indexOf(b.getRow2().charAt(0)) + 26 : column_names.indexOf(b.getRow2().charAt(0))) : b.getVASLBoardArchive().getBoardWidth() - 1;
                indexOfCol1 = isbboard ? indexOfCol1 -16 : indexOfCol1;  //adjust col value for "b" boards 1b - 22b to ensure correct hex name
                indexOfCol2 = isbboard ? indexOfCol2 -16 : indexOfCol2;
                int valueOfRow1 = b.getCoord1().equals("") ? 0 : Integer.parseInt(b.getCoord1());
                int valueOfRow2 = b.getCoord2().equals("") ? b.getVASLBoardArchive().getBoardHeight() : Integer.parseInt(b.getCoord2());
                b.setstartcropcol(b.getRow1(), indexOfCol1);
                b.setendcropcol(b.getRow2(), indexOfCol2);
                b.setstartcroprow(b.getCoord1(), valueOfRow1);
                b.setendcroprow(b.getCoord2(), valueOfRow2);
                boolean Col1isOdd = indexOfCol1 % 2 == 0 ? false : true;
                boolean Col1isEven = !Col1isOdd;
                boolean Col2isOdd = indexOfCol2 % 2 == 0 ? false : true;
                boolean Col2isEven = !Col2isOdd;
                // use crop values to determine left and right hex height configuration
                // hexgrid contains zero-based arrays so first col, col[0] (ie A) is always even
                // cropping height in hexes (via Coord) seems to have no impact
                if (Col1isEven) {
                    toplefthexheight = "LeftHexFullHeight";
                } else if (Col1isOdd) {
                    toplefthexheight = "LeftHexHalfHeight";
                }
                if (Col2isEven) {
                    toprighthexheight = "RightHexFullHeight";
                } else if (Col2isOdd) {
                    toprighthexheight = "RightHexHalfHeight";
                }
            }
            // flip values
            // these values are set here and passed to next method (addBoard . . . ) which uses them to flip
            if (b.isReversed()) {
                // hex width
                if (b.nearestFullRow) {
                    fliphexconfig = "FullHexWidth";
                }
                if (gridconfigWidth.equals("FullHexWidthRightHalf")) {
                    fliphexconfig = "FullHexWidthLeftHalf";
                } else if (gridconfigWidth.equals("FullHexWidthLeftHalf")) {
                    fliphexconfig = "FullHexWidthRightHalf";
                }
                // hex height
                fliphexconfig += toprighthexheight.equals("RightHexFullHeight") ? "LeftHexFullHeight" : "LeftHexHalfHeight";
            }

            // set crop variables
            double passA1centerx = setA1CenterX(toplefthexwidth);
            double passA1centery = toplefthexheight == "LeftHexFullHeight" ? hexheight/2 : 0;
            int passwidthinhexes = indexOfCol2 - indexOfCol1 +1;
            int passheightinhexes = (int) Math.round(mapBoundary.height / b.getHexHeight());

            // handle creation of VASL map with multiple boards separtely
            if (boards.size() == 1) {
                // create empty map
                VASLMap = new VASL.LOS.Map.Map(vaslboards.get(0), passA1centerx, passA1centery, sharedBoardMetadata.getTerrainTypes(), passwidthinhexes, passheightinhexes);
                // add board to map
                addOneBoardToMap(vaslboards.get(0), mod, passA1centerx, passA1centery, fliphexconfig);
            }
            else {
                // this should work with the same logic as the methods for single board map; just need to handle multiple boards
                VASLMap = createmultiboardmap(hexwidth, hexheight, passwidthinhexes, passheightinhexes,
                        passA1centerx, passA1centery, mapBoundary.width, mapBoundary.height,
                        sharedBoardMetadata.getTerrainTypes(), "", "", iscropping);
                addBoardsToMap(vaslboards, mod, passA1centerx, passA1centery, fliphexconfig);
            }
        // clean up and fall back to legacy mode if an unexpected exception is thrown
        }catch (BoardException e) {
            setLegacyMode();
            logError(e.toString());
            mod.getChatter().send(e.toString());
        } catch (Exception e) {
            setLegacyMode();
            vaslboards = null;
            logError("LOS disabled - unexpected error");
            logException(e);
            mod.getChatter().send("VASL LOS disabled due to unexpected board issue. Safe to continue play. Use VASSAL LOS string");
        }
    }
    /**
     * Populates the VASL map with terrain, elevation, and hex information from the boards used in the map
     * For each board used in the map, this method:
     */
     protected void addBoardsToMap(LinkedList<VASLBoard> vaslboards, GameModule mod, double passA1centerx, double passA1centery, String fliphexconfig) {
        // ToDo redo this method using addOneBoardToMap as guide
         // add the boards to the VASL map
        /*try {
            // load the LOS data
           *//* if(!legacyMode) {
                // read the LOS data and flip/crop the board if needed
                for (VASLBoard board : vaslboards) {
                    // variables to support cropping and flipping
                    boolean iscropping = false; double fullhexadj = 0; double gridadj = 0;
                    if (board.nearestFullRow) {
                        passcropgridconfig = "FullHex";
                        fullhexadj = board.getHexWidth() / 2;
                        if (board.getCropBounds().getX() == 0) {
                            passcropgridconfig = "FullHexLeftHalf";
                        }
                        if (board.getCropBounds().getMaxX() == board.getUncroppedSize().getWidth()) {
                            passcropgridconfig = "FullHexRightHalf";
                        }
                        if (passboardgridconfig.contains("EqualRowCount")) {
                            passcropgridconfig = passcropgridconfig + "EqualRowCount";
                        }
                    }
                    if(board.isCropped()) {
                        iscropping = true;
                        if (!passcropgridconfig.contains("LeftHalf")) {
                            if (!(board.getA1CenterX() == -901)) {
                                //ToDo here is where gridadj becomes non-zero
                                gridadj = board.getA1CenterX() - fullhexadj;
                                if (board.getCropBounds().width == -1) {gridadj = 0;}
                            }
                            else {
                                gridadj = - fullhexadj;
                            }
                        }
                    }
                    if (board.getA1CenterX() != 0 && board.getA1CenterX() != -999 && board.getA1CenterX() != -901) {
                        if (board.getCropBounds().getX() != 0) {
                    //        passcropgridconfig = passcropgridconfig + "Offset";  // only need to set this if cropping the left edge when board has offset (ie RB and RO)
                        }
                    }
                    //#2
                    //test change
                    //gridadj = 0;
                    //gridadj can always be passed as "0" because retrieving losdata for full map ToDo NO this is wrong see 20 lines above
                    VASL.LOS.Map.Map losdata = board.getLOSData(sharedBoardMetadata.getTerrainTypes(), false, 0); //passboardgridconfig, passcropgridconfig, false, 0);
                    // apply the SSR changes, crop and flip if needed
                    // ToDo need to test that this is working properly and whether gridadj should always be zero
                    board.applyColorSSRules(losdata, sharedBoardMetadata.getLOSSSRules(), gridadj);
                    if(board.isCropped()) {
                        //#3
                        //losdata = board.cropLOSData(losdata, passboardgridconfig, passcropgridconfig);
                    }
                    //add overlays to LOS
                    losdata = adjustLOSForOverlays(board, losdata);
                    // flip after overlay adjustment
                    if(board.isReversed()) {
                        //losdata.flip();
                    }
                    // add the board LOS data to the map
                    // .insertMap is designed to work with only geo board thus need to test for non-geo boards (in this situation geo boards inclues AP boards and deluxe boards)
                    if ((board.getWidth() == 33 && board.getHeight() == 10) || (board.getWidth() == 17 && board.getHeight() == 20) || (board.getWidth() == 15 && board.getHeight() ==5)) {
                        //line below is not a good fix; make sure it works in all situations or change
                        int cropadj = 1;  // ensures that cropping a board by row works properly DR (rows such as A7 have uneven total height which results in incorrect choice from gridToHex)
                        if (!VASLMap.insertMap(losdata, VASLMap.gridToHex(board.getBoardLocation().x, board.getBoardLocation().y + cropadj + (nullBoards ? 1 : 0)))) {
                            // didn't work, so assume an unsupported feature
                            throw new BoardException("VASL LOS Disabled: Unable to insert board " + board.getName() + " into the VASL map. Safe to continue play. VASSAL los active");
                        }
                    }
                    else {
                        // add board LOS data for non-standard size board
                        //line below is not a good fix; make sure it works in all situations or change
                        final int cropadj = 1;  // ensures that cropping a board by row number works properly DR (rows such as A7 have uneven total height which results in incorrect choice from gridToHex)
                        if (vaslboards.size() == 1) {
                            if(!VASLMap.insertOneMap(losdata)){
                                throw new BoardException("VASL LOS Disabled: Unable to insert board " + board.getName() + " into the VASL map. Safe to continue play. VASSAL los active");
                            }
                        }
                        else {
                            //HASL maps with LOS
                            if (!VASLMap.insertNonGeoMap(losdata, VASLMap.gridToHex(board.getBoardLocation().x, board.getBoardLocation().y + cropadj + (nullBoards ? 1 : 0)))) {
                                // didn't work, so assume an unsupported feature
                                throw new BoardException("VASL LOS Disabled: Unable to insert board " + board.getName() + " into the VASL map. Safe to continue play. VASSAL los active");
                            }
                        }
                    }
                }
                mod.warn("VASL LOS Enabled");
            }*//*
        }
        catch (BoardException e) {
            setLegacyMode();
            logError(e.toString());
            mod.getChatter().send("VASL LOS Disabled. Safe to continue to play: VASSAL los active");
        }
        catch (Exception e) {
            setLegacyMode();
            logError("LOS disabled - unexpected error");
            logException(e);
            mod.getChatter().send("VASL LOS disabled due to Board issue. Safe to continue to play. VASSAL los active");
        }
        finally {
            // free up memory
            vaslboards = null;
        }*/
    }
    protected void addOneBoardToMap(VASLBoard board, GameModule mod, double passA1centerx, double passA1centery, String fliphexconfig) {
        // add the board to the VASL map
        try {
            // load the LOS data
            if(!legacyMode) {
            // read the LOS data and flip/crop the board if needed
                // variables to support cropping and flipping
                //ToDo determine if these still needed; delete if not required
                double fullhexadj = 0; double gridadj = 0;
                // Add the LOS data to the map - cropped if necessary
                VASL.LOS.Map.Map newvaslmap = board.getVASLBoardArchive().addLOSDatatoVASLMap(sharedBoardMetadata.getTerrainTypes(), board, gridadj, VASLMap);
                // apply the SSR changes and flip if needed
                // ToDo need to test that this is working properly and whether gridadj should always be zero
                board.applyColorSSRules(newvaslmap, sharedBoardMetadata.getLOSSSRules(), gridadj);
                //add overlays to LOS
                newvaslmap = adjustLOSForOverlays(board, newvaslmap);
                // flip after overlay adjustment
                if(board.isReversed()) {
                    newvaslmap.flip(fliphexconfig);
                }
                mod.warn("VASL LOS Enabled");
                VASLMap = newvaslmap;
            }
        }
        catch (BoardException e) {
            setLegacyMode();
            logError(e.toString());
            mod.getChatter().send("VASL LOS Disabled. Safe to continue to play: VASSAL los active");
        }
        catch (Exception e) {
            setLegacyMode();
            logError("LOS disabled - unexpected error");
            logException(e);
            mod.getChatter().send("VASL LOS disabled due to Board issue. Safe to continue to play. VASSAL los active");
        }
        finally {
            // free up memory
            //vaslboards = null;
        }
    }
    /**
     * Use this method to initiate LOS for non-standard boards that support los checking (currently only RBv3, RO, and DaE)
    */
    private void buildVASLMapforNonStandardBoards(LinkedList<VASLBoard> vaslboards, GameModule mod){
        //ToDo recode this method; it is a paste of old code and will no longer work; see addOneBoardToMap
        String passcropgridconfig = "Normal"; //default value before cropping/flipping adjustment
        String passboardgridconfig = "Normal"; // default value of grid configuration of geo and a/b boards
        boolean iscropping = false;
        double hexheight = 0.0; //hex height in pixels
        double hexwidth = 0.0;  //hex width in pixels
        final Rectangle mapBoundary = new Rectangle(0, 0);
        // this is a hack to fix problem with board geometry. Standard geo hexes cannot have a width greater than 56.25 or they will exceed the board size of 1800 pixels
        // even if they are actually 56.3125 in size
        // ToDo need to edit BoardMetaData.xml to change hexHeight to 56.25 - this is a hack for incorrect BoardMetaData - need to correct Board files
        if (hexwidth == 56.3125) {hexwidth = 56.25;            }
        // remove the edge buffer from the map boundary size
        mapBoundary.width -= edgeBuffer.width;
        mapBoundary.height -= edgeBuffer.height;
        // create the VASL map object with the correct size and underlying hex grid configuration
        // variables to pass cropping values
        passcropgridconfig = "Normal"; //default value before cropping/flipping adjustment
        passboardgridconfig = "Normal"; // default value of grid configuration of geo and a/b boards
        int fullhexadj = 0;
        try {
        VASLBoard b = vaslboards.get(0); // this will always be the top left board and drives the configuration of the left side of the map
        if (b.getVASLBoardArchive().getHexGridConfig() != null) {  // many older geo boards do not include the hexgridconfig metadata in their boardMetadata.xml file
            passboardgridconfig = b.getVASLBoardArchive().getHexGridConfig();
        }
        if (b.isCropped()) {
            iscropping = true;
        }
        if (b.nearestFullRow) {  //value set in ASLBoard.getState() or ASLBoard.crop()
            // if both left and right edges of this board are cropped, passcropgridconfig will equal "FullHex"
            if (!(passcropgridconfig.contains("FullHex"))) {
                passcropgridconfig = "FullHex";
            }
            fullhexadj = -1;
            if (b.getCropBounds().getX() == 0) {
                passcropgridconfig = "FullHexLeftHalf";
                fullhexadj = 0;
            }
            if (b.getCropBounds().getMaxX() == b.getUncroppedSize().getWidth()) {
                passcropgridconfig = "FullHexRightHalf";
                fullhexadj = 0;
            }
        } else if(iscropping) {  // non-standard board such as DaE is cropped to middle of hex
            if (b.getCropBounds().getX() != 0 && b.getCropBounds().getMaxX() != b.getUncroppedSize().getWidth()) {  // cropped on both sides
                passcropgridconfig = "LeftHalfRightHalf";
            } else if (b.getCropBounds().getX() == 0 && (b.getCropBounds().getMaxX() == b.getUncroppedSize().getWidth()) || b.getCropBounds().getMaxX() == -1){  // cropped on neither side (rows cropped not columns)
                passcropgridconfig += "HeightCropOnly";
            } else {
                if (b.getCropBounds().getX() != 0) {  // left side cropped
                    passcropgridconfig = "LeftHalf";
                }
                if (b.getCropBounds().getMaxX() != b.getUncroppedSize().getWidth()) {  //right side cropped
                    passcropgridconfig = "RightHalf";
                }
            }
        }
        if(iscropping && passboardgridconfig.contains("EqualRowCount")){
            passcropgridconfig = passcropgridconfig + "EqualRowCount";
        }
        final double passA1centery = b.getA1CenterY();
        if (b.getA1CenterX() != 0 && b.getA1CenterX() != -999 && b.getA1CenterX() != -901) {
            if (b.getCropBounds().getX() != 0) {
                //passcropgridconfig = passcropgridconfig + "Offset";  // only need to set this if cropping the left edge when board has offset (ie RB and RO)
            }
        }
        int numofcolsadj = passboardgridconfig.contains("FullHex") ? 0 : 1;  // if board has one or more half-width hexes then need to add an extra col
        int passwidthinhexes = (int) Math.round(mapBoundary.width / b.getHexWidth() + numofcolsadj + fullhexadj);
        VASLMap = new VASL.LOS.Map.Map(hexwidth, hexheight, passwidthinhexes,
                (int) Math.round(mapBoundary.height / b.getHexHeight()), b.getA1CenterX(), passA1centery, mapBoundary.width, mapBoundary.height,
                sharedBoardMetadata.getTerrainTypes(), passboardgridconfig, passcropgridconfig, iscropping);
        }
    // clean up and fall back to legacy mode if an unexpected exception is thrown
        catch (Exception e) {
        setLegacyMode();
        logError(e.toString());
        mod.getChatter().send(e.toString());
    /*} catch (BoardException e) {
        setLegacyMode();
        vaslboards = null;
        logError("LOS disabled - unexpected error");
        logException(e);
        mod.getChatter().send("VASL LOS disabled due to unexpected board issue. Safe to continue play. Use VASSAL LOS string");*/
    }
    //addBoardsToMap(vaslboards, mod, nullBoards, passboardgridconfig, passcropgridconfig, iscropping);
    };

    private VASL.LOS.Map.Map createmultiboardmap(double hexWidth, double hexHeight, int width, int height, double A1CenterX, double A1CenterY, int imageWidth,
               int imageHeight, HashMap<String, Terrain> terrainNameMap, String passboardgridconfig, String passcropgridconfig, boolean isCropping){
        //this method not yet coded ToDo build this method
        VASL.LOS.Map.Map createmultimap = null;
        return createmultimap;
    }

    private double setA1CenterX(String topleftHexWidth){
        if (topleftHexWidth.equals("HalfHexWidth")) {
            return 0;
        }
        else if (topleftHexWidth.equals("FullHexWidth")) {
            return 28.125;
        }
        else {
            return 0;
        }
    };

    /**
     * A class that allows the LOSData, Graphic image and point information to be passed to various methods and classes
     * Note that all properties are public to eliminate getter/setter clutter
     */
    public class LOSonOverlays {
        public VASL.LOS.Map.Map newlosdata;
        public BufferedImage bi;
        public VASLBoard board;
        public Rectangle ovrrec;
        public int currentx;  //position on overlay
        public int currenty;  // position on overlay
        public int overpositionx; //position on mapboard
        public int overpositiony;  // position on mapboard

    }
    private VASL.LOS.Map.Map adjustLOSForOverlays(VASLBoard board, VASL.LOS.Map.Map losdata) {
        //ToDo check this still works with revised cropping and flipping
        final LOSonOverlays losonoverlays = new LOSonOverlays();
        losonoverlays.newlosdata = losdata;
        losonoverlays.board = board;
        final Enumeration overlays = board.getOverlays();
        while (overlays.hasMoreElements()) {
            Overlay o = (Overlay) overlays.nextElement();
            if(o.getName().equals("")){continue;} // prevents error when using underlays (which are added as overlays)
            if(o.getName().contains("BSO") && (!o.getName().contains("BSO_LFT3"))) {continue;} // prevents error when using BSO which are handled elsewhere
            if(o.getName().contains("NoCliffs")) {continue;} // cliff los adjustment handled in VASLBoard
            if(o.getName().contains("LightWoods")) {continue;} // Light Woods are handled by LOSSSRule terrain mapping. dont need to go through overlay method
            // BSO_LFT3 may be a special case; treat it as so for now; if find others then need to develop a proper solution
            losonoverlays.ovrrec = o.bounds();
            // get the image as a buffered image
            final Image i = o.getImage();
            losonoverlays.bi = new BufferedImage(i.getWidth(null), i.getHeight(null), BufferedImage.TYPE_INT_ARGB);
            final Graphics2D bgr = losonoverlays.bi.createGraphics();
            bgr.drawImage(i, 0, 0, null);
            bgr.dispose();
            // ToDo dessert overlays were test cases, they should be added to setOverlayTerrain()
            // ToDo can setDierLip() be model for Rice Paddies - banks?
            if (o.getFile().getName().equalsIgnoreCase("ovrH")) {
                setHillockTerrain(losonoverlays);
            }
            else if (o.getFile().getName().equalsIgnoreCase("ovrD")) {
                setDierTerrain(losonoverlays);
                setDierLip(losonoverlays);
            }
            else if (o.getFile().getName().equalsIgnoreCase("ovrSD")) {
                setSandDuneTerrain(losonoverlays);
            }
            else if (o.getFile().getName().equalsIgnoreCase("ovrW")) {
                setWadiTerrain(losonoverlays);
            }
            else {
                String terraintype = getOverlayTerrainType(o);
                terraintype = resetfortransform(terraintype, losonoverlays);
                setOverlayTerrain(losonoverlays, terraintype, o.getPreserveElevation());
            }
        }
        return losonoverlays.newlosdata;
    }

    private void setHillockTerrain(LOSonOverlays losonoverlays) {
        if (losonoverlays.board.isReversed()) {
            // flip the overlay grid
            for (losonoverlays.currentx = 0; losonoverlays.currentx < losonoverlays.bi.getWidth(); losonoverlays.currentx++) {
                for (losonoverlays.currenty = 0; losonoverlays.currenty < losonoverlays.bi.getHeight(); losonoverlays.currenty++) {
                    if (losonoverlays.newlosdata.onMap(losonoverlays.newlosdata.getGridWidth() - losonoverlays.ovrrec.x  -losonoverlays.currentx -1, losonoverlays.newlosdata.getGridHeight() - losonoverlays.ovrrec.y -losonoverlays.currenty -1)) {
                        int c = losonoverlays.bi.getRGB(losonoverlays.currentx, losonoverlays.currenty);
                        if ((c >> 24) != 0x00) { // not a transparent pixel
                            String terraintouse = "Hillock";
                            Terrain terr;
                            //Retrieving the R G B values
                            final Color color = getRGBColor(c);
                            Color testcolor = new Color(114, 83, 42); //have to use method as several colors have same RGB values
                            if (color.equals(testcolor)) {
                                terraintouse = "Hillock Summit";
                            }
                            losonoverlays.newlosdata.setGridTerrainCode(losonoverlays.newlosdata.getTerrain(terraintouse).getType(), losonoverlays.newlosdata.getGridWidth() - losonoverlays.ovrrec.x  -losonoverlays.currentx -1, losonoverlays.newlosdata.getGridHeight() - losonoverlays.ovrrec.y -losonoverlays.currenty -1);
                            if (!(losonoverlays.newlosdata.gridToHex(losonoverlays.newlosdata.getGridWidth() - losonoverlays.ovrrec.x  -losonoverlays.currentx -1, losonoverlays.newlosdata.getGridHeight() - losonoverlays.ovrrec.y -losonoverlays.currenty -1).getCenterLocation().getTerrain().getName().equals("Hillock Summit"))) {
                                losonoverlays.newlosdata.gridToHex(losonoverlays.newlosdata.getGridWidth() - losonoverlays.ovrrec.x  -losonoverlays.currentx -1, losonoverlays.newlosdata.getGridHeight() - losonoverlays.ovrrec.y -losonoverlays.currenty -1).getCenterLocation().setTerrain(losonoverlays.newlosdata.getTerrain(terraintouse));
                            }
                            else {
                                losonoverlays.newlosdata.gridToHex(losonoverlays.newlosdata.getGridWidth() - losonoverlays.ovrrec.x  -losonoverlays.currentx -1, losonoverlays.newlosdata.getGridHeight() - losonoverlays.ovrrec.y -losonoverlays.currenty -1).getCenterLocation().setLevelInHex(1);
                            }
                        }
                    }
                }
            }
        }
        else {
            for (losonoverlays.currentx = 0; losonoverlays.currentx < losonoverlays.bi.getWidth(); losonoverlays.currentx++) {
                for (losonoverlays.currenty = 0; losonoverlays.currenty < losonoverlays.bi.getHeight(); losonoverlays.currenty++) {
                    if (losonoverlays.newlosdata.onMap(losonoverlays.currentx + losonoverlays.ovrrec.x, losonoverlays.currenty + losonoverlays.ovrrec.y)) {
                        int c = losonoverlays.bi.getRGB(losonoverlays.currentx, losonoverlays.currenty);
                        if ((c >> 24) != 0x00) { // not a transparent pixel
                            String terraintouse = "Hillock";
                            Terrain terr;
                            //Retrieving the R G B values
                            final Color color = getRGBColor(c);
                            Color testcolor = new Color(114, 83, 42); //have to use method as several colors have same RGB values
                            if (color.equals(testcolor)) {
                                terraintouse = "Hillock Summit";
                            }
                            losonoverlays.newlosdata.setGridTerrainCode(losonoverlays.newlosdata.getTerrain(terraintouse).getType(), losonoverlays.currentx + losonoverlays.ovrrec.x, losonoverlays.currenty + losonoverlays.ovrrec.y);
                            if (!(losonoverlays.newlosdata.gridToHex(losonoverlays.currentx + losonoverlays.ovrrec.x, losonoverlays.currenty + losonoverlays.ovrrec.y).getCenterLocation().getTerrain().getName().equals("Hillock Summit"))) {
                                losonoverlays.newlosdata.gridToHex(losonoverlays.currentx + losonoverlays.ovrrec.x, losonoverlays.currenty + losonoverlays.ovrrec.y).getCenterLocation().setTerrain(losonoverlays.newlosdata.getTerrain(terraintouse));
                            }
                            else {
                                losonoverlays.newlosdata.gridToHex(losonoverlays.currentx + losonoverlays.ovrrec.x, losonoverlays.currenty + losonoverlays.ovrrec.y).getCenterLocation().setLevelInHex(1);
                            }
                        }
                    }
                }
            }
        }
    }
    private void setDierTerrain(LOSonOverlays losonoverlays) {
        if (losonoverlays.board.isReversed()) {
            // flip the overlay grid
            for (losonoverlays.currentx = 0; losonoverlays.currentx < losonoverlays.bi.getWidth(); losonoverlays.currentx++) {
                for (losonoverlays.currenty = 0; losonoverlays.currenty < losonoverlays.bi.getHeight(); losonoverlays.currenty++) {
                    if (losonoverlays.newlosdata.onMap(losonoverlays.newlosdata.getGridWidth() - losonoverlays.ovrrec.x  -losonoverlays.currentx -1, losonoverlays.newlosdata.getGridHeight() - losonoverlays.ovrrec.y -losonoverlays.currenty -1)) {
                        int c = losonoverlays.bi.getRGB(losonoverlays.currentx, losonoverlays.currenty);
                        if ((c >> 24) != 0x00) { // not a transparent pixel
                            String terraintouse = "Dier";
                            Terrain terr;
                            //Retrieving the R G B values
                            final Color color = getRGBColor(c);
                            final int terrint = losonoverlays.board.getVASLBoardArchive().getTerrainForColor(color);
                            if (terrint >= 0) {
                                terr = losonoverlays.newlosdata.getTerrain(terrint);
                                if (terr.getName().contains("Scrub")) {
                                    terraintouse = "Scrub";
                                }
                                else if (terr.getName().equals("Dier")) {
                                    terraintouse = "Dier";
                                }
                            }
                            losonoverlays.newlosdata.setGridTerrainCode(losonoverlays.newlosdata.getTerrain(terraintouse).getType(), losonoverlays.newlosdata.getGridWidth() - losonoverlays.ovrrec.x  -losonoverlays.currentx -1, losonoverlays.newlosdata.getGridHeight() - losonoverlays.ovrrec.y -losonoverlays.currenty -1);
                            if (!(losonoverlays.newlosdata.gridToHex(losonoverlays.newlosdata.getGridWidth() - losonoverlays.ovrrec.x  -losonoverlays.currentx -1, losonoverlays.newlosdata.getGridHeight() - losonoverlays.ovrrec.y -losonoverlays.currenty -1).getCenterLocation().getTerrain().getName().equals("Scrub"))) {
                                losonoverlays.newlosdata.gridToHex(losonoverlays.newlosdata.getGridWidth() - losonoverlays.ovrrec.x  -losonoverlays.currentx -1, losonoverlays.newlosdata.getGridHeight() - losonoverlays.ovrrec.y -losonoverlays.currenty -1).getCenterLocation().setTerrain(losonoverlays.newlosdata.getTerrain(terraintouse));
                            }
                        }
                    }
                }
            }
        }
        else {
            for (losonoverlays.currentx = 0; losonoverlays.currentx < losonoverlays.bi.getWidth(); losonoverlays.currentx++) {
                for (losonoverlays.currenty = 0; losonoverlays.currenty < losonoverlays.bi.getHeight(); losonoverlays.currenty++) {
                    if (losonoverlays.newlosdata.onMap(losonoverlays.currentx + losonoverlays.ovrrec.x, losonoverlays.currenty + losonoverlays.ovrrec.y)) {
                        int c = losonoverlays.bi.getRGB(losonoverlays.currentx, losonoverlays.currenty);
                        if ((c >> 24) != 0x00) { // not a transparent pixel
                            String terraintouse = "Dier";
                            Terrain terr;
                            //Retrieving the R G B values
                            final Color color = getRGBColor(c);
                            final int terrint = losonoverlays.board.getVASLBoardArchive().getTerrainForColor(color);
                            if (terrint >= 0) {
                                terr = losonoverlays.newlosdata.getTerrain(terrint);
                                if (terr.getName().contains("Scrub")) {
                                    terraintouse = "Scrub";
                                }
                                else if (terr.getName().equals("Dier")) {
                                    terraintouse = "Dier";
                                }
                            }
                            losonoverlays.newlosdata.setGridTerrainCode(losonoverlays.newlosdata.getTerrain(terraintouse).getType(), losonoverlays.currentx + losonoverlays.ovrrec.x, losonoverlays.currenty + losonoverlays.ovrrec.y);
                            if (!(losonoverlays.newlosdata.gridToHex(losonoverlays.currentx + losonoverlays.ovrrec.x, losonoverlays.currenty + losonoverlays.ovrrec.y).getCenterLocation().getTerrain().getName().equals("Scrub"))) {
                                losonoverlays.newlosdata.gridToHex(losonoverlays.currentx + losonoverlays.ovrrec.x, losonoverlays.currenty + losonoverlays.ovrrec.y).getCenterLocation().setTerrain(losonoverlays.newlosdata.getTerrain(terraintouse));
                            }
                        }
                    }
                }
            }
        }
    }
    private void setDierLip(LOSonOverlays losonoverlays) {
        // step through each hex and reset the terrain.
        //ToDo rework this as string test will no longer work - using different values
        if(losonoverlays.newlosdata.getMapConfiguration().equals("ToplefthalfheightEqualRowCount") || losonoverlays.newlosdata.getA1CenterY() == 65) {
            for (losonoverlays.currentx = 0; losonoverlays.currentx < losonoverlays.newlosdata.getWidth(); losonoverlays.currentx++) {
                for (losonoverlays.currenty = 0; losonoverlays.currenty < losonoverlays.newlosdata.getHeight(); losonoverlays.currenty++) { // no extra hex for boards where each col has same number of rows (eg RO)
                    if(losonoverlays.newlosdata.getHex(losonoverlays.currentx, losonoverlays.currenty).getCenterLocation().getTerrain().getName().equals("Dier")) {
                        for (int a = 0; a < 6; a++) {
                            Hex testhex = losonoverlays.newlosdata.getAdjacentHex(losonoverlays.newlosdata.getHex(losonoverlays.currentx, losonoverlays.currenty), a);
                            if ((testhex == null) || !(testhex.getCenterLocation().getTerrain().getName().equals("Dier"))) {
                                losonoverlays.newlosdata.getHex(losonoverlays.currentx, losonoverlays.currenty).setHexsideTerrain(a, losonoverlays.newlosdata.getTerrain("Dier Lip"));
                                losonoverlays.newlosdata.getHex(losonoverlays.currentx,losonoverlays.currenty).setHexsideLocationTerrain(a, losonoverlays.newlosdata.getTerrain("Dier Lip"));
                            }
                        }
                    }
                }
            }
        }
        else {
            for (losonoverlays.currentx = 0; losonoverlays.currentx < losonoverlays.newlosdata.getWidth(); losonoverlays.currentx++) {
                for (losonoverlays.currenty = 0; losonoverlays.currenty < losonoverlays.newlosdata.getHeight() + (losonoverlays.currentx % 2); losonoverlays.currenty++) { // add 1 hex if odd
                    if(losonoverlays.newlosdata.getHex(losonoverlays.currentx, losonoverlays.currenty).getCenterLocation().getTerrain().getName().equals("Dier")){
                        for (int a = 0; a < 6; a++) {
                            Hex testhex = losonoverlays.newlosdata.getAdjacentHex(losonoverlays.newlosdata.getHex(losonoverlays.currentx, losonoverlays.currenty), a);
                            if ((testhex == null) || !(testhex.getCenterLocation().getTerrain().getName().equals("Dier"))) {
                                losonoverlays.newlosdata.getHex(losonoverlays.currentx, losonoverlays.currenty).setHexsideTerrain(a, losonoverlays.newlosdata.getTerrain("Dier Lip"));
                                losonoverlays.newlosdata.getHex(losonoverlays.currentx,losonoverlays.currenty).setHexsideLocationTerrain(a, losonoverlays.newlosdata.getTerrain("Dier Lip"));
                            }
                        }
                    }
                }
            }
        }
    }

    private void setSandDuneTerrain(LOSonOverlays losonoverlays) {
        if (losonoverlays.board.isReversed()) {
            // flip the overlay grid
            for (losonoverlays.currentx = 0; losonoverlays.currentx < losonoverlays.bi.getWidth(); losonoverlays.currentx++) {
                for (losonoverlays.currenty = 0; losonoverlays.currenty < losonoverlays.bi.getHeight(); losonoverlays.currenty++) {
                    if (losonoverlays.newlosdata.onMap(losonoverlays.newlosdata.getGridWidth() - losonoverlays.ovrrec.x  -losonoverlays.currentx -1, losonoverlays.newlosdata.getGridHeight() - losonoverlays.ovrrec.y -losonoverlays.currenty -1)) {
                        int c = losonoverlays.bi.getRGB(losonoverlays.currentx, losonoverlays.currenty);
                        if ((c >> 24) != 0x00) { // not a transparent pixel
                            String terraintouse = "Sand Dune, Low";
                            Terrain terr;
                            //Retrieving the R G B values
                            final Color color = getRGBColor(c);
                            final int terrint = losonoverlays.board.getVASLBoardArchive().getTerrainForColor(color);
                            if (terrint >= 0) {
                                terr = losonoverlays.newlosdata.getTerrain(terrint);
                                if (terr.getName().equals("Dune, Crest Low")) {
                                    terraintouse = "Dune, Crest Low";
                                }
                                else if (terr.getName().contains("Scrub")) {
                                    terraintouse = "Scrub";
                                }
                            }
                            losonoverlays.newlosdata.setGridTerrainCode(losonoverlays.newlosdata.getTerrain(terraintouse).getType(), losonoverlays.newlosdata.getGridWidth() - losonoverlays.ovrrec.x  -losonoverlays.currentx -1, losonoverlays.newlosdata.getGridHeight() - losonoverlays.ovrrec.y -losonoverlays.currenty -1);
                            if (terraintouse.equals("Dune, Crest Low")) {
                                setDuneCrest(losonoverlays,losonoverlays.newlosdata.getGridWidth() - -losonoverlays.currentx -1, losonoverlays.newlosdata.getGridHeight() -losonoverlays.currenty -1, losonoverlays.ovrrec, true);
                            }
                            losonoverlays.newlosdata.gridToHex(losonoverlays.newlosdata.getGridWidth() - losonoverlays.ovrrec.x  -losonoverlays.currentx -1, losonoverlays.newlosdata.getGridHeight() - losonoverlays.ovrrec.y -losonoverlays.currenty -1).getCenterLocation().setTerrain(losonoverlays.newlosdata.getTerrain("Sand Dune, Low"));

                        }
                    }
                }
            }
        }
        else {
            for (losonoverlays.currentx = 0; losonoverlays.currentx < losonoverlays.bi.getWidth(); losonoverlays.currentx++) {
                for (losonoverlays.currenty = 0; losonoverlays.currenty < losonoverlays.bi.getHeight(); losonoverlays.currenty++) {
                    if (losonoverlays.newlosdata.onMap(losonoverlays.currentx + losonoverlays.ovrrec.x, losonoverlays.currenty + losonoverlays.ovrrec.y)) {
                        int c = losonoverlays.bi.getRGB(losonoverlays.currentx, losonoverlays.currenty);
                        if ((c >> 24) != 0x00) { // not a transparent pixel
                            String terraintouse = "Sand Dune, Low";
                            Terrain terr;
                            //Retrieving the R G B values
                            final Color color = getRGBColor(c);
                            final int terrint = losonoverlays.board.getVASLBoardArchive().getTerrainForColor(color);
                            if (terrint >= 0) {
                                terr = losonoverlays.newlosdata.getTerrain(terrint);
                                if (terr.getName().equals("Dune, Crest Low")) {
                                    terraintouse = "Dune, Crest Low";
                                }
                                else if (terr.getName().contains("Scrub")) {
                                    terraintouse = "Scrub";
                                }
                            }
                            losonoverlays.newlosdata.setGridTerrainCode(losonoverlays.newlosdata.getTerrain(terraintouse).getType(), losonoverlays.currentx + losonoverlays.ovrrec.x, losonoverlays.currenty + losonoverlays.ovrrec.y);
                            if (terraintouse.equals("Dune, Crest Low")) {
                                setDuneCrest(losonoverlays, losonoverlays.currentx, losonoverlays.currenty, losonoverlays.ovrrec, false);
                            }
                            losonoverlays.newlosdata.gridToHex(losonoverlays.currentx + losonoverlays.ovrrec.x, losonoverlays.currenty + losonoverlays.ovrrec.y).getCenterLocation().setTerrain(losonoverlays.newlosdata.getTerrain("Sand Dune, Low"));
                        }
                    }
                }
            }
        }
    }
    private void setDuneCrest(LOSonOverlays losonoverlays, int usepositionx, int usepositiony, Rectangle ovrRec, boolean isreversed){
        // reset the terrain
        Hex dunehex = null;
        Location dunecrestloc=null;
        if(isreversed) {
            // check code here - why duplicate 2 and 4th line?
            dunehex = losonoverlays.newlosdata.gridToHex(usepositionx - ovrRec.x, usepositiony - ovrRec.y);
            dunecrestloc = dunehex.getNearestLocation(usepositionx - ovrRec.x, usepositiony);
            dunehex = losonoverlays.newlosdata.gridToHex(usepositionx + ovrRec.x, usepositiony + ovrRec.y);
            dunecrestloc = dunehex.getNearestLocation(usepositionx + ovrRec.x, usepositiony);
        }
        int hexside = dunehex.getLocationHexside(dunecrestloc);
        if (hexside != -1) {
            dunehex.setHexsideTerrain(hexside, losonoverlays.newlosdata.getTerrain("Dune, Crest Low"));
            dunehex.setHexsideLocationTerrain(hexside, losonoverlays.newlosdata.getTerrain("Dune, Crest Low"));
        }
    }
    private void setWadiTerrain(LOSonOverlays losonoverlays){
        if (losonoverlays.board.isReversed()) {
            // flip the overlay grid
            for (losonoverlays.currentx = 0; losonoverlays.currentx < losonoverlays.bi.getWidth(); losonoverlays.currentx++) {
                for (losonoverlays.currenty = 0; losonoverlays.currenty < losonoverlays.bi.getHeight(); losonoverlays.currenty++) {
                    if (losonoverlays.newlosdata.onMap(losonoverlays.newlosdata.getGridWidth() - losonoverlays.ovrrec.x  -losonoverlays.currentx -1, losonoverlays.newlosdata.getGridHeight() - losonoverlays.ovrrec.y -losonoverlays.currenty -1)) {
                        int c = losonoverlays.bi.getRGB(losonoverlays.currentx, losonoverlays.currenty);
                        if ((c >> 24) != 0x00) { // not a transparent pixel
                            String terraintouse = "Open Ground";
                            Terrain terr;
                            //Retrieving the R G B values
                            final Color color = getRGBColor(c);
                            final int terrint = losonoverlays.board.getVASLBoardArchive().getTerrainForColor(color);
                            if (terrint >= 0) {
                                terr = losonoverlays.newlosdata.getTerrain(terrint);
                                if (terr.getName().equals("Wadi")) {
                                    terraintouse = "Wadi";
                                }
                                else if (terr.getName().equals("Cliff")) {
                                    terraintouse = "Cliff";
                                }
                            }
                            losonoverlays.newlosdata.setGridTerrainCode(losonoverlays.newlosdata.getTerrain(terraintouse).getType(), losonoverlays.newlosdata.getGridWidth() - losonoverlays.ovrrec.x  -losonoverlays.currentx -1, losonoverlays.newlosdata.getGridHeight() - losonoverlays.ovrrec.y -losonoverlays.currenty -1);
                            if (terraintouse == "Wadi") {
                                losonoverlays.newlosdata.gridToHex(losonoverlays.newlosdata.getGridWidth() - losonoverlays.ovrrec.x - losonoverlays.currentx - 1, losonoverlays.newlosdata.getGridHeight() - losonoverlays.ovrrec.y - losonoverlays.currenty - 1).getCenterLocation().setTerrain(losonoverlays.newlosdata.getTerrain("Wadi"));
                                losonoverlays.newlosdata.gridToHex(losonoverlays.newlosdata.getGridWidth() - losonoverlays.ovrrec.x - losonoverlays.currentx - 1, losonoverlays.newlosdata.getGridHeight() - losonoverlays.ovrrec.y - losonoverlays.currenty - 1).getCenterLocation().setLevelInHex(-1);
                            }
                        }
                    }
                }
            }
        }
        else {
            for (losonoverlays.currentx = 0; losonoverlays.currentx < losonoverlays.bi.getWidth(); losonoverlays.currentx++) {
                for (losonoverlays.currenty = 0; losonoverlays.currenty < losonoverlays.bi.getHeight(); losonoverlays.currenty++) {
                    if (losonoverlays.newlosdata.onMap(losonoverlays.currentx + losonoverlays.ovrrec.x, losonoverlays.currenty + losonoverlays.ovrrec.y)) {
                        int c = losonoverlays.bi.getRGB(losonoverlays.currentx, losonoverlays.currenty);
                        if ((c >> 24) != 0x00) { // not a transparent pixel
                            String terraintouse = "Open Ground";
                            Terrain terr;
                            //Retrieving the R G B values
                            final Color color = getRGBColor(c);
                            final int terrint = losonoverlays.board.getVASLBoardArchive().getTerrainForColor(color);
                            if (terrint >= 0) {
                                terr = losonoverlays.newlosdata.getTerrain(terrint);
                                if (terr.getName().equals("Wadi")) {
                                    terraintouse = "Wadi";
                                }
                                else if (terr.getName().equals("Cliff")) {
                                    terraintouse = "Cliff";
                                }
                            }
                            losonoverlays.newlosdata.setGridTerrainCode(losonoverlays.newlosdata.getTerrain(terraintouse).getType(), losonoverlays.currentx + losonoverlays.ovrrec.x, losonoverlays.currenty + losonoverlays.ovrrec.y);
                            if (terraintouse == "Wadi") {
                                losonoverlays.newlosdata.gridToHex(losonoverlays.currentx + losonoverlays.ovrrec.x, losonoverlays.currenty + losonoverlays.ovrrec.y).getCenterLocation().setTerrain(losonoverlays.newlosdata.getTerrain("Wadi"));
                                losonoverlays.newlosdata.gridToHex(losonoverlays.currentx + losonoverlays.ovrrec.x, losonoverlays.currenty + losonoverlays.ovrrec.y).getCenterLocation().setLevelInHex(-1);
                                // need to set depression and cliff hexsides, but how?
                            }
                        }
                    }
                }
            }
        }
    }

    // this is the generic method for terrain overlays
    private void setOverlayTerrain(LOSonOverlays losonoverlays, String terraintype, boolean preserveelevation) {
        // first test for inherent terrain type and send to separate method; use this method for non-inherent or mixed non-inherent/inherent overlays
        if (isInherenttype(terraintype)) {
            setOverlayInherentTerrain(losonoverlays, terraintype);
        }
        else {
            HashMap<VASL.LOS.Map.Hex, VASL.LOS.Map.Terrain>  inhhexes = new HashMap<VASL.LOS.Map.Hex, VASL.LOS.Map.Terrain>();
            HashMap<VASL.LOS.Map.Hex, VASL.LOS.Map.Terrain>  bdghexes = new HashMap<VASL.LOS.Map.Hex, VASL.LOS.Map.Terrain>();
            losonoverlays.overpositionx =0; losonoverlays.overpositiony=0;
                for (losonoverlays.currentx = 0; losonoverlays.currentx < losonoverlays.bi.getWidth(); losonoverlays.currentx++) {
                    for (losonoverlays.currenty = 0; losonoverlays.currenty < losonoverlays.bi.getHeight(); losonoverlays.currenty++) {
                        losonoverlays.overpositionx = losonoverlays.currentx + (int) losonoverlays.ovrrec.getX() - (int) losonoverlays.board.getCropBounds().getX();
                        losonoverlays.overpositiony = losonoverlays.currenty + (int) losonoverlays.ovrrec.getY() - (int) losonoverlays.board.getCropBounds().getY();
                        if (losonoverlays.newlosdata.onMap(losonoverlays.overpositionx, losonoverlays.overpositiony) && losonoverlays.newlosdata.gridToHex(losonoverlays.overpositionx, losonoverlays.overpositiony) !=null) {
                            int c = losonoverlays.bi.getRGB(losonoverlays.currentx, losonoverlays.currenty);
                            Terrain terr = null; int elevint = 0;
                            if ((c >> 24) != 0x00) { // not a transparent pixel
                                //Retrieving the R G B values
                                Color color = getRGBColor(c);
                                terr = getOverlayTerrainfromColor(color, losonoverlays);

                                while(terr == null){
                                    color = getOverlayNearestColor(losonoverlays, losonoverlays.overpositionx, losonoverlays.overpositiony);
                                    if (color.equals(Color.white)){
                                        terr = losonoverlays.newlosdata.getTerrain(losonoverlays.board.getVASLBoardArchive().getTerrainForVASLColor("L0Winter"));
                                    }
                                    else {
                                        terr = getOverlayTerrainfromColor(color, losonoverlays);
                                        if (terr == null) {   //bumpx += 1; bumpy += 1;}
                                            // use OG with elevint from existing losdata; this is a hack when can't find terrain
                                            int passelev = losonoverlays.newlosdata.getGridElevation(losonoverlays.overpositionx, losonoverlays.overpositiony);
                                            String colorname = useDefaultTerrain (passelev);
                                            terr = losonoverlays.newlosdata.getTerrain(losonoverlays.board.getVASLBoardArchive().getTerrainForVASLColor(colorname));

                                        }
                                    }
                                }
                                terr = resetterraintypefortransform(losonoverlays.board.getTerrainChanges(), terraintype, terr);
                                elevint = getOverlayElevationfromColor(losonoverlays, color);
                                // if elevint = -99 then method above could not find a proper elevation for terrain; revert to current elevation in mapboard losdata
                                if (elevint == -99){
                                    elevint = losonoverlays.newlosdata.getGridElevation(losonoverlays.overpositionx, losonoverlays.overpositiony);
                                }
                                if (terr.isDepression()){
                                    elevint = losonoverlays.newlosdata.getGridElevation(losonoverlays.overpositionx, losonoverlays.overpositiony) -1;
                                }
                                //add Hex to collections of inherent hexes and building hexes on the overlay
                                addHextoOverlayInhandBldgMaps(terraintype, terr, losonoverlays, inhhexes, bdghexes);
                                //set terrain type for center location or hexside location (if hexside terrain)
                                setOverlayTerrainType(losonoverlays, terr, terraintype);

                                if (!preserveelevation) {
                                    // turn this into a method if can do so with reversed board
                                    //set elevation for point
                                    losonoverlays.newlosdata.setGridElevation(elevint, losonoverlays.overpositionx, losonoverlays.overpositiony );
                                    //test if pixel is hex center
                                    if (losonoverlays.overpositionx + (int) losonoverlays.board.getCropBounds().getX() == (int)(losonoverlays.newlosdata.gridToHex(losonoverlays.overpositionx, losonoverlays.overpositiony ).getHexCenter()).getX() &&
                                    losonoverlays.overpositiony + (int) losonoverlays.board.getCropBounds().getY()  == (int)(losonoverlays.newlosdata.gridToHex(losonoverlays.overpositionx , losonoverlays.overpositiony).getHexCenter()).getY()) {
                                        // if white center dot on overlay aligns with hex center, won't set elevation properly so need to look for nearby terrain type
                                        // bit of a hack but should work - try it until we get a bug
                                        color = getRGBColor(c);
                                        if (color.equals(Color.white) || color.equals(Color.black)){ // && j<=(x+6)) {
                                            color = getOverlayNearestColor(losonoverlays, losonoverlays.overpositionx, losonoverlays.overpositiony);
                                            elevint = color.equals(Color.white) ? 0 : getOverlayElevationfromColor(losonoverlays, color);
                                            // if elevint = -99 then method above could not find a proper elevation for terrain; revert to current elevation in mapboard losdata
                                            if (elevint == -99){
                                                elevint = 0;  //this is a hack and may not always return a useful result - watch for errors
                                            }
                                        }
                                        // this sets base elevation for the hex - crest line & depression hexes can contain multiple elevations
                                        // hack for LFT3; change if applies to other boards
                                        if (!losonoverlays.board.getVASLBoardArchive().getVASLColorName(color).contains("SnowHexDots2")) {
                                            losonoverlays.newlosdata.gridToHex(losonoverlays.overpositionx, losonoverlays.overpositiony).setBaseLevelofHex(elevint);
                                        }

                                    }
                                }
                            } else { // transparent pixel
                                //test if pixel is hex center
                                if (losonoverlays.overpositionx + (int) losonoverlays.board.getCropBounds().getX() == (int)(losonoverlays.newlosdata.gridToHex(losonoverlays.overpositionx, losonoverlays.overpositiony ).getHexCenter()).getX() &&
                                        losonoverlays.overpositiony + (int) losonoverlays.board.getCropBounds().getY()  == (int)(losonoverlays.newlosdata.gridToHex(losonoverlays.overpositionx , losonoverlays.overpositiony).getHexCenter()).getY()) {
                                    // if center dot on overlay is transparent and aligns with hex center, won't set elevation properly so need to look for nearby terrain type
                                    // bit of a hack but should work - try it until we get a bug
                                    // the bug is with overlays where the border is transparent so test
                                    // (1) if pixel is on the overlay edge and (2) if so are pixels 2 away also transparent
                                    // in those conditions, skip actions
                                    if (!pixelOnTransparentOverlayBorder(losonoverlays)) {
                                        int j = 0;
                                        int k = 0;
                                        elevint = -99;
                                        while ((c >> 24) == 0x00 && j <= 6) {
                                            j += 2;
                                            k += 2;
                                            if (losonoverlays.newlosdata.onMap(losonoverlays.currentx + j, losonoverlays.currenty + k) && pointIsOnOverlay(losonoverlays.bi,losonoverlays.currentx+j, losonoverlays.currenty+k)) {
                                                c = losonoverlays.bi.getRGB(losonoverlays.currentx + j, losonoverlays.currenty + k);
                                            }
                                            else if (losonoverlays.newlosdata.onMap(losonoverlays.currentx + j, losonoverlays.currenty - k) && pointIsOnOverlay(losonoverlays.bi,losonoverlays.currentx+j, losonoverlays.currenty-k)) {
                                                c = losonoverlays.bi.getRGB(losonoverlays.currentx + j, losonoverlays.currenty - k);
                                            }
                                            else if (losonoverlays.newlosdata.onMap(losonoverlays.currentx - j, losonoverlays.currenty + k) && pointIsOnOverlay(losonoverlays.bi,losonoverlays.currentx-j, losonoverlays.currenty+k)) {
                                                c = losonoverlays.bi.getRGB(losonoverlays.currentx - j, losonoverlays.currenty + k);
                                            }
                                            else if (losonoverlays.newlosdata.onMap(losonoverlays.currentx - j, losonoverlays.currenty - k) && pointIsOnOverlay(losonoverlays.bi,losonoverlays.currentx-j, losonoverlays.currenty-k)) {
                                                c = losonoverlays.bi.getRGB(losonoverlays.currentx - j, losonoverlays.currenty - k);
                                            }
                                            else {
                                                break;
                                            }
                                            final Color color = getRGBColor(c);
                                            elevint = color.equals(Color.white) ? 0 : losonoverlays.board.getVASLBoardArchive().getElevationForColor(color);
                                        }
                                        // this sets base elevation for the hex - crest line & depression hexes can contain multiple elevations
                                        if (elevint != -99) {
                                            losonoverlays.newlosdata.gridToHex(losonoverlays.overpositionx, losonoverlays.overpositiony).setBaseLevelofHex(elevint);
                                        }
                                    }
                                }

                            }
                        }
                    }
                }
                addOverlayInhTerrainToLOS(inhhexes, losonoverlays, losonoverlays.board);
                addOverlayBldgLevelsToLOS(bdghexes, losonoverlays);

        }
    }

    private void addOverlayInhTerrainToLOS(HashMap<Hex, Terrain> inhhexes, LOSonOverlays losonoverlays, ASLBoard board) {
            for (Hex inhterrhex : inhhexes.keySet()) {
                final Integer terrtype = inhhexes.get(inhterrhex).getType();
                Rectangle s = inhterrhex.getHexBorder().getBounds();
                for (int i = (int) s.getX(); i < s.getX() + s.getWidth(); i++) {
                    for (int j = (int) s.getY(); j < s.getY() + s.getHeight(); j++) {
                        if(losonoverlays.newlosdata.onMap(i, j)) {
                            if (inhterrhex.contains(i, j)) {
                                if (!losonoverlays.newlosdata.getGridTerrain(i, j).isHexsideTerrain()) {
                                    if (board.isReversed()) {
                                        int cropheight = board.getCropBounds().getHeight() == -1 ? (int) board.getUncroppedSize().getHeight() : (int) board.getCropBounds().getHeight();
                                        int cropwidth = board.getCropBounds().getWidth() == -1 ? (int) board.getUncroppedSize().getWidth() : (int) board.getCropBounds().getWidth();
                                        losonoverlays.newlosdata.setGridTerrainCode(terrtype, cropwidth - i, cropheight - j);
                                    } else {
                                        losonoverlays.newlosdata.setGridTerrainCode(terrtype, i - (int) board.getCropBounds().getX(), j - (int) board.getCropBounds().getY());
                                    }
                                }
                            }
                        }
                    }
                }
                inhterrhex.getCenterLocation().setTerrain(losonoverlays.newlosdata.getTerrain(terrtype));
            }
    }
    private void addOverlayBldgLevelsToLOS(HashMap<Hex, Terrain> bdghexes, LOSonOverlays losonoverlays){
        for(Hex bdglevelhex : bdghexes.keySet()) {
            bdglevelhex.getCenterLocation().setTerrain(bdghexes.get(bdglevelhex));
            Terrain centerlocationterrain = bdglevelhex.getCenterLocation().getTerrain();
            final boolean multihex = isOverlayBldgMultiHex(bdglevelhex, losonoverlays);
            bdglevelhex.addBuildingLevels(centerlocationterrain, multihex);
        }
    }
    private boolean isOverlayBldgMultiHex(Hex bdglevelhex, LOSonOverlays losonoverlays){
        boolean multihexbdg = false;
        // find where on overlay the hex is centered
        final Point hexcentreonoverlay = new Point();
        hexcentreonoverlay.x = (int) (bdglevelhex.getHexCenter().getX() - losonoverlays.ovrrec.getX());
        hexcentreonoverlay.y = (int) (bdglevelhex.getHexCenter().getY() - losonoverlays.ovrrec.getY());
        // use hex center to test if hexsides contain building pixels
        final double verticaloffset = bdglevelhex.getMap().getHexHeight()/2.0;
        // the hexside point is the hexside center point translated one pixel toward the hex center point
        // [0] is the top hexside and the other points are clock-wise from there
        Point[] hexsidepoints = new Point[6];
        final double horizontaloffset = cos(Math.toRadians(30.0)) * verticaloffset;

        hexsidepoints[0] = new Point ((hexcentreonoverlay.x), (int) (-verticaloffset + hexcentreonoverlay.y + 1.0));
        hexsidepoints[1] = new Point ((int)(horizontaloffset + hexcentreonoverlay.x - 1),  (int) (-verticaloffset/2.0 + hexcentreonoverlay.y + 1.0));
        hexsidepoints[2] = new Point ((int)(horizontaloffset + hexcentreonoverlay.x - 1),  (int) (verticaloffset/2.0 + hexcentreonoverlay.y - 1.0));
        hexsidepoints[3] = new Point (hexcentreonoverlay.x, (int) (verticaloffset + hexcentreonoverlay.y - 1.0));
        hexsidepoints[4] = new Point ((int) (-horizontaloffset + hexcentreonoverlay.x + 1),  (int) (verticaloffset/2.0 + hexcentreonoverlay.y - 1.0));
        hexsidepoints[5] = new Point ((int) (-horizontaloffset + hexcentreonoverlay.x + 1),  (int) (-verticaloffset/2.0 + hexcentreonoverlay.y + 1.0));
        // now test if hexside points contain building colour; if the do, it is multihex building
        for (int i = 0; i < 6; i++) {
            // test hexsidepoint is on the overlay
            if (hexsidepoints[i].getX() >= 0 && hexsidepoints[i].getY() >= 0 && hexsidepoints[i].getX() <= losonoverlays.bi.getWidth() && hexsidepoints[i].getY() <= losonoverlays.bi.getHeight()) {
                final int c = losonoverlays.bi.getRGB((int) hexsidepoints[i].getX(), (int) hexsidepoints[i].getY());
                Terrain terr = null;
                if ((c >> 24) != 0x00) { // not a transparent pixel
                    String terraintouse = "Open Ground";
                    //Retrieving the R G B values
                    final Color color = getRGBColor(c);
                    final int terrint = losonoverlays.board.getVASLBoardArchive().getTerrainForColor(color);
                    if (terrint >= 0) {
                        terr = losonoverlays.newlosdata.getTerrain(terrint);
                        terraintouse = terr.getName();
                    }
                    if (terr != null) {
                        if (terr.isBuilding()) {
                            multihexbdg = true;
                            break;
                        }
                    }
                }
            }
        }
        return multihexbdg;
    }
    //set terrain type for point, and center location or hexside location (if hexside terrain)
    private void setOverlayTerrainType(LOSonOverlays losonoverlays, Terrain terr, String overlaytype) {
        losonoverlays.newlosdata.setGridTerrainCode(terr.getType(), losonoverlays.overpositionx, losonoverlays.overpositiony);
        if (losonoverlays.newlosdata.gridToHex(losonoverlays.overpositionx, losonoverlays.overpositiony).getNearestLocation(losonoverlays.overpositionx, losonoverlays.overpositiony).isCenterLocation() && !overlaytype.contains("NoRoads")) {
            losonoverlays.newlosdata.gridToHex(losonoverlays.overpositionx, losonoverlays.overpositiony).getCenterLocation().setTerrain(terr);
        }
        else if (terr != null && terr.isHexsideTerrain()) {
            int hexside = losonoverlays.newlosdata.gridToHex(losonoverlays.overpositionx, losonoverlays.overpositiony).getLocationHexside(losonoverlays.newlosdata.gridToHex(losonoverlays.overpositionx, losonoverlays.overpositiony).getNearestLocation(losonoverlays.overpositionx, losonoverlays.overpositiony));
            Point hexsidecenter = losonoverlays.newlosdata.gridToHex(losonoverlays.overpositionx, losonoverlays.overpositiony).getHexsideLocation(hexside).getEdgeCenterPoint();
            //only set hexside terrain for hex and hexside location if within 10 pixels of hexside centre - avoids mistaken hexsides
            if (Math.abs(losonoverlays.overpositionx - hexsidecenter.x) < 10 && Math.abs(losonoverlays.overpositiony - hexsidecenter.y) < 10) {
                losonoverlays.newlosdata.gridToHex(losonoverlays.overpositionx, losonoverlays.overpositiony).setHexsideTerrain(hexside, terr);
                losonoverlays.newlosdata.gridToHex(losonoverlays.overpositionx, losonoverlays.overpositiony).setHexsideLocationTerrain(hexside, terr);
            }
        }
    }
    private Terrain getOverlayTerrainfromColor(Color color, LOSonOverlays losonoverlays){
        final int terrint = losonoverlays.board.getVASLBoardArchive().getTerrainForColor(color);
        if (terrint >= 0) {
            return losonoverlays.newlosdata.getTerrain(terrint);
        }
        return null; //newlosdata.getTerrain("Open Ground");
    }
    private Integer getOverlayElevationfromColor(LOSonOverlays losonoverlays, Color color) {
        int elevint = losonoverlays.board.getVASLBoardArchive().getElevationForColor(color);
        if (elevint == BoardMetadata.NO_ELEVATION) {
            Color newcolor = getOverlayNearestColor(losonoverlays, losonoverlays.overpositionx, losonoverlays.overpositiony);
            if (newcolor == null) { //transparent pixel
                elevint = losonoverlays.newlosdata.getGridElevation(losonoverlays.overpositionx, losonoverlays.overpositiony);
            }
            else {
                if ((newcolor.equals(Color.white) || newcolor.equals(Color.BLACK))) {
                    elevint = 0;
                }
                else {
                    elevint = losonoverlays.board.getVASLBoardArchive().getElevationForColor(newcolor);
                    // DR comments this out Jan 25 due to evidence of errors.
                    // see calling code for related changes to handle a -99 return
                    //if (elevint == -99) {elevint = 0;}

                }
            }
        }
        return elevint;
    }
    private Color getOverlayNearestColor(LOSonOverlays losonoverlays, int newovrx, int newovry){
        int c = 0; int a = 2;
        Color color = Color.BLACK;
        //ToDo fix use of int values of c
        while (color.equals(Color.BLACK) || isOverlayBoardNumColor(color, losonoverlays) || color.equals(getRGBColor(-5261152)) || color.equals(getRGBColor(-262915))) {  //-5261152 = 175,184,160 - SnowHexDots2
            // point must be (a) on map (b) on overlay (c) not transparent
            if (losonoverlays.newlosdata.onMap(newovrx + a, newovry + a) && (pointIsOnOverlay(losonoverlays.bi, losonoverlays.currentx+(a-1), losonoverlays.currenty+a) && (!((losonoverlays.bi.getRGB(losonoverlays.currentx+(a-1), losonoverlays.currenty+a) >> 24) == 0X00)))) {
                c = losonoverlays.bi.getRGB(losonoverlays.currentx + (a - 1), losonoverlays.currenty + a);
            }
            else if ((losonoverlays.newlosdata.onMap(newovrx + a, newovry - a)) && (pointIsOnOverlay(losonoverlays.bi, losonoverlays.currentx+(a-1), losonoverlays.currenty-a) && (!((losonoverlays.bi.getRGB(losonoverlays.currentx+(a-1), losonoverlays.currenty-a) >> 24) == 0X00)))) {
                c = losonoverlays.bi.getRGB(losonoverlays.currentx + (a - 1), losonoverlays.currenty - a);
            }
            else if ((losonoverlays.newlosdata.onMap(newovrx - a, newovry + a)) && (pointIsOnOverlay(losonoverlays.bi, losonoverlays.currentx-(a-1), losonoverlays.currenty+a) && (!((losonoverlays.bi.getRGB(losonoverlays.currentx-(a-1), losonoverlays.currenty+a) >> 24) == 0X00)))) {
                c = losonoverlays.bi.getRGB(losonoverlays.currentx - (a - 1), losonoverlays.currenty + a);
            }
            else if ((losonoverlays.newlosdata.onMap(newovrx - a, newovry - a)) && (pointIsOnOverlay(losonoverlays.bi, losonoverlays.currentx-(a-1), losonoverlays.currenty-a) && (!((losonoverlays.bi.getRGB(losonoverlays.currentx-(a-1), losonoverlays.currenty-a) >> 24) == 0X00)))) {
                c = losonoverlays.bi.getRGB(losonoverlays.currentx - (a - 1), losonoverlays.currenty - a);
            }
            else {
                //c= -5260182;  // use OG as default - see if this causes LOS errors
            }
            if(c != 0){color = getRGBColor(c);}
            a += 1;
            if (a > 5){break;}
        }
        return color;
    }
    private Boolean pixelOnTransparentOverlayBorder(LOSonOverlays losonoverlays) {
        int c = 0, b = 0, a = 3;
        if (losonoverlays.currentx == 0 || losonoverlays.currentx == losonoverlays.bi.getWidth() - 1) {
            if (losonoverlays.newlosdata.onMap(losonoverlays.overpositionx, losonoverlays.overpositiony + a)) {
                if (losonoverlays.currenty + a > losonoverlays.bi.getHeight() - 1 ){ a = -3;}  //need to ensure testing with pixel on overlay
                c = losonoverlays.bi.getRGB(losonoverlays.currentx, losonoverlays.currenty + a);
                return (c >> 24) != 0x00 ? false : true;  // not a transparent pixel
            }
            if (losonoverlays.newlosdata.onMap(losonoverlays.overpositionx, losonoverlays.overpositiony - a)) {
                if (losonoverlays.currenty - a < 0 ){ a = -3;}  //need to ensure testing with pixel on overlay
                b = losonoverlays.bi.getRGB(losonoverlays.currentx, losonoverlays.currenty - a);
                return (b >> 24) != 0x00 ? false : true;  // not a transparent pixel
            }
            return true; //transparent pixel
        } else if (losonoverlays.currenty == 0 || losonoverlays.currenty == losonoverlays.bi.getHeight() - 1){
            if (losonoverlays.newlosdata.onMap(losonoverlays.overpositionx + a, losonoverlays.overpositiony)) {
                if (losonoverlays.currentx + a > losonoverlays.bi.getWidth() - 1 ){ a = -3;}  //need to ensure testing with pixel on overlay
                c = losonoverlays.bi.getRGB(losonoverlays.currentx + a, losonoverlays.currenty);
                return (c >> 24) != 0x00 ? false : true;  // not a transparent pixel
            }
            if (losonoverlays.newlosdata.onMap(losonoverlays.overpositionx - a, losonoverlays.overpositiony)) {
                if (losonoverlays.currentx - a < 0 ){ a = -3;}  //need to ensure testing with pixel on overlay
                b = losonoverlays.bi.getRGB(losonoverlays.currentx- a, losonoverlays.currenty);
                return (b >> 24) != 0x00 ? false : true;  // not a transparent pixel
            }
            return true; //transparent pixel
        }
        return false;  // not on border
    }
    private boolean pointIsOnOverlay(BufferedImage bi, int usex, int usey){
        return usex >= 0 && usex < bi.getWidth() && usey >= 0 && usey < bi.getHeight();
    }
    //add Hex to collections of inherent hexes and building hexes on the overlay
    private void addHextoOverlayInhandBldgMaps(String terraintype, Terrain terr, LOSonOverlays losonoverlays, HashMap<VASL.LOS.Map.Hex, VASL.LOS.Map.Terrain>  inhhexes, HashMap<VASL.LOS.Map.Hex, VASL.LOS.Map.Terrain> bdghexes) {
        if (terr != null) {
            if (terr.isInherentTerrain() ||
                    (terraintype == "Steppe" && (terr.getName().equals("Brush") || terr.getName().equals("Woods"))) ||
                    (terraintype == "Broken" && terr.getName().equals("Brush")) ||
                    (terraintype == "Bamboo" && (terr.getName().equals("Brush")))) {
                if (!inhhexes.containsKey(losonoverlays.newlosdata.gridToHex((int) losonoverlays.overpositionx, (int) losonoverlays.overpositiony))) {
                    //hack - ensure that the pixel is not close to a hexside as VASL geometry can put it in an adjacent hex
                    final Point hexcenter = losonoverlays.newlosdata.gridToHex((int) losonoverlays.overpositionx, (int) losonoverlays.overpositiony).getHexCenter();
                    final Double d =  Math.sqrt(((Math.pow(hexcenter.x - losonoverlays.overpositionx, 2) + (Math.pow(hexcenter.y - losonoverlays.overpositiony, 2)))));
                    if (d < 25) {
                        inhhexes.put(losonoverlays.newlosdata.gridToHex((int) losonoverlays.overpositionx, (int) losonoverlays.overpositiony), terr);
                        // added if then to ensure only used in specific circumstance
                        // TODO: remove if after 668 is released and test this works for other transforms
                        //if (terraintype == "Steppe" && (terr.getName().equals("Brush") || terr.getName().equals("Woods"))) {
                            doNonInherentToInherentFix(terraintype, terr, losonoverlays);
                        //}
                    }
                }

            }
            else if (terr.isBuilding()) {
                if (!terr.getName().equals("Stone Building") && !terr.getName().equals("Wooden Building") && !terr.getName().contains("Rowhouse Wall")) {
                    if (!bdghexes.containsKey(losonoverlays.newlosdata.gridToHex((int) losonoverlays.overpositionx, (int) losonoverlays.overpositiony))) {
                        bdghexes.put(losonoverlays.newlosdata.gridToHex((int) losonoverlays.overpositionx, (int) losonoverlays.overpositiony), terr);
                    }
                }
            }
        }
    }

    private boolean isOverlayBoardNumColor(Color testcolor, LOSonOverlays losonoverlays){
        if(testcolor == null) {
            return false;
        }
        final String colorName = losonoverlays.board.getVASLBoardArchive().getVASLColorName(testcolor);
        return "WhiteHexNumbers".equals(colorName) || "WinterBlackHexNumbers".equals(colorName) ||
                "MudBoardNum".equals(colorName) || "DTO_BoardNum".equals(colorName) ||
                "AD_WinterBlackHexNumbers".equals(colorName);
    }

    private String useDefaultTerrain (int passelev) {
        switch (passelev) {
            case -2:
                return "Level_2";
            case -1:
                return "Level_1";
            case 0:
                return "Level0";
            case 1:
                return "Level1";
            case 2:
                return "Level2";
            case 3:
                return "Level3";
            case 4:
                return "Level4";
            case 5:
                return "Level5";
            case 6:
                return "Level6";
            default:
                return "Level0";

        }

    }

    // if overlayname returns "" from this method then los checking won't work with the overlay
    // when adding items here also add them to VASLThread.initializeMap
    private String getOverlayTerrainType(Overlay o){
        final String overlayname = o.getName();
        if (overlayname.contains("Steppe")) {
            return "Steppe";
        }
        if(overlayname.contains("BrokenTerrain")) {
            return "Broken";
        }
        if (overlayname.contains("PalmTrees")) {
            return "Palm Trees";
        }
        if (overlayname.contains("Bamboo")) {
            return "Bamboo";
        }
        if (overlayname.contains("elrr")){
            return "Elevated Railroad";
        }
        if (overlayname.contains("surr")){
            return "Sunken Railroad";
        }
        if (overlayname.contains("rr")){
            return "Railroad";
        }
        if (overlayname.contains("NoRoads")){
            return "NoRoads";
        }
        if (overlayname.contains("rv")){
            return "River";
        }
        if (overlayname.contains("sw")){
            return "Swamp";
        }
        if (overlayname.contains("be")){
            return "Beach";
        }
        if (overlayname.contains("b")){
            return "Brush";
        }
        if (overlayname.contains("hd")){
            return "Hedges";
        }
        if (overlayname.contains("sh")){
            return "Shellholes";
        }
        else if (overlayname.contains("og") || overlayname.equals("dx1") || overlayname.equals("dx5")){
            return "Open Ground";
        }
        else if (overlayname.contains("m")){
            return "Marsh";
        }
        else if (overlayname.contains("g")){
            return "Grain";
        }
        else if (overlayname.contains("p")){
            return "Water";
        }
        else if (overlayname.contains("ow")){
            return "Woods";
        }
        else if (overlayname.contains("oc")){
            return "Ocean";
        }
        else if (overlayname.contains("o") || overlayname.equals("dx3") || overlayname.equals("dx7")){
            return "Orchard";
        }
        else if (overlayname.contains("wd") || overlayname.equals("dx2") || overlayname.equals("dx4")){
            return "Woods";
        }
        else if (overlayname.contains("x") && !overlayname.contains("dx")) {
            return "Building";
        }
        else if (overlayname.contains("hi")) {
            return "Hill";
        }
        else if (overlayname.contains("st")) {
            return "Stream";
        }
        else if (overlayname.contains("sr")) {
            return "Stone Rubble";
        }
        else if (overlayname.contains("wr")) {
            return "Wooden Rubble";
        }
        else if (overlayname.contains("wt")) {
            return "Water";
        }
        else if (overlayname.contains("v")) {
            return "Vineyard";
        }
        else {
            return "";
        }

    }

    private boolean isInherenttype(String terraintype) {
        return (terraintype.equals("Orchard") || terraintype.contains("Stone Rubble") || terraintype.contains("Wooden Rubble") || terraintype.equals("Palm Trees") );
    }
    private void setOverlayInherentTerrain(LOSonOverlays losonoverlays, String terraintype) {
        Hex temphex = null; Hex newhex;
        Hex previoushex = null;

        if (losonoverlays.board.isReversed()) {
            // flip the overlay grid
            for (losonoverlays.currentx = 0; losonoverlays.currentx < losonoverlays.bi.getWidth(); losonoverlays.currentx++) {
                for (losonoverlays.currenty = 0; losonoverlays.currenty < losonoverlays.bi.getHeight(); losonoverlays.currenty++) {
                    if (losonoverlays.newlosdata.onMap(losonoverlays.newlosdata.getGridWidth() - losonoverlays.ovrrec.x  -losonoverlays.currentx -1, losonoverlays.newlosdata.getGridHeight() - losonoverlays.ovrrec.y -losonoverlays.currenty -1)) {
                        Hex hextouse = losonoverlays.newlosdata.gridToHex(losonoverlays.newlosdata.getGridWidth() - losonoverlays.ovrrec.x -losonoverlays.currentx -1, losonoverlays.newlosdata.getGridHeight() - losonoverlays.ovrrec.y -losonoverlays.currenty -1);
                        if (!hextouse.equals(previoushex)) {
                            int c = losonoverlays.bi.getRGB(losonoverlays.currentx, losonoverlays.currenty);
                            if ((c >> 24) != 0x00) { // not a transparent pixel
                                String terraintouse = "Open Ground";
                                Terrain terr = null;
                                //Retrieving the R G B values
                                final Color color = getRGBColor(c);
                                final int terrint = losonoverlays.board.getVASLBoardArchive().getTerrainForColor(color);
                                if (terrint >= 0) {
                                    terr = losonoverlays.newlosdata.getTerrain(terrint);
                                    if (terr.getName().equals(terraintype)) {
                                        terraintouse = terraintype;
                                    }
                                }
                                if (!terraintouse.equals("Open Ground") && terr != null) {  // terrain is inherent terrain
                                    hextouse.getCenterLocation().setTerrain(losonoverlays.newlosdata.getTerrain(terraintouse));
                                    hextouse.setOverlayBorder();
                                    LOSDataEditor loseditor = new LOSDataEditor(losonoverlays.newlosdata);
                                    loseditor.setGridTerrain(hextouse.getoverlayborder(), terr);
                                    for (int z = 0; z < 6; z++) {
                                        hextouse.setHexsideTerrain(z, losonoverlays.newlosdata.getTerrain("Open Ground"));
                                        final Hex adjhex = losonoverlays.newlosdata.getAdjacentHex(hextouse, z);
                                        if (adjhex != null) {
                                            adjhex.setHexsideTerrain(Hex.getOppositeHexside(z), losonoverlays.newlosdata.getTerrain("Open Ground"));
                                        }
                                    }
                                    previoushex = hextouse;
                                }
                            }
                        }
                    }
                }
            }
        } else {
            for (losonoverlays.currentx = 0; losonoverlays.currentx < losonoverlays.bi.getWidth(); losonoverlays.currentx++) {
                for (losonoverlays.currenty = 0; losonoverlays.currenty < losonoverlays.bi.getHeight(); losonoverlays.currenty++) {
                    if (losonoverlays.newlosdata.onMap(losonoverlays.currentx + losonoverlays.ovrrec.x, losonoverlays.currenty + losonoverlays.ovrrec.y)) {
                        Hex hextouse = losonoverlays.newlosdata.gridToHex(losonoverlays.currentx + losonoverlays.ovrrec.x, losonoverlays.currenty + losonoverlays.ovrrec.y);
                        if (!hextouse.equals(previoushex)) {
                            int c = losonoverlays.bi.getRGB(losonoverlays.currentx, losonoverlays.currenty);
                            if ((c >> 24) != 0x00) { // not a transparent pixel
                                String terraintouse = "Open Ground";
                                Terrain terr = null;
                                //Retrieving the R G B values
                                final Color color = getRGBColor(c);
                                final int terrint = losonoverlays.board.getVASLBoardArchive().getTerrainForColor(color);
                                if (terrint >= 0) {
                                    terr = losonoverlays.newlosdata.getTerrain(terrint);
                                    if (terr.getName().equals(terraintype)) {
                                        terraintouse = terraintype;
                                    }
                                }

                                if (!terraintouse.equals("Open Ground") && terr != null) {  // terrain is inherent terrain

                                    hextouse.getCenterLocation().setTerrain(losonoverlays.newlosdata.getTerrain(terraintouse));
                                    hextouse.setOverlayBorder();
                                    final LOSDataEditor loseditor = new LOSDataEditor(losonoverlays.newlosdata);
                                    loseditor.setGridTerrain(hextouse.getoverlayborder(), terr);
                                    for (int z = 0; z < 6; z++) {
                                        hextouse.setHexsideTerrain(z, losonoverlays.newlosdata.getTerrain("Open Ground"));
                                        final Hex adjhex = losonoverlays.newlosdata.getAdjacentHex(hextouse, z);
                                        if (adjhex != null) {
                                            adjhex.setHexsideTerrain(Hex.getOppositeHexside(z), losonoverlays.newlosdata.getTerrain("Open Ground"));
                                        }
                                    }
                                    previoushex = hextouse;
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    private void doNonInherentToInherentFix(String terraintype, Terrain terr, LOSonOverlays losonoverlays){
        Point pointtoset = null;
        Hex setterrainonhexside = losonoverlays.newlosdata.gridToHex((int) losonoverlays.overpositionx, (int) losonoverlays.overpositiony);
        for (int z = 0; z < 6; z++) {
            pointtoset = setterrainonhexside.getHexsideLocation(z).getEdgeCenterPoint();
            if (z == 0){pointtoset.y -= 1;}
            if (z == 3){pointtoset.y += 1;}
            if(losonoverlays.newlosdata.onMap(pointtoset.x, pointtoset.y)) {
                losonoverlays.newlosdata.setGridTerrainCode(terr.getType(), pointtoset.x, pointtoset.y);
            }
        }
    }
    // enables terrain transformations to be applied to overlay losdata
    // this should be a generic approach but starting with specific transforms

    private Terrain resetterraintypefortransform(String terrainchanges, String terraintype, Terrain terr){
        if (terrainchanges.contains("Bamboo") && terr.getName().equals("Brush")){
            return sharedBoardMetadata.getTerrainTypes().get(terraintype);
        }
        return terr;
    }
    private String resetfortransform (String terraintype, LOSonOverlays losonoverlays){
        if (terraintype.equals("Brush") && losonoverlays.board.getTerrainChanges().contains("Bamboo")){
            return "Bamboo";
        }
        return terraintype;
    }

    /**
     * Sets status of LOS engine to legacy mode
     */
    private void setLegacyMode() {
        legacyMode = true;
        VASLMap = null;
    }
    /**
     * @return the VASL map
     */
    public VASL.LOS.Map.Map getVASLMap() {
        return VASLMap;
    }

    /**
     * @return the shared board metadata
     */
    public static SharedBoardMetadata getSharedBoardMetadata() {
        return sharedBoardMetadata;
    }

    /**
     * @return the counter metadata
     */
    public static CounterMetadataFile getCounterMetadata() {
        return counterMetadata;
    }

    /**
     * @return true if the map is in legacy mode (i.e. pre-6.0)
     */
    public boolean isLegacyMode(){
        return legacyMode;
    }

    /**
     * Log a string to the VASSAL error log
     * @param error the error string
     */
    private void logError(String error) {
        logger.info(error);
    }

    /**
     * Log an exception to the VASSAL error log
     * @param error the exception
     */
    private void logException(Throwable error) {
        logger.info("", error);
    }

    public BufferedImage getImgMapIcon(Point pt, double size, double os_scale) {
        BufferedImage img = new BufferedImage((int)size, (int)size, BufferedImage.TYPE_INT_ARGB);
        final Graphics2D g2d = img.createGraphics();

        double dzoom = 0.0;
        Rectangle rect = null;

        for (Board b: getBoards()) {
            if (rect == null) {
                final double mag = b.getMagnification();
                dzoom = os_scale / mag;

                rect = new Rectangle(
                  (int)((pt.x * os_scale - size/2) / mag),
                  (int)((pt.y * os_scale - size/2) / mag),
                  (int)(size / mag),
                  (int)(size / mag)
                );

                g2d.translate(-rect.x, -rect.y);
            }

            b.drawRegion(g2d, getLocation(b, dzoom), rect, dzoom, null);
        }

        drawPiecesNonStackableInRegion(g2d, rect, dzoom);

        g2d.dispose();
        return img;
    }

    protected void drawPiecesNonStackableInRegion(Graphics g, Rectangle visibleRect, double dZoom)
    {
        Graphics2D g2d = (Graphics2D) g;
        Composite oldComposite = g2d.getComposite();
        g2d.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, pieceOpacity));

        GamePiece[] stack = pieces.getPieces();

        for (int i = 0; i < stack.length; ++i)
        {
            Point pt = stack[i].getPosition();

            if (stack[i].getClass() != Stack.class)
            {
                if (Boolean.TRUE.equals(stack[i].getProperty(Properties.NO_STACK)))
                {
                    //JY
                    //stack[i].draw(g, (int) (pt.x * dZoom), (int) (pt.y * dZoom), null, dZoom);
                    double pZoom = PieceScalerBoardZoom(stack[i]);
                    stack[i].draw(g, (int) (pt.x * dZoom), (int) (pt.y * dZoom), null, dZoom*pZoom);
                    //JY
                }
            }
        }

        g2d.setComposite(oldComposite);
    }

    public void setShowMapLevel(ShowMapLevel showmaplevel) {
      this.showmaplevel = showmaplevel;
    }
    
    @Override
    public boolean isPiecesVisible() {
        return pieceOpacity != 0;
    }

    @Override
    public void drawPiecesInRegion(Graphics g,
                                   Rectangle visibleRect,
                                   Component c) {

        Graphics2D g2d = (Graphics2D) g;
        Composite oldComposite = g2d.getComposite();
        g2d.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, pieceOpacity));

        final double os_scale = g2d.getDeviceConfiguration().getDefaultTransform().getScaleX();
        final double dzoom = getZoom() * os_scale;

        GamePiece[] stack = pieces.getPieces();
        // Create a java.util.map indication how many stacks are at each point on the map
        // This is used to determine if a HIP stack has enemy game pieces at the same location
        // If it does, then it will be drawn offset to be visible to owner
        java.util.Map<Point, Integer> pieceMap = new HashMap<Point, Integer>();
        for (int i = 0; i < stack.length; ++i) {
            // increment the count of pieces at this point
            String name = stack[i].getName();
            Stack s = null;
            if (stack[i] instanceof Stack) {
                s = (Stack) stack[i];
            } else {
                continue;
            }
            int x = s.getPieceCount();
            if (x == 0 || name.equals(""))
                //empty stack, ignore
                continue;
            Point pt = stack[i].getPosition();
            Integer count = pieceMap.get(pt);
            if (count == null) {
                count = 0;
            }
            pieceMap.put(pt, count + 1);
        }

        for (int i = 0; i < stack.length; ++i)
        {
            Point pt = mapToDrawing(stack[i].getPosition(), os_scale);
            //JY
            double pZoom = PieceScalerBoardZoom(stack[i]);
            //JY

            if (stack[i].getClass() == Stack.class)
            {
                // If a unit is HIP and there are more than one stack in that location,
                // we offset the hidden units so they are visible to owner
                if (stack[i].getName().contains("HIP") && pieceMap.get(stack[i].getPosition()) != null && pieceMap.get(stack[i].getPosition()) > 1) {
                    // Create an offset point for the hidden stack
                    Point hiddenpoint = new Point(pt.x - 15, pt.y - 15);
                    getStackMetrics().draw((Stack) stack[i], hiddenpoint, g, this, dzoom*pZoom, visibleRect);
                } else if (showmaplevel == ShowMapLevel.ShowAll) {
                    //JY
                    //getStackMetrics().draw((Stack) stack[i], pt, g, this, dzoom, visibleRect);
                    getStackMetrics().draw((Stack) stack[i], pt, g, this, dzoom*pZoom, visibleRect);
                    //JY
                }
            }
            else
            {
                if (showmaplevel == ShowMapLevel.ShowAll  || (stack[i].getProperty("overlay") != null && showmaplevel == ShowMapLevel.ShowMapOnly)) // always show overlays
                {
                    //JY
                    //stack[i].draw(g, pt.x, pt.y, c, dzoom);
                    stack[i].draw(g, pt.x, pt.y, c, dzoom*pZoom);
                    //JY

                    if (Boolean.TRUE.equals(stack[i].getProperty(Properties.SELECTED))) {
                        //JY
                        // highlighter.draw(stack[i], g, pt.x, pt.y, c, dzoom);
                        highlighter.draw(stack[i], g, pt.x, pt.y, c, dzoom*pZoom);
                        //JY
                    }
                }
                else if (showmaplevel == ShowMapLevel.ShowMapAndOverlay)
                {
                    if (Boolean.TRUE.equals(stack[i].getProperty(Properties.NO_STACK)))
                    {
                        //JY
                        //stack[i].draw(g, pt.x, pt.y, c, dzoom);
                        stack[i].draw(g, pt.x, pt.y, c, dzoom*pZoom);
                        //JY

                        if (Boolean.TRUE.equals(stack[i].getProperty(Properties.SELECTED))) {
                            //JY
                            //highlighter.draw(stack[i], g, pt.x, pt.y, c, dzoom);
                            highlighter.draw(stack[i], g, pt.x, pt.y, c, dzoom*pZoom);
                            //JY
                        }
                    }
                }
            }
/*
        // draw bounding box for debugging
        final Rectangle bb = stack[i].boundingBox();
        g.drawRect(pt.x + bb.x, pt.y + bb.y, bb.width, bb.height);
*/
        }

        g2d.setComposite(oldComposite);
    }
    @Override
    public void drawPieces(Graphics g, int xOffset, int yOffset)
    {


        Graphics2D g2d = (Graphics2D) g;
        Composite oldComposite = g2d.getComposite();
        g2d.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, pieceOpacity));

        final double os_scale = g2d.getDeviceConfiguration().getDefaultTransform().getScaleX();
        final double dzoom = getZoom() * os_scale;

        GamePiece[] stack = pieces.getPieces();

        for (int i = 0; i < stack.length; ++i)
        {
            //JY
            double pZoom = PieceScalerBoardZoom(stack[i]);
            //JY
            if (showmaplevel == ShowMapLevel.ShowAll || (stack[i].getProperty("overlay") != null && showmaplevel == ShowMapLevel.ShowMapOnly)) // always show overlays
            {
                Point pt = mapToDrawing(stack[i].getPosition(), os_scale);

                //JY
                //stack[i].draw(g, pt.x + xOffset, pt.y + yOffset, theMap, dzoom);
                stack[i].draw(g, pt.x + xOffset, pt.y + yOffset, theMap, dzoom*pZoom);
                //JY

                if (Boolean.TRUE.equals(stack[i].getProperty(Properties.SELECTED))) {
                    //JY
                    //highlighter.draw(stack[i], g, pt.x - xOffset, pt.y - yOffset, theMap, dzoom);
                    highlighter.draw(stack[i], g, pt.x - xOffset, pt.y - yOffset, theMap, dzoom * pZoom);
                    //JY
                }
            }
            else if (showmaplevel == ShowMapLevel.ShowMapAndOverlay)
            {
                if (stack[i].getClass() != Stack.class)
                {
                    if (Boolean.TRUE.equals(stack[i].getProperty(Properties.NO_STACK)))
                    {
                        Point pt = mapToDrawing(stack[i].getPosition(), os_scale);

                        //JY
                        //stack[i].draw(g, pt.x + xOffset, pt.y + yOffset, theMap, dzoom);
                        stack[i].draw(g, pt.x + xOffset, pt.y + yOffset, theMap, dzoom*pZoom);
                        //JY

                        if (Boolean.TRUE.equals(stack[i].getProperty(Properties.SELECTED))) {
                            //JY
                            //highlighter.draw(stack[i], g, pt.x - xOffset, pt.y - yOffset, theMap, dzoom);
                            highlighter.draw(stack[i], g, pt.x - xOffset, pt.y - yOffset, theMap, dzoom * pZoom);
                            //JY
                        }
                    }
                }
            }
        }

        g2d.setComposite(oldComposite);
    }
    private Color getRGBColor(int c){
        final int red = (c & 0x00ff0000) >> 16;
        final int green = (c & 0x0000ff00) >> 8;
        final int blue = c & 0x000000ff;
        return new Color(red, green, blue);
    }
  
    public enum ShowMapLevel {
      ShowAll,
      ShowMapAndOverlay,
      ShowMapOnly        
    }

    //JY
    public void setStackMetrics(ASLStackMetrics sm) {
        this.ASLmetrics = sm;
    }
    @Override
    public ASLStackMetrics getStackMetrics() {
        if (this.ASLmetrics == null) {
            this.ASLmetrics = new ASLStackMetrics();
            this.ASLmetrics.build((Element)null);
            this.add(this.ASLmetrics);
            this.ASLmetrics.addTo(this);
        }
        return this.ASLmetrics;
    }

    public static double getbZoom() {
        return bZoom;
    }
    public void setbZoom (double z) {
        if (bZoom != 0.0D) {
            oldbZoom = bZoom;
        }
        else {
            oldbZoom = 1.0D;
        }
        bZoom = z;
    }

    private void findOverlays() {
        //All pieces that should scale with the map, not the counters
        //Mostly overlays, but some others
        String[] ovlPalettes = {"Draggable Overlays", "Deluxe Draggable", "Terrain Overlays", "Overlays (Large)"};
        java.util.List<PieceWindow> pwList = GameModule.getGameModule().getAllDescendantComponentsOf(PieceWindow.class);
        for (PieceWindow pw: pwList) {
            String pwName = pw.getAttributeValueString("name");
            if (Arrays.asList(ovlPalettes).contains(pwName)) {
                java.util.List<PieceSlot> psList = pw.getAllDescendantComponentsOf(PieceSlot.class);
                for (PieceSlot ps: psList) {
                    pieceslotgpidlist.add(ps.getGpId());
                }
            }
        }
        String[] ovlPanels = {"Phase Track", "Turn Markers"};
        java.util.List<PanelWidget> panList = GameModule.getGameModule().getAllDescendantComponentsOf(PanelWidget.class);
        for (PanelWidget pw: panList) {
            String pwName = pw.getAttributeValueString("entryName");
            if (Arrays.asList(ovlPanels).contains(pwName)) {
                java.util.List<PieceSlot> psList = pw.getAllDescendantComponentsOf(PieceSlot.class);
                for (PieceSlot ps: psList) {
                    pieceslotgpidlist.add(ps.getGpId());
                }
            }
        }
        String[] ovlScrolls = {"< Turn Tracks", "Turn Markers (by Module)"};
        java.util.List<ListWidget> scrList = GameModule.getGameModule().getAllDescendantComponentsOf(ListWidget.class);
        for (ListWidget pw: scrList) {
            String pwName = pw.getAttributeValueString("entryName");
            if (Arrays.asList(ovlScrolls).contains(pwName)) {
                java.util.List<PieceSlot> psList = pw.getAllDescendantComponentsOf(PieceSlot.class);
                for (PieceSlot ps: psList) {
                    pieceslotgpidlist.add(ps.getGpId());
                }
            }
        }
    }

    private void createDeluxeBoardsList() {
        dxAvailBoards = VASL.build.module.map.BoardDataReader.getDeluxeBoardNamesList();
    }

    public double PieceScalerBoardZoom(GamePiece gp) {
        //Test if piece is an overlay or otherwise should scale with the board
        boolean keepAtBoardScale = true;
        if (gp instanceof Stack) {
            Stack s = (Stack) gp;
            for (int i = s.getPieceCount(); i > 0; i--) { //Top down in the stack
                GamePiece sgp = s.getPieceAt(i - 1);
                if (sgp.getProperty(Properties.PIECE_ID) != null){
                    if (!(pieceslotgpidlist.contains(sgp.getProperty(Properties.PIECE_ID).toString()) || (sgp.getProperty(SCALEWITHBOARDZOOM) != null))) {
                        keepAtBoardScale = false;
                    }
                } else {
                    keepAtBoardScale = false;
                }
            }
        }
        else {
            if (gp.getProperty(Properties.PIECE_ID) != null) {
                if (!(pieceslotgpidlist.contains(gp.getProperty(Properties.PIECE_ID).toString()) || (gp.getProperty(SCALEWITHBOARDZOOM) != null))) {
                    keepAtBoardScale = false;
                }
            } else {
                keepAtBoardScale = false;
            }
        }
        return (keepAtBoardScale? 1.0D : 1.0D/getbZoom())*PieceScalerBoardMag(gp);
    }

    public double PieceScalerBoardMag(GamePiece gp) {
        //Look for pieces that should get additional zoom due to board magnification level
        double mag = 1.0;
        for (Board b: getBoards()) {
            mag = b.getMagnification();
        }
        boolean deluxe = false;
        for (Board b: getBoards()) {
            String bdName = b.getName();
            deluxe = dxAvailBoards.contains(bdName);
        }
        if (deluxe) {mag = mag*3.0;}

        double magZoom = mag;
        if (gp instanceof Stack) {
            Stack s = (Stack) gp;
            for (int i = s.getPieceCount(); i > 0; i--) { //Top down in the stack
                GamePiece sgp = s.getPieceAt(i - 1);
                if (!((sgp.getProperty(SCALEWITHBOARDMAG) != null) && (mag != 1.0))) {
                    magZoom = 1.0;
                }
            }
        }
        else {
            if (!((gp.getProperty(SCALEWITHBOARDMAG) != null) && (mag != 1.0))) {
                magZoom = 1.0;
            }
        }
        return magZoom;
    }
  }
