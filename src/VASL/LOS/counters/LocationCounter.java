/*
 * $Id: LocationCounter 4/26/14 davidsullivan1 $
 *
 * Copyright (c) 2014 by David Sullivan
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
package VASL.LOS.counters;

import VASL.LOS.counters.Counter;
import VASL.LOS.counters.CounterMetadata;
import VASL.LOS.counters.CounterMetadataFile;

/**
 * This class represents a location counter (e.g. foxhole, building level counter, etc.)
 */
public class LocationCounter extends Counter {

    private String position; // pieces in the location are above or below the counter?
    private int level;
    private int coverArch; // in hex coodinates - 0 is the top, etc.
    private LocationCounterType type;

    // types of location counters
    public static enum LocationCounterType {BUILDING_LEVEL, CREST, ROOF, ENTRENCHMENT, CLIMB, BRIDGE}

    public LocationCounter (CounterMetadata counter, LocationCounterType type) {

        super(counter.getName());
        this.type = type;
        this.position = counter.getPosition();
        this.level = counter.getLevel();
        this.coverArch = counter.getCoverArch();
    }


    /**
     * @return the location type
     */
    public LocationCounterType getType() {
        return type;
    }

    /**
     * @return the location level
     */
    public int getLevel() {
        return level;
    }

    /**
     * @return the covered arch
     */
    public int getCoverArch() {
        return coverArch;
    }

    /**
     * @return true if the location counter is above the pieces
     */
    public boolean isAbovePiece() {
        return position.equals(CounterMetadataFile.counterPositionAbove);
    }

    /**
     * @return true if the location counter is below the pieces
     */
    public boolean isBelowPiece() {
        return position.equals(CounterMetadataFile.getCounterPositionBelow);
    }
}
