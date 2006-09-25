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

import VASSAL.build.GameModule;
import VASSAL.tools.DataArchive;

import java.awt.*;
import java.awt.image.FilteredImageSource;
import java.awt.image.RGBImageFilter;
import java.io.*;
import java.util.*;

public class SSRFilter extends RGBImageFilter {
  /*
   ** A class to swap colors according to specified rules
   ** A set of color names is read from an input file with the format
   ** White    255,255,255
   ** Black    0,0,0
   **
   ** The swapping rules are read from an input file with the format:
   ** <key>
   **   <color>=<color>
   **   <color>=<color>
   **   <color>=<color>
   ** There can be any number of color entries per key.
   ** The color entries are names of colors as defined in the color file
   ** Example:
   **
   ** WoodsToBrush
   **  WoodsGreen=BrushL0
   **  WoodsBlack=BrushL0
   */

  private static File globalArchive;

  private Vector mappings;
  private String saveRules;
  private Hashtable colorValues;
  private Vector overlays;
  private File archive;

  //  public SSRFilter(String zip, String listOfRules)
  public SSRFilter(String listOfRules, File archive)
      throws BoardException {
    canFilterIndexColorModel = true;

    saveRules = listOfRules;
    this.archive = archive;

    try {
      DataArchive.getFileStream(archive, "data");
    }
    catch (IOException ex) {
      throw new BoardException("Board does not support terrain alterations");
    }

    readAllRules();
  }

  private static InputStream getStream(String name) {
    try {
      return globalArchive == null ?
          GameModule.getGameModule().getDataArchive()
          .getFileStream(name) :
          DataArchive.getFileStream(getGlobalArchive(), name);
    }
    catch (IOException ex) {
      return null;
    }
  }

  public static File getGlobalArchive() {
    return globalArchive;
  }

  public static void setGlobalArchive(File f) {
    globalArchive = f;
  }

  public Enumeration getOverlays() {
    return overlays.elements();
  }

  public int filterRGB(int x, int y, int rgb) {
    return ((0xff000000 & rgb) | newValue(rgb & 0xffffff));
  }

  private int newValue(int rgb) {
    /*
    ** Maps the color to it's transformed value by going through
    ** the rules.  All rules are applied in sequence.
    */
    int rval = rgb;

    for (Enumeration e = mappings.elements(); e.hasMoreElements();) {
      RGBMapping m = (RGBMapping) e.nextElement();
      if (m.from == rval)
        rval = m.to;
    }
    return rval;
  }

  public String toString() {
    return saveRules;
  }

  private int parseRGB(String s) {
    /*
     ** Calculate integer value from rr,gg,bb or 40a38f format
     */
    int rval = -1;
    try {
      Integer test = (Integer) colorValues.get(s);
      if (test != null) {
        rval = test.intValue();
      }
      else if (s.indexOf(',') >= 0) {
        StringTokenizer st = new StringTokenizer(s, ",");
        if (st.countTokens() == 3) {
          int red, green, blue;
          red = Integer.parseInt(st.nextToken());
          green = Integer.parseInt(st.nextToken());
          blue = Integer.parseInt(st.nextToken());
          if ((red >= 0 && red <= 255) &&
              (green >= 0 && green <= 255) &&
              (blue >= 0 && blue <= 255)) {
            rval = (red << 16) + (green << 8) + blue;
          }
        }
      }
      else if (s.length() == 6) {
        rval = Integer.parseInt(s, 16);
      }
    }
    catch (Exception e) {
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

    mappings = new Vector();
    colorValues = new Hashtable();
    overlays = new Vector();

// Read board-specific colors last to override defaults
    readColorValues(getStream("boardData/colors"));
    try {
      readColorValues(DataArchive.getFileStream(archive, "colors"));
    }
    catch (IOException ex) {
    }
// Read board-specific rules first to be applied before defaults
    try {
      readColorRules(DataArchive.getFileStream(archive, "colorSSR"), rules);
    }
    catch (IOException ex) {
    }
    readColorRules(getStream("boardData/colorSSR"), rules);
    overlays.removeAllElements();
// SSR Overlays are applied in reverse order to the order they're listed
// in the overlaySSR file.  Therefore, reading board-specific
// overlay rules first will override defaults
    try {
      readOverlayRules(DataArchive.getFileStream(archive, "overlaySSR"));
    }
    catch (IOException ex) {
    }
    readOverlayRules(getStream("boardData/overlaySSR"));
  }

  protected void setRules(String s) {
    saveRules = s;
  }

  protected void readColorValues(InputStream in) {
    /*
     ** Add to the list of color definitions, as read from input file
     */
    if (in == null)
      return;
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
      for (String s = reader.readLine(); s != null;
           s = reader.readLine()) {
        if (s.startsWith("/"))
          continue;
        StringTokenizer st2 = new StringTokenizer(s);
        if (st2.countTokens() < 2)
          continue;
        String s1 = st2.nextToken();
        int rgb = parseRGB(st2.nextToken());
        if (rgb >= 0) {
          colorValues.put(s1, new Integer(rgb));
        }
        else {
          System.err.println("Invalid color alias: " + s);
        }
      }
    }
    catch (Exception e) {
      System.err.println("Caught " + e + " reading colors");
    }
  }

  public void readColorRules(InputStream in, Vector rules) {
    /*
     ** Define the color transformations defined by each rule
     ** as read in from input file
     */
    if (in == null)
      return;
    StreamTokenizer st = new StreamTokenizer
        (new BufferedReader(new InputStreamReader(in)));
    st.resetSyntax();
    st.wordChars((int) ' ', 0xff);
    st.commentChar((int) '/');
    st.whitespaceChars((int) ' ', (int) ' ');
    st.whitespaceChars((int) '\n', (int) '\n');
    st.whitespaceChars((int) '\t', (int) '\t');
    st.slashSlashComments(true);
    st.slashStarComments(true);
    st.eolIsSignificant(false);

    boolean inCategory = false;  /* are we in a "selected" category */

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
          }
          else {
            inCategory = rules.removeElement(s);
          }
        }
        else if (inCategory) {
          int len = s.length();
          boolean valid = true;
          if (n + 1 == len) {
            valid = false;
          }
          else {
            String sfrom = s.substring(0, n);
            String sto = s.substring(n + 1, len);
            int ifrom = parseRGB(sfrom);
            int ito = parseRGB(sto);
            if (ifrom >= 0 && ito >= 0) {
              mappings.addElement(new RGBMapping(ifrom, ito));
            }
            else {
              valid = false;
              System.err.println("Invalid color mapping: " + s
                                 + " mapped to " + ifrom + "=" + ito);
            }
          }
          if (!valid) {
            System.err.println("Invalid color mapping: " + s);
          }
        }
      }
    }
    catch (Exception e) {
    }
  }

  public void readOverlayRules(InputStream in) {

    if (in == null)
      return;

    try {
      BufferedReader file;
      file = new BufferedReader(new InputStreamReader(in));
      String s;
      while ((s = file.readLine()) != null) {
        if (s.trim().length() == 0)
          continue;
        if (saveRules.indexOf(s.trim()) >= 0) {
          while ((s = file.readLine()) != null) {
            if (s.length() == 0)
              break;
            else if (s.toLowerCase().startsWith("underlay")) {
              try {
                StringTokenizer st = new StringTokenizer(s);
                st.nextToken();
                String underImage = st.nextToken();
                st = new StringTokenizer(st.nextToken(), ",");
                int trans[] = new int[st.countTokens()];
                int n = 0;
                while (st.hasMoreTokens()) {
                  trans[n++] =
                      ((Integer) colorValues.get(st.nextToken())).intValue();
                }
                overlays.addElement(new Underlay(underImage, trans));
              }
              catch (NoSuchElementException end) {
              }
            }
            else
              overlays.addElement(new SSROverlay(s.trim()));
          }
        }
      }
    }
    catch (Exception e) {
      System.err.println("Error opening rules file " + e);
    }
  }

  public Image recolor(Image oldImage, Component observer) {
    return Toolkit.getDefaultToolkit().createImage
        (new FilteredImageSource(oldImage.getSource(), this));
  }
}

class RGBMapping {
  int from;
  int to;

  RGBMapping(int fromVal, int toVal) {
    from = fromVal;
    to = toVal;
  }
}

