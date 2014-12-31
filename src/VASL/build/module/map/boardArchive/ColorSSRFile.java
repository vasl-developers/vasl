package VASL.build.module.map.boardArchive;
/*
 * Copyright (c) 2015 by David Sullivan on 12/31/2014.
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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.util.LinkedHashMap;
import java.util.Scanner;

public class ColorSSRFile {

    // Maps rule name to the rule object
    protected LinkedHashMap<String, ColorSSRule> colorSSRules = new LinkedHashMap<String, ColorSSRule>(100);

    public ColorSSRFile(InputStream overlaySSRFile, String archiveName) throws IOException {

        Logger logger = LoggerFactory.getLogger(OverlaySSRFile.class);

        // open the overlay SSR file and set up the text scanner
        Scanner scanner = new Scanner(overlaySSRFile).useDelimiter("\n");

        // read each line
        String ruleName;
        ColorSSRule colorSSRule = null;
        String line = scanner.next();;

        while (scanner.hasNext()){

            // skip empty lines and comments (//)
            if(line.length() > 1 && !line.startsWith("//")){

                try {
                    // set up the new rule - remove comments if necessary
                    if(line.indexOf("//") > 0) {
                        line = line.substring(0, line.indexOf("//"));
                    }
                    ruleName = line.trim();
                    line = scanner.next();
                    colorSSRule = new ColorSSRule();

                    if(ruleName.equals("ETOtoDTO")) {
                        System.out.println();
                    }

                    // mapping lines start with two spaces
                    while (scanner.hasNext() && ((line.startsWith("  ") && line.length() > 2) || line.length() < 2)) {

                        if(line.length() > 2) {

                            String tokens[] = line.split("=");
                            if(tokens.length == 2){
                                colorSSRule.addColorMap(tokens[0].trim(), tokens[1].trim());
                            }
                        }

                        if(scanner.hasNext()){
                            line = scanner.next();
                        }
                    }

                    if(ruleName != null && ruleName.length() > 0 && colorSSRule.getColorMaps().size() > 0){
                        colorSSRules.put(ruleName, colorSSRule);
                    }

                } catch (Exception e) {

                    logger.warn("Invalid color SSR value ignored in colorSSR file " + archiveName);
                    logger.warn(line);
                }
            }
            else {
                if(scanner.hasNext()){
                    line = scanner.next();
                }
            }
        }

        // clean up
        scanner.close();
        overlaySSRFile.close();
    }

    public LinkedHashMap<String, ColorSSRule> getColorSSRules() {return colorSSRules;}
}
