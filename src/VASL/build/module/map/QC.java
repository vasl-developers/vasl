package VASL.build.module.map;

import java.awt.Color;
import java.awt.Component;
import java.awt.Insets;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.Iterator;
import java.util.List;
import java.util.Vector;

import javax.swing.ButtonGroup;
import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JMenuItem;
import javax.swing.JPopupMenu;
import javax.swing.JRadioButtonMenuItem;
import javax.swing.JSeparator;

import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import VASSAL.Info;
import VASSAL.build.Buildable;
import VASSAL.build.Builder;
import VASSAL.build.GameModule;
import VASSAL.build.module.Chatter;
import VASSAL.build.module.GlobalOptions;
import VASSAL.build.module.Map;
import VASSAL.build.widget.PieceSlot;
import VASSAL.command.AddPiece;
import VASSAL.command.Command;
import VASSAL.command.NullCommand;
import VASSAL.counters.GamePiece;
import VASSAL.counters.KeyBuffer;
import VASSAL.counters.PieceCloner;
import VASSAL.counters.Properties;
import VASSAL.tools.FormattedString;
import VASSAL.tools.imageop.Op;

/**
 * A class to remove all GamePieces with a given name
 */
public class QC implements Buildable 
{
    private final int c_iModeNormal = 4;
    private final int c_iModeCompact = 2;
    private final int c_iModeUltra = 1;

    private Map m_Map;
    private Vector<JButton> m_ButtonsV = new Vector<JButton>();
    private Vector<JButton> m_RemovedButtonsV = new Vector<JButton>();
    private Vector<JPopupMenu> m_PopupMenusV = new Vector<JPopupMenu>();
    private Vector<Entry> m_EntriesV = new Vector<Entry>();
    private JPopupMenu m_mnuMainPopup = null;
    private List<PieceSlot> m_PieceSlotL = null;
    private int m_iMode = c_iModeNormal;

    private void toggleVisibility(JButton button) 
    {
        String l_strButtonName = button.getName();

        if (l_strButtonName != null) 
        {
            if (l_strButtonName.length() > 1) 
            {
                try 
                {
                    int l_iVisibility = Integer.valueOf(l_strButtonName.substring(0, 1));

                    button.setVisible((m_iMode & l_iVisibility) != 0);
                } 
                catch (Exception ex) 
                {
                }
            }
        }
    }

    private void toggleVisibility()
    {
        for (Enumeration<JButton> l_enum = m_ButtonsV.elements(); l_enum.hasMoreElements();) 
        {
            toggleVisibility(l_enum.nextElement());
        }
    }

    protected PieceSlot getPieceSlot(String l_strPieceDefinition) 
    {
        if (m_PieceSlotL == null)
            m_PieceSlotL = GameModule.getGameModule().getAllDescendantComponentsOf(PieceSlot.class);

        for (PieceSlot l_pieceSlot : m_PieceSlotL) 
        {
            String l_id = l_pieceSlot.getGpId();

            if (l_id != null) 
            {
                if (l_id.length() != 0) 
                {
                    if (l_id.compareTo(l_strPieceDefinition) == 0) 
                        return l_pieceSlot;
                }
            }
        }

        return null;
    }

    // read the last working mode from file
    public int loadLastWorkingMode() 
    {
        Integer l_iMode = c_iModeNormal;
        java.util.Properties l_objProperties = new java.util.Properties();
        InputStream l_objInputStream = null;

        // try loading QC.properties from from the home directory
        try 
        {
            l_objInputStream = new FileInputStream(new File(Info.getHomeDir() + System.getProperty("file.separator","\\") + "QC.properties"));
        }
        catch (Exception ex) 
        { 
            l_objInputStream = null; 
        }

        try 
        {
            l_objProperties.load(l_objInputStream);

            l_objInputStream.close();

            l_iMode = Integer.valueOf(l_objProperties.getProperty("QC_working_mode", "4"));
        }
        catch (Exception ex) 
        { 
        }

        return l_iMode;
    }	

    // save the current working mode to file
    public void saveLastWorkingMode() 
    {
        try 
        {
            java.util.Properties l_Props = new java.util.Properties();
            l_Props.setProperty("QC_working_mode", Integer.toString(m_iMode));

            OutputStream l_objOutputStream = new FileOutputStream(new File(Info.getHomeDir() + System.getProperty("file.separator","\\") + "QC.properties"));

            l_Props.store(l_objOutputStream, "QC last working mode");

            l_objOutputStream.flush();
            l_objOutputStream.close();
        }
        catch (Exception ex) 
        {
            ex.printStackTrace();
        }
    }

    public void build(Element e) 
    {	
        m_iMode = loadLastWorkingMode();

        NodeList l_NodeL = e.getElementsByTagName("*");

        for (int i = 0; i < l_NodeL.getLength(); ++i) 
        {
            Element l_Element = (Element) l_NodeL.item(i);

            String l_strActionName = l_Element.getAttribute("name");
            String l_strImageName = l_Element.getAttribute("image");
            String l_strMenu = l_Element.getAttribute("menu");
            String l_strPieceDefinition = Builder.getText(l_Element);

            JButton l_Button = new JButton("");

            if (l_strMenu.compareToIgnoreCase("false") == 0) 
            {
                Entry l_Entry = new Entry(l_strActionName, l_strPieceDefinition, l_strImageName, l_strMenu, null, null, true);

                try 
                {
                    l_Button.setName(l_strActionName);                    
                    l_Button.setIcon(new ImageIcon(Op.load(l_strImageName + ".png").getImage(null)));
                    l_Button.setMargin(new Insets(0, 0, 0, 0));
                } 
                catch (Exception ex) 
                {
                    ex.printStackTrace();
                }

                l_Button.setAlignmentY(0.0F);
                l_Button.addActionListener(new PlaceMarkAction(l_Entry));

                m_EntriesV.addElement(l_Entry);
                m_ButtonsV.addElement(l_Button);
            } 
            else 
            {
                JPopupMenu l_PopupMenu = new JPopupMenu();

                Entry l_Entry = new Entry(l_strActionName, l_strPieceDefinition, l_strImageName, l_strMenu, null, l_PopupMenu, true);

                try 
                {
                    l_Button.setName(l_strActionName);
                    l_Button.setIcon(new ImageIcon(Op.load(l_strImageName + ".png").getImage(null)));
                    l_Button.setMargin(new Insets(0, 0, 0, 0));
                } 
                catch (Exception ex) 
                {
                    ex.printStackTrace();
                }

                l_Button.setAlignmentY(0.0F);
                l_Button.addActionListener(new PlaceMarkAction(l_Entry));

                m_EntriesV.addElement(l_Entry);
                m_ButtonsV.addElement(l_Button);
                m_PopupMenusV.addElement(l_PopupMenu);

                l_PopupMenu.add(new JMenuItem("Select"));
                l_PopupMenu.add(new JSeparator());

                // create the submenu
                String l_strDelims = "[,]";
                String[] l_strTokensA = l_strPieceDefinition.split(l_strDelims);

                for (int l_i = 0; l_i < l_strTokensA.length; l_i++)
                {
                    String l_strSingleDelims = "[:]";
                    String[] l_strTextElemA = l_strTokensA[l_i].split(l_strSingleDelims);

                    if (l_strTextElemA.length == 2) 
                    {
                        String l_strText = l_strTextElemA[0];
                        String l_strID = l_strTextElemA[1];
                        JMenuItem l_MenuItem = new JMenuItem(l_strText);

                        Entry l_PopupEntry = new Entry(l_strActionName + l_strID, l_strID, l_strImageName + l_strID, "false", null, null, false);

                        try 
                        {
                            l_MenuItem.setIcon(new ImageIcon(Op.load(l_strImageName + l_strID + ".png").getImage(null)));
                        } 
                        catch (Exception ex) 
                        {
                            ex.printStackTrace();
                        }

                        l_MenuItem.addActionListener(new PlaceMarkAction(l_PopupEntry));
                        l_PopupMenu.add(l_MenuItem);

                        m_EntriesV.addElement(l_PopupEntry);
                    }
                }
            }
        }
    }

    public org.w3c.dom.Element getBuildElement(org.w3c.dom.Document doc) 
    {
        Element l_Elem = doc.createElement(getClass().getName());

        for (Enumeration<Entry> l_Enum = m_EntriesV.elements(); l_Enum.hasMoreElements();) 
        {
            Entry l_Entry = l_Enum.nextElement();

            if (l_Entry.m_bTopLevel)
            {
                Element l_Sub = doc.createElement("entry");
                
                l_Sub.setAttribute("name", l_Entry.m_strActionName);
                l_Sub.setAttribute("image", l_Entry.m_strImageName);
                l_Sub.setAttribute("menu", l_Entry.m_strMenu);
                l_Sub.appendChild(doc.createTextNode(l_Entry.m_strPieceDefinition));

                l_Elem.appendChild(l_Sub);
            }
        }

        return l_Elem;
    }
  
    private JMenuItem ConvertComponent(Component objComp, String strText)
    {
        JMenuItem l_MenuItem = null;
	  
        if ((objComp instanceof JButton) && (((JButton)objComp).getListeners(ActionListener.class).length > 0))
        {
            l_MenuItem = new JMenuItem(strText);
            l_MenuItem.addActionListener(((JButton)objComp).getListeners(ActionListener.class)[0]);
            l_MenuItem.setIcon(((JButton)objComp).getIcon());
            l_MenuItem.setMargin(new Insets(0,0,0,0));
        }
		
        return l_MenuItem;
    }

    public void addTo(Buildable b) 
    {
        m_Map = (Map) b;
	Vector<Component> l_ComponentV = new Vector<Component>();
        
        if (m_Map.getToolBar().getComponentCount() >= 11)
        {
	    for (int l_i = 0; l_i < m_Map.getToolBar().getComponentCount(); l_i++)
                if (m_Map.getToolBar().getComponent(l_i) instanceof JButton)
                    l_ComponentV.add(m_Map.getToolBar().getComponent(l_i));
	    
            m_Map.getToolBar().removeAll();
	    
	    JButton l_Menu = new JButton("Menu");
	    l_Menu.setAlignmentY(0.0F);
	    
	    m_mnuMainPopup = new JPopupMenu();
	    
	    l_Menu.addActionListener(new ActionListener() 
	    {
	        public void actionPerformed(ActionEvent evt) 
	        {
                    if (evt.getSource() instanceof JButton)
                        m_mnuMainPopup.show((JButton)evt.getSource(), 0, 0);
	        }
	    });
    
            JMenuItem l_SelectItem = new JMenuItem("Select");
            l_SelectItem.setBackground(new Color(255,255,255));
            m_mnuMainPopup.add(l_SelectItem);
            m_mnuMainPopup.addSeparator();
		
            Component l_ShowHide = l_ComponentV.get(0);
            JMenuItem l_ShowHideItem = ConvertComponent(l_ShowHide, "Show/Hide overview window");
		
            if (l_ShowHideItem != null)
	    	m_mnuMainPopup.add(l_ShowHideItem);
		
            m_RemovedButtonsV.add((JButton)l_ShowHide);
		

            Component l_SavePNG = l_ComponentV.get(7);
            JMenuItem l_SavePNGItem = ConvertComponent(l_SavePNG, "Save Map as PNG file");
		
            if (l_SavePNGItem != null)
	    	m_mnuMainPopup.add(l_SavePNGItem);
		
            m_RemovedButtonsV.add((JButton)l_SavePNG);
		

            Component l_SaveText = l_ComponentV.get(8);
            JMenuItem l_SaveTextItem = ConvertComponent(l_SaveText, "Save Map contents as plain text file");
		
            if (l_SaveTextItem != null)
            {
                try
                {
                    l_SaveTextItem.setIcon(new ImageIcon(Op.load("QC/text.png").getImage(null)));
                }
                catch (Exception ex) 
                {
                    ex.printStackTrace();
                }
    		
                m_mnuMainPopup.add(l_SaveTextItem);
            }
		
            m_RemovedButtonsV.add((JButton)l_SaveText);
		
		
            Component l_PickNewBoards = l_ComponentV.get(9);
            JMenuItem l_PickNewBoardsItem = ConvertComponent(l_PickNewBoards, "Pick new boards for this scenario");
		
            if (l_PickNewBoardsItem != null)
	    	m_mnuMainPopup.add(l_PickNewBoardsItem);
		
            m_RemovedButtonsV.add((JButton)l_PickNewBoards);

            m_mnuMainPopup.addSeparator();
            JMenuItem l_SelectQCItem = new JMenuItem("Select QC working mode");
            l_SelectQCItem.setBackground(new Color(255,255,255));
            m_mnuMainPopup.add(l_SelectQCItem);
            m_mnuMainPopup.addSeparator();
    	
            // normal mode
            ButtonGroup l_Group = new ButtonGroup();
            JRadioButtonMenuItem l_NormalMode = new JRadioButtonMenuItem("Normal working mode");
    	
            l_NormalMode.addActionListener(new ActionListener() 
            {
	        public void actionPerformed(ActionEvent evt) 
	        {
                    m_iMode = c_iModeNormal;

                    saveLastWorkingMode();
                    toggleVisibility();
                }
            });
    	
            l_Group.add(l_NormalMode);
            m_mnuMainPopup.add(l_NormalMode);
		
		
            JRadioButtonMenuItem l_CompactMode = new JRadioButtonMenuItem("Compact working mode");
    	
            l_CompactMode.addActionListener(new ActionListener() 
            {
	        public void actionPerformed(ActionEvent evt) 
	        {
                    m_iMode = c_iModeCompact;
	        	
                    saveLastWorkingMode();
                    toggleVisibility();
                }
            });
    	
            l_Group.add(l_CompactMode);
            m_mnuMainPopup.add(l_CompactMode);
		
    	
            JRadioButtonMenuItem l_VeryCompactMode = new JRadioButtonMenuItem("Very compact working mode");
    	
            l_VeryCompactMode.addActionListener(new ActionListener() 
            {
	        public void actionPerformed(ActionEvent evt) 
	        {
                    m_iMode = c_iModeUltra;

                    saveLastWorkingMode();
                    toggleVisibility();
                }
            });
    	
            l_Group.add(l_VeryCompactMode);
            m_mnuMainPopup.add(l_VeryCompactMode);

            l_NormalMode.setSelected(m_iMode == c_iModeNormal);
            l_CompactMode.setSelected(m_iMode == c_iModeCompact);
	    l_VeryCompactMode.setSelected(m_iMode == c_iModeUltra);
    	
            m_Map.getToolBar().add(l_Menu); 
            m_Map.getToolBar().addSeparator();
    	
            if (l_ComponentV.get(1) instanceof JButton)
                ((JButton) l_ComponentV.get(1)).setText("L");
            if (l_ComponentV.get(2) instanceof JButton)
                ((JButton) l_ComponentV.get(2)).setText("l");

            m_Map.getToolBar().add(l_ComponentV.get(1)); // LOS
            m_Map.getToolBar().add(l_ComponentV.get(2)); // los
            m_Map.getToolBar().addSeparator();

            m_Map.getToolBar().add(l_ComponentV.get(3)); // zoom +
            m_Map.getToolBar().add(l_ComponentV.get(4)); // menu zoom
            m_Map.getToolBar().add(l_ComponentV.get(5)); // zoom -
            m_Map.getToolBar().addSeparator();

            m_Map.getToolBar().add(l_ComponentV.get(10)); // toggli le unità
            m_Map.getToolBar().addSeparator();

            Component l_RemoveAll = l_ComponentV.get(6);
    	
            if (l_RemoveAll instanceof JButton)
            {
                ((JButton) l_RemoveAll).setText("ALL ");    			

                try
                {
                    ((JButton) l_RemoveAll).setIcon(new ImageIcon(Op.load("QC/Remove.png").getImage(null)));
                    ((JButton) l_RemoveAll).setMargin(new Insets(0,0,0,0));
                }
                catch (Exception ex) 
                {
                    ex.printStackTrace();
                }
            }
    	
            m_Map.getToolBar().add(l_RemoveAll); // remove all
            m_Map.getToolBar().addSeparator();
    	
            if (l_ComponentV.size() > 11)
            {
	    	for (int l_i = 11; l_i < l_ComponentV.size(); l_i++)
	    	{
                    m_Map.getToolBar().add(l_ComponentV.get(l_i)); 
	    	}
	    	
	    	m_Map.getToolBar().addSeparator();
            }
        }
    
        toggleVisibility();
    
        for (Enumeration<JButton> l_Enum = m_ButtonsV.elements(); l_Enum.hasMoreElements();) 
        {
            m_Map.getToolBar().add(l_Enum.nextElement());
        }

        m_Map.getToolBar().addSeparator();
    }

    public void add(Buildable b) 
    {
    }

    protected boolean isInvisible(GamePiece piece) 
    {
        return Boolean.TRUE.equals(piece.getProperty(Properties.INVISIBLE_TO_ME))
        || Boolean.TRUE.equals(piece.getProperty(Properties.INVISIBLE_TO_OTHERS));
    }
  
    public Command PlaceMarker(Entry entry) 
    {
        Command l_Comm = new NullCommand();
		
        final ArrayList<String> l_PiecesIdA = new ArrayList<String>();
		
        for (Iterator<GamePiece> l_it = KeyBuffer.getBuffer().getPiecesIterator(); l_it.hasNext(); ) 
        {
            l_PiecesIdA.add(l_it.next().getId());	        
        }	
	      
        for (String l_strId : l_PiecesIdA) 
        {
            GamePiece l_createdPiece = null, l_Piece = null;

            l_Piece = GameModule.getGameModule().getGameState().getPieceForId(l_strId);

            if (l_Piece != null)
            {
                if (l_Piece.getMap() != null) 
                {
                    if (!isInvisible(l_Piece))
                    {
                        if (l_Piece.getParent() != null)
                        {
                            if (l_Piece.getParent().topPiece().getProperty(Properties.PIECE_ID).toString().compareTo(entry.m_pieceModelSlot.getGpId()) != 0)
                            {
                                l_createdPiece = PieceCloner.getInstance().clonePiece(entry.m_pieceModelSlot.getPiece());
                                l_createdPiece.setId(null);

                                l_Comm.append(new AddPiece(l_createdPiece));

                                GameModule.getGameModule().getGameState().addPiece(l_createdPiece);

                                l_Comm.append(l_Piece.getMap().placeAt(l_createdPiece, l_Piece.getPosition()));
                                l_Comm.append(l_Piece.getMap().placeOrMerge(l_createdPiece,l_Piece.getPosition()));

                                KeyBuffer.getBuffer().add(l_createdPiece);

                                if (GlobalOptions.getInstance().autoReportEnabled()) 
                                {
                                    FormattedString l_Fmt = new FormattedString();

                                    l_Fmt.setFormat(l_Piece.getMap().getCreateFormat());

                                    l_Fmt.setProperty(Map.PIECE_NAME, l_createdPiece.getLocalizedName());
                                    l_Fmt.setProperty(Map.LOCATION, l_Piece.getMap().localizedLocationName(l_Piece.getPosition()));

                                    String l_strText = l_Fmt.getLocalizedText();

                                    if (l_strText.length() > 0) 
                                    {
                                        l_Comm = l_Comm.append(new Chatter.DisplayText(GameModule.getGameModule().getChatter(), "* " + l_strText));
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        return l_Comm;
    }

    private class PlaceMarkAction implements ActionListener 
    {
        private Entry m_Entry = null;
	  
        public PlaceMarkAction(Entry entry) 
        {
            m_Entry = entry;
        }

        public void actionPerformed(ActionEvent evt) 
        {
            if (m_Entry.m_popupMenu == null)
            {
                if (m_Entry.m_pieceModelSlot == null)
                    m_Entry.m_pieceModelSlot = getPieceSlot(m_Entry.m_strPieceDefinition);
                
                if (m_Entry.m_pieceModelSlot != null)
                {
                    Command l_Command = PlaceMarker(m_Entry);

                    l_Command.execute();

                    GameModule.getGameModule().sendAndLog(l_Command);
                }
            }
            else
            {
                if (evt.getSource() instanceof JButton)
                    m_Entry.m_popupMenu.show((JButton)evt.getSource(), 0, 0);
            }
        }
    }

    private static class Entry 
    {
        public String m_strActionName;
        public String m_strPieceDefinition;
        public String m_strImageName;
        public String m_strMenu;
        public PieceSlot m_pieceModelSlot;
        public JPopupMenu m_popupMenu;
        public boolean m_bTopLevel;

        public Entry(String strName, String strDef, String strImg, String strMenu, PieceSlot pieceModelSlot, JPopupMenu popupMenu, boolean bTopLevel) 
        {
            m_strActionName = strName;
            m_strPieceDefinition = strDef;
            m_strImageName = strImg;
            m_strMenu = strMenu;
            m_pieceModelSlot = pieceModelSlot;
            m_popupMenu = popupMenu;
            m_bTopLevel = bTopLevel;
        }
    }
}
