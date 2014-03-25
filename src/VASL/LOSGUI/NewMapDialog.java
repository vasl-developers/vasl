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
 * Title:        NewMapDialog.java
 * Copyright:    Copyright (c) 2001 David Sullivan Zuericher Strasse 6 12205 Berlin Germany. All rights reserved.
 *
 * @author David Sullivan
 * @version 1.0
 */
public class NewMapDialog extends JDialog {

    private LOSEditorJFrame frame;

    private JPanel NewMapPanel = new JPanel();
    private JPanel panel2 = new JPanel();
    private JButton button1 = new JButton();
    private JButton button2 = new JButton();
    private Border border1;
    private JPanel jPanel1 = new JPanel();
    private GridBagLayout gridBagLayout1 = new GridBagLayout();
    private GridLayout gridLayout1 = new GridLayout();
    private JLabel rowsLabel = new JLabel();
    private JLabel coldLabel = new JLabel();
    private JLabel headerLabel = new JLabel();
    private GridBagLayout gridBagLayout2 = new GridBagLayout();
    private JTextField rowsTextField = new JTextField();
    private JTextField colsTextField = new JTextField();

    public NewMapDialog(Frame frame, String title, boolean modal) {

        super(frame, title, modal);

        this.frame = (LOSEditorJFrame) frame;
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
        button1.addActionListener(new newMapDialog_button1_actionAdapter(this));
        button2.setText("Cancel");
        gridLayout1.setHgap(4);
        button2.addActionListener(new newMapDialog_button2_actionAdapter(this));
        this.addWindowListener(new newMapDialog_this_windowAdapter(this));
        NewMapPanel.setLayout(gridBagLayout1);
        rowsLabel.setHorizontalAlignment(SwingConstants.RIGHT);
        rowsLabel.setText("Rows:");
        coldLabel.setHorizontalAlignment(SwingConstants.RIGHT);
        coldLabel.setText("Columns:");
        headerLabel.setText("Please enter the size of the new map:");
        rowsTextField.setText("10");
        colsTextField.setMaximumSize(new Dimension(18, 21));
        colsTextField.setMinimumSize(new Dimension(18, 21));
        colsTextField.setText("33");
        NewMapPanel.add(jPanel1, new GridBagConstraints(0, 1, 1, 1, 1.0, 0.0
                , GridBagConstraints.CENTER, GridBagConstraints.NONE, new Insets(4, 8, 4, 8), 0, 0));
        jPanel1.add(button1, null);
        jPanel1.add(button2, null);
        NewMapPanel.add(panel2, new GridBagConstraints(0, 0, 1, 1, 1.0, 1.0
                , GridBagConstraints.CENTER, GridBagConstraints.BOTH, new Insets(0, 0, 0, 2), 0, 0));
        panel2.add(rowsLabel, new GridBagConstraints(0, 1, 1, 2, 0.0, 0.0
                , GridBagConstraints.NORTHWEST, GridBagConstraints.NONE, new Insets(0, 118, 4, 18), 17, 5));
        panel2.add(rowsTextField, new GridBagConstraints(1, 1, 1, 1, 0.0, 0.0
                , GridBagConstraints.WEST, GridBagConstraints.NONE, new Insets(0, 0, 0, 0), 65, 0));
        panel2.add(colsTextField, new GridBagConstraints(1, 3, 2, 1, 0.0, 0.0
                , GridBagConstraints.NORTHWEST, GridBagConstraints.NONE, new Insets(25, 0, 0, 0), 73, 0));
        panel2.add(coldLabel, new GridBagConstraints(0, 3, 1, 1, 0.0, 0.0
                , GridBagConstraints.WEST, GridBagConstraints.NONE, new Insets(25, 88, 73, 9), 43, 5));
        panel2.add(headerLabel, new GridBagConstraints(0, 0, 3, 1, 0.0, 0.0
                , GridBagConstraints.NORTH, GridBagConstraints.NONE, new Insets(17, 112, 9, 230), 4, 5));
        getContentPane().add(NewMapPanel);

        panel2.setPreferredSize(new Dimension(400, 200));
        this.validate();
    }

    // OK
    void button1_actionPerformed(ActionEvent e) {
        int cols = 0,
                rows = 0;
        boolean error = false;

        // convert input to integer values
        try {
            cols = Integer.parseInt(colsTextField.getText());
            rows = Integer.parseInt(rowsTextField.getText());
        } catch (Exception exp) {
            error = true;
        }

        if (!error) {

            // number of cols even?
            if (cols % 2 == 1) {

                // create a new map
                dispose();
                frame.createNewMap(cols, rows);
            } else {
                // show error dialog box
            }
        } else {

            // show error dialog box
        }
    }

    // Cancel
    void button2_actionPerformed(ActionEvent e) {
        dispose();
    }

    void this_windowClosing(WindowEvent e) {
        dispose();
    }
}

class newMapDialog_button1_actionAdapter implements ActionListener {
    NewMapDialog adaptee;

    newMapDialog_button1_actionAdapter(NewMapDialog adaptee) {
        this.adaptee = adaptee;
    }

    public void actionPerformed(ActionEvent e) {
        adaptee.button1_actionPerformed(e);
    }
}

class newMapDialog_button2_actionAdapter implements ActionListener {
    NewMapDialog adaptee;

    newMapDialog_button2_actionAdapter(NewMapDialog adaptee) {
        this.adaptee = adaptee;
    }

    public void actionPerformed(ActionEvent e) {
        adaptee.button2_actionPerformed(e);
    }
}

class newMapDialog_this_windowAdapter extends WindowAdapter {
    NewMapDialog adaptee;

    newMapDialog_this_windowAdapter(NewMapDialog adaptee) {
        this.adaptee = adaptee;
    }

    public void windowClosing(WindowEvent e) {
        adaptee.this_windowClosing(e);
    }
}
