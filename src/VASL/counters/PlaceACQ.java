package VASL.counters;

import VASSAL.command.Command;
import VASSAL.counters.Decorator;
import VASSAL.counters.GamePiece;
import VASSAL.counters.KeyCommand;
import VASSAL.counters.PlaceMarker;

import javax.swing.*;
import java.util.Iterator;

public class PlaceACQ extends PlaceMarker {
    public static final String ID = "placeACQ;";

    public PlaceACQ() {
        this(ID + "+ ACQ;E;null", null);
    }

    public PlaceACQ(String type, GamePiece inner) {
        super(type, inner);
    }

    protected KeyCommand[] myGetKeyCommands() {
        command.setEnabled(true);
        return new KeyCommand[]{command};
    }

    public Command myKeyEvent(KeyStroke stroke) {
        KeyCommand[] k = myGetKeyCommands();
        if (!k[0].matches(stroke)) {
            return null;
        }
        Command result = null;
        if (getMap() != null) {
            boolean acqExists = false;
            if (getParent() != null) {
                GamePiece outer = Decorator.getOutermost(this);
                for (Iterator<GamePiece> it = getParent().getPiecesReverseIterator();
                     it.hasNext();) {
                    GamePiece p = it.next();
                    if (p.getName().equals("ACQ")) {
                        acqExists = true;
                        break;
                    }
                    if (p == outer) {
                        break;
                    }
                }
            }
            if (!acqExists) {
                result = super.myKeyEvent(stroke);
            }
        }
        return result;
    }

    public String getDescription() {
        return "Place ACQ";
    }

    public String myGetType() {
        String s = super.myGetType();
        return ID + s.substring(s.indexOf(";") + 1);
    }
}
