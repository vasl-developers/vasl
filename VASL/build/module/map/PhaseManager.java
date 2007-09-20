/*
 * $Id: PhaseManager.java 133 2004-01-03 02:07:37Z davidsullivan $
 *
 * Copyright (c) 2003 by David Sullivan
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

import java.awt.Dimension;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.ArrayList;
import java.util.Vector;

import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPopupMenu;
import javax.swing.JTextField;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import VASSAL.build.Buildable;
import VASSAL.build.module.Map;
/**
 * @author David Sullivan
 *
 * Manages customized phase logic
 */
public class PhaseManager implements Buildable {

	// local variables
	private JButton nextPhaseButton, previousPhaseButton;
	private JLabel phaseLabel = new JLabel();
	private JTextField phaseTextField;
	private JPopupMenu popup;
	private Vector entries = new Vector();
	private int 		fieldHeight, 
						fieldWidth,
						labelWidth;
	private ArrayList	phases = new ArrayList();
	private int			currentPhase = 0; 
	private Map map;

	// constants
	private static final String PHASE = "phase";
	private static final String FIELD_WIDTH = "fieldWidth";
	private static final String FIELD_HEIGHT = "fieldHeight";
	private static final String PHASE_LABEL = "label";
	private static final String LABEL_WIDTH = "labelWidth";
	
	public void build(Element e) {
		
		// local variables
		String	tempString = "";
		int		tempInt = 0;
		
		// get the control information
		NodeList n 	= e.getElementsByTagName("VASL.build.module.map.PhaseManager");
		fieldWidth  = Integer.parseInt(e.getAttribute(FIELD_WIDTH));
		fieldHeight = Integer.parseInt(e.getAttribute(FIELD_HEIGHT));
		labelWidth	= Integer.parseInt(e.getAttribute(LABEL_WIDTH));

		
		// set up the widgets
		phaseLabel.setText(e.getAttribute(PHASE_LABEL));
		phaseLabel.setMaximumSize(new Dimension(labelWidth, fieldHeight));
		phaseLabel.setVerticalAlignment(JLabel.BOTTOM);
		phaseLabel.setHorizontalAlignment(JLabel.RIGHT);
		
		previousPhaseButton = new JButton("<<");
		previousPhaseButton.setToolTipText("Previous phase");
		previousPhaseButton.setAlignmentY(0.0F);
		previousPhaseButton.addActionListener(new ActionListener() {
		  public void actionPerformed(ActionEvent evt) {
			previousPhase();
		  }
		 });

		nextPhaseButton = new JButton(">>");
		nextPhaseButton.setToolTipText("Next phase");
		nextPhaseButton.setAlignmentY(0.0F);
		nextPhaseButton.addActionListener(new ActionListener() {
		  public void actionPerformed(ActionEvent evt) {
			nextPhase();
		  }
		});
		
		phaseTextField = new JTextField();
		phaseTextField.setEditable(false);
		phaseTextField.setAlignmentY(0.0F);
		phaseTextField.setToolTipText("Current phase");
		phaseTextField.setText("<none>");	
		phaseTextField.setMaximumSize(new Dimension(fieldWidth, fieldHeight));
				
		// load the phases
	  	n = e.getElementsByTagName("phase");
		for (int i = 0; i < n.getLength(); ++i) {

			// read the phase
			String phase = ((Element) n.item(i)).getAttribute("name");
			
			// add it to the collection
			phases.add(i, phase);

			System.out.println(phase);
		}

		// set the initial phase
		phaseTextField.setText((String) phases.get(0));	

		// build the menu
		
	}

	/* (non-Javadoc)
	 * @see VASSAL.build.Buildable#addTo(VASSAL.build.Buildable)
	 */
	public void addTo(Buildable b) {
		map = (Map) b;
		map.getToolBar().add(phaseLabel);
		map.getToolBar().add(previousPhaseButton);
		map.getToolBar().add(phaseTextField);
		map.getToolBar().add(nextPhaseButton);
		
	}
	/* (non-Javadoc)
	 * @see VASSAL.build.Buildable#add(VASSAL.build.Buildable)
	 */
	public void add(Buildable child) {
		// TODO Auto-generated method stub
		
	}
	/* (non-Javadoc)
	 * @see VASSAL.build.Buildable#getBuildElement(org.w3c.dom.Document)
	 */
	public Element getBuildElement(Document doc) {
		// TODO Auto-generated method stub
		return null;
	}
	private void previousPhase() {
			
		if (currentPhase == 0){
		
			currentPhase = phases.size() -1;
		} 
		else currentPhase--;
		
		phaseTextField.setText((String) phases.get(currentPhase));
	}

	private void nextPhase() {
			
		if (currentPhase == phases.size() -1){
		
			currentPhase = 0;
		} 
		else currentPhase++;
		
		phaseTextField.setText((String) phases.get(currentPhase));
	}
}
