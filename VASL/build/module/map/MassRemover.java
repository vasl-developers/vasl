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
package VASL.build.module.map;

import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.Enumeration;
import java.util.Hashtable;
import java.util.Iterator;
import java.util.Vector;

import javax.swing.AbstractAction;
import javax.swing.JButton;
import javax.swing.JPopupMenu;

import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import VASSAL.build.Buildable;
import VASSAL.build.Builder;
import VASSAL.build.GameModule;
import VASSAL.build.module.Map;
import VASSAL.command.Command;
import VASSAL.command.NullCommand;
import VASSAL.command.RemovePiece;
import VASSAL.counters.GamePiece;
import VASSAL.counters.Stack;

/**
 * A class to remove all GamePieces with a given name
 */
public class MassRemover implements Buildable {
  private Map map;
  private JButton launch;
  private JPopupMenu popup;
  private Vector entries = new Vector();

  public void build(Element e) {
    launch = new JButton("Remove All");
    launch.setToolTipText("Remove all counters of a given type");
    launch.setAlignmentY(0.0F);
    popup = new JPopupMenu();
    Hashtable actions = new Hashtable();

    NodeList n = e.getElementsByTagName("*");
    for (int i = 0; i < n.getLength(); ++i) {
      Element el = (Element) n.item(i);
      String actionName = el.getAttribute("name");
      String pieceName = Builder.getText(el);
      RemAction a = (RemAction) actions.get(actionName);
      entries.addElement(new Entry(actionName, pieceName));
      if (a == null) {
        a = new RemAction(actionName, pieceName);
        actions.put(actionName, a);
        popup.add(a);
      }
      else {
        a.addPiece(pieceName);
      }
    }
    launch.addActionListener(new ActionListener() {
      public void actionPerformed(ActionEvent evt) {
        popup.show(launch, 0, 0);
      }
    });
  }

  protected boolean isMatch(GamePiece p, String name) {
    return p.getName().equals(name);
  }

  public org.w3c.dom.Element getBuildElement(org.w3c.dom.Document doc) {
    Element el = doc.createElement(getClass().getName());
    for (Enumeration e = entries.elements();
         e.hasMoreElements();) {
      Entry en = (Entry) e.nextElement();
      Element sub = doc.createElement("entry");
      sub.setAttribute("name", en.name);
      sub.appendChild(doc.createTextNode(en.piece));
      el.appendChild(sub);
    }
    return el;
  }

  public void addTo(Buildable b) {
    map = (Map) b;
    map.getToolBar().add(launch);
  }

  public void add(Buildable b) {
  }

  public Command removePiecesWithName(String name) {
    Command comm = new NullCommand();
    GamePiece piece[] = map.getPieces();

    for (int i = 0; i < piece.length; ++i) {
      if (piece[i] instanceof Stack) {
        for (Iterator<GamePiece> it = ((Stack) piece[i]).getPiecesIterator();it.hasNext();) {
          GamePiece child = it.next();
          if (isMatch(child, name)) {
            comm = comm.append(new RemovePiece(child));
          }
        }
      }
      else if (isMatch(piece[i], name)) {
        comm = comm.append(new RemovePiece(piece[i]));
      }
    }
    return comm;
  }

  private class RemAction extends AbstractAction {
    private Vector pieces = new Vector();

    public RemAction(String actionName, String pieceName) {
      super(actionName);
      addPiece(pieceName);
    }

    public void addPiece(String name) {
      pieces.addElement(name);
    }

    public void actionPerformed(ActionEvent evt) {
      Command comm = new NullCommand();

      for (Enumeration e = pieces.elements();
           e.hasMoreElements();) {
        String name = (String) e.nextElement();
        comm = comm.append(removePiecesWithName(name));
      }
      comm.execute();
      GameModule.getGameModule().sendAndLog(comm);
    }
  }

  private static class Entry {
    public String name;
    public String piece;

    public Entry(String n, String p) {
      name = n;
      piece = p;
    }
  }
}
