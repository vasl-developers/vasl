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
import VASSAL.build.Buildable;
import VASSAL.build.module.Map;
import VASSAL.build.module.map.ForwardToKeyBuffer;

import java.awt.event.KeyEvent;

/**
 * Forwards the keys pressed on the map to the selected pieces. On a rotated map view Ctrl + keypad moves the pieces
 * toward the direction of the key on the screen: the pieces get the key with that direction on the unrotated map, or
 * no key if there is no hex that way. The other key listeners of the map, e.g. the Scroller, get the key as it is,
 * so a key that moves no piece scrolls the map toward its direction on the screen.
 */
public class ASLForwardToKeyBuffer extends ForwardToKeyBuffer {
    private Map map;

    @Override
    public void addTo(Buildable parent) {
        super.addTo(parent);
        map = (Map) parent;
    }

    @Override
    protected void process(KeyEvent e) {
        final int rotation = map instanceof ASLMap ? ((ASLMap) map).getViewRotation() : 0;
        if (rotation == 0 || e.getID() == KeyEvent.KEY_TYPED || !(e.isControlDown() || e.isMetaDown())) {
            super.process(e);
            return;
        }
        final int key = MapViewRotation.keypadKeyOnUnrotatedView(e.getKeyCode(), rotation);
        if (key == e.getKeyCode()) {
            super.process(e);
        }
        else if (key != KeyEvent.VK_UNDEFINED) {
            final KeyEvent keyOnMap = new KeyEvent(e.getComponent(), e.getID(), e.getWhen(), e.getModifiersEx(),
                key, KeyEvent.CHAR_UNDEFINED, e.getKeyLocation());
            super.process(keyOnMap);
            if (keyOnMap.isConsumed()) {
                e.consume();
            }
        }
    }
}
