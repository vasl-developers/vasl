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
package VASL.LOS.Unit;

import java.awt.event.*;
import javax.swing.*;


public class UnitPopupMenuAction
	extends AbstractAction {

	private Unit		unit;
	private String	action;

	UnitPopupMenuAction (String action) {
		super(action);
		putValue(Action.SHORT_DESCRIPTION, action);
		this.action = action;
	}

	UnitPopupMenuAction (String action, Unit u) {
		super(action);
		putValue(Action.SHORT_DESCRIPTION, action);
		this.action = action;
		unit = u;
	}

	public void setUnit(Unit u) {unit = u;}

	public void actionPerformed(ActionEvent actionEvent) {
		unit.popupMenuEvent(action);
	}
}
