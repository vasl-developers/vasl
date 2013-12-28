/*
 * $Id: VASLBoard.java 8947 2013-11-21 15:49:18Z davidsullivan1 $
 *
 * Copyright (c) 2013 by David Sullivan
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

package VASL.build.module.map.boardPicker;

import VASL.LOS.Map.Terrain;
import VASL.build.module.ASLMap;
import VASL.build.module.map.boardArchive.BoardArchive;
import VASSAL.build.module.map.boardPicker.board.HexGrid;

import java.awt.*;
import java.io.File;
import java.io.IOException;
import java.util.HashMap;

/**
 * Extends ASLBoard to add support for version 6+ boards
 */
public class VASLBoard extends ASLBoard {


    private boolean legacyBoard = true; // is the board legacy (i.e. V5) format?
    private BoardArchive VASLBoardArchive;

    /**
     * @return true if the board is already set
     */
    public boolean isFlipped() {
        return flipped;
    }

    /**
     * Mark board as having been flipped. This is a hack to prevent boards from being flipped multiple time
     * @param flipped
     */
    public void setFlipped(boolean flipped) {
        this.flipped = flipped;
    }

    private boolean flipped = false; // prevents the same board from being flipped multiple times

    public VASLBoard(){

        super();
    }

    /**
     * Sets the board archive and all archive variables
     * @param s the image file name - not used
     * @param f the archive file
     */
    //TODO: refactor the board picker so there's a better way to initialize the board and instantiate the archive interface
    public void setBaseImageFileName(String s, File f) {

        //TODO: eliminate the need to call the super class method
        super.setBaseImageFileName(s,f);

        // set the board archive
        try {
            VASLBoardArchive = new BoardArchive(f.getName(), f.getParent(), ASLMap.getSharedBoardMetadata());
            legacyBoard = false;

        } catch (IOException e) {

            // must be a legacy board
            legacyBoard = true;
        }
    }

    /**
     * @return true if this board is legacy format (pre 6.0)
     */
    public boolean isLegacyBoard() {
        return legacyBoard;
    }

    /**
     * Is the board cropped?
     */
    public boolean isCropped() {
        Rectangle croppedBounds = getCropBounds();
        return !(croppedBounds.x == 0 && croppedBounds.y == 0 && croppedBounds.width == -1 && croppedBounds.height == -1);
    }


    /**
     * Crops the LOS data
     * @param losData
     */
    public VASL.LOS.Map.Map cropLOSData(VASL.LOS.Map.Map losData) {
        if(!isCropped()) {
            return null;
        }
        else {

            Rectangle bounds = new Rectangle(getCropBounds());
            if(bounds.width == -1) {
                bounds.width = getUncroppedSize().width;
            }
            if(bounds.height == -1) {
                bounds.height = getUncroppedSize().height;
            }
            return losData.crop(new Point(bounds.x, bounds.y), new Point(bounds.x + bounds.width, bounds.y + bounds.height));
        }
    }

    /**
     * @return a rectangle defining the board's location within the map
     */
    public Rectangle getBoardLocation() {

        // the easiest way to do this is to use the boundary rectangle and remove the edge buffer
        Rectangle rectangle = new Rectangle(this.boundaries);
        rectangle.translate(-1 * map.getEdgeBuffer().width, -1 * map.getEdgeBuffer().height);
        return rectangle;
    }

    /**
     * @return the LOS data
     * @param terrainTypes the terrain types
     */
    public VASL.LOS.Map.Map getLOSData(HashMap<String, Terrain> terrainTypes){
        return VASLBoardArchive.getLOSData(terrainTypes);
    }

    /**
     * Set the information formerly in the data file
     */
    //TODO: deprecate when pre-6.0 boards are no longer supported
    @Override
    public void readData(){

        if (isLegacyBoard()) {
            super.readData();
        }
        else {

            version = VASLBoardArchive.getVersion();
            if(VASLBoardArchive.getA1CenterX() != VASLBoardArchive.missingValue()){
                ((HexGrid) getGrid()).setAttribute(HexGrid.X0, VASLBoardArchive.getA1CenterX());
            }
            if(VASLBoardArchive.getA1CenterY() != VASLBoardArchive.missingValue()){
                ((HexGrid) getGrid()).setAttribute(HexGrid.Y0, VASLBoardArchive.getA1CenterY());
            }
            if(VASLBoardArchive.getHexWidth() != VASLBoardArchive.missingValue()){
                ((HexGrid) getGrid()).setAttribute(HexGrid.DX, VASLBoardArchive.getHexWidth());
            }
            if(VASLBoardArchive.getHexHeight() != VASLBoardArchive.missingValue()){
                ((HexGrid) getGrid()).setAttribute(HexGrid.DY, VASLBoardArchive.getHexHeight());
            }
        }

    }

    public String getName() {
        if(isLegacyBoard()) {
            return super.getName();
        }
        else {
            return VASLBoardArchive.getBoardName();
        }
    }

}
