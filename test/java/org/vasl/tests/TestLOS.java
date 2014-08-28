/*
 * $Id: TestLOS 3/3/14 davidsullivan1 $
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
package org.vasl.tests;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;

import VASL.LOS.LOSDataEditor;
import VASL.LOS.Map.LOSResult;
import VASL.LOS.Map.Location;
import VASL.LOS.Map.Map;
import VASL.build.module.map.boardArchive.BoardArchive;
import VASL.build.module.map.boardArchive.SharedBoardMetadata;
import VASSAL.tools.DataArchive;
import VASSAL.tools.io.IOUtils;
import org.jdom2.JDOMException;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Tests for the LOS engine
 */
public class TestLOS {
    private static final Logger logger = LoggerFactory.getLogger(TestLOS.class);

    // board directory constants
    final private static String boardDirectory = "C:\\Users\\usulld2\\Documents\\ASL\\VASL\\boards";
    final private static String VASLModule = "C:\\Users\\usulld2\\IdeaProjects\\VASL\\target\\vasl-6.2.0.jar";

    SharedBoardMetadata sharedBoardMetadata;

    @Test
    /**
     * This test compares the results between the old LOS method and the refactored one
     * It spins through all boards and documents LOS differences for investigation
     */
    public void noLOSExceptionsInAllBoards() {

        // read the shared board metadata
        try {
            readSharedBoardMetadata();

        } catch (JDOMException e) {

            // give up if there's any problem reading the shared metadata file
            logger.error("Cannot read the shared board metadata file from " + VASLModule, e);
            return;
        }

        // get the list of boards in the board directory - give up if there are none
        File folder = new File(boardDirectory);
        File[] listOfFiles = folder.listFiles();
        if(listOfFiles == null) {
            return;
        }

        // loop through all boards
        for (File listOfFile : listOfFiles) {

            if (listOfFile.isFile()) {

                String fileName = listOfFile.getName();
                logger.info("Checking all LOS on board: " + fileName);

                try {

                    try {

                        // open the board
                        LOSDataEditor losDataEditor = new LOSDataEditor(
                                fileName,
                                listOfFile.getParent(),
                                sharedBoardMetadata);

                        losDataEditor.readLOSData();

                        // check the LOS on the board
                        try {

                            checkAllLOS(losDataEditor.getMap());

                        } catch (Exception e) {
                            logger.error("LOS error on board " + fileName, e);
                            return;
                        }

                    } catch (IOException e) {

                        logger.error("Cannot open the board archive: " + fileName, e);
                    }


                } catch (Exception e) {

                    logger.error("Unable to read the shared board metadata from the LOS archive", e);
                }
            }
        }
    }

    /**
     * read the shared board metadata
     * Note - this method stolen from ASLMap
     */
    private void readSharedBoardMetadata() throws JDOMException {

        // read the shared board metadata
        InputStream metadata = null;
        try {
            DataArchive archive = new DataArchive(VASLModule);

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

    public void checkAllLOS(Map map) {

        int width = map.getWidth();
        int height = map.getHeight();

        LOSResult result = new LOSResult();

        // check all LOS on the board
        for (int col = 0; col < width; col++) {
            for (int row = 0; row < height + (col % 2); row++) {
                for(int loc = 0; loc <= 6; loc++) {

                    Location l2;
                    if(loc == 6) {
                        l2 = map.getHex(col, row).getCenterLocation();
                    }
                    else {
                        l2 = map.getHex(col, row).getHexsideLocation(loc);
                    }

                    // all upper level locations
                    for(Location l3 = l2; l3 != null; l3 = l3.getUpLocation()) {
                        for (int col2 = 0; col2 < width && map.onMap(l3.getLOSPoint().x, l3.getLOSPoint().y); col2++) {
                            for (int row2 = 0; row2 < height + (col2 % 2); row2++) {

                                result.reset();
                                map.LOS(l3, false, map.getHex(col2, row2).getCenterLocation(), false, result, null);
                            }
                        }
                    }
                }
            }
        }
    }
}


