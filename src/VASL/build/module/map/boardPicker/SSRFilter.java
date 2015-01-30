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
import VASSAL.build.GameModule;
import VASSAL.tools.DataArchive;
import VASSAL.tools.io.IOUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.awt.image.FilteredImageSource;
import java.awt.image.RGBImageFilter;
import java.io.*;
import java.util.*;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.StringTokenizer;
import java.util.Vector;
import java.util.Map.Entry;

public class SSRFilter extends RGBImageFilter {
    /*
     * * A class to swap colors according to specified rules * A set of color names is read from an input file with the
     * format * White 255,255,255 * Black 0,0,0 * * The swapping rules are read from an input file with the format: *
     * <key> * <color>=<color> * <color>=<color> * <color>=<color> * There can be any number of color entries per key. *
     * The color entries are names of colors as defined in the color file * Example: * * WoodsToBrush * WoodsGreen=BrushL0 *
     * WoodsBlack=BrushL0
     */
//    private Map<Integer, Integer> mappings_old;
//    private Map<String, Integer> colorValues_old;
//    private List<SSROverlay> overlays;
    private String saveRules;
    private File archiveFile;
    private DataArchive archive;
    private ASLBoard board;

    private BoardArchive boardArchive;
    private Map<Integer, Integer> mappings;
    private Map<String, Integer> colorValues;
    private List<SSROverlay> _overlays;

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
        initialize();
        // readAllRules();
    }

    public Iterable<SSROverlay> getOverlays() {
        return _overlays;
    }

    public int filterRGB(int x, int y, int rgb) {
        return ((0xff000000 & rgb) | newValue(rgb & 0xffffff));
    }

    private int newValue(int rgb) {
    /*
     * * Maps the color to it's transformed value by going through * the rules. All rules are applied in sequence.
     */
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
    
    return rules;
  }

/*
    private int parseRGB(String s) {

     // Calculate integer value from rr,gg,bb or 40a38f format
        int rval = -1;
        try {
            Integer test = (Integer) colorValues_old.get(s);
            if (test != null) {
                rval = test.intValue();
            } else if (s.indexOf(',') >= 0) {
                StringTokenizer st = new StringTokenizer(s, ",");
                if (st.countTokens() == 3) {
                    int red, green, blue;
                    red = Integer.parseInt(st.nextToken());
                    green = Integer.parseInt(st.nextToken());
                    blue = Integer.parseInt(st.nextToken());
                    if ((red >= 0 && red <= 255) && (green >= 0 && green <= 255) && (blue >= 0 && blue <= 255)) {
                        rval = (red << 16) + (green << 8) + blue;
                    }
                }
            } else if (s.length() == 6) {
                rval = Integer.parseInt(s, 16);
            }
        } catch (Exception e) {
            rval = -1;
        }
        return rval;
    }

    public void readAllRules() {
        // Build the list of rules in use
        Vector rules = new Vector();
        StringTokenizer st = new StringTokenizer(saveRules);
        while (st.hasMoreTokens()) {
            String s = st.nextToken();
            if (!rules.contains(s)) {
                rules.addElement(s);
            }
        }

        mappings_old = new HashMap<Integer, Integer>();
        colorValues_old = new HashMap<String, Integer>();
        overlays = new Vector();

        final DataArchive da = GameModule.getGameModule().getDataArchive();

        // Read board-specific colors last to override defaults
        InputStream in = null;
        try {
            in = da.getInputStream("boardData/colors");
            readColorValues(in);
        } catch (IOException ignore) {
        } finally {
            IOUtils.closeQuietly(in);
        }

        in = null;
        try {
            in = archive.getInputStream("colors");
            readColorValues(in);
        } catch (IOException ignore) {
        } finally {
            IOUtils.closeQuietly(in);
        }

        // Read board-specific rules first to be applied before defaults
        in = null;
        try {
            in = archive.getInputStream("colorSSR");
            readColorRules(in, rules);
        } catch (IOException ignore) {
        } finally {
            IOUtils.closeQuietly(in);
        }

        in = null;
        try {
            in = da.getInputStream("boardData/colorSSR");
            readColorRules(in, rules);
        } catch (IOException ignore) {
        } finally {
            IOUtils.closeQuietly(in);
        }

*/
/*
        // check for any differences in color mappings
        for (Integer color : mappings.keySet()) {
            if (mappings_old.containsKey(color) && !mappings_old.get(color).equals(mappings.get(color))) {
                System.out.println("Color mapping not the same for " + color + ": Old: " + mappings_old.get(color) + " New: " + mappings.get(color));
            }
        }
*//*


        overlays.clear();
        // SSR Overlays are applied in reverse order to the order they're listed
        // in the overlaySSR file. Therefore, reading board-specific
        // overlay rules first will override defaults
        in = null;
        try {
            in = archive.getInputStream("overlaySSR");
            readOverlayRules(in);
        } catch (IOException ignore) {
        } finally {
            IOUtils.closeQuietly(in);
        }

        in = null;
        try {
            in = da.getInputStream("boardData/overlaySSR");
            readOverlayRules(in);
        } catch (IOException ignore) {
        } finally {
            IOUtils.closeQuietly(in);
        }
    }
*/

    private int colorToInt(Color color) {
        return (color.getRed() << 16) + (color.getGreen() << 8) + color.getBlue();
    }

    private void initialize() {

        colorValues = new HashMap<String, Integer>();
        mappings = new HashMap<Integer, Integer>();
        _overlays = new Vector<SSROverlay>();

        // Build the list of rules in use
        Vector rules = new Vector();
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
                // System.out.println(ruleName);

                ColorSSRule colorSSRule = boardArchive.getColorSSRules().get(ruleName);

                for (Map.Entry<String, String> entry1 : colorSSRule.getColorMaps().entrySet()) {

                    int fromColor = 0;
                    int toColor = 0;
                    try {
                        fromColor = colorValues.get(entry1.getKey());
                        toColor = colorValues.get(entry1.getValue());
                    } catch (Exception e) {
                        logger.warn("Missing color entry in color SSR mapping: " + entry1.getKey() + " or " + entry1.getValue());
                    }
                    if (fromColor >= 0 && toColor >= 0) {

                        if (!mappings.containsKey(fromColor)) {
                            mappings.put(fromColor, toColor);
                            // System.out.println("Refactored mapped " + entry1.getKey() + " to " + entry1.getValue());

                        }

                        // Also apply this mapping to previous mappings
                        if (mappings.containsValue(fromColor)) {

                            for (Entry<Integer, Integer> e : mappings.entrySet()) {
                                if (e.getValue() == fromColor)
                                    e.setValue(toColor);
                            }
                        }
                    }
                }
            }
        }

        // load the overlay SSR
        for(Map.Entry<String, OverlaySSRule> entry: boardArchive.getOverlaySSRules().entrySet()) {

            try {
                _overlays.add(new SSROverlay(entry.getValue(), archiveFile));
            }
            catch (IllegalArgumentException e) {
                logger.warn("Invalid Overlay SSR: " + entry.getKey() + " - " + e.getMessage());
            }
        }

        // load the underlay SSR
        for(Map.Entry<String, UnderlaySSRule> entry: boardArchive.getUnderlaySSRules().entrySet()) {

            UnderlaySSRule rule = entry.getValue();

            try {
                // create the array of transparency colors
                int[] colors = new int[rule.getColors().size()];
                int current = 0;
                for (String color: rule.getColors()) {
                    colors[current++] = colorValues.get(color);
                }
                _overlays.add(new Underlay(rule.getImageName(), colors, archive, board));
            }
            catch (Exception e) {
                logger.warn("Invalid Underlay SSR: " + entry.getKey() + " - " + e.getMessage());
            }
        }
    }

/*
    protected void readColorValues(InputStream in) {
    */
/*
     * * Add to the list of color definitions, as read from input file
     *//*

        if (in == null) {
            return;
        }

        BufferedReader reader = new BufferedReader(new InputStreamReader(in));
        StreamTokenizer st = new StreamTokenizer(reader);
        st.resetSyntax();
        st.wordChars((int) ' ', 0xff);
        st.commentChar((int) '/');
        st.whitespaceChars((int) ' ', (int) ' ');
        st.whitespaceChars((int) '\n', (int) '\n');
        st.whitespaceChars((int) '\t', (int) '\t');
        st.slashSlashComments(true);
        st.slashStarComments(true);
        st.eolIsSignificant(false);
        try {
            for (String s = reader.readLine(); s != null; s = reader.readLine()) {
                if (s.startsWith("/")) {
                    continue;
                }
                StringTokenizer st2 = new StringTokenizer(s);
                if (st2.countTokens() < 2) {
                    continue;
                }
                String s1 = st2.nextToken();
                int rgb = parseRGB(st2.nextToken());
                if (rgb >= 0) {
                    colorValues_old.put(s1, new Integer(rgb));
                } else {
                    
                    logger.warn("Invalid color alias: " + s);
                }
            }
        } catch (Exception e) {
            logger.warn("Caught " + e + " reading colors");
        }
    }

    public void readColorRules(InputStream in, Vector rules) {
    */
/*
     * * Define the color transformations defined by each rule * as read in from input file
     *//*

        if (in == null) {
            return;
        }

        StreamTokenizer st = new StreamTokenizer(new BufferedReader(new InputStreamReader(in)));
        st.resetSyntax();
        st.wordChars((int) ' ', 0xff);
        st.commentChar((int) '/');
        st.whitespaceChars((int) ' ', (int) ' ');
        st.whitespaceChars((int) '\n', (int) '\n');
        st.whitespaceChars((int) '\t', (int) '\t');
        st.slashSlashComments(true);
        st.slashStarComments(true);
        st.eolIsSignificant(false);
        boolean inCategory = false; */
/* are we in a "selected" category *//*

        try {
            while (st.nextToken() != StreamTokenizer.TT_EOF) {
                String s = st.sval;
                if (s == null) {
                    continue;
                }
                int n = s.indexOf('=');
                if (n < 0) {
                    if (s.charAt(0) == '+') {
                        inCategory = rules.contains(s.substring(1));
                    } else {
                        inCategory = rules.removeElement(s);
                    }
                } else if (inCategory) {
                    int len = s.length();
                    boolean valid = true;
                    if (n + 1 == len) {
                        valid = false;
                    } else {
                        String sfrom = s.substring(0, n);
                        String sto = s.substring(n + 1, len);
                        int ifrom = parseRGB(sfrom);
                        int ito = parseRGB(sto);
                        if (ifrom >= 0 && ito >= 0) {

                            if (!mappings_old.containsKey(ifrom))
                                mappings_old.put(ifrom, ito);
                            // System.out.println("Legacy mapped " + sfrom + " to " + sto);

              */
/*
               * Also apply this mapping to previous mappings
               *//*

                            if (mappings_old.containsValue(ifrom)) {
                                for (Iterator<Entry<Integer, Integer>> it = mappings_old.entrySet().iterator(); it.hasNext(); ) {
                                    Entry<Integer, Integer> e = it.next();
                                    if (e.getValue() == ifrom)
                                        e.setValue(ito);
                                }
                            }
                        } else {
                            valid = false;
                            System.err.println("Invalid color mapping: " + s + " mapped to " + ifrom + "=" + ito);
                        }
                    }
                    if (!valid) {
                        System.err.println("Invalid color mapping: " + s);
                    }
                }
            }
        } catch (Exception e) {
        }
    }

    public void readOverlayRules(InputStream in) {
        if (in == null) {
            return;
        }

        try {
            BufferedReader file;
            file = new BufferedReader(new InputStreamReader(in));
            String s;
            while ((s = file.readLine()) != null) {
                if (s.trim().length() == 0) {
                    continue;
                }
                if (saveRules.indexOf(s.trim()) >= 0) {
                    while ((s = file.readLine()) != null) {
                        if (s.length() == 0) {
                            break;
                        } else if (s.toLowerCase().startsWith("underlay")) {
                            try {
                                StringTokenizer st = new StringTokenizer(s);
                                st.nextToken();
                                String underImage = st.nextToken();
                                st = new StringTokenizer(st.nextToken(), ",");
                                int trans[] = new int[st.countTokens()];
                                int n = 0;
                                while (st.hasMoreTokens()) {
                                    trans[n++] = ((Integer) colorValues_old.get(st.nextToken())).intValue();
                                }
                                overlays.add(new Underlay(underImage, trans, archive, board));
                            } catch (NoSuchElementException end) {
                            }
                        } else {
                            overlays.add(new SSROverlay_old(s.trim(), archiveFile));
                        }
                    }
                }
            }
        } catch (Exception e) {
            System.err.println("Error opening rules file " + e);
        }
    }
*/

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
