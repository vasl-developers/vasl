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

import java.awt.Color;
import java.awt.Insets;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.IOException;
import java.io.InputStream;
import java.util.Collection;
import java.util.Enumeration;
import java.util.LinkedList;

import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JMenuItem;
import javax.swing.JPopupMenu;

import VASL.build.module.map.boardArchive.BoardArchive;
import VASL.build.module.map.boardArchive.SharedBoardMetadata;
import VASL.build.module.map.boardPicker.BoardException;
import VASL.build.module.map.boardPicker.VASLBoard;
import VASSAL.build.GameModule;
import VASSAL.build.module.Map;
import VASSAL.build.module.map.boardPicker.Board;
import VASSAL.tools.DataArchive;
import VASSAL.tools.ErrorDialog;
import VASSAL.tools.imageop.Op;
import VASSAL.tools.io.IOUtils;
import org.jdom2.JDOMException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ASLMap extends Map {

    // FredKors popup menu
    private JPopupMenu m_mnuMainPopup = null;

    // board metadata
    //TODO: the shared metadata is static so it doesn't have to be passed around. Is there a better way?
    private VASL.LOS.Map.Map VASLMap;
    private static SharedBoardMetadata sharedBoardMetadata = null;
    private boolean legacyMode;                     // true if unable to create a VASL map or LOS data is missing
    private boolean unsupportedFeature = false;     // true if one or more boards in legacy format, overlays not supported, etc

    // used to log errors in the VASSAL error log
    private static final Logger logger = LoggerFactory.getLogger(ASLMap.class);

  public ASLMap() {

      super();
      try {
          readSharedBoardMetadata();
      } catch (JDOMException e) {

          // give up if there's any problem reading the shared metadata file
          ErrorDialog.bug(e);
      }
      m_mnuMainPopup = new JPopupMenu();

      // FredKors: creation of the toolbar button
      // that opens the popup menu
      final JButton l_Menu = new JButton();

    try
    {
        l_Menu.setIcon(new ImageIcon(Op.load("QC/menu.png").getImage(null)));
    }
    catch (Exception ex) 
    {
        ex.printStackTrace();
    }
    
    l_Menu.setMargin(new Insets(0, 0, 0, 0));
    l_Menu.setAlignmentY(0.0F);
    
    l_Menu.addActionListener(new ActionListener() 
    {
        public void actionPerformed(ActionEvent evt) 
        {
            if (evt.getSource() instanceof JButton)
                m_mnuMainPopup.show((JButton)evt.getSource(), 0, 0);
        }
    });

    // add the first element to the popupp menu
    final JMenuItem l_SelectItem = new JMenuItem("Select");
    l_SelectItem.setBackground(new Color(255,255,255));
    m_mnuMainPopup.add(l_SelectItem);
    m_mnuMainPopup.addSeparator();    

    // add the menu button to the toolbar
    getToolBar().add(l_Menu); 
    getToolBar().addSeparator();
}
  
  /*
   *  Work-around for VASL board being 1 pixel too large causing double stacks to form along board edges.
   *  Any snap to a board top or left edge half hex, bump it 1 pixel up or left on to the next board.
   *  
   *  */
  public Point snapTo(Point p) 
  { // FredKors 22/12/2013
    final Point l_pSnapTo = super.snapTo(p);
    Point l_pShiftedXY, l_pShiftedY, l_pShiftedX;
    
    l_pShiftedXY = new Point (l_pSnapTo);

    l_pShiftedXY.x -= 3;
    l_pShiftedXY.y -= 3; // I move the snap point 3 pixel up and left: if the map changes, the snapTo could return a different point, otherwise nothing changes
    l_pShiftedXY = super.snapTo(l_pShiftedXY);
    
    if (findBoard(l_pShiftedXY) != null) //  Return to the snapTo point if I moved off the top border or the left border
        return l_pShiftedXY;
    
    l_pShiftedY = new Point (l_pSnapTo);
    
    l_pShiftedY.y -= 3; // I move the snap point 3 pixel up: if the map changes, the snapTo could return a different point, otherwise nothing changes
    l_pShiftedY = super.snapTo(l_pShiftedY);
    
    if (findBoard(l_pShiftedY) == null) // I moved off the top border, return to the snapTo point
        l_pShiftedY.y = l_pSnapTo.y;
    
    l_pShiftedX = new Point (l_pShiftedY);
    
    l_pShiftedX.x -= 3;// I move the snap point 3 pixel left: if the map changes, the snapTo could return a different point, otherwise nothing changes
    l_pShiftedX = super.snapTo(l_pShiftedX);
    
    if (findBoard(l_pShiftedX) == null) // I moved off the left border
        return l_pShiftedY;
    
    return l_pShiftedX;        
    
  }
  // return the popup menu
  public JPopupMenu getPopupMenu()
  {
      return m_mnuMainPopup;
  }

    @Override
    public synchronized void setBoards(Collection<Board> c) {

        super.setBoards(c);
        buildVASLMap();
    }

    /**
     * read the shared board metadata from the game archive
     */
    private void readSharedBoardMetadata() throws JDOMException {

        // read the shared board metadata
        InputStream metadata = null;
        try {
            final DataArchive archive = GameModule.getGameModule().getDataArchive();

            metadata =  archive.getInputStream(BoardArchive.getSharedBoardMetadataFileName());

            sharedBoardMetadata = new SharedBoardMetadata();
            sharedBoardMetadata.parseSharedBoardMetadataFile(metadata);

        // give up on any errors
        } catch (IOException e) {
            sharedBoardMetadata = null;
            throw new JDOMException("Cannot read the shared metadata file", e);
        } catch (JDOMException e) {
            sharedBoardMetadata = null;
            throw new JDOMException("Cannot read the shared metadata file", e);
        } catch (NullPointerException e) {
            sharedBoardMetadata = null;
            throw new JDOMException("Cannot read the shared metadata file", e);
        }
        finally {
            IOUtils.closeQuietly(metadata);
        }
    }

    /**
     * Builds the VASL map
     */
    protected void buildVASLMap() {

        // create an empty VASL map of the correct size
        LinkedList<VASLBoard> VASLBoards = new LinkedList<VASLBoard>(); // keep the boards so they are instantiated once
        try {

            // see if there are any legacy boards in the board set
            // and determine the size of the map
            unsupportedFeature = false;
            legacyMode = false;
            final Rectangle mapBoundary = new Rectangle(0,0);
			double hexHeight = 0.0;
            double hexWidth = 0.0;
            for(Board b: boards) {
                final VASLBoard board = (VASLBoard) b;
                mapBoundary.add(b.bounds());
                VASLBoards.add(board);

				// make sure the hex geometry of all boards is the same
				if (hexHeight != 0.0 && board.getHexHeight() != hexHeight || hexWidth != 0.0 && board.getHexWidth() != hexWidth) {
					throw new BoardException("Map configuration contains multiple hex sizes - disabling LOS");
				}
				hexHeight = board.getHexHeight();
                hexWidth = board.getHexWidth();
            }

            // remove the edge buffer from the map boundary size
            mapBoundary.width -= edgeBuffer.width;
            mapBoundary.height -= edgeBuffer.height;

            // create the VASL map
            VASLBoard b = VASLBoards.get(0); // we can use the geometry of any board - assuming all are the same
            VASLMap = new VASL.LOS.Map.Map(
                    (int) Math.round(mapBoundary.width/ b.getHexWidth()) + 1,
                    (int) Math.round(mapBoundary.height/ b.getHexHeight()),
                    b.getA1CenterX(),
                    b.getA1CenterY(),
                    mapBoundary.width,
                    mapBoundary.height,
                    sharedBoardMetadata.getTerrainTypes());
        }

        // clean up and fall back to legacy mode if an unexpected exception is thrown
        catch (Exception e) {

            legacyMode = true;
            VASLMap = null;
            VASLBoards = null;
            logError("LOS disabled - unexpected error");
            logException(e);
        }

        // add the boards to the VASL map
        try {

            // load the LOS data
            if(!(legacyMode)) {

                for (VASLBoard board : VASLBoards) {

                    // read the LOS data and flip/crop the board if needed
                    VASL.LOS.Map.Map LOSData = board.getLOSData(sharedBoardMetadata.getTerrainTypes());

                    // check for overlays
                    final Enumeration overlays = board.getOverlays();
                    while (overlays.hasMoreElements()) {
                        if(!overlays.nextElement().toString().isEmpty()) {
                            throw new BoardException("Overlays are not supported - disabling LOS");
                        }
                    }

                    // apply the SSR changes, crop and flip if needed
					board.applyColorSSRules(LOSData, sharedBoardMetadata.getLOSSSRules());

                    if(board.isCropped()) {
                        LOSData = board.cropLOSData(LOSData);
                    }

                    if(board.isReversed()){
                        LOSData.flip();
                    }

                    // add the board LOS data to the map
                    if (!VASLMap.insertMap(
                            LOSData,
                            VASLMap.gridToHex(board.getBoardLocation().x, board.getBoardLocation().y))) {

                        // didn't work, so assume an unsupported feature
                        throw  new BoardException("Unable to insert board " + board.getName() + " into the VASL map - LOS disabled");
                    }
                }
            }
        }
        // assume we're using an unsupported feature if an exception is thrown
        catch (BoardException e) {

            logError(e.toString());
            unsupportedFeature = true;
        }
        catch (Exception e) {

            logError("LOS disabled - unexpected error");
            logException(e);
            unsupportedFeature = true;
        }
        finally {
            // free up memory
            VASLBoards = null;
        }
    }

    /**
     * @return the VASL map
     */
    public VASL.LOS.Map.Map getVASLMap(){
        return VASLMap;
    }

    /**
     * @return the shared board metadata
     */
    public static SharedBoardMetadata getSharedBoardMetadata() {
        return sharedBoardMetadata;
    }

    /**
     * @return true if the map is in legacy mode (i.e. pre-6.0)
     */
    public boolean isLegacyMode(){
        return legacyMode || unsupportedFeature;
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
}