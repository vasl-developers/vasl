package VASL.build.module.map.boardArchive;

/**
 * Copyright (c) 2015 by David Sullivan
 * <p/>
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Library General Public
 * License (LGPL) as published by the Free Software Foundation.
 * <p/>
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Library General Public License for more details.
 * <p/>
 * You should have received a copy of the GNU Library General Public
 * License along with this library; if not, copies are available
 * at http://www.opensource.org.
 */

/**
 * A class for an overlay SSR image
 */
public class OverlaySSRuleImage {

    String imageName;
    int x;
    int y;

    OverlaySSRuleImage(String imageName, int x, int y) {

        this.imageName = imageName;
        this.x = x;
        this.y = y;
    }

    public String getImageName() {
        return imageName;
    }

    public int getX() {
        return x;
    }

    public int getY() {
        return y;
    }
}