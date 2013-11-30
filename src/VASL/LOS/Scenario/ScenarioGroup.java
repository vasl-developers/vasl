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
package VASL.LOS.Scenario;

import VASL.LOS.Unit.*;

import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.awt.geom.Rectangle2D;
import java.awt.image.ImageObserver;
import java.io.Serializable;

// the bucket class
class Bucket
implements Serializable  {

	// serial ID: version 1.0.0
	static final long serialVersionUID = 000100L;

	// the bucket
	Counter counter;
	int		qty;

	// graphics vars
	private static final int paintAreaWidth = 70;
	private static final int paintAreaHeight = 76;
	private static Rectangle paintArea		= new Rectangle(0, 0, paintAreaWidth, paintAreaHeight);

	// other vars
	private boolean selected = false;

	// constructors
	Bucket (Counter c, int q){

		counter 	= c;
		qty		= q;
	}

	Bucket (){
	}

	public void		setCounter(Counter c)	{ counter = c;}
	public Counter	getCounter() 		{ return counter;}

	public void	setQty(int q)	{ qty = q;}
	public int	getQty()		{ return qty;}

	// graphics routines
	public static Rectangle	getPaintArea(){ return paintArea;}
	public static int		getPaintAreaWidth(){ return paintAreaWidth;}
	public static int		getPaintAreaHeight(){ return paintAreaHeight;}

	public void		setSelected(boolean s){ selected = s;}
	public boolean	isSelected(){ return selected;}

	// paint the bucket
	public void paint(int x, int y, Color background, Color text, Color border, Graphics2D g, ImageObserver obs){

		FontMetrics fm			= g.getFontMetrics();
		String 		displayQty	= "x " + Integer.toString(qty);
		Rectangle2D	qtyRect		= fm.getStringBounds(displayQty, g);
		int			topMargin		= 3;
		int			bottomMargin	= 3;

		// clear the background
		g.setColor(background);
		g.fillRect(x, y, paintAreaWidth, paintAreaHeight);

		// paint the border (red if selected)
		if (selected) {
			g.setColor(Color.red);
			g.fillRect(x, y, paintAreaWidth, 3);	// top
			g.fillRect(x, y, 3, paintAreaHeight);	// left
			g.fillRect(x, y + paintAreaHeight - 2, paintAreaWidth, 2);	// bottom
			g.fillRect(x + paintAreaWidth - 2, y, 2, paintAreaHeight);	// right
		}
		else {
			g.setColor(border);
			g.drawLine(x, y, x + paintAreaWidth, y);	// top
			g.drawLine(x, y, x, y + paintAreaHeight);	// left
			g.drawLine(x, y + paintAreaHeight, x + paintAreaWidth, y + paintAreaHeight);	// bottom
			g.drawLine(x + paintAreaWidth, y, x + paintAreaWidth, y + paintAreaHeight);		// right
		}

		// done if no counter
		if (counter == null) return;

		// set the text color
		g.setColor(text);

		if (counter.getImage() != null){

			// paint the image
			g.drawImage(
				counter.getImage(),
				x + (paintAreaWidth  - (int) counter.getImage().getWidth(obs))/2,
				y + topMargin,
				counter.getImage().getHeight(obs),
				counter.getImage().getWidth(obs),
				obs);

			// draw the qty
			g.drawString(
				displayQty,
				x + (paintAreaWidth - (int) qtyRect.getWidth())/2,
				y + paintAreaHeight - bottomMargin);
		}
		else {

			// paint the image name
			g.drawString(
				counter.getName(),
				x + (paintAreaWidth - (int) fm.getStringBounds(counter.getName(), g).getWidth())/2,
				y + topMargin + (int) fm.getStringBounds(counter.getName(), g).getHeight());

			// draw the qty
			g.drawString(
				displayQty,
				x + (paintAreaWidth - (int) qtyRect.getWidth())/2,
				y + paintAreaHeight - bottomMargin);
		}
	}
}

public class ScenarioGroup
	extends 	JComponent
	implements	Serializable,
			MouseListener,
			MouseMotionListener {

	// the mother scenario
	private Scenario	scenario;
	private int		side;

	// counter buckets
	public final static int MAX_BUCKETS	= 25;
	private Bucket buckets[] = new Bucket[MAX_BUCKETS];

	// group description
	private String description = "";

//	private transient GameEditor	gameEditor;

	// constructors
	public ScenarioGroup(Scenario s, int side) {

		// set the scenario
		scenario = s;
		this.side = side;

		// create the BUCKETS
		for (int x = 0; x < MAX_BUCKETS; x++){

			buckets[x] = new Bucket();
		}

		// set up the mouse listeners
		addMouseListener(this);
		addMouseMotionListener(this);
	}

	// bucket functions
	public Counter	getCounter(int bucket)			{ return buckets[bucket].getCounter();}
	public void	setCounter(int bucket, Counter c)	{ buckets[bucket].setCounter(c);}

	public int	getQty(int bucket)			{ return buckets[bucket].getQty();}
	public void	setQty(int bucket, int q)	{ buckets[bucket].setQty(q);}

	public void	addCounter(int bucket, Counter c, int q)	{

		buckets[bucket].setCounter(c);
		buckets[bucket].setQty(q);

		// set the scenario changed flag
		scenario.setChanged(true);
	}

	public void clearBucket(int bucket){

		buckets[bucket].setCounter(null);
		buckets[bucket].setQty(0);

		// set the scenario changed flag
		scenario.setChanged(true);
	}
	public boolean isEmpty(int bucket) {return buckets[bucket].getQty() == 0;}
	public int getBucketWidth() { return Bucket.getPaintAreaWidth();}
	public int getBucketHeight() { return Bucket.getPaintAreaHeight();}
	public int getNumberOfBuckets() { return MAX_BUCKETS;}

	// game editor functions
//	public void setGameEditor(GameEditor ge){ gameEditor = ge;}

	// side functionws
	public int	getSide()		{ return side;}
	public void	setSide(int s)	{ side = s;}

	// graphics functions
	public void paint(int x, int y, Color background, Color text, Color border, Graphics2D g, ImageObserver obs){

		// left side of each bucket
		int left	= x;

		// paint each bucket
		for (int i = 0; i < MAX_BUCKETS; i++){

			if (buckets[i] != null) {

				// paint the bucket
				buckets[i].paint(left, y, background, text, border, g, obs);

				// advance the left side
				left	+= Bucket.getPaintAreaWidth();
			}
		}
	}

	public void paint(Graphics g){

		Graphics2D graphics = (Graphics2D) g;

		// left side of each bucket
		int left	= 0;

		// paint each bucket
		for (int i = 0; i < MAX_BUCKETS; i++){

			if (buckets[i] != null) {

				// paint the bucket
				buckets[i].paint(left, 0, Color.white, Color.black, Color.black, graphics, this);

				// advance the left side
				left	+= Bucket.getPaintAreaWidth();
			}
		}
	}

	public void resetCounterObjects(
		Infantry[] 		MMCUnitsList,
		Image[] 		MMCImages,
		Unit[] 		SMCUnitsList,
		Image[] 		SMCImages,
		SW[]	 		SWList,
		Image[] 		SWImages,
		Fortification[]	fortificationList,
		Image[] 		fortificationImages
	){

		// step through each bucket
		for (int x = 0; x < MAX_BUCKETS; x++){

			if (buckets[x] != null) {

				Counter c = buckets[x].getCounter();

				if (c != null && (c instanceof Unit)){

					Unit u = (Unit) c;

					if (u instanceof SMC){
						SMC s = (SMC) u;

						// set the image
						s.setImage(SMCImages[s.getTypeID()]);

						// set the ELR units
						if (s.getELRUnitTypeID() != Infantry.NO_UNIT_TYPE_ID){
							s.setELRUnitType((Infantry) SMCUnitsList[s.getELRUnitTypeID()]);
						}
						if (s.getELRFromUnitTypeID() != Infantry.NO_UNIT_TYPE_ID){
							s.setELRFromUnitType((Infantry) SMCUnitsList[s.getELRFromUnitTypeID()]);
						}
					}
					else if (u instanceof Infantry){

						Infantry i = (Infantry) u;
						i.setImage(MMCImages[i.getTypeID()]);

						// set the ELR units
						if (i.getELRUnitTypeID() != Infantry.NO_UNIT_TYPE_ID){
							i.setELRUnitType((Infantry) MMCUnitsList[i.getELRUnitTypeID()]);
						}
						if (i.getELRFromUnitTypeID() != Infantry.NO_UNIT_TYPE_ID){
							i.setELRFromUnitType((Infantry) MMCUnitsList[i.getELRFromUnitTypeID()]);
						}
						if (i.getReducedUnitTypeID() != Infantry.NO_UNIT_TYPE_ID){
							i.setReducedUnitType((Infantry) MMCUnitsList[i.getReducedUnitTypeID()]);
						}
						if (i.getParentSquadTypeID() != Infantry.NO_UNIT_TYPE_ID){
							i.setParentSquadType((Infantry) MMCUnitsList[i.getParentSquadTypeID()]);
						}
					}
				}

				else if (c != null && (c instanceof SW)){

					SW sw = (SW) c;

					// set the image
					sw.setImage(SWImages[sw.getTypeID()]);

				}

				else if (c != null && (c instanceof Fortification)){

					Fortification f = (Fortification) c;

					// set the image
					f.setImage(fortificationImages[f.getTypeID()]);

				}
			}
		}
	}

	/******************************
	Mouse methods
	******************************/
	public void mouseReleased(MouseEvent e) {

		// select/unselect the appropriate bucket
		int bucket = (int) e.getX()/Bucket.getPaintAreaWidth();

		// right mouse button for menu?
		if (e.isPopupTrigger()){

			// get the counter
			Counter c = buckets[bucket].getCounter();

			// create the counter menu
			if (c != null) {

				// create the listener
				ActionListener listener = new PopupMenuActionListener(this, c);

				JPopupMenu	menu = c.getSetupMenu(listener);

				// show menu
				menu.show(e.getComponent(), e.getX(), e.getY());
				return;
			}

			// create the group menu
			else{

				// create the listener
				ActionListener listener = new PopupMenuActionListener(this, null);

				JPopupMenu	menu = getSetupMenu(listener);

				// show menu
				menu.show(e.getComponent(), e.getX(), e.getY());
				return;
			}
		}

		// left click
		else {

			// Ctl click to add selection
			if (e.isControlDown()){

				buckets[bucket].setSelected(!buckets[bucket].isSelected());
			}

			// if double-click, show info for the counter
			else if (e.getClickCount() == 2 && buckets[bucket].getCounter() != null){

//				gameEditor.showAllCounterInformation(buckets[bucket].getCounter());
			}

			// otherwise select the counter
			else {
				scenario.clearAllSelections();
				buckets[bucket].setSelected(true);
			}
		}
		repaint();
	}

	public void popupMenuListener(Counter c, ActionEvent e) {

		// on counter?
		if (c != null) {
			if (c.setupMenuHandler(e)) {

				scenario.setChanged(true);
			}
		}
		else {
			// not on counter
			if (setupMenuHandler(e)) {

				scenario.setChanged(true);
			}
		}
	}

	// menu functions
	public JPopupMenu getSetupMenu(ActionListener listener){

		// create the menu
		JPopupMenu	menu = new JPopupMenu();
		menu.setFont(new Font("Dialog", 0, 11));

		JMenu		elrMenu		= new JMenu("ELR");
		JMenu		capabilitiesMenu	= new JMenu("Special Capabilities");
		JMenu		weaponsMenu		= new JMenu("Special Weapons");
		JMenuItem	item 			= new JMenuItem();

		// create the ELR menu
		for (int x = 0; x < 6; x++){

			item = new JMenuItem("ELR = " + x);
			item.addActionListener(listener);
			elrMenu.add(item);
		}

		menu.add(elrMenu);

		// create the special capabilities menu
		item = new JMenuItem("Elite");
		item.addActionListener(listener);
		capabilitiesMenu.add(item);

		item = new JMenuItem("Stealthy");
		item.addActionListener(listener);
		capabilitiesMenu.add(item);

		item = new JMenuItem("Lax");
		item.addActionListener(listener);
		capabilitiesMenu.add(item);

		item = new JMenuItem("Deployable");
		item.addActionListener(listener);
		capabilitiesMenu.add(item);

		item = new JMenuItem("Ski Equipped");
		item.addActionListener(listener);
		capabilitiesMenu.add(item);

		item = new JMenuItem("Winter Camouflage");
		item.addActionListener(listener);
		capabilitiesMenu.add(item);

		item = new JMenuItem("Commando");
		item.addActionListener(listener);
		capabilitiesMenu.add(item);

		item = new JMenuItem("Combat Engineer");
		item.addActionListener(listener);
		capabilitiesMenu.add(item);

		item = new JMenuItem("Assault Engineer");
		item.addActionListener(listener);
		capabilitiesMenu.add(item);

		item = new JMenuItem("Untrained Boat Use");
		item.addActionListener(listener);
		capabilitiesMenu.add(item);

		item = new JMenuItem("Sewer Movement");
		item.addActionListener(listener);
		capabilitiesMenu.add(item);

		item = new JMenuItem("Ammo Shortage");
		item.addActionListener(listener);
		capabilitiesMenu.add(item);

		menu.add(capabilitiesMenu);

		// create the weapons menu
		item = new JCheckBoxMenuItem("ATMM");
		item.addActionListener(listener);
		weaponsMenu.add(item);

		item = new JCheckBoxMenuItem("MOL");
		item.addActionListener(listener);
		weaponsMenu.add(item);

		item = new JMenuItem("PF");
		item.addActionListener(listener);
		weaponsMenu.add(item);

		menu.add(weaponsMenu);

		// add the setup area item
		item = new JMenuItem("Setup Area...");
		item.addActionListener(listener);
		menu.addSeparator();
		menu.add(item);

		return menu;
	}

	public boolean setupMenuHandler(ActionEvent e) {

		boolean changed = false;

		// get the command
		String cmd = e.paramString().substring(e.paramString().indexOf('=') + 1);

		// setup area option?
		if (cmd.equals("Setup Area..."))	{

/*			// show the setup area dialog box
			ScenarioSetupAreaDialog dialog = new ScenarioSetupAreaDialog(
				gameEditor.getFrame(),
				"Select the scenario group setup area",
				true,
				this
			);

			//Center the dialog box
			Dimension screenSize = Toolkit.getDefaultToolkit().getScreenSize();
			Dimension frameSize = dialog.getSize();
			if (frameSize.height > screenSize.height)	frameSize.height = screenSize.height;
			if (frameSize.width > screenSize.width)  	frameSize.width = screenSize.width;
			dialog.setLocation((screenSize.width - frameSize.width) / 2, (screenSize.height - frameSize.height) / 2);
			dialog.show();

			return false;
*/	
		}

		// apply the command to each counter in the group
		for (int x = 0; x < MAX_BUCKETS; x++){

			if (buckets[x] != null) {

				Counter c = buckets[x].getCounter();

				if (c != null && c instanceof Infantry) {

					Infantry i = (Infantry) c;

					// execute the command
					if      (cmd.equals("ATMM"))		{i.setATMM(!i.hasATMM());	changed = true;}
					else if (cmd.equals("PF"))		{i.setPF(!i.hasPF());		changed = true;}
					else if (cmd.equals("MOL"))		{i.setMOL(!i.hasMOL());		changed = true;}

					else if (cmd.equals("ELR = 0"))	{i.setELR(0);		changed = true;}
					else if (cmd.equals("ELR = 1"))	{i.setELR(1);		changed = true;}
					else if (cmd.equals("ELR = 2"))	{i.setELR(2);		changed = true;}
					else if (cmd.equals("ELR = 3"))	{i.setELR(3);		changed = true;}
					else if (cmd.equals("ELR = 4"))	{i.setELR(4);		changed = true;}
					else if (cmd.equals("ELR = 5"))	{i.setELR(5);		changed = true;}

					else if (cmd.equals("Elite"))			{i.setElite(!i.isElite());				changed = true;}
					else if (cmd.equals("Stealthy"))		{i.setStealthy(!i.isStealthy());			changed = true;}
					else if (cmd.equals("Lax"))			{i.setLax(!i.isLax());					changed = true;}
					else if (cmd.equals("Deployable"))		{i.setDeploy(!i.canDeploy());				changed = true;}
					else if (cmd.equals("Ski Equipped"))	{i.setSkiEquipped(!i.isSkiEquipped());		changed = true;}
					else if (cmd.equals("Winter Camouflage")) {i.setHasWinterCamouflage(!i.hasWinterCamouflage());changed = true;}
					else if (cmd.equals("Commando"))		{i.setCommando(!i.isCommando());			changed = true;}
					else if (cmd.equals("Combat Engineer"))	{i.setCombatEngineer(!i.isCombatEngineer());	changed = true;}
					else if (cmd.equals("Assault Engineer"))	{i.setAssaultEngineer(!i.isAssaultEngineer());	changed = true;}
					else if (cmd.equals("Untrained Boat Use")){i.setUntrainedBoatUse(!i.hasUntrainedBoatUse());changed = true;}
					else if (cmd.equals("Sewer Movement"))	{i.setSewerMovement(!i.hasSewerMovement());	changed = true;}
					else if (cmd.equals("Ammo Shortage"))	{i.setAmmoShortage(!i.hasAmmoShortage());		changed = true;}
				}
			}
		}

		return changed;
	}

	public void mousePressed(MouseEvent e) {}

	public void mouseClicked(MouseEvent e) {}

	public void mouseEntered(MouseEvent e) {}

	public void mouseExited(MouseEvent e) {}

	public void mouseMoved(MouseEvent e) {}

	public void mouseDragged(MouseEvent e){}

	public void clearAllSelections(){

		boolean hadSelection = false;

		// clear the selections
		for (int x = 0; x < MAX_BUCKETS; x++){

			if (buckets[x].isSelected()) {
				buckets[x].setSelected(false);
				hadSelection = true;
			}
		}

		// repaint if necessary
		if (hadSelection) repaint();
	}

	public void addCounterToSelected(Counter c) {

		// add to selected bucket
		for (int x = 0; x < MAX_BUCKETS; x++){

			// bucket selected?
			if (buckets[x].isSelected()) {

				// has a unit?
				if (buckets[x].getCounter() != null) {

					// increase the qty
					buckets[x].setQty(buckets[x].getQty() + 1);

					// set the scenario changed flag
					scenario.setChanged(true);
				}
				else {

					// add the unit
					buckets[x].setCounter((Counter) c.clone());
					buckets[x].setQty(1);

					// initialize the unit
					scenario.initializeCounter(buckets[x].getCounter(), this);

					// set the scenario changed flag
					scenario.setChanged(true);
				}
			}
		}

		repaint();
	}

	public void removeCounterFromSelected() {

		// remove from selected bucket
		for (int x = 0; x < MAX_BUCKETS; x++){

			// bucket selected?
			if (buckets[x].isSelected()) {

				// has a counter and ?
				if (buckets[x].getCounter() != null && buckets[x].getQty() > 1) {

					// decrease the qty
					buckets[x].setQty(buckets[x].getQty() - 1);

					// set the scenario changed flag
					scenario.setChanged(true);
				}
				else if (buckets[x].getCounter() != null && buckets[x].getQty() == 1){

					// remove the counter
					buckets[x].setCounter(null);
					buckets[x].setQty(0);

					// set the scenario changed flag
					scenario.setChanged(true);
				}
			}
		}
		repaint();
	}

	public String 	getDescription(){return description;}
	public void		setDescription(String d){

		description = d;

		// set the scenario changed flag
		scenario.setChanged(true);
	}
}

class PopupMenuActionListener implements ActionListener  {

	Counter 		counter;
	ScenarioGroup 	group;

	PopupMenuActionListener(ScenarioGroup g, Counter c) {

		group = g;
		counter = c;
	}

	public void actionPerformed(ActionEvent e) {
		group.popupMenuListener(counter, e);
	}
}
