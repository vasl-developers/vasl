/*
 * $Id: LOSSSRule 1/19/14 davidsullivan1 $
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


/**
 * Maps the VASL SSR tokens (created by the board picker) to LOS rules applied to the board
 */
public class LOSSSRule {

    private String name;
    private String type;
    private String fromValue;
    private String toValue;

    public LOSSSRule(String name, String type, String fromValue, String toValue) {
        this.name = name;
        this.type = type;
        this.fromValue = fromValue;
        this.toValue = toValue;
    }

    /**
     * @return the rule name
     */
    public String getName() {
        return name;
    }

    /**
     * @return rule type. See <code>AbstractMetadata</code> for values
     */
    public String getType() {
        return type;
    }

    /**
     * @return the from value
     */
    public String getFromValue() {
        return fromValue;
    }

    /**
     * @return the to value
     */
    public String getToValue() {
        return toValue;
    }
}
