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

package VASL.build.module;

import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.FocusAdapter;
import java.awt.event.FocusEvent;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import java.util.Random;

import javax.swing.*;
import javax.swing.border.Border;
import javax.swing.border.EtchedBorder;

import VASL.LOS.Map.Hex;
import VASSAL.build.AbstractBuildable;
import VASSAL.build.Buildable;
import VASSAL.build.GameModule;
import VASSAL.build.module.Chatter;
import VASSAL.build.module.GameComponent;
import VASSAL.build.module.Map;
import VASSAL.command.Command;
import VASSAL.command.CommandEncoder;
import VASSAL.command.NullCommand;
import VASSAL.tools.LaunchButton;
import VASSAL.tools.SequenceEncoder;

/**
 * This components keeps track of OBA draw piles
 */
public class OBA extends AbstractBuildable
	implements GameComponent, CommandEncoder {
	public static final String HOTKEY = "hotkey";

	private JFrame frame;
	private JPanel controls;

	private LaunchButton launch;


	public OBA() {
		frame = new JFrame();
		frame.setTitle("OBA Modules");
		frame.setDefaultCloseOperation(WindowConstants.HIDE_ON_CLOSE);

		frame.getContentPane().setLayout(new BoxLayout(frame.getContentPane(), BoxLayout.Y_AXIS));

		controls = new JPanel();
		controls.setLayout(new BoxLayout(controls, BoxLayout.Y_AXIS));

		frame.getContentPane().add(controls);

		final JButton b = new JButton("Add Module");
		b.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent evt) {
				final Module mod = new Module(OBA.this);
				final CreateModule c = new CreateModule(OBA.this, getModuleCount(), mod.getState());
				c.execute();
				GameModule.getGameModule().sendAndLog(c);
			}
		});
		frame.getContentPane().add(b);
		frame.pack();

		final ActionListener al = new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent e) {
				frame.setVisible(!frame.isShowing());
			}
		};
		launch = new LaunchButton("OBA", null, HOTKEY, al);
		launch.setEnabled(false);

	}

	@Override
	public void addTo(Buildable b) {
		GameModule.getGameModule().getToolBar().add(launch);
		GameModule.getGameModule().getGameState().addGameComponent(this);
		GameModule.getGameModule().addCommandEncoder(this);

	}

	@Override
	public String[] getAttributeNames() {
		return new String[]{HOTKEY};
	}

	@Override
	public void setAttribute(String name, Object value) {
		if (HOTKEY.equals(name)) {
			launch.setAttribute(name, value);
		}
	}

	@Override
	public String getAttributeValueString(String name) {
		if (HOTKEY.equals(name)) {
			return launch.getAttributeValueString(name);
		} else {
			return null;
		}
	}

	@Override
	public String encode(Command c) {
		if (c instanceof UpdateModule) {
			final SequenceEncoder se = new SequenceEncoder("OBA", '/');
			se.append("D")
				.append(((UpdateModule)c).getIndex())
				.append(((UpdateModule)c).getNewState());
			return se.getValue();
		} else if (c instanceof CreateModule) {
			final SequenceEncoder se = new SequenceEncoder("OBA", '/');
			se.append("+")
				.append(((CreateModule)c).getIndex())
				.append(((CreateModule)c).getState());
			return se.getValue();
		} else if (c instanceof RemoveModule) {
			final SequenceEncoder se = new SequenceEncoder("OBA", '/');
			se.append("-")
				.append(((RemoveModule)c).getIndex());
			return se.getValue();
		} else {
			return null;
		}
	}

	@Override
	public Command decode(String s) {
		if (s.startsWith("OBA")) {
			final SequenceEncoder.Decoder st = new SequenceEncoder.Decoder(s, '/');
			st.nextToken();
			int index = -1;
			final String state;
			switch (st.nextToken().charAt(0)) {
				case '+':
					index = Integer.parseInt(st.nextToken());
					state = st.nextToken();
					return new CreateModule(this, index, state);
				case '-':
					index = Integer.parseInt(st.nextToken());
					return new RemoveModule(this, index);
				case 'D':
					index = Integer.parseInt(st.nextToken());
					state = st.nextToken();
					return new UpdateModule(this, index, state);
				default:
					return null;
			}
		} else {
			return null;
		}
	}


	public void addModule(Module mod, int index) {
		controls.add(mod.getControls(), index);
		frame.pack();
	}


	public int getModuleCount() {
		return controls.getComponentCount();
	}


	public Module getModuleAt(int index) {
		// invalid indexes occasionally are passed - not sure why/how - ignore these
		try {
			controls.getComponent(index);
		} catch (ArrayIndexOutOfBoundsException e) {
			return null;
		}
		return index < 0 ? null
			: ((ModuleControls)controls.getComponent(index)).getModule();
	}


	public int indexOf(Module mod) {
		for (int i = 0, max = getModuleCount(); i < max; ++i) {
			if (((ModuleControls)controls.getComponent(i)).getModule() == mod) {
				return i;
			}
		}
		return -1;
	}


	public void removeModule(Module mod) {
		controls.remove(mod.getControls());
		frame.pack();
	}

	@Override
	public void setup(boolean show) {
		launch.setEnabled(show);
		if (!show) {
			controls.removeAll();
			frame.setVisible(false);
		}
	}

	@Override
	public Command getRestoreCommand() {
		Command c = new NullCommand();
		for (int i = 0; i < getModuleCount(); ++i) {
			c = c.append(new CreateModule(this, i, getModuleAt(i).getState()));
		}
		return c;
	}

	public void checkforOBO(){
		int modcount = getModuleCount();
		for (int i = 0; i < modcount; ++i) {
			Module mod = getModuleAt(i);
			if (!mod.getObohex().equals("")){
				mod.getControls().obosave.doClick();

			}
		}
	}


	public static class UpdateModule extends Command {
		private String oldState;
		private String newState;
		private OBA oba;
		private int index;


		public UpdateModule(OBA oba, int index, String newState) {
			this.oba = oba;
			this.index = index;
			this.newState = newState;
		}


		public UpdateModule(Module mod, String oldState, String newState) {
			oba = mod.getOba();
			index = oba.indexOf(mod);
			this.oldState = oldState;
			this.newState = newState;
		}

		@Override
		public void executeCommand() {
			final Module mod = oba.getModuleAt(index);
			oldState = mod.getState();
			mod.setState(newState);
		}

		@Override
		protected Command myUndoCommand() {
			return new UpdateModule(oba, index, oldState);
		}


		public String getOldState() {
			return oldState;
		}


		public String getNewState() {
			return newState;
		}


		public int getIndex() {
			return index;
		}
	}


	public static class CreateModule extends Command {
		private OBA oba;
		private Module mod;
		private int index;
		private String newState;


		public CreateModule(OBA oba, int index, String newState) {
			this.oba = oba;
			this.index = index;
			this.newState = newState;
			mod = new Module(oba);
			mod.setState(newState);

		}

		@Override
		public void executeCommand() {
			oba.addModule(mod, index);
		}

		@Override
		protected Command myUndoCommand() {
			return new RemoveModule(oba, index);
		}


		public int getIndex() {
			return index;
		}


		public String getState() {
			return newState;
		}
	}


	public static class RemoveModule extends Command {
		private String state;
		private OBA oba;
		private int index;


		public RemoveModule(Module mod) {
			this(mod.getOba(), mod.getOba().indexOf(mod));
		}


		public RemoveModule(OBA oba, int index) {
			this.oba = oba;
			this.index = index;
		}

		@Override
		public void executeCommand() {
			final Module mod = oba.getModuleAt(index);
			if (mod != null) {
				state = mod.getState();
				oba.removeModule(mod);
			}
		}

		@Override
		protected Command myUndoCommand() {
			try {
				return new CreateModule(oba, index, state);
			} catch (RuntimeException ex) {
				// ex.printStackTrace();
				return null;
			}
		}


		public int getIndex() {
			return index;
		}
	}


	public static class Module {
		private OBA oba;
		private String owner;
		private String label = "";
		private String showing = "";
		private int nRed = 3;
		private int nBlack = 8;
		private String obohex = "";
		private int obolevel = 0;

		private ModuleControls controls;


		public Module(OBA oba) {
			this.oba = oba;
			owner = GameModule.getUserId();
			controls = new ModuleControls(this);

		}


		public OBA getOba() {
			return oba;
		}


		public int getRed() {
			return nRed;
		}


		public int getBlack() {
			return nBlack;
		}


		public void setRed(int r) {
			nRed = r;
		}


		public void setBlack(int b) {
			nBlack = b;
		}


		public String getShowing() {
			return showing;
		}


		public void setShowing(String s) {
			showing = s;
		}


		public String getOwner() {
			return owner;
		}


		public void setOwner(String s) {
			owner = s;
		}


		public String getLabel() {
			return label;
		}


		public void setLabel(String s) {
			label = s;
		}

		public String getObohex() {return obohex;}

		public void setObohex(String s) {obohex = s;}

		public int getObolevel() {return obolevel;}

		public void setObolevel(int i) {obolevel=i;}


		public ModuleControls getControls() {
			return controls;
		}


		public String getState() {
			final SequenceEncoder se = new SequenceEncoder(',');
			se.append(owner).append(label).append(nRed).append(nBlack).append(showing).append(obohex).append(obolevel);
			return se.getValue();
		}


		public void setState(String s) {
			final SequenceEncoder.Decoder st = new SequenceEncoder.Decoder(s, ',');
			owner = st.nextToken();
			label = st.nextToken();
			nRed = Integer.parseInt(st.nextToken());
			nBlack = Integer.parseInt(st.nextToken());
			showing = st.nextToken();
			obohex = st.nextToken();
			obolevel = Integer.parseInt(st.nextToken());

			controls.refresh();
		}


		public void sendUpdate(String oldState) {
			GameModule.getGameModule().sendAndLog(new UpdateModule(this, oldState, getState()));
		}
	}


	/**
	 * A Module has a deck of cards and controls to set the
	 * number of reds and blacks
	 * The controls are shown only if the user is the owner
	 */
	public static class ModuleControls extends JPanel {
		private JTextField red;
		private JTextField black;
		private JTextField label;
		private JLabel modname;
		private JLabel brnumbers;
		private Module mod;
		private DeckView view;
		private JTextField obohex;
		private JTextField obolevel;
		private JButton obosave;
		private JLabel savetext;

		public ModuleControls(Module m) {
			Border titledBorder = BorderFactory.createTitledBorder("OBA Module");
			setBorder(titledBorder);
			mod = m;
			setLayout(new BoxLayout(this, BoxLayout.X_AXIS));


			final FocusAdapter updateOnFocus = new FocusAdapter() {
				@Override
				public void focusLost(FocusEvent e) {
					valuesUpdated();
				}
			};

			final ActionListener updateOnAction = new ActionListener() {
				@Override
				public void actionPerformed(ActionEvent e) {
					valuesUpdated();
				}
			};

			red = new JTextField(2);
			red.setMaximumSize(red.getPreferredSize());
			red.addActionListener(updateOnAction);
			red.addFocusListener(updateOnFocus);
			red.setEditable(mod.getOwner().equals(GameModule.getUserId()));

			black = new JTextField(2);
			black.setMaximumSize(red.getPreferredSize());
			black.addActionListener(updateOnAction);
			black.addFocusListener(updateOnFocus);
			black.setEditable(mod.getOwner().equals(GameModule.getUserId()));

			label = new JTextField(5);
			label.setMaximumSize(new Dimension(label.getMaximumSize().width, label.getPreferredSize().height));
			label.addActionListener(updateOnAction);
			label.addFocusListener(updateOnFocus);
			label.setEditable(mod.getOwner().equals(GameModule.getUserId()));

			modname = new JLabel("Module Name:   ");
			modname.setMaximumSize(modname.getPreferredSize());
			modname.setVisible(true);

			brnumbers = new JLabel("Draw Pile (B/R): ");
			brnumbers.setMaximumSize(brnumbers.getPreferredSize());
			brnumbers.setVisible(true);

			obohex = new JTextField(5);
			obohex.setMaximumSize(obohex.getPreferredSize());
			obohex.setEditable(mod.getOwner().equals(GameModule.getUserId()));

			obolevel = new JTextField(2);
			obolevel.setMaximumSize(obolevel.getPreferredSize());
			obolevel.setEditable(mod.getOwner().equals(GameModule.getUserId()));

			savetext = new JLabel("Saved");
			savetext.setMaximumSize(savetext.getPreferredSize());
			savetext.setVisible(false);

			final Box vBox = Box.createVerticalBox();
			final Box hBox = Box.createHorizontalBox();
			hBox.setAlignmentX(Component.LEFT_ALIGNMENT);
			final Box hBox2 = Box.createHorizontalBox();
			hBox2.setAlignmentX(Component.LEFT_ALIGNMENT);
			hBox.add(modname);
			hBox.add(label);
			vBox.add(hBox);
			hBox2.add(brnumbers);
			hBox2.add(black);
			hBox2.add(red);
			vBox.add(hBox2);
			add(vBox);
			add(Box.createRigidArea(new Dimension(10,0)));
			final Box voboBox = Box.createVerticalBox();
			final Box hoboBox = Box.createHorizontalBox();
			hoboBox.setAlignmentX(Component.LEFT_ALIGNMENT);
			final Box hobo2Box = Box.createHorizontalBox();
			hobo2Box.setAlignmentX(Component.LEFT_ALIGNMENT);
			hoboBox.add(new JLabel("OBO Bd/Hex:   "));
			hoboBox.add(obohex);
			voboBox.add(hoboBox);
			hobo2Box.add(new JLabel("OBO Level: "));
			hobo2Box.add(obolevel);
			voboBox.add(hobo2Box);
			add(voboBox);
			add(Box.createRigidArea(new Dimension(10,0)));
			obosave = new JButton("Add OBO");
			obosave.addActionListener(new ActionListener() {
				@Override
				public void actionPerformed(ActionEvent evt) {
					savetext.setVisible(true);
					valuesUpdated();
					final AddOffBObserver aOBO = new AddOffBObserver(mod.obohex, mod.obolevel);
					aOBO.execute();
					GameModule.getGameModule().sendAndLog(aOBO);
				}
			});

			final Box svBox = Box.createVerticalBox();
			final Box shBox = Box.createHorizontalBox();
			shBox.setAlignmentX(Component.LEFT_ALIGNMENT);
			svBox.add(obosave);
			svBox.add(savetext);
			shBox.add(svBox);
			add(shBox);

			view = new DeckView(mod);

			add(view);
			add(Box.createRigidArea(new Dimension(20,0)));

		}


		public Module getModule() {
			return mod;
		}


		public void refresh() {
			red.setText(Integer.toString(mod.getRed()));
			black.setText(Integer.toString(mod.getBlack()));
			label.setText(mod.getLabel());
			obolevel.setText(Integer.toString(mod.getObolevel()));
			view.refresh();
		}


		public void valuesUpdated() {
			final String oldState = mod.getState();
			mod.setLabel(label.getText());
			if (!(obohex.getText().equals(""))){
				mod.setObohex(obohex.getText());
			}
			if (!(obohex.getText().equals(""))){
				mod.setObolevel(Integer.parseInt(obolevel.getText()));
			}

			try {
				final int r = Integer.parseInt(red.getText());
				final int b = Integer.parseInt(black.getText());
				if (r != mod.getRed() || b != mod.getBlack()) {
					mod.setShowing("");
				}
				if (r + b == 0 && mod.getShowing().isEmpty()) {
					final Command c = new RemoveModule(mod);
					c.execute();
					GameModule.getGameModule().sendAndLog(c);
					return;
				}
				mod.setRed(r);
				mod.setBlack(b);
				view.refresh();
			} catch (NumberFormatException ex) {
			}
			if (!oldState.equals(mod.getState())) {
				mod.sendUpdate(oldState);
			}
		}
	}


	/**
	 * Draws face-up cards (represented by showing string) and
	 * deck of face-down cards.
	 * Clicking draws the next card or shuffles a face-up card back into the deck
	 */
	public static class DeckView extends Canvas implements MouseListener {
		private static Color cardBack = new Color(200, 200, 200);
		private static int boxX = 22;
		private static int boxY = 33;
		private Random ran = new Random();
		private Module mod;


		DeckView(Module m) {
			mod = m;
			if (m.getOwner().equals(GameModule.getUserId())) {
				addMouseListener(this);
			}
		}


		public void refresh() {
			setSize(getPreferredSize());
			mod.getOba().frame.pack();
			repaint();

		}

		@Override
		public void paint(Graphics g) {
			for (int i = 0; i < mod.getShowing().length(); ++i) {
				if ("r".equals(mod.getShowing().substring(i, i + 1))) {
					g.setColor(Color.red);
					g.fillRect(2 + (boxX + 4) * i, 0, boxX, boxY);
				} else if ("b".equals(mod.getShowing().substring(i, i + 1))) {
					g.setColor(Color.black);
					g.fillRect(2 + (boxX + 4) * i, 0, boxX, boxY);
				}
			}
			for (int i = 0; i < mod.getRed() + mod.getBlack(); ++i) {
				g.setColor(cardBack);
				final int nx = 2 + (boxX + 4) *
					(mod.getRed() + mod.getBlack() + mod.getShowing().length() + 1) + 2 * i;
				final int ny = boxY - 2 * i;
				g.fillRect(nx, ny, boxX, boxY);
				g.setColor(Color.black);
				g.drawRect(nx, ny, boxX, boxY);
			}
		}

		@Override
		public void mousePressed(MouseEvent e) {
		}

		@Override
		public void mouseEntered(MouseEvent e) {
		}

		@Override
		public void mouseExited(MouseEvent e) {
		}

		@Override
		public void mouseClicked(MouseEvent e) {
		}

		@Override
		public void mouseReleased(MouseEvent e) {
			final int x = e.getX();

			final String oldState = mod.getState();
			final int pos = x / (boxX + 4);

			Command report = null;
			if (pos > mod.getRed() + mod.getBlack() + mod.getShowing().length()) {        // Draw from the Deck
				if (mod.getBlack() + mod.getRed() == 0 ||
					x < 2 + (boxX + 4) * (mod.getRed() + mod.getBlack() + mod.getShowing().length() + 1)) {
					return;
				}
				if (ran.nextFloat() <= ((float) mod.getBlack() / ((float)(mod.getRed() + mod.getBlack())))) {
					mod.setBlack(mod.getBlack() - 1);
					mod.setShowing(mod.getShowing() + "b");
					report = new Chatter.DisplayText
						(GameModule.getGameModule().getChatter(), " *** Battery Access Draw = Black ***");
				} else {
					mod.setRed(mod.getRed() - 1);
					mod.setShowing(mod.getShowing() + "r");
					report = new Chatter.DisplayText
						(GameModule.getGameModule().getChatter(), " *** Battery Access Draw = Red ***");
				}
			} else if (pos < mod.getShowing().length()) {    // Shuffle card back into Deck
				final String col = mod.getShowing().substring(pos, pos + 1);
				mod.setShowing(mod.getShowing().substring(0, pos) + mod.getShowing().substring(pos + 1));
				if ("r".equals(col)) {
					mod.setRed(mod.getRed() + 1);
				} else {
					mod.setBlack(mod.getBlack() + 1);
				}
			} else {
				return;
			}
			if (report != null) {
				report.execute();
				report.append(new UpdateModule(mod, oldState, mod.getState()));
				GameModule.getGameModule().sendAndLog(report);
			} else {
				mod.sendUpdate(oldState);
			}
			mod.getControls().refresh();
		}

		@Override
		public Dimension getPreferredSize() {
			return (new Dimension((boxX + 4)
				* (mod.getRed() + mod.getBlack() + mod.getShowing().length() + 3) + 30
				, 2 * (boxY + 4)));
		}
	}

	public static class AddOffBObserver extends Command {
		private int obolevel;
		private String obohex;
		private VASL.LOS.Map.Map LOSMap;

		public AddOffBObserver(String obohex, int obolevel) {
			this.obolevel = obolevel;
			this.obohex = obohex;

		}

		@Override
		public void executeCommand() {
			ASLMap map = GameModule.getGameModule().getComponentsOf(ASLMap.class).iterator().next();
			LOSMap = map.getVASLMap();
			Hex hex = LOSMap.getHex(obohex, map);
			if (hex != null) {
				hex.setOBO(obolevel);
			}
		}

		@Override
		protected Command myUndoCommand() {
			return null;
		}
	}
}
