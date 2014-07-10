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
import java.awt.dnd.DragSourceDragEvent;
import java.awt.dnd.DragSourceDropEvent;
import java.awt.dnd.DragSourceEvent;
import java.awt.dnd.DragSourceListener;
import java.io.FilenameFilter;
import java.io.IOException;
import java.io.StringReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.swing.JOptionPane;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerConfigurationException;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import org.w3c.dom.Attr;
import org.w3c.dom.Document;
import org.xml.sax.Attributes;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.DefaultHandler;

class QCButton extends JButton
// <editor-fold>
{
    QCPieceEntry m_objEntry;
    
    public QCButton(QCPieceEntry objEntry) 
    {
        super();
        m_objEntry = objEntry;
    }
    
    public void InitDragDrop()
    {
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
// </editor-fold>

class QCMenuItem extends JMenuItem implements DragSourceListener
// <editor-fold>
{
    QCPieceEntry m_objEntry;
    
    public QCMenuItem(QCPieceEntry objEntry) 
    {
        super();
        m_objEntry = objEntry;
    }
    
    public void InitDragDrop()
    {
        DragGestureListener dragGestureListener = new DragGestureListener() {
            public void dragGestureRecognized(DragGestureEvent dge) {
                if ((dge.getComponent() != null) && (dge.getComponent() instanceof QCMenuItem))
                {
                    DragSource.getDefaultDragSource().addDragSourceListener(((QCMenuItem)dge.getComponent()));
                }
                
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

    public void dragEnter(DragSourceDragEvent dsde) {
    }

    public void dragOver(DragSourceDragEvent dsde) {
    }

    public void dropActionChanged(DragSourceDragEvent dsde) {
    }

    public void dragExit(DragSourceEvent dse) {
    }

    public void dragDropEnd(DragSourceDropEvent dsde) {
        if (getParent() != null)
            if (getParent() instanceof JPopupMenu)
                ((JPopupMenu) getParent()).setVisible(false);
        
        DragSource.getDefaultDragSource().removeDragSourceListener(this);
    }
}
// </editor-fold>

class QCConfiguration extends DefaultHandler 
// <editor-fold>
{
    private QC m_objQC;
    private File m_objFile;
    private String m_strDescription;
    private ArrayList<QCConfigurationEntry> mar_objListConfigurationEntry;
    private QCConfigurationEntry m_objCurrentSubMenu;
    
    public QCConfiguration(QC objQC, File objFile)
    {
        m_objCurrentSubMenu = null;
        
        m_objQC = objQC;
        m_objFile = objFile;
        
        m_strDescription = "";
        mar_objListConfigurationEntry = new ArrayList<QCConfigurationEntry>();
    }    

    /**
     * @return the m_objQC
     */
    public QC getQC() {
        return m_objQC;
    }

    /**
     * @return the m_objFilePath
     */
    public File getFile() {
        return m_objFile;
    }

    /**
     * @return the m_strDescription
     */
    public String getDescription() {
        return m_strDescription;
    }

    /**
     * @param m_strDescription the m_strDescription to set
     */
    public void setDescription(String m_strDescription) {
        this.m_strDescription = m_strDescription;
    }

    /**
     * @return the mar_objConfigurationEntry
     */
    public ArrayList<QCConfigurationEntry> getListConfigurationEntry() {
        return mar_objListConfigurationEntry;
    }
    
    @Override
    public void startElement (String uri, String localName,
                              String qName, Attributes attributes)
        throws SAXException
    {
        if (qName.equalsIgnoreCase("qcconfig")) 
        {
            setDescription(attributes.getValue("descr"));
        }
        else if (qName.equalsIgnoreCase("qcsubmenu")) 
        {
            QCConfigurationEntry l_newEntry = new QCConfigurationEntry(getQC());
            
            l_newEntry.setMenu(true);
            l_newEntry.setPieceSlot(attributes.getValue("slot"));
            
            if (m_objCurrentSubMenu != null)
            {
                l_newEntry.setParent(m_objCurrentSubMenu);
                m_objCurrentSubMenu.getListConfigurationEntry().add(l_newEntry);
            }
            else
                mar_objListConfigurationEntry.add(l_newEntry);
            
            m_objCurrentSubMenu = l_newEntry;
        }
        else if (qName.equalsIgnoreCase("qcentry")) 
        {
            QCConfigurationEntry l_newEntry = new QCConfigurationEntry(getQC());

            l_newEntry.setPieceSlot(attributes.getValue("slot"));
            
            if (m_objCurrentSubMenu != null)
                m_objCurrentSubMenu.getListConfigurationEntry().add(l_newEntry);
            else
                mar_objListConfigurationEntry.add(l_newEntry);            
        }
    }  
    
    @Override
    public void endElement (String uri, String localName, String qName)
        throws SAXException
    {
        if (qName.equalsIgnoreCase("qcsubmenu")) 
            m_objCurrentSubMenu = m_objCurrentSubMenu.getParent();
    }  
    
    public boolean SaveXML() 
    {
        try 
        {
            if (m_objFile != null)
            {
                if (!m_objFile.exists())
                    m_objFile.createNewFile();

                DocumentBuilderFactory l_objDocumentBuilderFactory = DocumentBuilderFactory.newInstance();
                DocumentBuilder l_objDocumentBuilder = l_objDocumentBuilderFactory.newDocumentBuilder();

                // root elements
                Document l_objDocument = l_objDocumentBuilder.newDocument();
                Element l_objRootElement = l_objDocument.createElement("qcconfig");

                Attr l_objAttributes = l_objDocument.createAttribute("descr");
                l_objAttributes.setValue(getDescription());
                l_objRootElement.setAttributeNode(l_objAttributes);

                l_objDocument.appendChild(l_objRootElement);

                for (QCConfigurationEntry l_objConfigurationEntry : mar_objListConfigurationEntry)
                    l_objConfigurationEntry.WriteXML(l_objDocument, l_objRootElement);

                // write the content into xml file
                TransformerFactory l_objTransformerFactory = TransformerFactory.newInstance();
                Transformer l_objTransformer = l_objTransformerFactory.newTransformer();
                DOMSource l_objDOMSource = new DOMSource(l_objDocument);

                l_objTransformer.transform(l_objDOMSource, new StreamResult(new FileOutputStream(m_objFile)));
            }
            
            return true;
 
        } 
        catch (Exception e) 
        {
        }

        return false;
    }
}
// </editor-fold>

class QCConfigurationEntry
// <editor-fold>
{
    private QC m_objQC;
    private boolean m_bMenu;
    private ArrayList<QCConfigurationEntry> mar_objListConfigurationEntry;
    private String m_strPieceSlot;    
    private QCConfigurationEntry m_objParentEntry;
    
    public QCConfigurationEntry(QC objQC)
    {
        m_objQC = objQC;
        m_bMenu = false;
        mar_objListConfigurationEntry = null;
        m_strPieceSlot = "";
        m_objParentEntry = null;
    }   

    /**
     * @return the m_bMenu
     */
    public boolean isMenu() {
        return m_bMenu;
    }

    /**
     * @param m_bMenu the m_bMenu to set
     */
    public void setMenu(boolean m_bMenu) {
        this.m_bMenu = m_bMenu;
        
        if (m_bMenu)
            mar_objListConfigurationEntry = new ArrayList<QCConfigurationEntry>();
    }

    /**
     * @return the mar_objListConfigurationEntry
     */
    public ArrayList<QCConfigurationEntry> getListConfigurationEntry() {
        return mar_objListConfigurationEntry;
    }

    /**
     * @return the m_strPieceSlot
     */
    public String getPieceSlot() {
        return m_strPieceSlot;
    }

    /**
     * @param m_strPieceSlot the m_strPieceSlot to set
     */
    public void setPieceSlot(String m_strPieceSlot) {
        this.m_strPieceSlot = m_strPieceSlot;
    }

    /**
     * @return the m_objQC
     */
    public QC getQC() {
        return m_objQC;
    }

    /**
     * @return the m_objParentEntry
     */
    public QCConfigurationEntry getParent() {
        return m_objParentEntry;
    }

    /**
     * @param m_objParentEntry the m_objParentEntry to set
     */
    public void setParent(QCConfigurationEntry m_objParentEntry) {
        this.m_objParentEntry = m_objParentEntry;
    }

    void WriteXML(Document objDocument, Element objElement) 
    {
        if (isMenu())
        {
            Element l_objEntry = objDocument.createElement("qcsubmenu");

            // set attribute to staff element
            Attr l_objAttributes = objDocument.createAttribute("slot");
            l_objAttributes.setValue(getPieceSlot());
            l_objEntry.setAttributeNode(l_objAttributes);
            
            objElement.appendChild(l_objEntry);
            
            for (QCConfigurationEntry l_objConfigurationEntry : mar_objListConfigurationEntry)
                l_objConfigurationEntry.WriteXML(objDocument, l_objEntry);
        }
        else
        {
            Element l_objEntry = objDocument.createElement("qcentry");

            // set attribute to staff element
            Attr l_objAttributes = objDocument.createAttribute("slot");
            l_objAttributes.setValue(getPieceSlot());
            l_objEntry.setAttributeNode(l_objAttributes);
            
            objElement.appendChild(l_objEntry);
        }
    }
}
// </editor-fold>

class QCPieceEntry 
// <editor-fold>
{
    
    public QC m_objParent;
    public String m_strName;
    public String m_strPieceDefinition;
    public String m_strImageName;
    public String m_strMenu;
    public PieceSlot m_objPieceSlot;
    public JPopupMenu m_popupMenu;
    public boolean m_bTopLevel;

    public QCPieceEntry(QC objParent, String strName, String strDef, String strImg, String strMenu, PieceSlot pieceModelSlot, JPopupMenu popupMenu, boolean bTopLevel) 
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
// </editor-fold>

/**
 * A class to remove all GamePieces with a given name
 */
public class QC implements Buildable 
{
    private final String mc_strDefaultConfig = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" +
"<qcconfig descr=\"Default configuration\">\n" +
"	<qcentry slot=\"0\"/>\n" +
"	<qcentry slot=\"11\"/>\n" +
"	<qcentry slot=\"12\"/>\n" +
"	<qcentry slot=\"13\"/>\n" +
"	<qcentry slot=\"15\"/>\n" +
"	<qcentry slot=\"18\"/>\n" +
"	<qcentry slot=\"19\"/>\n" +
"	<qcentry slot=\"21\"/>\n" +
"	<qcentry slot=\"1\"/>\n" +
"	<qcentry slot=\"2\"/>\n" +
"	<qcentry slot=\"4\"/>\n" +
"	<qcentry slot=\"63\"/>\n" +
"	<qcsubmenu slot=\"47\">\n" +
"		<qcentry slot=\"47\"/>\n" +
"		<qcentry slot=\"48\"/>\n" +
"		<qcentry slot=\"49\"/>\n" +
"		<qcentry slot=\"50\"/>\n" +
"		<qcentry slot=\"51\"/>\n" +
"		<qcentry slot=\"52\"/>\n" +
"	</qcsubmenu>\n" +
"	<qcsubmenu slot=\"53\">\n" +
"		<qcentry slot=\"53\"/>\n" +
"		<qcentry slot=\"54\"/>\n" +
"		<qcentry slot=\"55\"/>\n" +
"		<qcentry slot=\"56\"/>\n" +
"	</qcsubmenu>\n" +
"	<qcsubmenu slot=\"6\">\n" +
"		<qcentry slot=\"6\"/>\n" +
"		<qcentry slot=\"57\"/>\n" +
"		<qcentry slot=\"59\"/>\n" +
"		<qcentry slot=\"61\"/>\n" +
"		<qcentry slot=\"72\"/>\n" +
"		<qcentry slot=\"58\"/>\n" +
"		<qcentry slot=\"60\"/>\n" +
"		<qcentry slot=\"62\"/>\n" +
"		<qcentry slot=\"114\"/>\n" +
"	</qcsubmenu>\n" +
"	<qcentry slot=\"69\"/>\n" +
"	<qcentry slot=\"146\"/>\n" +
"	<qcentry slot=\"64\"/>\n" +
"	<qcsubmenu slot=\"73\">\n" +
"		<qcentry slot=\"73\"/>\n" +
"		<qcentry slot=\"74\"/>\n" +
"		<qcentry slot=\"83\"/>\n" +
"		<qcentry slot=\"85\"/>\n" +
"		<qcentry slot=\"88\"/>\n" +
"	</qcsubmenu>\n" +
"	<qcsubmenu slot=\"6390\">\n" +
"		<qcentry slot=\"6390\"/>\n" +
"		<qcentry slot=\"6391\"/>\n" +
"	</qcsubmenu>\n" +
"	<qcsubmenu slot=\"68\">\n" +
"		<qcentry slot=\"68\"/>\n" +
"		<qcentry slot=\"66\"/>\n" +
"		<qcentry slot=\"5903\"/>\n" +
"		<qcentry slot=\"5902\"/>\n" +
"	</qcsubmenu>\n" +
"	<qcsubmenu slot=\"423\">\n" +
"		<qcentry slot=\"423\"/>\n" +
"		<qcentry slot=\"773\"/>\n" +
"		<qcentry slot=\"1066\"/>\n" +
"		<qcentry slot=\"1589\"/>\n" +
"		<qcentry slot=\"1939\"/>\n" +
"		<qcentry slot=\"2122\"/>\n" +
"		<qcentry slot=\"2353\"/>\n" +
"		<qcentry slot=\"2831\"/>\n" +
"		<qcentry slot=\"3192\"/>\n" +
"		<qcentry slot=\"3380\"/>\n" +
"		<qcentry slot=\"3573\"/>\n" +
"		<qcentry slot=\"3634\"/>\n" +
"		<qcentry slot=\"3916\"/>\n" +
"	</qcsubmenu>\n" +
"	<qcsubmenu slot=\"41\">\n" +
"		<qcentry slot=\"41\"/>\n" +
"		<qcentry slot=\"5918\"/>\n" +
"		<qcentry slot=\"5919\"/>\n" +
"		<qcentry slot=\"5920\"/>\n" +
"	</qcsubmenu>\n" +
"	<qcentry slot=\"101\"/>\n" +
"	<qcentry slot=\"109\"/>\n" +
"	<qcentry slot=\"111\"/>\n" +
"	<qcentry slot=\"113\"/>\n" +
"	<qcentry slot=\"126\"/>\n" +
"	<qcentry slot=\"128\"/>\n" +
"	<qcentry slot=\"129\"/>\n" +
"	<qcentry slot=\"116\"/>\n" +
"	<qcentry slot=\"123\"/>\n" +
"	<qcentry slot=\"104\"/>\n" +
"	<qcentry slot=\"344\"/>\n" +
"	<qcsubmenu slot=\"41\">\n" +
"		<qcentry slot=\"41\"/>\n" +
"		<qcentry slot=\"171\"/>\n" +
"		<qcentry slot=\"167\"/>\n" +
"		<qcentry slot=\"169\"/>\n" +
"		<qcentry slot=\"163\"/>\n" +
"		<qcentry slot=\"165\"/>\n" +
"		<qcentry slot=\"202\"/>\n" +
"		<qcentry slot=\"203\"/>\n" +
"		<qcentry slot=\"204\"/>\n" +
"	</qcsubmenu>\n" +
"	<qcsubmenu slot=\"359\">\n" +
"		<qcentry slot=\"359\"/>\n" +
"		<qcentry slot=\"360\"/>\n" +
"		<qcentry slot=\"361\"/>\n" +
"		<qcentry slot=\"362\"/>\n" +
"		<qcentry slot=\"363\"/>\n" +
"		<qcentry slot=\"364\"/>\n" +
"		<qcentry slot=\"365\"/>\n" +
"		<qcentry slot=\"366\"/>\n" +
"		<qcentry slot=\"367\"/>\n" +
"	</qcsubmenu>\n" +
"</qcconfig>";
    
    private final int c_iModeNormal = 4;
    private final int c_iModeCompact = 2;
    private final int c_iModeUltra = 1;

    private Map m_Map;
    private Vector<JButton> m_ButtonsV = new Vector<JButton>();
    private Vector<JPopupMenu> m_PopupMenusV = new Vector<JPopupMenu>();
    private Vector<QCPieceEntry> m_EntriesV = new Vector<QCPieceEntry>();
    private int m_iMode = c_iModeNormal;
    private ArrayList<QCConfiguration> mar_objListQCConfiguration = new ArrayList<QCConfiguration>();

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
        
        for (Enumeration<QCPieceEntry> l_Enum = m_EntriesV.elements(); l_Enum.hasMoreElements();) 
        {
            QCPieceEntry l_Entry = l_Enum.nextElement();

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

    public void loadConfigurations() 
    {
        Path l_pathConfigs = Paths.get(Info.getHomeDir() + System.getProperty("file.separator","\\") + "qcconfigs");
        
        // TODO Set the default config
        mar_objListQCConfiguration.clear();
        
        // check for configs dir
        if (Files.notExists(l_pathConfigs)) 
        {
            try
            {
                Files.createDirectory(l_pathConfigs);
            } 
            catch(Exception e)
            {
            }        
        }
        else // browsing configs files
        {
            SAXParser l_objXMLParser = null;

            try 
            {
                SAXParserFactory l_objXMLParserFactory = SAXParserFactory.newInstance();

                l_objXMLParser = l_objXMLParserFactory.newSAXParser();
            } 
            catch (Exception ex) 
            {
                l_objXMLParser = null;                    
            }

            if (l_objXMLParser != null)
            {
                // read default configuration
                QCConfiguration l_objQCDefaultConfiguration = new QCConfiguration(this, null);
                
                try 
                {
                    // parse the default configuration
                    l_objXMLParser.parse(new InputSource(new StringReader(mc_strDefaultConfig)), l_objQCDefaultConfiguration);
                }
                catch (Exception ex) 
                {
                    l_objQCDefaultConfiguration = null;
                }

                if (l_objQCDefaultConfiguration != null)
                    mar_objListQCConfiguration.add(l_objQCDefaultConfiguration);            
                            
                File[] lar_objConfigFiles = l_pathConfigs.toFile().listFiles(new FilenameFilter() { public boolean accept(File objFile, String strName) 
                                                                                    {
                                                                                        return strName.toLowerCase().endsWith(".xml");
                                                                                    }});            
                for (File l_objConfigFile : lar_objConfigFiles) 
                {
                    //Create an instance of this class; it defines all the handler methods
                    QCConfiguration l_objQCConfiguration = new QCConfiguration(this, l_objConfigFile);

                    try 
                    {
                        // parse the config file
                        l_objXMLParser.parse(l_objConfigFile, l_objQCConfiguration);
                    }
                    catch (Exception ex) 
                    {
                        l_objQCConfiguration = null;
                    }

                    if (l_objQCConfiguration != null)
                        mar_objListQCConfiguration.add(l_objQCConfiguration);
                    else
                    {
                        try
                        {
                            l_objConfigFile.delete();
                        }
                        catch (Exception ex) 
                        {
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
        loadConfigurations();
        
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
                QCPieceEntry l_Entry = new QCPieceEntry(this, l_strName, l_strPieceDefinition, l_strImageName, l_strMenu, null, null, true);
                QCButton l_ASLButton = new QCButton(l_Entry);

                try 
                {
                    l_ASLButton.InitDragDrop();
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

                QCPieceEntry l_Entry = new QCPieceEntry(this, l_strName, l_strPieceDefinition, l_strImageName, l_strMenu, null, l_PopupMenu, true);

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

                        QCPieceEntry l_PopupEntry = new QCPieceEntry(this, l_strName + l_strID, l_strID, l_strImageName + l_strID, "false", null, null, false);
                        QCMenuItem l_MenuItem = new QCMenuItem(l_PopupEntry);

                        try 
                        {
                            l_MenuItem.setText(l_strText);
                            l_MenuItem.setIcon(new ImageIcon(Op.load(l_strImageName + l_strID + ".png").getImage(null)));
                            l_MenuItem.InitDragDrop();
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

        for (Enumeration<QCPieceEntry> l_Enum = m_EntriesV.elements(); l_Enum.hasMoreElements();) 
        {
            QCPieceEntry l_Entry = l_Enum.nextElement();

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
        private QCPieceEntry m_Entry = null;
	  
        public PlaceMarkAction(QCPieceEntry entry) 
        {
            m_Entry = entry;
        }

        public void actionPerformed(ActionEvent evt) 
        {
            if (m_Entry.m_popupMenu != null)
            {
                if (evt.getSource() instanceof JButton)
                    m_Entry.m_popupMenu.show((JButton)evt.getSource(), ((JButton)evt.getSource()).getWidth() - 5, ((JButton)evt.getSource()).getHeight() - 5);
            }
        }
    }
}
