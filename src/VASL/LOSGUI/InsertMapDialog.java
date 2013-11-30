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

import VASL.LOS.LOSDataEditor;

import javax.swing.*;
import javax.swing.border.Border;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;

/**
 * Title:        InsertMapDialog.java
 * Copyright:    Copyright (c) 2001 David Sullivan Zuericher Strasse 6 12205 Berlin Germany. All rights reserved.
 *
 * @author David Sullivan
 * @version 1.0
 */
public class InsertMapDialog extends JDialog {

    private LOSEditorJFrame frame;

    private JPanel InsertMapPanel = new JPanel();
    private JPanel panel2 = new JPanel();
    private JButton button1 = new JButton();
    private JButton button2 = new JButton();
    private Border border1;
    private JPanel jPanel1 = new JPanel();
    private GridBagLayout gridBagLayout1 = new GridBagLayout();
    private GridLayout gridLayout1 = new GridLayout();
    private JLabel upperLeftLabel = new JLabel();
    private JLabel headerLabel = new JLabel();
    private GridBagLayout gridBagLayout2 = new GridBagLayout();
    private JTextField upperLeftTextField = new JTextField();
    private JLabel jLabel1 = new JLabel();

    private LOSDataEditor LOSDataEditor;

    public InsertMapDialog(Frame frame, String title, boolean modal, LOSDataEditor LOSDataEditor) {

        super(frame, title, modal);

        this.frame = (LOSEditorJFrame) frame;
        this.LOSDataEditor = LOSDataEditor;

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
        panel2.setMaximumSize(new Dimension(400, 200));
        panel2.setMinimumSize(new Dimension(400, 200));
        panel2.setLayout(gridBagLayout2);
        button1.setText("OK");
        button1.addActionListener(new insertMapDialog_button1_actionAdapter(this));
        button2.setText("Cancel");
        gridLayout1.setHgap(4);
        button2.addActionListener(new insertMapDialog_button2_actionAdapter(this));
        this.addWindowListener(new insertMapDialog_this_windowAdapter(this));
        InsertMapPanel.setLayout(gridBagLayout1);
        upperLeftLabel.setHorizontalAlignment(SwingConstants.RIGHT);
        upperLeftLabel.setText("Upper-left Hex:");
        headerLabel.setText("Please enter the hex where the upper-left ");
        upperLeftTextField.setText("A1");
        jLabel1.setText("corner of the selected map will be placed:");
        InsertMapPanel.add(jPanel1, new GridBagConstraints(0, 1, 1, 1, 1.0, 0.0
                , GridBagConstraints.CENTER, GridBagConstraints.NONE, new Insets(4, 8, 4, 8), 0, 0));
        jPanel1.add(button1, null);
        jPanel1.add(button2, null);
        InsertMapPanel.add(panel2, new GridBagConstraints(0, 0, 1, 1, 1.0, 1.0
                , GridBagConstraints.CENTER, GridBagConstraints.BOTH, new Insets(0, 0, 0, 2), 0, 0));
        panel2.add(upperLeftLabel, new GridBagConstraints(0, 2, 1, 1, 0.0, 0.0
                , GridBagConstraints.NORTHWEST, GridBagConstraints.NONE, new Insets(20, 118, 4, 12), 88, 5));
        panel2.add(headerLabel, new GridBagConstraints(0, 0, 2, 1, 0.0, 0.0
                , GridBagConstraints.NORTHEAST, GridBagConstraints.NONE, new Insets(-7, 186, 0, 156), 69, 12));
        panel2.add(upperLeftTextField, new GridBagConstraints(1, 2, 1, 2, 0.0, 0.0
                , GridBagConstraints.WEST, GridBagConstraints.NONE, new Insets(17, 0, 0, 0), 65, 0));
        panel2.add(jLabel1, new GridBagConstraints(0, 1, 2, 1, 0.0, 0.0
                , GridBagConstraints.EAST, GridBagConstraints.NONE, new Insets(0, 16, 0, 0), 225, 0));
        getContentPane().add(InsertMapPanel);

        panel2.setPreferredSize(new Dimension(400, 200));
        this.validate();
    }

    // OK
    void button1_actionPerformed(ActionEvent e) {

        String upperLeft = upperLeftTextField.getText();

        // create a  map
        frame.insertMap(LOSDataEditor.getMap(), upperLeft);
        dispose();
    }

    // Cancel
    void button2_actionPerformed(ActionEvent e) {
        dispose();
    }

    void this_windowClosing(WindowEvent e) {
        dispose();
    }
}

class insertMapDialog_button1_actionAdapter implements ActionListener {
    InsertMapDialog adaptee;

    insertMapDialog_button1_actionAdapter(InsertMapDialog adaptee) {
        this.adaptee = adaptee;
    }

    public void actionPerformed(ActionEvent e) {
        adaptee.button1_actionPerformed(e);
    }
}

class insertMapDialog_button2_actionAdapter implements ActionListener {
    InsertMapDialog adaptee;

    insertMapDialog_button2_actionAdapter(InsertMapDialog adaptee) {
        this.adaptee = adaptee;
    }

    public void actionPerformed(ActionEvent e) {
        adaptee.button2_actionPerformed(e);
    }
}

class insertMapDialog_this_windowAdapter extends WindowAdapter {
    InsertMapDialog adaptee;

    insertMapDialog_this_windowAdapter(InsertMapDialog adaptee) {
        this.adaptee = adaptee;
    }

    public void windowClosing(WindowEvent e) {
        adaptee.this_windowClosing(e);
    }
}
