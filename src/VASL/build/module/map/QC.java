package VASL.build.module.map;

import VASL.build.module.ASLMap;
import java.awt.Color;
import java.awt.Insets;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Enumeration;
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
import VASSAL.build.module.Map;
import VASSAL.build.module.map.PieceMover;
import VASSAL.build.widget.PieceSlot;
import VASSAL.counters.DragBuffer;
import VASSAL.counters.GamePiece;
import VASSAL.counters.KeyBuffer;
import VASSAL.counters.PieceCloner;
import VASSAL.counters.Properties;
import VASSAL.tools.imageop.Op;
import java.awt.Point;
import java.awt.dnd.DnDConstants;
import java.awt.dnd.DragGestureEvent;
import java.awt.dnd.DragGestureListener;
import java.awt.dnd.DragSource;

class ASLButton extends JButton
{
    ButtonEntry m_objEntry;
    
    public ASLButton(ButtonEntry objEntry) 
    {
        m_objEntry = objEntry;

        setDropTarget(PieceMover.AbstractDragHandler.makeDropTarget(this, DnDConstants.ACTION_MOVE, null));

        DragGestureListener dragGestureListener = new DragGestureListener() {
            public void dragGestureRecognized(DragGestureEvent dge) {
                startDrag();
                PieceMover.AbstractDragHandler.getTheDragHandler().dragGestureRecognized(dge);
            }
        };
        DragSource.getDefaultDragSource().createDefaultDragGestureRecognizer(this, DnDConstants.ACTION_MOVE, dragGestureListener);
    }
    
    // Puts counter in DragBuffer. Call when mouse gesture recognized
    protected void startDrag() 
    {
        if (m_objEntry.m_objPieceSlot == null)
            m_objEntry.m_objParent.getPiecesSlot();
            
        if (m_objEntry.m_objPieceSlot != null)
        {
            m_objEntry.m_objPieceSlot.getPiece().setPosition(new Point(0, 0));

            // Erase selection border to avoid leaving selected after mouse dragged out
            m_objEntry.m_objPieceSlot.getPiece().setProperty(Properties.SELECTED, null);

            if (m_objEntry.m_objPieceSlot.getPiece() != null) {
                KeyBuffer.getBuffer().clear();
                DragBuffer.getBuffer().clear();
                GamePiece l_objNewPiece = PieceCloner.getInstance().clonePiece(m_objEntry.m_objPieceSlot.getPiece());
                l_objNewPiece.setProperty(Properties.PIECE_ID, m_objEntry.m_objPieceSlot.getGpId());
                DragBuffer.getBuffer().add(l_objNewPiece);
            }
        }
    }
}

class ASLMenuItem extends JMenuItem
{
    ButtonEntry m_objEntry;
    
    public ASLMenuItem(ButtonEntry objEntry) 
    {
        m_objEntry = objEntry;

        setDropTarget(PieceMover.AbstractDragHandler.makeDropTarget(this, DnDConstants.ACTION_MOVE, null));

        DragGestureListener dragGestureListener = new DragGestureListener() {
            public void dragGestureRecognized(DragGestureEvent dge) {
                startDrag();
                PieceMover.AbstractDragHandler.getTheDragHandler().dragGestureRecognized(dge);
            }
        };
        DragSource.getDefaultDragSource().createDefaultDragGestureRecognizer(this, DnDConstants.ACTION_MOVE, dragGestureListener);
    }
    
    // Puts counter in DragBuffer. Call when mouse gesture recognized
    protected void startDrag() 
    {
        if (m_objEntry.m_objPieceSlot == null)
            m_objEntry.m_objParent.getPiecesSlot();
            
        if (m_objEntry.m_objPieceSlot != null)
        {
            m_objEntry.m_objPieceSlot.getPiece().setPosition(new Point(0, 0));

            // Erase selection border to avoid leaving selected after mouse dragged out
            m_objEntry.m_objPieceSlot.getPiece().setProperty(Properties.SELECTED, null);

            if (m_objEntry.m_objPieceSlot.getPiece() != null) {
                KeyBuffer.getBuffer().clear();
                DragBuffer.getBuffer().clear();
                GamePiece l_objNewPiece = PieceCloner.getInstance().clonePiece(m_objEntry.m_objPieceSlot.getPiece());
                l_objNewPiece.setProperty(Properties.PIECE_ID, m_objEntry.m_objPieceSlot.getGpId());
                DragBuffer.getBuffer().add(l_objNewPiece);
            }
        }
    }
}

class ButtonEntry {
    
    public QC m_objParent;
    public String m_strName;
    public String m_strPieceDefinition;
    public String m_strImageName;
    public String m_strMenu;
    public PieceSlot m_objPieceSlot;
    public JPopupMenu m_popupMenu;
    public boolean m_bTopLevel;

    public ButtonEntry(QC objParent, String strName, String strDef, String strImg, String strMenu, PieceSlot pieceModelSlot, JPopupMenu popupMenu, boolean bTopLevel) 
    {
        m_objParent = objParent;
        m_strName = strName;
        m_strPieceDefinition = strDef;
        m_strImageName = strImg;
        m_strMenu = strMenu;
        m_objPieceSlot = pieceModelSlot;
        m_popupMenu = popupMenu;
        m_bTopLevel = bTopLevel;
    }
}
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
    private Vector<JPopupMenu> m_PopupMenusV = new Vector<JPopupMenu>();
    private Vector<ButtonEntry> m_EntriesV = new Vector<ButtonEntry>();
    private int m_iMode = c_iModeNormal;

    private void toggleVisibility(JButton objButton) 
    {
        String l_strButtonName = objButton.getName();

        if (l_strButtonName != null) 
        {
            if (l_strButtonName.length() > 1) 
            {
                try 
                {
                    int l_iVisibility = Integer.valueOf(l_strButtonName.substring(0, 1));

                    objButton.setVisible((m_iMode & l_iVisibility) != 0);
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

    protected void getPiecesSlot() 
    {
        List<PieceSlot> lar_PieceSlotL = GameModule.getGameModule().getAllDescendantComponentsOf(PieceSlot.class);
        
        for (Enumeration<ButtonEntry> l_Enum = m_EntriesV.elements(); l_Enum.hasMoreElements();) 
        {
            ButtonEntry l_Entry = l_Enum.nextElement();

            if (l_Entry.m_popupMenu == null)
            {
                for (PieceSlot l_objPieceSlot : lar_PieceSlotL) 
                {
                    String l_id = l_objPieceSlot.getGpId();

                    if (l_id != null) 
                    {
                        if (l_id.length() != 0) 
                        {
                            if (l_id.compareTo(l_Entry.m_strPieceDefinition) == 0) 
                            {
                                l_Entry.m_objPieceSlot = l_objPieceSlot;
                                break;
                            }
                        }
                    }
                }
            }
        }
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

            String l_strName = l_Element.getAttribute("name");
            String l_strImageName = l_Element.getAttribute("image");
            String l_strMenu = l_Element.getAttribute("menu");
            String l_strPieceDefinition = Builder.getText(l_Element);

            // is a standard button
            if (l_strMenu.compareToIgnoreCase("false") == 0) 
            {
                ButtonEntry l_Entry = new ButtonEntry(this, l_strName, l_strPieceDefinition, l_strImageName, l_strMenu, null, null, true);
                ASLButton l_ASLButton = new ASLButton(l_Entry);

                try 
                {
                    l_ASLButton.setName(l_strName);                    
                    l_ASLButton.setIcon(new ImageIcon(Op.load(l_strImageName + ".png").getImage(null)));
                    l_ASLButton.setMargin(new Insets(0, 0, 0, 0));
                } 
                catch (Exception ex) 
                {
                    ex.printStackTrace();
                }

                l_ASLButton.setAlignmentY(0.0F);

                m_EntriesV.addElement(l_Entry);
                m_ButtonsV.addElement(l_ASLButton);
            } 
            else // is a submenu
            {
                JButton l_Button = new JButton("");
                JPopupMenu l_PopupMenu = new JPopupMenu();

                ButtonEntry l_Entry = new ButtonEntry(this, l_strName, l_strPieceDefinition, l_strImageName, l_strMenu, null, l_PopupMenu, true);

                try 
                {
                    l_Button.setName(l_strName);
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

                        ButtonEntry l_PopupEntry = new ButtonEntry(this, l_strName + l_strID, l_strID, l_strImageName + l_strID, "false", null, null, false);
                        ASLMenuItem l_MenuItem = new ASLMenuItem(l_PopupEntry);
                        l_MenuItem.setText(l_strText);

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

        for (Enumeration<ButtonEntry> l_Enum = m_EntriesV.elements(); l_Enum.hasMoreElements();) 
        {
            ButtonEntry l_Entry = l_Enum.nextElement();

            if (l_Entry.m_bTopLevel)
            {
                Element l_Sub = doc.createElement("entry");
                
                l_Sub.setAttribute("name", l_Entry.m_strName);
                l_Sub.setAttribute("image", l_Entry.m_strImageName);
                l_Sub.setAttribute("menu", l_Entry.m_strMenu);
                l_Sub.appendChild(doc.createTextNode(l_Entry.m_strPieceDefinition));

                l_Elem.appendChild(l_Sub);
            }
        }

        return l_Elem;
    }
  
    public void addTo(Buildable b) 
    {
        m_Map = (Map) b;
        
        ((ASLMap)m_Map).getPopupMenu().addSeparator();
        JMenuItem l_SelectQCItem = new JMenuItem("Select QC working mode");
        l_SelectQCItem.setBackground(new Color(255,255,255));
        ((ASLMap)m_Map).getPopupMenu().add(l_SelectQCItem);
        ((ASLMap)m_Map).getPopupMenu().addSeparator();

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
        ((ASLMap)m_Map).getPopupMenu().add(l_NormalMode);


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
        ((ASLMap)m_Map).getPopupMenu().add(l_CompactMode);


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
        ((ASLMap)m_Map).getPopupMenu().add(l_VeryCompactMode);

        l_NormalMode.setSelected(m_iMode == c_iModeNormal);
        l_CompactMode.setSelected(m_iMode == c_iModeCompact);
        l_VeryCompactMode.setSelected(m_iMode == c_iModeUltra);
    
        toggleVisibility();
    
        m_Map.getToolBar().addSeparator();
        
        for (Enumeration<JButton> l_Enum = m_ButtonsV.elements(); l_Enum.hasMoreElements();) 
        {
            m_Map.getToolBar().add(l_Enum.nextElement());
        }

        m_Map.getToolBar().addSeparator();
    }

    public void add(Buildable b) 
    {
    }

    private class PlaceMarkAction implements ActionListener 
    {
        private ButtonEntry m_Entry = null;
	  
        public PlaceMarkAction(ButtonEntry entry) 
        {
            m_Entry = entry;
        }

        public void actionPerformed(ActionEvent evt) 
        {
            if (m_Entry.m_popupMenu != null)
            {
                if (evt.getSource() instanceof JButton)
                    m_Entry.m_popupMenu.show((JButton)evt.getSource(), 0, 0);
            }
        }
    }
}
