/*
 * Copyright (c) 2015 by David Sullivan
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
package VASL.build.module.map.boardArchive;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.awt.*;
import java.io.IOException;
import java.io.InputStream;
import java.util.LinkedHashMap;
import java.util.Scanner;

/**
 * A class that accesses the "colorSSR" file in the board archive
 */
public class ColorsFile {

    // the collection of all colors SSR rules in the file
    private LinkedHashMap<String, BoardColor> colorRules = new LinkedHashMap<String, BoardColor>();

    public ColorsFile(InputStream overlaysFile, String archiveName) throws IOException{

        Logger logger = LoggerFactory.getLogger(OverlaySSRFile.class);

        Scanner scanner = new Scanner(overlaysFile).useDelimiter("\n");

        // read each line
        while (scanner.hasNext()){

            String line = scanner.next();

            // skip empty lines and comments (//)
            if(line.trim().length() > 1 && !line.startsWith("//")){

                try {

                    String tokens[] = line.split("[, ]+");

                    // standard color line
                    if(tokens.length == 4) {

                        String colorName;
                        int red;
                        int green;
                        int blue;
                        colorName = tokens[0].trim();
                        red = Integer.parseInt(tokens[1].trim());
                        green = Integer.parseInt(tokens[2].trim());
                        blue = Integer.parseInt(tokens[3].trim());

                        colorRules.put(colorName, new BoardColor(colorName, new Color(red, green, blue), BoardMetadata.UNKNOWN, BoardMetadata.UNKNOWN));
                    }

                    // two token assumes color is mnemonic - replace with real color
                    else if(tokens.length == 2) {

                        BoardColor color = colorRules.get(tokens[1].trim());
                        if(color != null) {
                            colorRules.put(tokens[0].trim(), color);
                        }
                    }
                }
                catch (Exception e) {
                    logger.warn("Invalid color value ignored in colors file " + archiveName);
                    logger.warn(line);
                }
            }
        }

        // clean up
        scanner.close();
        overlaysFile.close();
    }

    public LinkedHashMap<String, BoardColor> getColorRules(){ return colorRules;}
}
