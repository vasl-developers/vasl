package VASL.build.module.map.boardArchive;/*
 * Copyright (c) 2015 by David Sullivan on 3/2/2015.
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

import VASSAL.build.Builder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Document;
import org.w3c.dom.NodeList;

import java.io.IOException;
import java.io.InputStream;

public class SSRControlsFile {

    private static final String BASIC_ELEMENT_NAME = "Basic";
    private static final String OPTION_ELEMENT_NAME = "Option";

    private NodeList basicNodes;
    private NodeList optionNodes;

    public SSRControlsFile(InputStream SSRControlsFile, String archiveName) throws IOException {

        Logger logger = LoggerFactory.getLogger(SSRControlsFile.class);


        try {

            Document doc = Builder.createDocument(SSRControlsFile);
            if(doc != null) {
                basicNodes = doc.getElementsByTagName(BASIC_ELEMENT_NAME);
                optionNodes = doc.getElementsByTagName(OPTION_ELEMENT_NAME);
            }
        }
        catch (Exception e) {
            logger.warn("Unable to read SSR control file in archive " + archiveName);
            logger.warn(e.getMessage());
        }
    }

    /**
     * @return  a list of the "basic" nodes in the SSR control file
     */
    public NodeList getBasicNodes() {

        return basicNodes;
    }

    /**
     * @return  a list of the "option" nodes in the SSR control file
     */
    public NodeList getOptionNodes() {

        return optionNodes;
    }
}
