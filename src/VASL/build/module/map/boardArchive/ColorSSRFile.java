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
import java.util.*;

public class ColorSSRFile {

    private final String COMMENT_CHARS = "//";

    // Maps rule name to the rule object
    protected LinkedHashMap<String, ColorSSRule> colorSSRules = new LinkedHashMap<String, ColorSSRule>(100);

    public ColorSSRFile(InputStream overlaySSRFile, String archiveName) throws IOException {

        Logger logger = LoggerFactory.getLogger(OverlaySSRFile.class);


        // open the overlay SSR file and set up the text scanner
        Scanner scanner = new Scanner(overlaySSRFile).useDelimiter("\n");

        // read each line
        String ruleName;
        ColorSSRule colorSSRule;
        String line = scanner.next();

        while (scanner.hasNext()){

            // skip empty lines and comments (//)
            if(line.length() > 1 && !line.startsWith(COMMENT_CHARS)){

                try {

                    line = removeEOLComments(line);
                    ruleName = line.trim();
                    line = scanner.next();
                    colorSSRule = new ColorSSRule();

                    // mapping lines start with two spaces
                    while (scanner.hasNext() && ((line.startsWith("  ") && line.length() > 2) || line.length() < 2)) {

                        if(line.length() > 2) {

                            line = removeEOLComments(line);
                            String tokens[] = line.split("=");
                            if(tokens.length == 2){
                                colorSSRule.addColorMap(tokens[0].trim(), tokens[1].trim());
                            }
                        }

                        if(scanner.hasNext()){
                            line = scanner.next();
                        }
                    }

                    if(ruleName.length() > 0 && colorSSRule.getColorMaps().size() > 0){
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

    /**
     * Remove end of line comments
     * @param line the line
     * @return a string with comments removed
     */
    private String removeEOLComments(String line) {

        // remove end-of-line comments
        if(line.length() > 1 && line.contains(COMMENT_CHARS)) {
            return line.substring(0, line.indexOf(COMMENT_CHARS) - 1);
        }
        return line;
    }

    /**
     * Prints the color SSR as XML for the metadata file
     */
    @SuppressWarnings("unused")
    public void printAsXML(){

        System.out.println("\t<colorSSRules>");
        for (Map.Entry<String, ColorSSRule> rule : colorSSRules.entrySet()) {


            System.out.println("\t\t<colorSSR name=\"" + rule.getKey() + "\">");

            for(Map.Entry<String, String> mapping: colorSSRules.get(rule.getKey()).getColorMaps().entrySet()){
                System.out.println("\t\t\t<colorMap fromColor=\"" + mapping.getKey() + "\" toColor=\"" + mapping.getValue() + "\"/>");
            }

            System.out.println("\t\t</colorSSR>");
        }

        System.out.println("\t</colorSSRules>");
    }
}
