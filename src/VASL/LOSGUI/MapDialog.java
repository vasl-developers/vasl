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
package VASL.LOSGUI;

import javax.swing.*;
import javax.swing.border.Border;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;

public class MapDialog extends JDialog {

    @SuppressWarnings("unused")
    private LOSEditorJFrame frame;

    private JPanel panel1 = new JPanel();
    private JPanel panel2 = new JPanel();
    private JButton yesButton = new JButton();
    private JButton cancelButton = new JButton();
    private JButton noButton = new JButton();

    private Border border1;
    private JPanel jPanel1 = new JPanel();
    private GridBagLayout gridBagLayout1 = new GridBagLayout();
    private GridLayout gridLayout1 = new GridLayout();

    public MapDialog(Frame newFrame, String title, boolean modal) {

        super(newFrame, title, modal);

        frame = (LOSEditorJFrame) newFrame;

        try {
            jbInit();
        } catch (Exception e) {
            e.printStackTrace();
        }

        pack();
    }

    private void jbInit() throws Exception {

        border1 = BorderFactory.createRaisedBevelBorder();
        jPanel1.setLayout(gridLayout1);
        panel2.setBorder(border1);
        yesButton.setText("Yes");
        yesButton.addActionListener(new MapDialog_yesButton_actionAdapter(this));
        cancelButton.setText("Cancel");
        gridLayout1.setHgap(4);
        cancelButton.addActionListener(new MapDialog_cancelButton_actionAdapter(this));
        this.addWindowListener(new MapDialog_this_windowAdapter(this));
        panel1.setLayout(gridBagLayout1);
        noButton.addActionListener(new MapDialog_noButton_actionAdapter(this));
        noButton.setText("No");
        panel1.add(panel2, new GridBagConstraints(0, 0, 1, 1, 1.0, 1.0
                , GridBagConstraints.CENTER, GridBagConstraints.BOTH, new Insets(0, 0, 0, 0), 0, 0));
        panel1.add(jPanel1, new GridBagConstraints(0, 1, 1, 1, 1.0, 0.0
                , GridBagConstraints.CENTER, GridBagConstraints.NONE, new Insets(4, 8, 4, 8), 0, 0));
        jPanel1.add(yesButton, null);
        jPanel1.add(noButton, null);
        jPanel1.add(cancelButton, null);
        getContentPane().add(panel1);
    }

    // Yes
    void yesButton_actionPerformed(ActionEvent e) {
        dispose();
    }

    // No
    void noButton_actionPerformed(ActionEvent e) {
        dispose();
    }

    // Cancel
    void cancelButton_actionPerformed(ActionEvent e) {
        dispose();
    }

    void this_windowClosing(WindowEvent e) {
        dispose();
    }
}

class MapDialog_yesButton_actionAdapter implements ActionListener {
    MapDialog adaptee;

    MapDialog_yesButton_actionAdapter(MapDialog adaptee) {
        this.adaptee = adaptee;
    }

    public void actionPerformed(ActionEvent e) {
        adaptee.yesButton_actionPerformed(e);
    }
}

class MapDialog_noButton_actionAdapter implements ActionListener {
    MapDialog adaptee;

    MapDialog_noButton_actionAdapter(MapDialog adaptee) {
        this.adaptee = adaptee;
    }

    public void actionPerformed(ActionEvent e) {
        adaptee.noButton_actionPerformed(e);
    }
}

class MapDialog_cancelButton_actionAdapter implements ActionListener {
    MapDialog adaptee;

    MapDialog_cancelButton_actionAdapter(MapDialog adaptee) {
        this.adaptee = adaptee;
    }

    public void actionPerformed(ActionEvent e) {
        adaptee.cancelButton_actionPerformed(e);
    }
}

class MapDialog_this_windowAdapter extends WindowAdapter {
    MapDialog adaptee;

    MapDialog_this_windowAdapter(MapDialog adaptee) {
        this.adaptee = adaptee;
    }

    public void windowClosing(WindowEvent e) {
        adaptee.this_windowClosing(e);
    }
}
