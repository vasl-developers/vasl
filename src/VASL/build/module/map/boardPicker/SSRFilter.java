/*
 * $Id$
 *
 * Copyright (c) 2000-2003 by Rodney Kinney
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

import VASL.build.module.ASLMap;
import VASL.build.module.map.boardArchive.*;
import VASSAL.tools.DataArchive;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.awt.image.RGBImageFilter;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.*;
import java.util.List;
import java.util.Map.Entry;

/**
 * A class to swap colors according to specified rules * A set of color names is read from an input file with the
 * format * White 255,255,255 * Black 0,0,0 * * The swapping rules are read from an input file with the format: *
 * <key> * <color>=<color> * <color>=<color> * <color>=<color> * There can be any number of color entries per key. *
 * The color entries are names of colors as defined in the color file * Example: * * WoodsToBrush * WoodsGreen=BrushL0 *
 * WoodsBlack=BrushL0
 */
public class SSRFilter extends RGBImageFilter {

    private String saveRules;
    private File archiveFile;
    private DataArchive archive;
    private ASLBoard board;

    private BoardArchive boardArchive;
    private Map<Integer, Integer> mappings;
    private List<SSROverlay> overlays;

    Logger logger = LoggerFactory.getLogger(SSRFilter.class);

    public SSRFilter(String listOfRules, File archiveFile, ASLBoard board) throws BoardException {
        canFilterIndexColorModel = true;
        saveRules = listOfRules;
        this.archiveFile = archiveFile;

        try {
            archive = new DataArchive(archiveFile.getPath());
            if (!archive.contains("data")) {
                throw new FileNotFoundException("data");
            }

            boardArchive = new BoardArchive(archiveFile.getName(), archiveFile.getParent(), ASLMap.getSharedBoardMetadata());

        } catch (IOException ex) {
            throw new BoardException("Board does not support terrain alterations");
        }

        this.board = board;
        loadRules();
    }

    public Iterable<SSROverlay> getOverlays() {
        return overlays;
    }

    /**
     * We ignore the pixel location and just map the color per SSR rules
     * {@inheritDoc}
     */
    @Override
    public int filterRGB(int x, int y, int rgb) {
        return ((0xff000000 & rgb) | mapColor(rgb & 0xffffff));
    }

    /**
     * Maps the color to it's transformed value by going through the rules. All rules are applied in sequence.
     * @param rgb int value of RGB color
     * @return int value of transformed color
     */
    private int mapColor(int rgb) {
        int rval = rgb;
        Integer mappedValue = mappings.get(rgb);
        if (mappedValue != null) {
            rval = mappedValue;
        }
        return rval;
    }

    public String toString() {
        return saveRules;
    }

    private int colorToInt(Color color) {
        return (color.getRed() << 16) + (color.getGreen() << 8) + color.getBlue();
    }

    /**
     * Load all color/SSR rules for the board
     */
    private void loadRules() {

        Map<String, Integer> colorValues = new HashMap<String, Integer>();
        mappings = new HashMap<Integer, Integer>();
        overlays = new Vector<SSROverlay>();

        // Build the list of rules in use
        Vector<String> rules = new Vector<String>();
        StringTokenizer st = new StringTokenizer(saveRules);
        while (st.hasMoreTokens()) {
            String s = st.nextToken();
            if (!rules.contains(s)) {
                rules.addElement(s);
            }
        }

        // load the color mappings
        for (BoardColor boardColor : boardArchive.getBoardColors().values()) {
            colorValues.put(boardColor.getVASLColorName(), colorToInt(boardColor.getColor()));
        }

        // map colors
        for (Map.Entry<String, ColorSSRule> entry : boardArchive.getColorSSRules().entrySet()) {

            String ruleName = entry.getKey();

            if (rules.contains(ruleName)) {

                ColorSSRule colorSSRule = boardArchive.getColorSSRules().get(ruleName);

                for (Map.Entry<String, String> entry1 : colorSSRule.getColorMaps().entrySet()) {

                    int fromColor = 0;
                    int toColor = 0;
                    try {
                        fromColor = colorValues.get(entry1.getKey());
                        toColor = colorValues.get(entry1.getValue());

                        if (fromColor >= 0 && toColor >= 0 && fromColor != toColor) {

                            if (!mappings.containsKey(fromColor)) {
                                mappings.put(fromColor, toColor);
                            }

                            // Also apply this mapping to previous mappings
                            if (mappings.containsValue(fromColor)) {

                                for (Entry<Integer, Integer> e : mappings.entrySet()) {
                                    if (e.getValue() == fromColor)
                                        e.setValue(toColor);
                                }
                            }
                        }
                    } catch (Exception e) {
                        if (colorValues.get(entry1.getKey()) == null){
                            logger.warn("Board " + board.getName() + " missing color entry in color SSR mapping: " + entry1.getKey());
                        }
                        if (colorValues.get(entry1.getValue()) == null){
                            logger.warn("Board " + board.getName() + " missing color entry in color SSR mapping: " + entry1.getValue());
                        }
                    }
                }
            }
        }

        // load the overlay SSR
        for(Map.Entry<String, OverlaySSRule> entry: boardArchive.getOverlaySSRules().entrySet()) {

            try {
                if(rules.contains(entry.getKey())) {

                    OverlaySSRule rule = boardArchive.getOverlaySSRules().get(entry.getKey());
                    for(Map.Entry<String, OverlaySSRuleImage> image: rule.getImages().entrySet()) {

                        overlays.add(new SSROverlay(image.getValue(), archiveFile));
                    }
                    rules.remove(entry.getKey());
                }
            }
            catch (IllegalArgumentException e) {
                logger.warn("Invalid Overlay SSR: " + entry.getKey() + " - " + e.getMessage());
            }
        }

        // load the underlay SSR
        for(Map.Entry<String, UnderlaySSRule> entry: boardArchive.getUnderlaySSRules().entrySet()) {

            UnderlaySSRule rule = entry.getValue();

            try {

                if(rules.contains(entry.getKey())) {

                    // create the array of transparency colors
                    int[] colors = new int[rule.getColors().size()];
                    int current = 0;
                    for (String color: rule.getColors()) {
                        colors[current] = colorValues.get(color);
                        current++;
                    }
                     overlays.add(new Underlay(rule.getImageName(), colors, archive, board));
                    rules.remove(entry.getKey());
                }
            }
            catch (Exception e) {
                logger.warn("Invalid Underlay SSR: " + entry.getKey() + " - " + e.getMessage());
            }
        }
    }

    public void transform(BufferedImage image) {
        if (!mappings.isEmpty()) {
            final int h = image.getHeight();
            final int[] row = new int[image.getWidth()];
            for (int y = 0; y < h; ++y) {
                image.getRGB(0, y, row.length, 1, row, 0, row.length);
                for (int x = 0; x < row.length; ++x) {
                    row[x] = filterRGB(x, y, row[x]);
                }
                image.setRGB(0, y, row.length, 1, row, 0, row.length);
            }
        }
    }

    @Override
    public boolean equals(Object obj) {
        return obj instanceof SSRFilter && saveRules.equals(((SSRFilter) obj).saveRules);
    }

    @Override
    public int hashCode() {
        return saveRules.hashCode();
    }
}
