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

/**
 * Title:        BridgeDialog.java
 * Copyright:    Copyright (c) 2001 David Sullivan Zuericher Strasse 6 12205 Berlin Germany. All rights reserved.
 *
 * @author David Sullivan
 * @version 1.0
 */
public class BridgeDialog extends JDialog {
    private JPanel panel1 = new JPanel();
    private JPanel panel2 = new JPanel();
    private JButton button1 = new JButton();
    private JButton button2 = new JButton();
    private Border border1;
    private JPanel jPanel1 = new JPanel();
    private GridBagLayout gridBagLayout1 = new GridBagLayout();
    private GridLayout gridLayout1 = new GridLayout();
    private JLabel jLabel1 = new JLabel();
    private GridBagLayout gridBagLayout2 = new GridBagLayout();
    private JLabel jLabel5 = new JLabel();
    private JComboBox terrainComboBox = new JComboBox();

    private LOSEditorJFrame frame;

    // size variables
    private String terrainName;
    private int roadElevation;

    private JLabel jLabel6 = new JLabel();
    private JTextField roadElevationTextField = new JTextField();

    public BridgeDialog(Frame frame, String title, boolean modal) {
        super(frame, title, modal);

        this.frame = (LOSEditorJFrame) frame;

        try {
            jbInit();
        } catch (Exception e) {
            e.printStackTrace();
        }

        pack();
    }

    public BridgeDialog(
            Frame frame,
            String title,
            boolean modal,
            String terrainName,
            int roadElevation) {
        super(frame, title, modal);

        this.frame = (LOSEditorJFrame) frame;
        this.terrainName = terrainName;
        this.roadElevation = roadElevation;

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
        panel2.setMaximumSize(new Dimension(400, 300));
        panel2.setMinimumSize(new Dimension(400, 300));
        panel2.setPreferredSize(new Dimension(400, 300));
        panel2.setLayout(gridBagLayout2);
        button1.setText("OK");
        button1.addActionListener(new BridgeDialog_button1_actionAdapter(this));
        button2.setText("Cancel");
        gridLayout1.setHgap(4);
        button2.addActionListener(new BridgeDialog_button2_actionAdapter(this));
        this.addWindowListener(new BridgeDialog_this_windowAdapter(this));
        panel1.setLayout(gridBagLayout1);
        jLabel1.setText("Enter the custom bridge parameters:");
        jLabel1.setVerticalAlignment(SwingConstants.TOP);
        jLabel1.setVerticalTextPosition(SwingConstants.TOP);
        jLabel5.setText("Construction:");
        panel1.setMinimumSize(new Dimension(400, 220));
        panel1.setMaximumSize(new Dimension(400, 220));
        terrainComboBox.setMaximumSize(new Dimension(124, 24));
        jLabel6.setText("Road elevation:");
        roadElevationTextField.setText("0");
        panel1.add(panel2, new GridBagConstraints(0, 0, 1, 1, 1.0, 1.0
                , GridBagConstraints.CENTER, GridBagConstraints.BOTH, new Insets(0, 0, 0, 0), 0, 0));
        panel2.add(jLabel1, new GridBagConstraints(0, 0, 2, 1, 0.0, 0.0
                , GridBagConstraints.SOUTHWEST, GridBagConstraints.NONE, new Insets(1, 3, 17, 147), 0, 0));
        panel2.add(roadElevationTextField, new GridBagConstraints(1, 2, 1, 1, 0.0, 0.0
                , GridBagConstraints.WEST, GridBagConstraints.NONE, new Insets(0, 0, 0, 0), 41, 0));
        panel2.add(terrainComboBox, new GridBagConstraints(1, 1, 1, 1, 0.0, 0.0
                , GridBagConstraints.SOUTHWEST, GridBagConstraints.NONE, new Insets(2, 0, 1, 0), 51, 0));
        panel2.add(jLabel5, new GridBagConstraints(0, 1, 1, 1, 0.0, 0.0
                , GridBagConstraints.SOUTHEAST, GridBagConstraints.NONE, new Insets(9, 0, 1, 0), 6, 0));
        panel2.add(jLabel6, new GridBagConstraints(0, 2, 1, 1, 0.0, 0.0
                , GridBagConstraints.SOUTHEAST, GridBagConstraints.NONE, new Insets(6, 0, 0, 0), 11, 0));
        panel1.add(jPanel1, new GridBagConstraints(0, 1, 1, 1, 1.0, 0.0
                , GridBagConstraints.CENTER, GridBagConstraints.NONE, new Insets(4, 8, 4, 8), 0, 0));
        jPanel1.add(button1, null);
        jPanel1.add(button2, null);
        getContentPane().add(panel1);

        terrainComboBox.addItem("Single Hex Wooden Bridge");
        terrainComboBox.addItem("Single Hex Stone Bridge");
        terrainComboBox.addItem("Wooden Bridge");
        terrainComboBox.addItem("Stone Bridge");

        // initialize terrain
        terrainComboBox.setSelectedItem(terrainName);

    }

    // OK
    void button1_actionPerformed(ActionEvent e) {
        @SuppressWarnings("unused")
        int width = 0,
                height = 0;
        boolean error = false;

        // convert input to integer values
        try {
            roadElevation = Integer.parseInt(roadElevationTextField.getText());
        } catch (Exception exp) {
            error = true;
        }

        frame.setBridgeParameters(
                (String) terrainComboBox.getSelectedItem(),
                roadElevation
        );

        if (!error) {
            dispose();
        }
    }

    // Cancel
    void button2_actionPerformed(ActionEvent e) {
        dispose();
    }

    void this_windowClosing(WindowEvent e) {
        dispose();
    }

    void heightTextField_actionPerformed(ActionEvent e) {

    }
}

class BridgeDialog_button1_actionAdapter implements java.awt.event.ActionListener {
    BridgeDialog adaptee;

    BridgeDialog_button1_actionAdapter(BridgeDialog adaptee) {
        this.adaptee = adaptee;
    }

    public void actionPerformed(ActionEvent e) {
        adaptee.button1_actionPerformed(e);
    }
}

class BridgeDialog_button2_actionAdapter implements ActionListener {
    BridgeDialog adaptee;

    BridgeDialog_button2_actionAdapter(BridgeDialog adaptee) {
        this.adaptee = adaptee;
    }

    public void actionPerformed(ActionEvent e) {
        adaptee.button2_actionPerformed(e);
    }
}

class BridgeDialog_this_windowAdapter extends WindowAdapter {
    BridgeDialog adaptee;

    BridgeDialog_this_windowAdapter(BridgeDialog adaptee) {
        this.adaptee = adaptee;
    }

    public void windowClosing(WindowEvent e) {
        adaptee.this_windowClosing(e);
    }
}
