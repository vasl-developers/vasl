/*
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
package VASL.build.module.map;

import VASL.build.module.ASLMap;
import VASSAL.build.module.map.Zoomer;

import java.awt.Dimension;
import java.awt.event.ActionEvent;

/**
 * Zoomer whose "Fit Width", "Fit Height" and "Fit Visible" choices take into account the rotation of the map view
 */
public class ASLZoomer extends Zoomer {

    private static final String FIT_WIDTH = "Fit Width";
    private static final String FIT_HEIGHT = "Fit Height";
    private static final String FIT_VISIBLE = "Fit Visible";

    public ASLZoomer() {
        super();
        zoomMenu = new RotatedViewZoomMenu();
        zoomMenu.initZoomItems();
    }

    protected class RotatedViewZoomMenu extends ZoomMenu {
        private static final long serialVersionUID = 1L;

        @Override
        public void actionPerformed(ActionEvent a) {
            final String cmd = a.getActionCommand();
            if (isRotatedByQuarterTurn() && (FIT_WIDTH.equals(cmd) || FIT_HEIGHT.equals(cmd) || FIT_VISIBLE.equals(cmd))) {
                fitRotatedView(cmd);
            }
            else {
                super.actionPerformed(a);
            }
        }
    }

    // true if the map view is turned by 90 or 270 degrees, so that the width and height of the map are swapped
    private boolean isRotatedByQuarterTurn() {
        return map instanceof ASLMap && MapViewRotation.swapsAxes(((ASLMap) map).getViewRotation());
    }

    // same as the VASSAL choices, but with the width and height of the map swapped
    private void fitRotatedView(String cmd) {
        Dimension vd = map.getView().getVisibleRect().getSize();
        Dimension md = MapViewRotation.rotate(map.mapSize(), ((ASLMap) map).getViewRotation());
        if (FIT_WIDTH.equals(cmd)) {
            setZoomFactor(vd.getWidth() / md.getWidth());
        }
        else if (FIT_HEIGHT.equals(cmd)) {
            setZoomFactor(vd.getHeight() / md.getHeight());
            // again, as the visible part may have changed with the scroll bars
            vd = map.getView().getVisibleRect().getSize();
            md = MapViewRotation.rotate(map.mapSize(), ((ASLMap) map).getViewRotation());
            setZoomFactor(vd.getHeight() / md.getHeight());
        }
        else {
            setZoomFactor(Math.min(vd.getWidth() / md.getWidth(), vd.getHeight() / md.getHeight()));
        }
    }
}
