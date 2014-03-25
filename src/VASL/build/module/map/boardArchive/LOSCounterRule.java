/*
 * $Id: LOSCounterRule 2/4/14 davidsullivan1 $
 *
 * Copyright (c) 2013 by David Sullivan
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

public class LOSCounterRule {

    private String name;
    private String terrain;
    private int height;
    private int hindrance;
    private CounterType type;

    public static enum CounterType {SMOKE, WRECK, OBA, TERRAIN, IGNORE}

    public LOSCounterRule(String name, CounterType type) {
        this.name = name;
        this.type = type;
    }

    /**
     * @return the counter name
     */
    public String getName() {
        return name;
    }

    /**
     * @return the terrain name (for terrain-type counters)
     */
    public String getTerrain() {
        return terrain;
    }

    /**
     * @return the height (for smoke-type counters)
     */
    public int getHeight() {
        return height;
    }

    /**
     * @return the hindrance amount (for smoke counters)
     */
    public int getHindrance() {
        return hindrance;
    }

    /**
     * @return the counter type
     */
    public CounterType getType() {
        return type;
    }

    /**
     * Set the terrain (for terrain-type counters)
     * @param terrain the terrain name
     */
    public void setTerrain(String terrain) {
        this.terrain = terrain;
    }

    /**
     * Set the smoke height (for smoke-type counter)
     * @param height the smoke height
     */
    public void setHeight(int height) {
        this.height = height;
    }

    /**
     * Set the smoke hindrance (for smoke-type counters)
     * @param hindrance the smoke hindrance level
     */
    public void setHindrance(int hindrance) {
        this.hindrance = hindrance;
    }
}
