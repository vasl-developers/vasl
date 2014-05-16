/*
 * $Id: OBA 2/15/14 davidsullivan1 $
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
package VASL.LOS.counters;

import VASL.LOS.Map.Hex;
import VASL.LOS.counters.Counter;

/**
 * A simple class for an OBA counter
 */
public class OBA extends Counter {

    private Hex hex;
    private int blastHeight = 2;
    private int blastAreaRadius = 1;

    /**
     * Create an OBA counter
     * @param name the counter name
     * @param hex the hex location of the counter
     */
    public OBA(String name, Hex hex){
        super(name);
        this.hex = hex;
    }

    /**
     * @return the hex location of the OBA counter
     */
    public Hex getHex(){
        return hex;
    }

    /**
     * @return the blast height of the OBA
     */
    public int getBlastHeight(){
        return blastHeight;
    }

    /**
     * @return the max range of the blast area from the counter
     */
    public int getBlastAreaRadius(){
        return blastAreaRadius;
    }
}
