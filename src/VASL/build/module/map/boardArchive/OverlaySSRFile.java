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

import java.io.*;
import java.util.*;

/**
 * A class that accesses the legacy "overlaySSR" file in the board archive
 * Note that bad lines are ignore and no error is thrown
 */
public class OverlaySSRFile {

    // Maps SSR name to the overlay rule object
    private LinkedHashMap<String, OverlaySSRule> overlaySSRules = new LinkedHashMap<String, OverlaySSRule>();

    // Maps SSR name to the underlay rule object
    private LinkedHashMap<String, UnderlaySSRule> underlaySSRules = new LinkedHashMap<String, UnderlaySSRule>();

    /**
     * @param overlaySSRFile an input stream to the overlaySSR file in the board archive
     * @throws IOException
     */
    public OverlaySSRFile(InputStream overlaySSRFile, String archiveName) throws IOException {

        Logger logger = LoggerFactory.getLogger(OverlaySSRFile.class);

        // open the overlay SSR file and set up the text scanner
        Scanner scanner = new Scanner(overlaySSRFile).useDelimiter("\n");
        final String COMMENT_CHARS = "//";

        // read each line
        String ruleName;
        String nextLine;
        while (scanner.hasNext()){

            String line = scanner.next();

            // skip empty lines and comments (//)
            if(line.length() > 1 && !line.startsWith(COMMENT_CHARS)){

                // remove end-of-line comments
                if(line.contains(COMMENT_CHARS)) {
                    line = line.substring(0, line.indexOf(COMMENT_CHARS) - 1);
                }

                // set up the new rule
                ruleName = line.trim();
                nextLine = scanner.next();

                // line is an overlay
                if(nextLine.startsWith("underlay")) {

                    try {
                        String tokens[] = nextLine.split("[, ]+");

                        String imageName = tokens[1].trim();

                        ArrayList<String> colors = new ArrayList<String>(1);
                        for(int x = 2; x < tokens.length; x++) {

                            colors.add(tokens[x].trim());
                        }

                        underlaySSRules.put(
                                ruleName,
                                new UnderlaySSRule(
                                        ruleName,
                                        imageName,
                                        colors));
                    } catch (Exception e) {
                        logger.warn("Invalid overlay value ignored in overlaySSR file " + archiveName);
                        logger.warn(nextLine);
                    }
                }

                // line is a overlay
                else  {

                    try {

                        OverlaySSRule rule = new OverlaySSRule(ruleName);

                        // overlays can have multiple images
                        boolean moreLines = true;
                        while (moreLines){

                            String imageName = nextLine.substring(0, nextLine.indexOf(" ")).trim();
                            nextLine = nextLine.substring(nextLine.indexOf(" "));
                            String x = nextLine.substring(0, nextLine.indexOf(",")).trim();
                            String y = nextLine.substring(nextLine.indexOf(",") + 1).trim();

                            rule.addImage(new OverlaySSRuleImage(
                                    imageName,
                                    Integer.parseInt(x),
                                    Integer.parseInt(y)));

                            // no more overlay images if the next line is blank
                            if(scanner.hasNext()) {
                                nextLine = scanner.next();
                                if(nextLine.trim().isEmpty()) {
                                    moreLines = false;
                                }
                            }
                            else {
                                moreLines = false;
                            }
                        }

                        overlaySSRules.put(rule.getName(), rule);

                    } catch (Exception e) {
                        logger.warn("Invalid underlay value ignored in overlaySSR file " + archiveName);
                        logger.warn(nextLine);
                    }
                }
            }
        }

        // clean up
        scanner.close();
        overlaySSRFile.close();
    }

    public LinkedHashMap<String, OverlaySSRule> getOverlaySSRules(){return overlaySSRules;}

    public LinkedHashMap<String, UnderlaySSRule> getUnderlaySSRules(){return underlaySSRules;}
}
