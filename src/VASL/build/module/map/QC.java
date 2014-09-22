package VASL.build.module.map;

import VASL.build.module.ASLMap;
import java.awt.Color;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import javax.swing.ButtonGroup;
import javax.swing.JButton;
import javax.swing.JMenuItem;
import javax.swing.JPopupMenu;
import javax.swing.JRadioButtonMenuItem;
import org.w3c.dom.Element;
import VASSAL.Info;
import VASSAL.build.Buildable;
import VASSAL.build.GameModule;
import VASSAL.build.module.Inventory;
import VASSAL.build.module.Map;
import VASSAL.build.module.map.PieceMover;
import VASSAL.build.widget.PieceSlot;
import VASSAL.counters.DragBuffer;
import VASSAL.counters.GamePiece;
import VASSAL.counters.KeyBuffer;
import VASSAL.counters.PieceCloner;
import VASSAL.counters.Properties;
import VASSAL.tools.image.ImageUtils;
import VASSAL.tools.imageop.Op;
import java.awt.AlphaComposite;
import java.awt.Component;
import java.awt.Container;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Image;
import java.awt.Insets;
import java.awt.Point;
import java.awt.PopupMenu;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.dnd.DnDConstants;
import java.awt.dnd.DragGestureEvent;
import java.awt.dnd.DragGestureListener;
import java.awt.dnd.DragSource;
import java.awt.dnd.DragSourceDragEvent;
import java.awt.dnd.DragSourceDropEvent;
import java.awt.dnd.DragSourceEvent;
import java.awt.dnd.DragSourceListener;
import java.awt.image.BufferedImage;
import java.io.FilenameFilter;
import java.io.StringReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Enumeration;
import java.util.Hashtable;
import java.util.List;
import java.util.UUID;
import javax.swing.Icon;
import javax.swing.ImageIcon;
import javax.swing.JMenu;
import javax.swing.JOptionPane;
import javax.swing.JToolBar;
import javax.swing.Timer;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.TreeNode;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import org.w3c.dom.Attr;
import org.w3c.dom.Document;
import org.xml.sax.Attributes;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.DefaultHandler;

class QCStartMenuItem extends JPopupMenu.Separator
{}

class QCEndMenuItem extends JPopupMenu.Separator
{}

class QCStartToolBarItem extends JToolBar.Separator
{}

class QCEndToolBarItem extends JToolBar.Separator
{}

class QCRadioButtonMenuItem extends JRadioButtonMenuItem
// <editor-fold defaultstate="collapsed">
{
    private final QCConfiguration m_objQCConfiguration;
    
    QCRadioButtonMenuItem(QCConfiguration objConfiguration)
    {
        super(objConfiguration.getDescription());
        
        m_objQCConfiguration = objConfiguration;
    }

    /**
     * @return the m_objQCConfiguration
     */
    public QCConfiguration getQCConfiguration() {
        return m_objQCConfiguration;
    }
}
// </editor-fold>

class QCButton extends JButton
// <editor-fold defaultstate="collapsed">
{
    PieceSlot m_objPieceSlot;
        
    public QCButton(PieceSlot objPieceSlot) 
    {
        super();
        m_objPieceSlot = objPieceSlot;
    }
    
    public void InitDragDrop()
    {
        DragGestureListener dragGestureListener = new DragGestureListener() 
        {
            public void dragGestureRecognized(DragGestureEvent dge) 
            {
                startDrag();
                PieceMover.AbstractDragHandler.getTheDragHandler().dragGestureRecognized(dge);
            }
        };
        DragSource.getDefaultDragSource().createDefaultDragGestureRecognizer(this, DnDConstants.ACTION_MOVE, dragGestureListener);
    }
    
    // Puts counter in DragBuffer. Call when mouse gesture recognized
    protected void startDrag() 
    {
        if (m_objPieceSlot != null)
        {
            m_objPieceSlot.getPiece().setPosition(new Point(0, 0));

            // Erase selection border to avoid leaving selected after mouse dragged out
            m_objPieceSlot.getPiece().setProperty(Properties.SELECTED, null);

            if (m_objPieceSlot.getPiece() != null) {
                KeyBuffer.getBuffer().clear();
                DragBuffer.getBuffer().clear();
                GamePiece l_objNewPiece = PieceCloner.getInstance().clonePiece(m_objPieceSlot.getPiece());
                l_objNewPiece.setProperty(Properties.PIECE_ID, m_objPieceSlot.getGpId());
                DragBuffer.getBuffer().add(l_objNewPiece);
            }
        }
    }
}
// </editor-fold>

class QCButtonMenu extends JButton
// <editor-fold defaultstate="collapsed">
{
    private PieceSlot m_objPieceSlot;
    private JPopupMenu m_objPopupMenu = new JPopupMenu();
        
    public QCButtonMenu(PieceSlot objPieceSlot) 
    {
        m_objPieceSlot = objPieceSlot;
        
        addActionListener(new ActionListener() 
        {
            public void actionPerformed(ActionEvent e)
            {
                if (getPopupMenu() != null)
                    if (e.getSource() instanceof JButton)
                        getPopupMenu().show((JButton)e.getSource(), ((JButton)e.getSource()).getWidth() - 5, ((JButton)e.getSource()).getHeight() - 5);
            }
        });      
    }    

    /**
     * @return the m_objParentPopupMenu
     */
    public JPopupMenu getPopupMenu() {
        return m_objPopupMenu;
    }
}
// </editor-fold>

class QCMenuItem extends JMenuItem implements DragSourceListener
// <editor-fold defaultstate="collapsed">
{
    PieceSlot m_objPieceSlot;
    JPopupMenu m_objParentPopupMenu;
    
    public QCMenuItem(PieceSlot objPieceSlot, JPopupMenu objPopupMenu) 
    {
        super();
        m_objPieceSlot = objPieceSlot;
        m_objParentPopupMenu = objPopupMenu;
    }
    
    public void InitDragDrop()
    {
        DragGestureListener dragGestureListener = new DragGestureListener() 
        {
            public void dragGestureRecognized(DragGestureEvent dge) 
            {
                if ((dge.getComponent() != null) && (dge.getComponent() instanceof QCMenuItem))
                    DragSource.getDefaultDragSource().addDragSourceListener(((QCMenuItem)dge.getComponent()));
                
                startDrag();
                PieceMover.AbstractDragHandler.getTheDragHandler().dragGestureRecognized(dge);
            }
        };
        
        DragSource.getDefaultDragSource().createDefaultDragGestureRecognizer(this, DnDConstants.ACTION_MOVE, dragGestureListener);
    }
    
    // Puts counter in DragBuffer. Call when mouse gesture recognized
    protected void startDrag() 
    {
        if (m_objPieceSlot != null)
        {
            m_objPieceSlot.getPiece().setPosition(new Point(0, 0));

            // Erase selection border to avoid leaving selected after mouse dragged out
            m_objPieceSlot.getPiece().setProperty(Properties.SELECTED, null);

            if (m_objPieceSlot.getPiece() != null) 
            {
                KeyBuffer.getBuffer().clear();
                DragBuffer.getBuffer().clear();
                GamePiece l_objNewPiece = PieceCloner.getInstance().clonePiece(m_objPieceSlot.getPiece());
                l_objNewPiece.setProperty(Properties.PIECE_ID, m_objPieceSlot.getGpId());
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
        
        if (m_objParentPopupMenu != null)
            m_objParentPopupMenu.setVisible(false);
        
        DragSource.getDefaultDragSource().removeDragSourceListener(this);
    }
}
// </editor-fold>

class QCConfigurationComparator implements Comparator<QCConfiguration> 
// <editor-fold defaultstate="collapsed">
{
    @Override
    public int compare(QCConfiguration o1, QCConfiguration o2) 
    {
        return o1.getDescription().compareTo(o2.getDescription());
    }
}
// </editor-fold>

class QCConfigurationParser extends DefaultHandler 
// <editor-fold defaultstate="collapsed">
{
    private QCConfiguration m_objQCConfiguration;
    private TreeNode m_objCurrentSubMenu;
    
    public QCConfigurationParser(QCConfiguration objQCConfiguration)
    {
        m_objQCConfiguration = objQCConfiguration;
        m_objCurrentSubMenu = objQCConfiguration;
    }
    
    @Override
    public void startElement (String uri, String localName,
                              String qName, Attributes attributes)
        throws SAXException
    {
        if (qName.equalsIgnoreCase("qcconfig")) 
        {
            m_objQCConfiguration.setDescription(attributes.getValue("descr"));
        }
        else if (qName.equalsIgnoreCase("qcsubmenu")) 
        {
            QCConfigurationEntry l_newEntry = new QCConfigurationEntry(m_objQCConfiguration.getQC());
            
            l_newEntry.setMenu(true);
            l_newEntry.setGpID(attributes.getValue("slot"));
            l_newEntry.setText(attributes.getValue("text"));
            
            if (m_objCurrentSubMenu != m_objQCConfiguration)
                ((DefaultMutableTreeNode)m_objCurrentSubMenu).add(l_newEntry);
            else
                m_objQCConfiguration.add(l_newEntry);
            
            m_objCurrentSubMenu = l_newEntry;
        }
        else if (qName.equalsIgnoreCase("qcentry")) 
        {
            QCConfigurationEntry l_newEntry = new QCConfigurationEntry(m_objQCConfiguration.getQC());

            l_newEntry.setGpID(attributes.getValue("slot"));
            
            if (m_objCurrentSubMenu != m_objQCConfiguration)
                ((DefaultMutableTreeNode)m_objCurrentSubMenu).add(l_newEntry);
            else
                m_objQCConfiguration.add(l_newEntry);            
        }
    }  
    
    @Override
    public void endElement (String uri, String localName, String qName)
        throws SAXException
    {
        if (qName.equalsIgnoreCase("qcsubmenu")) 
            m_objCurrentSubMenu = m_objCurrentSubMenu.getParent();
    }  
}
// </editor-fold>

class QCConfiguration extends DefaultMutableTreeNode
// <editor-fold defaultstate="collapsed">
{
    private boolean m_bBuiltinConfiguration;
    private QC m_objQC;
    private File m_objFile;
    private String m_strDescription;
    
    public QCConfiguration(QC objQC, File objFile)
    {
        m_bBuiltinConfiguration = false;
        
        m_objQC = objQC;
        m_objFile = objFile;
        
        m_strDescription = "";
    }    

    // copy object
    public QCConfiguration(QCConfiguration objMaster)
    {
        m_bBuiltinConfiguration = false;
        
        m_objQC = objMaster.getQC();
        
        m_strDescription = objMaster.getDescription() + " (copy)";
        
        Enumeration<QCConfigurationEntry> l_objChildrenEnum = objMaster.children();

        while(l_objChildrenEnum.hasMoreElements())
            add(new QCConfigurationEntry(l_objChildrenEnum.nextElement()));        
        
        Path l_pathConfigs = Paths.get(Info.getHomeDir() + System.getProperty("file.separator","\\") + "qcconfigs");
        
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

        Path l_pathFile = Paths.get(l_pathConfigs + System.getProperty("file.separator","\\") + UUID.randomUUID().toString() + ".xml");

        m_objFile = l_pathFile.toFile();
    }
    
    public void ReadDataFrom(QCConfiguration objMaster)
    {
        m_bBuiltinConfiguration = objMaster.isBuiltinConfiguration();
        m_strDescription = objMaster.getDescription();
        
        FreeAllNodes(this);
        
        Enumeration<QCConfigurationEntry> l_objChildrenEnum = objMaster.children();

        while(l_objChildrenEnum.hasMoreElements())
            add(new QCConfigurationEntry(l_objChildrenEnum.nextElement()));        
    }
    
    private void FreeAllNodes(DefaultMutableTreeNode objNode)
    {
        Enumeration<QCConfigurationEntry> l_objChildrenEnum = objNode.children();

        while(l_objChildrenEnum.hasMoreElements())
            FreeAllNodes(l_objChildrenEnum.nextElement());        
        
        objNode.removeAllChildren();
    }
        
    /**
     * @return the m_objQC
     */
    public QC getQC() {
        return m_objQC;
    }

    /**
     * @return the m_objFile
     */
    public File getFile() {
        return m_objFile;
    }

    /**
     * @return the namne of the file
     */
    public String getName() {
        if (m_objFile != null)
            return m_objFile.getName();
        else
            return "";
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
    public void setDescription(String strDescription) {
        if (strDescription != null)
            this.m_strDescription = strDescription;
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

                Enumeration<QCConfigurationEntry> l_objChildrenEnum = children();
                
                while(l_objChildrenEnum.hasMoreElements())
                    l_objChildrenEnum.nextElement().WriteXML(l_objDocument, l_objRootElement);

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

    /**
     * @return the m_bBuiltinConfiguration
     */
    public Boolean isBuiltinConfiguration() {
        return m_bBuiltinConfiguration;
    }

    /**
     * @param m_bBuiltinConfiguration the m_bBuiltinConfiguration to set
     */
    public void setBuiltintConfiguration(Boolean m_bBuiltinConfiguration) {
        this.m_bBuiltinConfiguration = m_bBuiltinConfiguration;
    }

    public boolean DeleteXML() 
    {
        try 
        {
            if (m_objFile != null)
            {
                if (m_objFile.exists())
                    m_objFile.delete();
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

class QCConfigurationEntry  extends DefaultMutableTreeNode
// <editor-fold defaultstate="collapsed">
{
    private static BufferedImage m_objMnuBtn = null;
    
    private final QC m_objQC;
    private boolean m_bMenu;
    private String m_strGpID, m_strText;    
    private PieceSlot m_objPieceSlot;
    
    public QCConfigurationEntry(QC objQC)
    {
        m_objQC = objQC;
        m_bMenu = false;
        m_strGpID = null;
        m_strText = null;
        m_objPieceSlot = null;
    }   

    public QCConfigurationEntry(QCConfigurationEntry objMaster)
    {
        m_objQC = objMaster.getQC();
        m_bMenu = objMaster.isMenu();
        
        if (m_bMenu)
        {
            Enumeration<QCConfigurationEntry> l_objChildrenEnum = objMaster.children();

            while(l_objChildrenEnum.hasMoreElements())
                add(new QCConfigurationEntry(l_objChildrenEnum.nextElement()));        
        }
        
        m_strGpID = objMaster.getGpID();
        m_strText = objMaster.getText();
        m_objPieceSlot = objMaster.getPieceSlot();
    }   
    
    /**
     * @return the m_bMenu
     */
    public boolean isMenu() {
        return m_bMenu;
    }

    /**
     * @param bMenu the bMenu to set
     */
    public void setMenu(boolean bMenu) {
        this.m_bMenu = bMenu;
    }

    /**
     * @return the m_strPieceSlot
     */
    public String getGpID() {
        return m_strGpID;
    }

    /**
     * @param strGpID the strGpID to set
     */
    public void setGpID(String strGpID) {
        this.m_strGpID = strGpID;
    }

    /**
     * @return the m_objQC
     */
    public QC getQC() {
        return m_objQC;
    }

    void WriteXML(Document objDocument, Element objElement) 
    {
        if (isMenu())
        {
            Element l_objEntry = objDocument.createElement("qcsubmenu");

            if (getGpID() != null)
            {
                Attr l_objAttribute = objDocument.createAttribute("slot");
                l_objAttribute.setValue(getGpID());
                l_objEntry.setAttributeNode(l_objAttribute);
            }
            
            if (getText() != null)
            {
                Attr l_objAttribute = objDocument.createAttribute("text");
                l_objAttribute.setValue(getText());
                l_objEntry.setAttributeNode(l_objAttribute);
            }
            
            objElement.appendChild(l_objEntry);
            
            Enumeration<QCConfigurationEntry> l_objChildrenEnum = children();

            while(l_objChildrenEnum.hasMoreElements())
                l_objChildrenEnum.nextElement().WriteXML(objDocument, l_objEntry);
        }
        else
        {
            Element l_objEntry = objDocument.createElement("qcentry");

            if (getGpID() != null)
            {
                Attr l_objAttribute = objDocument.createAttribute("slot");
                l_objAttribute.setValue(getGpID());
                l_objEntry.setAttributeNode(l_objAttribute);
            }
            
            objElement.appendChild(l_objEntry);
        }
    }

    /**
     * @return the m_objPieceSlot
     */
    public PieceSlot getPieceSlot() {
        return m_objPieceSlot;
    }

    /**
     * @param objPieceSlot the objPieceSlot to set
     */
    public void setPieceSlot(PieceSlot objPieceSlot) {
        this.m_objPieceSlot = objPieceSlot;
    }

    /**
     * @return the m_strText
     */
    public String getText() {
        return m_strText;
    }

    /**
     * @param m_strText the m_strText to set
     */
    public void setText(String m_strText) {
        this.m_strText = m_strText;
    }
    
    public BufferedImage CreateButtonIcon() 
    {
        final int l_iSize = 30, l_iSizeRendering = 35;
        final BufferedImage l_objBI = ImageUtils.createCompatibleTranslucentImage(l_iSize, l_iSize);
        final Graphics2D l_objGraphics = l_objBI.createGraphics();
        
        l_objGraphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        
        final GamePiece l_objPiece = getPieceSlot().getPiece();
        final Rectangle r = l_objPiece.getShape().getBounds();
        final double l_dZoom = l_iSizeRendering / (double)r.width;

        r.x = (int) Math.round(r.x * l_dZoom);
        r.y = (int) Math.round(r.y * l_dZoom);
        r.width = l_iSize;
        r.height = l_iSize;

        l_objPiece.draw(l_objGraphics, -r.x-3, -r.y-3, null, l_dZoom);
        l_objGraphics.dispose();
        
        return l_objBI;
    }    
    
    public BufferedImage CreateButtonMenuIcon() 
    {
        QCConfigurationEntry l_objConfigurationEntryIcon = null;
        
        if (getPieceSlot() != null)
        {
            l_objConfigurationEntryIcon = this;            
        }
        else
        {
            Enumeration<QCConfigurationEntry> l_objChildrenEnum = children();

            while(l_objChildrenEnum.hasMoreElements())
            {
                QCConfigurationEntry l_objConfigurationEntry = l_objChildrenEnum.nextElement();
                
                if (l_objConfigurationEntry.getPieceSlot() != null)
                {
                    l_objConfigurationEntryIcon = l_objConfigurationEntry;            
                    break;
                }
            }          
        }
        
        if (l_objConfigurationEntryIcon != null)
        {
            final BufferedImage l_objBI = l_objConfigurationEntryIcon.CreateButtonIcon();
            final Graphics2D l_objGraphics = l_objBI.createGraphics();
    
            l_objGraphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);            
            l_objGraphics.drawImage(GetMenuOverlay(), 0, 0, null);            
            l_objGraphics.dispose();

            return l_objBI;
        }       
        else
        {
            final int l_iSize = 30;
            final BufferedImage l_objBI = ImageUtils.createCompatibleTranslucentImage(l_iSize, l_iSize);
            final Graphics2D l_objGraphics = l_objBI.createGraphics();

            l_objGraphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            
            l_objGraphics.setColor(Color.WHITE);
            l_objGraphics.fillRect(0, 0, l_iSize, l_iSize);            
            
            l_objGraphics.drawImage(GetMenuOverlay(), 0, 0, null);
            
            l_objGraphics.dispose();
            
            return l_objBI;
        }
    }
    
    private static BufferedImage GetMenuOverlay() 
    {
        if (m_objMnuBtn == null)
        {
            try
            {
                m_objMnuBtn = Op.load("QC/mnubtn.png").getImage();
            }
            catch (Exception ex) 
            {
                ex.printStackTrace();
            }
        }
        
        return m_objMnuBtn;
    }
}
// </editor-fold>

/**
 * A class to represent the counters toolbar
 */
public class QC implements Buildable 
{
// <editor-fold defaultstate="collapsed">
    private final String mc_strBuiltinConfig = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" +
"<qcconfig descr=\"Built-in configuration\">\n" +
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
"	<qcsubmenu slot=\"171\">\n" +
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
// </editor-fold>

    private Map m_Map;
    private final ArrayList<QCConfiguration> mar_objListQCConfigurations = new ArrayList<QCConfiguration>();
    private QCConfiguration m_objQCWorkingConfiguration = null;
    private Timer m_objLinkTimer;
    private QCConfig m_objQCConfig = null;
    private boolean m_bEditing = false;

    public void loadConfigurations() 
    {
        SAXParser l_objXMLParser;
        Path l_pathConfigs = Paths.get(Info.getHomeDir() + System.getProperty("file.separator","\\") + "qcconfigs");
        
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
            // clear the old configurations (if any)
            mar_objListQCConfigurations.clear();
            
            // read built-in configuration
            m_objQCWorkingConfiguration = new QCConfiguration(this, null); // null file for the built-in configuration

            try 
            {
                QCConfigurationParser l_objQCConfigurationParser = new QCConfigurationParser(m_objQCWorkingConfiguration);
                // parse the built-in configuration
                l_objXMLParser.parse(new InputSource(new StringReader(mc_strBuiltinConfig)), l_objQCConfigurationParser);
            }
            catch (Exception ex) 
            {
                m_objQCWorkingConfiguration = null;
            }

            if (m_objQCWorkingConfiguration != null)
                m_objQCWorkingConfiguration.setBuiltintConfiguration(true);
            
            // now read the custom configuration files
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
                        QCConfigurationParser l_objQCConfigurationParser = new QCConfigurationParser(l_objQCConfiguration);
                        // parse the config file
                        l_objXMLParser.parse(l_objConfigFile, l_objQCConfigurationParser);
                    }
                    catch (Exception ex) 
                    {
                        l_objQCConfiguration = null;
                    }

                    if (l_objQCConfiguration != null)
                        mar_objListQCConfigurations.add(l_objQCConfiguration);
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
                
                Collections.sort(mar_objListQCConfigurations, new QCConfigurationComparator());
                
                if (m_objQCWorkingConfiguration != null)
                    mar_objListQCConfigurations.add(0, m_objQCWorkingConfiguration);
            }      
        }
    }
    public void readWorkingConfiguration() 
    {
        java.util.Properties l_objProperties = new java.util.Properties();
        InputStream l_objInputStream;
        String l_strWorkingConfigurationName = "";

        // try loading QC.properties from from the home directory
        try 
        {
            l_objInputStream = new FileInputStream(new File(Info.getHomeDir() + System.getProperty("file.separator","\\") + "QC.properties"));
        }
        catch (Exception ex) 
        { 
            l_objInputStream = null; 
        }
        
        if (l_objInputStream != null)
        {
            try 
            {
                l_objProperties.load(l_objInputStream);

                l_objInputStream.close();

                l_strWorkingConfigurationName = l_objProperties.getProperty("QC_working_configuration", "");
            }
            catch (Exception ex) 
            { 
            }
        }
        
        for (QCConfiguration l_objQCConfiguration : mar_objListQCConfigurations)
        {
            if (l_strWorkingConfigurationName.equalsIgnoreCase(l_objQCConfiguration.getName()))
            {
                m_objQCWorkingConfiguration = l_objQCConfiguration;
                break;                
            }
        }
    }	

    public void saveWorkingConfiguration() 
    {
        if (m_objQCWorkingConfiguration != null)
        {
            try 
            {
                java.util.Properties l_Props = new java.util.Properties();
                l_Props.setProperty("QC_working_configuration", m_objQCWorkingConfiguration.getName());

                OutputStream l_objOutputStream = new FileOutputStream(new File(Info.getHomeDir() + System.getProperty("file.separator","\\") + "QC.properties"));

                l_Props.store(l_objOutputStream, "QC working configuration");

                l_objOutputStream.flush();
                l_objOutputStream.close();
            }
            catch (Exception ex) 
            {
            }
        }
    }

    public void build(Element e)
    {
        loadConfigurations();
        readWorkingConfiguration();
    }

    public org.w3c.dom.Element getBuildElement(org.w3c.dom.Document doc) 
    {
        return doc.createElement(getClass().getName());
    }
  
    public void addTo(Buildable b) 
    {
        m_Map = (Map) b;
        
        ((ASLMap)m_Map).getPopupMenu().add(new QCStartMenuItem());
        ((ASLMap)m_Map).getPopupMenu().add(new QCEndMenuItem());
        
        RebuildPopupMenu();

        m_Map.getToolBar().add(new QCStartToolBarItem());
        m_Map.getToolBar().add(new QCEndToolBarItem());
        
        m_objLinkTimer = new Timer(5000, new ActionListener() 
        {
            public void actionPerformed(ActionEvent e) 
            {
                m_objLinkTimer.stop();
                
                ReadPiecesSlot();
                RebuildToolBar();
            }
        });
            
        m_objLinkTimer.setRepeats(false);
        m_objLinkTimer.start();
    }
    
    private void ReadPiecesSlot()
    {
        List<PieceSlot> lar_PieceSlotL = GameModule.getGameModule().getAllDescendantComponentsOf(PieceSlot.class);
        Hashtable lar_HashPieceSlot = new Hashtable();

        for (PieceSlot l_objPieceSlot : lar_PieceSlotL) 
            lar_HashPieceSlot.put(l_objPieceSlot.getGpId(), l_objPieceSlot);
        
        lar_PieceSlotL = null;
        
        for (QCConfiguration l_objQCConfiguration : mar_objListQCConfigurations)
        {
            Enumeration<QCConfigurationEntry> l_objChildrenEnum = l_objQCConfiguration.children();

            while(l_objChildrenEnum.hasMoreElements())
                setPieceSlot(l_objChildrenEnum.nextElement(), lar_HashPieceSlot);
        }
    }
    
    private void setPieceSlot(QCConfigurationEntry objConfigurationEntry, Hashtable ar_HashPieceSlot) 
    {
        PieceSlot l_objPieceSlot = null;
        
        if (objConfigurationEntry.getGpID() != null)
        {
            l_objPieceSlot = (PieceSlot) ar_HashPieceSlot.get(objConfigurationEntry.getGpID());

            if (l_objPieceSlot != null)
                objConfigurationEntry.setPieceSlot(l_objPieceSlot);
        }

        if (objConfigurationEntry.isMenu())
        {
            Enumeration<QCConfigurationEntry> l_objChildrenEnum = objConfigurationEntry.children();

            while(l_objChildrenEnum.hasMoreElements())
                setPieceSlot(l_objChildrenEnum.nextElement(), ar_HashPieceSlot);
        }
    }

    public void add(Buildable b) {}

    private void RebuildToolBar() 
    {
        JToolBar l_objToolBar = m_Map.getToolBar();
        boolean l_bEndElementNotFound = true;
        int l_iStartPos = 0;
        
        if (m_objQCWorkingConfiguration != null)
        {
            // remove the old element
            for (int l_i = l_objToolBar.getComponents().length - 1; l_i >= 0; l_i--)
            {
                Component l_objComponent = l_objToolBar.getComponent(l_i);

                if (l_bEndElementNotFound)
                {
                    if (l_objComponent instanceof QCEndToolBarItem) 
                        l_bEndElementNotFound = false;                
                }
                else
                {
                    if (l_objComponent instanceof QCStartToolBarItem) 
                    {
                        l_iStartPos = l_i + 1;
                        break;
                    }
                    else
                    {
                        l_objToolBar.remove(l_i);               
                    }
                }
            }
            
            Enumeration<QCConfigurationEntry> l_objChildrenEnum = m_objQCWorkingConfiguration.children();

            while(l_objChildrenEnum.hasMoreElements())
            {                
                Component l_objComponent = CreateToolBarItem(l_objChildrenEnum.nextElement());

                if (l_objComponent != null)
                    l_objToolBar.add(l_objComponent, l_iStartPos++);                
            }
            
            l_objToolBar.revalidate();
            l_objToolBar.repaint();
        }
    }

    private Component CreateToolBarItem(QCConfigurationEntry objConfigurationEntry) 
    {
        if (objConfigurationEntry.isMenu())
        { // submenu
            QCButtonMenu l_objQCButtonMenu = new QCButtonMenu(objConfigurationEntry.getPieceSlot());

            try 
            {
                l_objQCButtonMenu.setIcon(new ImageIcon(objConfigurationEntry.CreateButtonMenuIcon()));
                l_objQCButtonMenu.setMargin(new Insets(0, 0, 0, 0));
                
                CreatePopupMenu(objConfigurationEntry, l_objQCButtonMenu);                
            } 
            catch (Exception ex) 
            {
                ex.printStackTrace();
            }

            l_objQCButtonMenu.setAlignmentY(0.0F);

            return l_objQCButtonMenu;
        }
        else // button standard
        {
            if (objConfigurationEntry.getPieceSlot() != null)
            {
                QCButton l_objQCButton = new QCButton(objConfigurationEntry.getPieceSlot());

                try 
                {
                    l_objQCButton.InitDragDrop();
                    l_objQCButton.setIcon(new ImageIcon(objConfigurationEntry.CreateButtonIcon()));
                    l_objQCButton.setMargin(new Insets(0, 0, 0, 0));
                } 
                catch (Exception ex) 
                {
                    ex.printStackTrace();
                }

                l_objQCButton.setAlignmentY(0.0F);

                return l_objQCButton;
            }
        } 
        
        return null;
    }

    private void CreatePopupMenu(QCConfigurationEntry objConfigurationEntry, QCButtonMenu objQCButtonMenu) 
    {
        JPopupMenu l_objPopupMenu = objQCButtonMenu.getPopupMenu();
        
        Enumeration<QCConfigurationEntry> l_objChildrenEnum = objConfigurationEntry.children();

        while(l_objChildrenEnum.hasMoreElements())
        {
            JMenuItem l_objMenuItem = CreateMenuItem(l_objChildrenEnum.nextElement(), l_objPopupMenu);
            
            if (l_objMenuItem != null)
                l_objPopupMenu.add(l_objMenuItem);
        }
    }
    
    private JMenuItem CreateMenuItem(QCConfigurationEntry objConfigurationEntry, JPopupMenu objPopupMenu) 
    {
        if (objConfigurationEntry.isMenu()) //submenu
        {
            JMenu l_objMenu = new JMenu();

            try 
            {
                if (objConfigurationEntry.getText() != null)
                    l_objMenu.setText(objConfigurationEntry.getText()); 
                else
                    l_objMenu.setText("submenu");

                l_objMenu.setIcon(new ImageIcon(objConfigurationEntry.CreateButtonMenuIcon()));
            } 
            catch (Exception ex) 
            {
                ex.printStackTrace();
            }
            
            Enumeration<QCConfigurationEntry> l_objChildrenEnum = objConfigurationEntry.children();

            while(l_objChildrenEnum.hasMoreElements())
            {
                JMenuItem l_objMenuItem = CreateMenuItem(l_objChildrenEnum.nextElement(), objPopupMenu);

                if (l_objMenuItem != null)
                    l_objMenu.add(l_objMenuItem);
            }

            return l_objMenu;
        }
        else
        {
            if (objConfigurationEntry.getPieceSlot() != null)
            {
                QCMenuItem l_MenuItem = new QCMenuItem(objConfigurationEntry.getPieceSlot(), objPopupMenu);

                try 
                {
                    l_MenuItem.setText(objConfigurationEntry.getPieceSlot().getPiece().getName());
                    l_MenuItem.setIcon(new ImageIcon(objConfigurationEntry.CreateButtonIcon()));
                    l_MenuItem.InitDragDrop();
                } 
                catch (Exception ex) 
                {
                    ex.printStackTrace();
                }

                return l_MenuItem;
            }
        }            
        
        return null;
    }
    
    private void RebuildPopupMenu() 
    {
        JPopupMenu l_objPopupMenu = ((ASLMap)m_Map).getPopupMenu();
        final JMenuItem l_objCopyConfigurationMenuItem  = new JMenuItem(), l_objRemoveConfigurationMenuItem  = new JMenuItem(), l_objEditConfigurationMenuItem = new JMenuItem();;
        int l_iStartPos = 0;
        boolean l_bEndElementNotFound = true;
        
        // remove the old element
        for (int l_i = l_objPopupMenu.getComponents().length - 1; l_i >= 0; l_i--)
        {
            Component l_objComponent = l_objPopupMenu.getComponent(l_i);
            
            if (l_bEndElementNotFound)
            {
                if (l_objComponent instanceof QCEndMenuItem) 
                    l_bEndElementNotFound = false;                
            }
            else
            {
                if (l_objComponent instanceof QCStartMenuItem) 
                {
                    l_iStartPos = l_i + 1;
                    break;
                }
                else
                {
                    l_objPopupMenu.remove(l_i);               
                }
            }
        }
        
        // title
        JMenuItem l_SelectQCItem = new JMenuItem("Select QC configuration");
        l_SelectQCItem.setBackground(new Color(255,255,255));
        l_objPopupMenu.add(l_SelectQCItem, l_iStartPos++);
        
        l_objPopupMenu.add(new JPopupMenu.Separator(), l_iStartPos++);

        // button group
        ButtonGroup l_Group = new ButtonGroup();
        
        for (QCConfiguration l_objQCConfiguration : mar_objListQCConfigurations)
        {
            QCRadioButtonMenuItem l_objQCRadioButtonMenuItem = new QCRadioButtonMenuItem(l_objQCConfiguration);
            
            l_objQCRadioButtonMenuItem.addActionListener(new ActionListener() 
            {
                public void actionPerformed(ActionEvent evt) 
                {
                    if (evt.getSource() instanceof QCRadioButtonMenuItem)
                    {
                        m_objQCWorkingConfiguration = ((QCRadioButtonMenuItem)evt.getSource()).getQCConfiguration();
                        saveWorkingConfiguration();
                        RebuildToolBar();
                        
                        if ((m_objQCWorkingConfiguration != null) && (!m_bEditing))
                        {
                            l_objCopyConfigurationMenuItem.setEnabled(true);
                            
                            if (m_objQCWorkingConfiguration.isBuiltinConfiguration())
                            {
                                l_objRemoveConfigurationMenuItem.setEnabled(false);
                                l_objEditConfigurationMenuItem.setEnabled(false);
                            }
                            else
                            {
                                l_objRemoveConfigurationMenuItem.setEnabled(true);
                                l_objEditConfigurationMenuItem.setEnabled(true);
                            }
                        }
                        else
                        {
                            l_objCopyConfigurationMenuItem.setEnabled(false);
                            l_objRemoveConfigurationMenuItem.setEnabled(false);
                            l_objEditConfigurationMenuItem.setEnabled(false);
                        }
                        
                    }
                }
            });

            l_Group.add(l_objQCRadioButtonMenuItem);
            l_objPopupMenu.add(l_objQCRadioButtonMenuItem, l_iStartPos++);
            
            l_objQCRadioButtonMenuItem.setSelected(l_objQCConfiguration == m_objQCWorkingConfiguration);
        }
        
        l_objPopupMenu.add(new JPopupMenu.Separator(), l_iStartPos++);
        
        // copy configuration copy configuration copy configuration copy configuration copy configuration copy configuration copy configuration copy configuration
        // copy configuration copy configuration copy configuration copy configuration copy configuration copy configuration copy configuration copy configuration
        l_objCopyConfigurationMenuItem.setText("Copy current QC configuration");
        
        if ((m_objQCWorkingConfiguration != null) && (!m_bEditing))
            l_objCopyConfigurationMenuItem.setEnabled(true);
        else
            l_objCopyConfigurationMenuItem.setEnabled(false);
        
        try
        {
            l_objCopyConfigurationMenuItem.setIcon(new ImageIcon(Op.load("QC/copy.png").getImage(null)));
        }
        catch (Exception ex) 
        {
            ex.printStackTrace();
        }

        l_objCopyConfigurationMenuItem.addActionListener(new ActionListener() 
        {
            public void actionPerformed(ActionEvent evt) 
            {
                if (m_objQCWorkingConfiguration != null)
                {
                    QCConfiguration l_objNewConfiguration = new QCConfiguration(m_objQCWorkingConfiguration);
                    
                    if (l_objNewConfiguration.SaveXML())
                    {
                        mar_objListQCConfigurations.add(l_objNewConfiguration);
                        m_objQCWorkingConfiguration = l_objNewConfiguration;
                        saveWorkingConfiguration();
                        
                        QCConfiguration l_objBuiltinConfiguration = mar_objListQCConfigurations.get(0);
                        mar_objListQCConfigurations.remove(l_objBuiltinConfiguration);

                        Collections.sort(mar_objListQCConfigurations, new QCConfigurationComparator());
                        mar_objListQCConfigurations.add(0, l_objBuiltinConfiguration);
                        
                        RebuildPopupMenu();
                        RebuildToolBar();
                    }
                }
            }
        });
        
        l_objPopupMenu.add(l_objCopyConfigurationMenuItem, l_iStartPos++);
        
        // remove configuration remove configuration remove configuration remove configuration remove configuration remove configuration
        // remove configuration remove configuration remove configuration remove configuration remove configuration remove configuration
        l_objRemoveConfigurationMenuItem.setText("Delete current QC configuration");
        
        if ((m_objQCWorkingConfiguration != null) && (!m_bEditing))
        {
            if (m_objQCWorkingConfiguration.isBuiltinConfiguration())
                l_objRemoveConfigurationMenuItem.setEnabled(false);
            else
                l_objRemoveConfigurationMenuItem.setEnabled(true);
        }
        else
            l_objRemoveConfigurationMenuItem.setEnabled(false);
        
        try
        {
            l_objRemoveConfigurationMenuItem.setIcon(new ImageIcon(Op.load("QC/delete.png").getImage(null))); 
        }
        catch (Exception ex) 
        {
            ex.printStackTrace();
        }

        l_objRemoveConfigurationMenuItem.addActionListener(new ActionListener() 
        {
            public void actionPerformed(ActionEvent evt) 
            {
                if (m_objQCWorkingConfiguration != null)
                {
                    int l_iSelectedOption = JOptionPane.showConfirmDialog(m_Map.getView(), 
                                                      "Do you really want to delete the '" + m_objQCWorkingConfiguration.getDescription() + "' QC configuration? This is NOT undoable or reversible!", 
                                                      "Delete the current QC configuration", 
                                                      JOptionPane.YES_NO_OPTION,
                                                      JOptionPane.QUESTION_MESSAGE); 
                    
                    if (l_iSelectedOption == JOptionPane.YES_OPTION) 
                    {
                        m_objQCWorkingConfiguration.DeleteXML();
                        
                        int l_iIndex = mar_objListQCConfigurations.indexOf(m_objQCWorkingConfiguration);
                        
                        if (l_iIndex != -1)
                        {
                            if (l_iIndex > 0)
                            {
                                mar_objListQCConfigurations.remove(m_objQCWorkingConfiguration);
                                m_objQCWorkingConfiguration = mar_objListQCConfigurations.get(l_iIndex - 1);
                            }
                        }
                        
                        saveWorkingConfiguration();

                        RebuildPopupMenu();
                        RebuildToolBar();
                    }
                }
            }
        });
        
        l_objPopupMenu.add(l_objRemoveConfigurationMenuItem, l_iStartPos++);
        
        // separator
        l_objPopupMenu.add(new JPopupMenu.Separator(), l_iStartPos++);
        
        // edit configuration edit configuration edit configuration edit configuration edit configuration edit configuration edit configuration
        // edit configuration edit configuration edit configuration edit configuration edit configuration edit configuration edit configuration
        l_objEditConfigurationMenuItem.setText("Modify current QC configuration");

        if ((m_objQCWorkingConfiguration != null) && (!m_bEditing))
        {
            if (m_objQCWorkingConfiguration.isBuiltinConfiguration())
                l_objEditConfigurationMenuItem.setEnabled(false);
            else
                l_objEditConfigurationMenuItem.setEnabled(true);
        }
        else
            l_objEditConfigurationMenuItem.setEnabled(false);
        
        try
        {
            l_objEditConfigurationMenuItem.setIcon(new ImageIcon(Op.load("QC/edit.png").getImage(null)));
        }
        catch (Exception ex) 
        {
            ex.printStackTrace();
        }

        l_objEditConfigurationMenuItem.addActionListener(new ActionListener() 
        {
            public void actionPerformed(ActionEvent evt) 
            {
                if (m_objQCWorkingConfiguration != null)
                {
                    //open the configuration window
                    if (m_objQCConfig == null)
                        m_objQCConfig = new QCConfig();
                    
                    m_objQCConfig.setConfiguration(m_objQCWorkingConfiguration);
                    
                    m_bEditing = true;
                    RebuildPopupMenu();
                }
            }
        });
        
        l_objPopupMenu.add(l_objEditConfigurationMenuItem, l_iStartPos++);
    }
    
    public void UpdateQC(boolean bClosing)
    {
        if (bClosing)
        {
            m_bEditing = false;
            RebuildPopupMenu();
        }
        
        RebuildToolBar();
    }
}
