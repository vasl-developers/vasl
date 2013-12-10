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

import VASL.build.module.map.boardArchive.BoardArchive;
import VASL.build.module.map.boardArchive.SharedBoardMetadata;
import VASL.build.module.map.boardPicker.ASLBoard;
import VASL.build.module.map.boardPicker.VASLBoard;
import VASSAL.build.GameModule;
import VASSAL.build.module.Map;
import VASSAL.build.module.map.boardPicker.Board;
import VASSAL.tools.DataArchive;
import VASSAL.tools.imageop.Op;
import VASSAL.tools.io.IOUtils;
import org.jdom2.JDOMException;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.IOException;
import java.io.InputStream;
import java.util.Collection;
import java.util.LinkedList;

public class ASLMap extends Map {

    // FredKors popup menu
    private JPopupMenu m_mnuMainPopup = null;

    // board metadata
    //TODO: the shared metadata is static so it doesn't have to be passed around. Is there a better way?
    private VASL.LOS.Map.Map VASLMap;
    private static SharedBoardMetadata sharedBoardMetadata = null;
    private boolean legacyMode;                  // true if unable to create a VASL map
    private boolean unsupportedFeature = false;   // true if one or more boards in legacy format, overlays not supported, etc

  public ASLMap() {

      super();
      readSharedBoardMetadata();
      m_mnuMainPopup = new JPopupMenu();

      // FredKors: creation of the toolbar button
      // that opens the popup menu
      JButton l_Menu = new JButton();

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
    JMenuItem l_SelectItem = new JMenuItem("Select");
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
  public Point snapTo(Point p) {
    final Point p1 = super.snapTo(p);
    final Point p2 = new Point (p1);
    final String loc = locationName(p1);
    
    // Ignore Edge and Corner locations
    if (! loc.contains("/")) {
      // Zero row hexes are all top edge half hexes, bump the snap up 1 pixel to the board above.
      if (loc.endsWith("0") && ! loc.endsWith("10")) {
        p2.y -= 1;
      }
      // Column A hexes are all left edge half hexes, bump the snap left 1 pixel to the board to the left
      else if (loc.contains("A") && ! loc.contains("AA")) {
        p2.x -=1;
      }
    }
    // If the snap has been bumped off map (must be top or right edge of map), use the original snap.
    if (findBoard(p2) == null) {
      return p1;
    }
    return p2;
  }

  // return the popup menu
  public JPopupMenu getPopupMenu()  {
      return m_mnuMainPopup;
  }

    @Override
    public synchronized void setBoards(Collection<Board> c) {

        super.setBoards(c);
        buildVASLMap();
    }

    /**
     * read the shared board metadata
     */
    private void readSharedBoardMetadata(){

        DataArchive archive = GameModule.getGameModule().getDataArchive();

        // read the shared board metadata
        InputStream metadata = null;
        try {
            metadata =  archive.getInputStream(BoardArchive.getSharedBoardMetadataFileName());

            sharedBoardMetadata = new SharedBoardMetadata();
            sharedBoardMetadata.parseSharedBoardMetadataFile(metadata);

        // give up on any errors
        } catch (IOException e) {
            sharedBoardMetadata = null;
        } catch (JDOMException e) {
            sharedBoardMetadata = null;
        }
        finally {
            IOUtils.closeQuietly(metadata);
        }
    }

    // board stuff
    private int mapWidth = 0;		// VASL map dimensions (i.e. number of rows and columns of boards)
    private int mapHeight = 0;
    private ASLBoard upperLeftBoard;

    /**
     * Builds the VASL map
     */
    protected void buildVASLMap() {

        // can't build the map without the metadata so quit
        if(sharedBoardMetadata == null) {
            legacyMode = true;
            return;
        }
        else {
            legacyMode = false;
        }

        LinkedList<VASLBoard> VASLBoards = new LinkedList<VASLBoard>(); // keep the boards so they are instantiated once
        try {

            // see there are any legacy boards in the board set
            unsupportedFeature = false;
            for(Board b: boards) {
                try {
                    VASLBoard board = (VASLBoard) b;
                    VASLBoards.add(board);
                    if(board.isLegacyBoard() || board.getOverlays().hasMoreElements()) {
                        unsupportedFeature = true;
                    }
                }
                catch (Exception e) {
                    unsupportedFeature = true;
                }
            }

            // determine the VASL map dimensions
            for (VASLBoard b : VASLBoards) {
                mapWidth = Math.max(b.relativePosition().x, mapWidth);
                mapHeight = Math.max(b.relativePosition().y, mapHeight);
            }
            mapWidth++;
            mapHeight++;

            // create the VASL map
            //TODO: remove hard-coded hex sizes
            if (mapWidth > 0) {
                VASLMap = new VASL.LOS.Map.Map(
                        mapWidth * (int) Math.round(((ASLBoard) boards.get(0)).getUncroppedSize().getWidth() / 56.25) + 1,
                        mapHeight * (int) Math.round(((ASLBoard) boards.get(0)).getUncroppedSize().getHeight() / 64.5),
                        sharedBoardMetadata.getTerrainTypes());
            }
        }
        // fall back to legacy mode if an unexpected exception is thrown
        catch (Exception e) {

            legacyMode = true;
        }

        try {

            // load the LOS data
            if(!(legacyMode || unsupportedFeature)) {

                for (VASLBoard board : VASLBoards) {

                    // set the upper left board
                    if (board.relativePosition().x == 0 && board.relativePosition().y == 0) {

                        upperLeftBoard = board;
                    }

                    // add to map
                    if (!VASLMap.insertGEOMap(
                            board.getLOSData(sharedBoardMetadata.getTerrainTypes()),
                            //TODO: remove hard-coded hex sizes
                            VASLMap.getHex(
                                    board.relativePosition().x * ((int) (Math.round(board.getUncroppedSize().getWidth() / 56.25))),
                                    board.relativePosition().y * ((int) (Math.round(board.getUncroppedSize().getHeight() / 64.5)))))
                            ) {
                    }
                }
            }
        }
        // assume we're using an unsupported feature if an unexpected exception is thrown
        catch (Exception e) {

            unsupportedFeature = true;
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
}