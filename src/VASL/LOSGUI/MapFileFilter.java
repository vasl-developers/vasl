/*
 * Copyright (c) 2000-2003 by David Sullivan
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
package VASL.LOSGUI;

/*
 * @(#)ExampleFileFilter.java	1.6 98/08/26
 *
 * Copyright 1998 by Sun Microsystems, Inc.,
 * 901 San Antonio Road, Palo Alto, California, 94303, U.S.A.
 * All rights reserved.
 *
 * This software is the confidential and proprietary information
 * of Sun Microsystems, Inc. ("Confidential Information").  You
 * shall not disclose such Confidential Information and shall use
 * it only in accordance with the terms of the license agreement
 * you entered into with Sun.
 */


import javax.swing.filechooser.FileFilter;
import java.io.File;
import java.util.Enumeration;
import java.util.Hashtable;

/**
 * A convenience implementation of FileFilter that filters out
 * all files except for those type extensions that it knows about.
 * <p/>
 * Extensions are of the type ".foo", which is typically found on
 * Windows and Unix boxes, but not on Macinthosh. Case is ignored.
 * <p/>
 * Example - create a new filter that filerts out all files
 * but gif and jpg image files:
 * <p/>
 * JFileChooser chooser = new JFileChooser();
 * ExampleFileFilter filter = new ExampleFileFilter(
 * new String{"gif", "jpg"}, "JPEG & GIF Images")
 * chooser.addChoosableFileFilter(filter);
 * chooser.showOpenDialog(this);
 *
 * @author Jeff Dinkins
 * @version 1.6 08/26/98
 */
public class MapFileFilter extends FileFilter {

    @SuppressWarnings("unused")
    private static String TYPE_UNKNOWN = "Type Unknown";
    @SuppressWarnings("unused")
    private static String HIDDEN_FILE = "Hidden File";

    private Hashtable filters = null;
    private String description = null;
    private String fullDescription = null;
    private boolean useExtensionsInDescription = true;

    /**
     * Creates a file filter. If no filters are added, then all
     * files are accepted.
     *
     * @see #addExtension
     */
    public MapFileFilter() {
        this((String) null, (String) null);
    }

    /**
     * Creates a file filter that accepts files with the given extension.
     * Example: new ExampleFileFilter("jpg");
     *
     * @see #addExtension
     */
    public MapFileFilter(String extension) {
        this(extension, null);
    }

    /**
     * Creates a file filter that accepts the given file type.
     * Example: new ExampleFileFilter("jpg", "JPEG Image Images");
     * <p/>
     * Note that the "." before the extension is not needed. If
     * provided, it will be ignored.
     *
     * @see #addExtension
     */
    public MapFileFilter(String extension, String description) {
        this(new String[]{extension}, description);
    }

    /**
     * Creates a file filter from the given string array.
     * Example: new ExampleFileFilter(String {"gif", "jpg"});
     * <p/>
     * Note that the "." before the extension is not needed adn
     * will be ignored.
     *
     * @see #addExtension
     */
    public MapFileFilter(String[] filters) {
        this(filters, null);
    }

    /**
     * Creates a file filter from the given string array and description.
     * Example: new ExampleFileFilter(String {"gif", "jpg"}, "Gif and JPG Images");
     * <p/>
     * Note that the "." before the extension is not needed and will be ignored.
     *
     * @see #addExtension
     */
    public MapFileFilter(String[] filters, String description) {
        this.filters = new Hashtable(filters.length);
        for (int i = 0; i < filters.length; i++) {
            // add filters one by one
            addExtension(filters[i]);
        }
        setDescription(description);
    }

    /**
     * Return true if this file should be shown in the directory pane,
     * false if it shouldn't.
     * <p/>
     * Files that begin with "." are ignored.
     *
     * @see #getExtension
     * @see FileFilter
     */
    public boolean accept(File f) {
        if (f != null) {
            if (f.isDirectory()) {
                return true;
            }
            String extension = getExtension(f);
            if (extension != null && filters.get(getExtension(f)) != null) {
                return true;
            }
            ;
        }
        return false;
    }

    /**
     * Return the extension portion of the file's name .
     *
     * @see #getExtension
     * @see FileFilter#accept
     */
    public String getExtension(File f) {
        if (f != null) {
            String filename = f.getName();
            int i = filename.lastIndexOf('.');
            if (i > 0 && i < filename.length() - 1) {
                return filename.substring(i + 1).toLowerCase();
            }
            ;
        }
        return null;
    }

    /**
     * Adds a filetype "dot" extension to filter against.
     * <p/>
     * For example: the following code will create a filter that filters
     * out all files except those that end in ".jpg" and ".tif":
     * <p/>
     * ExampleFileFilter filter = new ExampleFileFilter();
     * filter.addExtension("jpg");
     * filter.addExtension("tif");
     * <p/>
     * Note that the "." before the extension is not needed and will be ignored.
     */
    public void addExtension(String extension) {
        if (filters == null) {
            filters = new Hashtable(5);
        }
        filters.put(extension.toLowerCase(), this);
        fullDescription = null;
    }


    /**
     * Returns the human readable description of this filter. For
     * example: "JPEG and GIF Image Files (*.jpg, *.gif)"
     *
     * @see FileFilter#getDescription
     */
    public String getDescription() {
        if (fullDescription == null) {
            if (description == null || isExtensionListInDescription()) {
                if (description != null) {
                    fullDescription = description;
                }
                fullDescription += " (";
                // build the description from the extension list
                Enumeration extensions = filters.keys();
                if (extensions != null) {
                    fullDescription += "." + (String) extensions.nextElement();
                    while (extensions.hasMoreElements()) {
                        fullDescription += ", " + (String) extensions.nextElement();
                    }
                }
                fullDescription += ")";
            } else {
                fullDescription = description;
            }
        }
        return fullDescription;
    }

    /**
     * Sets the human readable description of this filter. For
     * example: filter.setDescription("Gif and JPG Images");
     */
    public void setDescription(String description) {
        this.description = description;
        fullDescription = null;
    }

    /**
     * Determines whether the extension list (.jpg, .gif, etc) should
     * show up in the human readable description.
     * <p/>
     * Only relevent if a description was provided in the constructor
     * or using setDescription();
     */
    public void setExtensionListInDescription(boolean b) {
        useExtensionsInDescription = b;
        fullDescription = null;
    }

    /**
     * Returns whether the extension list (.jpg, .gif, etc) should
     * show up in the human readable description.
     * <p/>
     * Only relevent if a description was provided in the constructor
     * or using setDescription();
     */
    public boolean isExtensionListInDescription() {
        return useExtensionsInDescription;
    }
}
