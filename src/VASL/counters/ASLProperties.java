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
package VASL.counters;

/**
 * Defines the properties defined for ASL pieces
 */
public interface ASLProperties {
  /** The piece is a location marker (e.g. level, entrenchment) */
  public static final String LOCATION = "Location";

  /** The piece is an LOS hindrance */
  public static final String HINDRANCE = "Hindrance";

  public static final String NATIONALITY = "Nation";

  /** This piece is a terrain overlay */
  public static final String OVERLAY = "Overlay";
}
