package VASL.build.module.map;

import java.awt.Color;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.File;
import java.io.FileOutputStream;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Image;
import java.awt.Insets;
import java.awt.Point;
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
import java.awt.image.BaseMultiResolutionImage;
import java.awt.image.BufferedImage;
import java.awt.image.MultiResolutionImage;
import java.io.FilenameFilter;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Enumeration;
import java.util.Hashtable;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ExecutionException;

import javax.swing.ButtonGroup;
import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JMenuItem;
import javax.swing.JPopupMenu;
import javax.swing.JRadioButtonMenuItem;
import javax.swing.JMenu;
import javax.swing.JOptionPane;
import javax.swing.JToggleButton;
import javax.swing.JToolBar;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.MutableTreeNode;
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
import org.w3c.dom.Element;
import org.xml.sax.Attributes;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.DefaultHandler;

import VASSAL.Info;
import VASSAL.build.Buildable;
import VASSAL.build.GameModule;
import VASSAL.build.module.BasicLogger;
import VASSAL.build.module.GameComponent;
import VASSAL.build.module.Map;
import VASSAL.build.module.map.PieceMover;
import VASSAL.build.widget.PieceSlot;
import VASSAL.command.Command;
import VASSAL.configure.StringConfigurer;
import VASSAL.counters.DragBuffer;
import VASSAL.counters.GamePiece;
import VASSAL.counters.KeyBuffer;
import VASSAL.counters.PieceCloner;
import VASSAL.counters.Properties;
import VASSAL.tools.image.ImageUtils;
import VASSAL.tools.imageop.ImageOp;
import VASSAL.tools.imageop.Op;
import VASSAL.tools.imageop.OpMultiResolutionImage;

import VASL.build.module.ASLMap;

class SizedImageIcon extends ImageIcon {
  protected final int w;
  protected final int h;
  private final Image img;

  public SizedImageIcon(Image img, int w, int h) {
    super(img);
    this.w = w;
    this.h = h;
    this.img = img;
  }

  @Override
  public int getIconHeight() {
    return h;
  }

  @Override
  public int getIconWidth() {
    return w;
  }

  @Override
  public void paintIcon(Component c, Graphics g, int x, int y) {
    g.drawImage(img, x, y, w, h, c);
  }
}

class MenuSizedImageIcon extends SizedImageIcon {
  private final int menu_w;
  private final int menu_h;
  private final OpMultiResolutionImage menuOverlay;

  public MenuSizedImageIcon(Image img, int w, int h) {
    super(img, w, h);
    final ImageOp op = Op.load("QC/mnubtn.png");
    final Dimension d = op.getSize();
    menu_w = d.width;
    menu_h = d.height;
    menuOverlay = new OpMultiResolutionImage(op);
  }

  @Override
  public void paintIcon(Component c, Graphics g, int x, int y) {
    super.paintIcon(c, g, x, y);
    g.drawImage(menuOverlay, x + w - menu_w, y + h - menu_h, menu_w, menu_h, c);
  }
}

class QCStartMenuItem extends JPopupMenu.Separator {}

class QCEndMenuItem extends JPopupMenu.Separator {}

class QCStartToolBarItem extends JToolBar.Separator {}

class QCEndToolBarItem extends JToolBar.Separator {}

class QCRadioButtonMenuItem extends JRadioButtonMenuItem {
  private final QCConfiguration m_objQCConfiguration;

  QCRadioButtonMenuItem(QCConfiguration objConfiguration) {
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

class QCButton extends JButton {
  PieceSlot m_objPieceSlot;

  public QCButton(PieceSlot objPieceSlot) {
    super();
    m_objPieceSlot = objPieceSlot;
  }

  public void InitDragDrop() {
    DragGestureListener dragGestureListener = new DragGestureListener() {
      public void dragGestureRecognized(DragGestureEvent dge) {
        startDrag();
        PieceMover.AbstractDragHandler.getTheDragHandler().dragGestureRecognized(dge);
      }
    };
    DragSource.getDefaultDragSource().createDefaultDragGestureRecognizer(this, DnDConstants.ACTION_MOVE, dragGestureListener);
  }

  // Puts counter in DragBuffer. Call when mouse gesture recognized
  protected void startDrag() {
    if (m_objPieceSlot != null) {
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

class QCButtonMenu extends JButton {
  private PieceSlot m_objPieceSlot;
  private JPopupMenu m_objPopupMenu = new JPopupMenu();

  public QCButtonMenu(PieceSlot objPieceSlot) {
    m_objPieceSlot = objPieceSlot;

    addActionListener(e -> { 
      if (getPopupMenu() != null && e.getSource() instanceof JButton) {
        getPopupMenu().show(
          (JButton) e.getSource(),
          ((JButton) e.getSource()).getWidth() - 5,
          ((JButton)e.getSource()).getHeight() - 5
        );
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

class QCMenuItem extends JMenuItem implements DragSourceListener {
  PieceSlot m_objPieceSlot;
  JPopupMenu m_objParentPopupMenu;

  public QCMenuItem(PieceSlot objPieceSlot, JPopupMenu objPopupMenu) {
    super();
    m_objPieceSlot = objPieceSlot;
    m_objParentPopupMenu = objPopupMenu;
  }

  public void InitDragDrop() {
    DragGestureListener dragGestureListener = e -> {
      // FredKors 18.11.2015 Seems to be obsolete
      //if ((dge.getComponent() != null) && (dge.getComponent() instanceof QCMenuItem))
      //DragSource.getDefaultDragSource().addDragSourceListener(((QCMenuItem)dge.getComponent()));

      startDrag();
      PieceMover.AbstractDragHandler.getTheDragHandler().dragGestureRecognized(e);

      // FredKors 18.11.2015 Move the code after the dragGestureRecognized to avoid exception
      // FredKors 29.03.2015 Fix a small annoying thing dragging a counter from a menu
      if (m_objParentPopupMenu != null) {
        m_objParentPopupMenu.setVisible(false);
      }
    };

    DragSource.getDefaultDragSource().createDefaultDragGestureRecognizer(this, DnDConstants.ACTION_MOVE, dragGestureListener);
  }

  // Puts counter in DragBuffer. Call when mouse gesture recognized
  protected void startDrag() {
    if (m_objPieceSlot != null) {
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
      // FredKors 18.11.2015 Move the code after the dragGestureRecognized to avoid exception
      // FredKors 29.03.2015 Fix a small annoying thing dragging a counter from a menu
      //if (m_objParentPopupMenu != null)
      //m_objParentPopupMenu.setVisible(false);            
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
    DragSource.getDefaultDragSource().removeDragSourceListener(this);
  }
}

class QCConfigurationComparator implements Comparator<QCConfiguration> {
  @Override
  public int compare(QCConfiguration o1, QCConfiguration o2) {
    return o1.getDescription().compareTo(o2.getDescription());
  }
}

class QCConfigurationParser extends DefaultHandler {
  private QCConfiguration m_objQCConfiguration;
  private TreeNode m_objCurrentSubMenu;

  public QCConfigurationParser(QCConfiguration objQCConfiguration) {
    m_objQCConfiguration = objQCConfiguration;
    m_objCurrentSubMenu = objQCConfiguration;
  }

  @Override
  public void startElement(String uri, String localName, String qName, Attributes attributes) throws SAXException {
    if (qName.equalsIgnoreCase("qcconfig")) {
      m_objQCConfiguration.setDescription(attributes.getValue("descr"));
    }
    else if (qName.equalsIgnoreCase("qcsubmenu")) {
      QCConfigurationEntry l_newEntry = new QCConfigurationEntry(m_objQCConfiguration.getQC());

      l_newEntry.setMenu(true);
      l_newEntry.setText(attributes.getValue("text"));

      if (l_newEntry.getText() == null) {
        l_newEntry.setText(QCConfiguration.EmptyMenuTitle());
      }

      if (m_objCurrentSubMenu != m_objQCConfiguration) {
        ((DefaultMutableTreeNode)m_objCurrentSubMenu).add(l_newEntry);
      }
      else {
        m_objQCConfiguration.add(l_newEntry);
      }

      m_objCurrentSubMenu = l_newEntry;
    }
    else if (qName.equalsIgnoreCase("qcentry")) {
      QCConfigurationEntry l_newEntry = new QCConfigurationEntry(m_objQCConfiguration.getQC());

      l_newEntry.setGpID(attributes.getValue("slot"));

      if (m_objCurrentSubMenu != m_objQCConfiguration) {
        ((DefaultMutableTreeNode)m_objCurrentSubMenu).add(l_newEntry);
      }
      else {
        m_objQCConfiguration.add(l_newEntry);
      }
    }
  } 

  @Override
  public void endElement (String uri, String localName, String qName) throws SAXException {
    if (qName.equalsIgnoreCase("qcsubmenu")) 
      m_objCurrentSubMenu = m_objCurrentSubMenu.getParent();
  }  
}

class QCConfiguration extends DefaultMutableTreeNode {
  private static final String mc_EmptyMenuTitle = "Empty menu title";

  /**
   * @return the mc_EmptyMenuTitle
   */
  public static String EmptyMenuTitle() {
    return mc_EmptyMenuTitle;
  }

  private boolean m_bBuiltinConfiguration;
  private QC m_objQC;
  private File m_objFile;
  private String m_strDescription;

  public QCConfiguration(QC objQC, File objFile) {
    m_bBuiltinConfiguration = false;

    m_objQC = objQC;
    m_objFile = objFile;

    m_strDescription = "";
  }    

  // copy object
  public QCConfiguration(QCConfiguration objMaster) {
    m_bBuiltinConfiguration = false;

    m_objQC = objMaster.getQC();

    m_strDescription = objMaster.getDescription() + " (copy)";

    Enumeration<TreeNode> l_objChildrenEnum = objMaster.children();

    while (l_objChildrenEnum.hasMoreElements()) {
      add(new QCConfigurationEntry((QCConfigurationEntry) l_objChildrenEnum.nextElement()));        
    }

    File l_dirConfigs = new File(Info.getHomeDir() + System.getProperty("file.separator","\\") + "qcconfigs");

    if (!l_dirConfigs.exists()) {
      try {
        l_dirConfigs.mkdir();
      } 
      catch(Exception e) {
      }        
    }
    else if (!l_dirConfigs.isDirectory()) {
      try {
        l_dirConfigs.delete();
        l_dirConfigs.mkdir();
      } 
      catch(Exception e) {
      }        
    }            

    m_objFile = new File(l_dirConfigs.getPath() + System.getProperty("file.separator","\\") + UUID.randomUUID().toString() + ".xml");
  }

  public void ReadDataFrom(QCConfiguration objMaster) {
    m_bBuiltinConfiguration = objMaster.isBuiltinConfiguration();
    m_objQC = objMaster.getQC();
    m_strDescription = objMaster.getDescription();

    FreeAllNodes(this);

    Enumeration<TreeNode> l_objChildrenEnum = objMaster.children();

    while (l_objChildrenEnum.hasMoreElements()) {
      add(new QCConfigurationEntry((QCConfigurationEntry) l_objChildrenEnum.nextElement()));        
    }
  }

  private void FreeAllNodes(DefaultMutableTreeNode objNode) {
    Enumeration<TreeNode> l_objChildrenEnum = objNode.children();

    while (l_objChildrenEnum.hasMoreElements()) {
      FreeAllNodes((DefaultMutableTreeNode) l_objChildrenEnum.nextElement());
    }

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
    return m_objFile != null ? m_objFile.getName() : "";
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
    if (strDescription != null) {
      this.m_strDescription = strDescription;
    }
  }

  public boolean SaveXML() {
    try {
      if (m_objFile != null) {
        if (!m_objFile.exists()) {
          m_objFile.createNewFile();
        }

        DocumentBuilderFactory l_objDocumentBuilderFactory = DocumentBuilderFactory.newInstance();
        DocumentBuilder l_objDocumentBuilder = l_objDocumentBuilderFactory.newDocumentBuilder();

        // root elements
        Document l_objDocument = l_objDocumentBuilder.newDocument();
        Element l_objRootElement = l_objDocument.createElement("qcconfig");

        Attr l_objAttributes = l_objDocument.createAttribute("descr");
        l_objAttributes.setValue(getDescription());
        l_objRootElement.setAttributeNode(l_objAttributes);

        l_objDocument.appendChild(l_objRootElement);

        Enumeration<TreeNode> l_objChildrenEnum = children();

        while (l_objChildrenEnum.hasMoreElements()) {
          ((QCConfigurationEntry) l_objChildrenEnum.nextElement()).WriteXML(l_objDocument, l_objRootElement);
        }

        // write the content into xml file
        TransformerFactory l_objTransformerFactory = TransformerFactory.newInstance();
        Transformer l_objTransformer = l_objTransformerFactory.newTransformer();
        DOMSource l_objDOMSource = new DOMSource(l_objDocument);

        FileOutputStream l_objFOS = new FileOutputStream(m_objFile);

        l_objTransformer.transform(l_objDOMSource, new StreamResult(l_objFOS));
        l_objFOS.close();
      }

      return true;
    } 
    catch (Exception e) {
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

  public boolean DeleteXML() {
    try {
      if (m_objFile != null && m_objFile.exists()) {
        m_objFile.delete();
      }

      return true;
    } 
    catch (Exception e) {
    }

    return false;
  }
}


class QCConfigurationEntry extends DefaultMutableTreeNode {
  private static BufferedImage m_objMnuBtn = null;

  private final QC m_objQC;
  private boolean m_bMenu;
  private String m_strGpID, m_strText;    
  private PieceSlot m_objPieceSlot;

  public QCConfigurationEntry(QC objQC) {
    super();

    m_objQC = objQC;
    m_bMenu = false;
    m_strGpID = null;
    m_strText = null;
    m_objPieceSlot = null;
  }   

  public QCConfigurationEntry(QCConfigurationEntry objMaster) {
    super();

    m_objQC = objMaster.getQC();
    m_bMenu = objMaster.isMenu();

    if (m_bMenu) {
      Enumeration<TreeNode> l_objChildrenEnum = objMaster.children();

      while(l_objChildrenEnum.hasMoreElements()) {
        add(new QCConfigurationEntry((QCConfigurationEntry) l_objChildrenEnum.nextElement()));
      }
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

  void WriteXML(Document objDocument, Element objElement) {
    if (isMenu()) {
      Element l_objEntry = objDocument.createElement("qcsubmenu");

      if (getText() != null) {
        Attr l_objAttribute = objDocument.createAttribute("text");
        l_objAttribute.setValue(getText());
        l_objEntry.setAttributeNode(l_objAttribute);
      }

      objElement.appendChild(l_objEntry);

      Enumeration<TreeNode> l_objChildrenEnum = children();

      while(l_objChildrenEnum.hasMoreElements()) {
        ((QCConfigurationEntry) l_objChildrenEnum.nextElement()).WriteXML(objDocument, l_objEntry);
      }
    }
    else {
      Element l_objEntry = objDocument.createElement("qcentry");

      if (getGpID() != null) {
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

  public Image CreateButtonIcon() {
    final GamePiece p = getPieceSlot().getPiece();
    final ImageOp pop = Op.piece(p);
    final Dimension d = pop.getSize();
    final int min = Math.min(d.width, d.height);
    return new OpMultiResolutionImage(Op.crop(pop, 0, 0, min, min));
  } 

  /*
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
  */

  public Image CreateButtonMenuIcon() {
    QCConfigurationEntry l_objConfigurationEntryIcon = null;

    if (getPieceSlot() != null) {
      l_objConfigurationEntryIcon = this;            
    }
    else {
      Enumeration<TreeNode> l_objChildrenEnum = children();

      while (l_objChildrenEnum.hasMoreElements()) {
        QCConfigurationEntry l_objConfigurationEntry = (QCConfigurationEntry) l_objChildrenEnum.nextElement();

        if (l_objConfigurationEntry.getPieceSlot() != null) {
          l_objConfigurationEntryIcon = l_objConfigurationEntry;
          break;
        }
      }
    }

    if (l_objConfigurationEntryIcon != null) {
      return l_objConfigurationEntryIcon.CreateButtonIcon();
    }
    else {
      final int l_iSize = 30;
      final BufferedImage l_objBI = ImageUtils.createCompatibleTranslucentImage(l_iSize, l_iSize);
      final Graphics2D l_objGraphics = l_objBI.createGraphics();

      l_objGraphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

      l_objGraphics.setColor(Color.WHITE);
      l_objGraphics.fillRect(0, 0, l_iSize, l_iSize);            

      l_objGraphics.dispose();

      return new BaseMultiResolutionImage(l_objBI);
    }
  }

  /*
     public BufferedImage CreateButtonMenuIcon() 
     {
     QCConfigurationEntry l_objConfigurationEntryIcon = null;

     if (getPieceSlot() != null)
     {
     l_objConfigurationEntryIcon = this;            
     }
     else
     {
     Enumeration<TreeNode> l_objChildrenEnum = children();

     while(l_objChildrenEnum.hasMoreElements())
     {
     QCConfigurationEntry l_objConfigurationEntry = (QCConfigurationEntry) l_objChildrenEnum.nextElement();

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
  */
}

/**
 * A class to represent the counters toolbar
 */
public class QC implements Buildable, GameComponent {
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
    "	<qcsubmenu text=\"Residual Fires\">\n" +
    "		<qcentry slot=\"47\"/>\n" +
    "		<qcentry slot=\"48\"/>\n" +
    "		<qcentry slot=\"49\"/>\n" +
    "		<qcentry slot=\"50\"/>\n" +
    "		<qcentry slot=\"51\"/>\n" +
    "		<qcentry slot=\"52\"/>\n" +
    "	</qcsubmenu>\n" +
    "	<qcsubmenu text=\"Fire Lanes\">\n" +
    "		<qcentry slot=\"53\"/>\n" +
    "		<qcentry slot=\"54\"/>\n" +
    "		<qcentry slot=\"55\"/>\n" +
    "		<qcentry slot=\"56\"/>\n" +
    "	</qcsubmenu>\n" +
    "	<qcsubmenu text=\"Smokes\">\n" +
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
    "	<qcsubmenu text=\"CC, HW\">\n" +
    "		<qcentry slot=\"73\"/>\n" +
    "		<qcentry slot=\"74\"/>\n" +
    "		<qcentry slot=\"83\"/>\n" +
    "		<qcentry slot=\"85\"/>\n" +
    "		<qcentry slot=\"88\"/>\n" +
    "	</qcsubmenu>\n" +
    "	<qcsubmenu text=\"Prisoners\">\n" +
    "		<qcentry slot=\"6390\"/>\n" +
    "		<qcentry slot=\"6391\"/>\n" +
    "	</qcsubmenu>\n" +
    "	<qcsubmenu text=\"TI, Labor, CA, Low Ammo\">\n" +
    "		<qcentry slot=\"68\"/>\n" +
    "		<qcentry slot=\"66\"/>\n" +
    "		<qcentry slot=\"5903\"/>\n" +
    "		<qcentry slot=\"5902\"/>\n" +
    "	</qcsubmenu>\n" +
    "	<qcsubmenu text=\"Acquisitions\">\n" +
    "		<qcentry slot=\"423\"/>\n" +
    "		<qcentry slot=\"773\"/>\n" +
    "		<qcentry slot=\"1066\"/>\n" +
    "		<qcentry slot=\"1589\"/>\n" +
    "		<qcentry slot=\"1939\"/>\n" +
    "		<qcentry slot=\"2122\"/>\n" +
    "		<qcentry slot=\"3b5:4188\"/>\n" +
    "		<qcentry slot=\"11430\"/>\n" +
    "		<qcentry slot=\"2353\"/>\n" +
    "		<qcentry slot=\"2831\"/>\n" +
    "		<qcentry slot=\"3192\"/>\n" +
    "		<qcentry slot=\"3380\"/>\n" +
    "		<qcentry slot=\"3573\"/>\n" +
    "		<qcentry slot=\"11487\"/>\n" +
    "		<qcentry slot=\"3916\"/>\n" +
    "		<qcentry slot=\"11542\"/>\n" +
    "		<qcentry slot=\"3b5:7863\"/>\n" +
    "	</qcsubmenu>\n" +
    "	<qcsubmenu text=\"AFV Bad Things\">\n" +
    "		<qcentry slot=\"41\"/>\n" +
    "		<qcentry slot=\"5918\"/>\n" +
    "		<qcentry slot=\"5919\"/>\n" +
    "		<qcentry slot=\"5920\"/>\n" +
    "	</qcsubmenu>\n" +
    "	<qcsubmenu text=\"AFV Motion status\">\n" +
    "               <qcentry slot=\"101\"/>\n" +
    "               <qcentry slot=\"111\"/>\n" +
    "         	<qcentry slot=\"116\"/>\n" +
    "               <qcentry slot=\"109\"/>\n" +
    "               <qcentry slot=\"113\"/>\n" +
    "	</qcsubmenu>\n" +
    "	<qcentry slot=\"126\"/>\n" +
    "	<qcentry slot=\"128\"/>\n" +
    "	<qcentry slot=\"129\"/>\n" +
    "	<qcentry slot=\"123\"/>\n" +
    "	<qcentry slot=\"104\"/>\n" +
    "	<qcentry slot=\"344\"/>\n" +
    "	<qcsubmenu text=\"Building Levels\">\n" +
    "		<qcentry slot=\"171\"/>\n" +
    "		<qcentry slot=\"167\"/>\n" +
    "		<qcentry slot=\"169\"/>\n" +
    "		<qcentry slot=\"163\"/>\n" +
    "		<qcentry slot=\"165\"/>\n" +
    "		<qcentry slot=\"202\"/>\n" +
    "		<qcentry slot=\"203\"/>\n" +
    "		<qcentry slot=\"204\"/>\n" +
    "	</qcsubmenu>\n" +
    "</qcconfig>";

  private JButton m_objUndoButton = null;
  private JButton m_objStepButton = null;
  private JButton m_objCountersWindowButton = null;
  private JButton m_objDraggableOverlaysWindowButton = null;
  private JButton m_objDeluxeDraggableOverlaysWindowButton = null;
  private JToggleButton m_objBrokenFinderButton = null;
  private JToggleButton sniperFinderButton = null;
  //ASLCasbin
  private JToggleButton m_objASLCasbinButton = null;
  private Map m_objMap;
  private final ArrayList<QCConfiguration> mar_objListQCConfigurations = new ArrayList<QCConfiguration>();
  private QCConfiguration m_objQCWorkingConfiguration = null;
  private QCConfig m_objQCConfig = null;
  private boolean m_bEditing = false;
  private Hashtable mar_HashPieceSlot = new Hashtable();
  private final String QCLASTCONFIGURATIONUSED = "QCLastConfigurationUsed"; //$NON-NLS-1$

  public void loadConfigurations() {
    SAXParser l_objXMLParser;
    File l_dirConfigs = new File(Info.getHomeDir() + System.getProperty("file.separator","\\") + "qcconfigs");

    try {
      SAXParserFactory l_objXMLParserFactory = SAXParserFactory.newInstance();
      l_objXMLParser = l_objXMLParserFactory.newSAXParser();
    } 
    catch (Exception ex) {
      l_objXMLParser = null;                    
    }

    if (l_objXMLParser != null) {
      // clear the old configurations (if any)
      mar_objListQCConfigurations.clear();

      // read built-in configuration
      m_objQCWorkingConfiguration = new QCConfiguration(this, null); // null file for the built-in configuration

      try {
        QCConfigurationParser l_objQCConfigurationParser = new QCConfigurationParser(m_objQCWorkingConfiguration);
        // parse the built-in configuration
        l_objXMLParser.parse(new InputSource(new StringReader(mc_strBuiltinConfig)), l_objQCConfigurationParser);
      }
      catch (Exception ex) {
        m_objQCWorkingConfiguration = null;
      }

      if (m_objQCWorkingConfiguration != null) {
        m_objQCWorkingConfiguration.setBuiltintConfiguration(true);
      }

      // now read the custom configuration files
      // check for configs dir
      if (!l_dirConfigs.exists()) {
        try {
          l_dirConfigs.mkdir();
        } 
        catch(Exception e) {
        }        
      }
      else if (!l_dirConfigs.isDirectory()) {
        try {
          l_dirConfigs.delete();
          l_dirConfigs.mkdir();
        } 
        catch(Exception e) {
        }        
      }            
      else {
        // browsing configs files
        File[] lar_objConfigFiles = l_dirConfigs.listFiles(
          (objFile, strName) -> strName.toLowerCase().endsWith(".xml")
        );
        for (File l_objConfigFile : lar_objConfigFiles) {
          //Create an instance of this class; it defines all the handler methods
          QCConfiguration l_objQCConfiguration = new QCConfiguration(this, l_objConfigFile);

          try {
            QCConfigurationParser l_objQCConfigurationParser = new QCConfigurationParser(l_objQCConfiguration);
            // parse the config file
            l_objXMLParser.parse(l_objConfigFile, l_objQCConfigurationParser);
          }
          catch (Exception ex) {
            l_objQCConfiguration = null;
          }

          if (l_objQCConfiguration != null) {
            mar_objListQCConfigurations.add(l_objQCConfiguration);
          }
          else {
            try {
              l_objConfigFile.delete();
            }
            catch (Exception ex) {
            }
          }
        }

        Collections.sort(mar_objListQCConfigurations, new QCConfigurationComparator());
      }      

      if (m_objQCWorkingConfiguration != null) {
        mar_objListQCConfigurations.add(0, m_objQCWorkingConfiguration);
      }
    }
  }

  public void readWorkingConfiguration() {
    String l_strWorkingConfigurationName = (String)GameModule.getGameModule().getPrefs().getValue(QCLASTCONFIGURATIONUSED);

    if (l_strWorkingConfigurationName == null) l_strWorkingConfigurationName = "";

    for (QCConfiguration l_objQCConfiguration : mar_objListQCConfigurations) {
      if (l_strWorkingConfigurationName.equalsIgnoreCase(l_objQCConfiguration.getName())) {
        m_objQCWorkingConfiguration = l_objQCConfiguration;
        break;                
      }
    }
  }	

  public void saveWorkingConfiguration() {
    if (m_objQCWorkingConfiguration != null) {
      GameModule.getGameModule().getPrefs().setValue(QCLASTCONFIGURATIONUSED, m_objQCWorkingConfiguration.getName());

      try {
        GameModule.getGameModule().getPrefs().save();
      }
      catch (Exception ex) {
        ex.printStackTrace();
      }
    }
  }

  public void build(Element e) {
    if (GameModule.getGameModule().getPrefs().getOption(QCLASTCONFIGURATIONUSED) == null) {
      GameModule.getGameModule().getPrefs().addOption(null, new StringConfigurer(QCLASTCONFIGURATIONUSED, null));            
    }

    loadConfigurations();
    readWorkingConfiguration();
  }

  public org.w3c.dom.Element getBuildElement(org.w3c.dom.Document doc) {
    return doc.createElement(getClass().getName());
  }

  public void addTo(Buildable b) {
    m_objMap = (Map) b;

    ((ASLMap)m_objMap).getPopupMenu().add(new QCStartMenuItem());
    ((ASLMap)m_objMap).getPopupMenu().add(new QCEndMenuItem());

    RebuildPopupMenu();

    if (!m_objMap.shouldDockIntoMainWindow()) {
      m_objMap.getToolBar().add(new JToolBar.Separator(), 1);
      m_objUndoButton = new JButton();
      m_objMap.getToolBar().add(m_objUndoButton, 2);

      m_objStepButton = new JButton();
      m_objMap.getToolBar().add(m_objStepButton, 3);
    }

    m_objMap.getToolBar().add(new JToolBar.Separator());

    if (!m_objMap.shouldDockIntoMainWindow()) {
      if (m_objCountersWindowButton == null) {
        m_objCountersWindowButton = new JButton();
        m_objMap.getToolBar().add(m_objCountersWindowButton);       
      }

      if (m_objDraggableOverlaysWindowButton == null) {
        m_objDraggableOverlaysWindowButton = new JButton();
        m_objMap.getToolBar().add(m_objDraggableOverlaysWindowButton);
      }

      if (m_objDeluxeDraggableOverlaysWindowButton == null) {
        m_objDeluxeDraggableOverlaysWindowButton = new JButton();
        m_objMap.getToolBar().add(m_objDeluxeDraggableOverlaysWindowButton);
      }
    }

    if (m_objBrokenFinderButton == null) {
      m_objBrokenFinderButton = new JToggleButton();
      m_objMap.getToolBar().add(m_objBrokenFinderButton);       
    }

    if (sniperFinderButton == null) {
      sniperFinderButton = new JToggleButton();
      m_objMap.getToolBar().add(sniperFinderButton);
    }
    //ASLCasbin
    if (m_objASLCasbinButton == null) {
      m_objASLCasbinButton = new JToggleButton();
      m_objMap.getToolBar().add(m_objASLCasbinButton);
    }

    JButton l_objButtonMarkMoved = new JButton();
    l_objButtonMarkMoved.setName("MarkMovedPlaceHolder");
    m_objMap.getToolBar().add(l_objButtonMarkMoved);

    m_objMap.getToolBar().add(new QCStartToolBarItem());
    m_objMap.getToolBar().add(new QCEndToolBarItem());

    GameModule.getGameModule().getGameState().addGameComponent(this);
  }

  private void ReadPiecesSlot() {
    List<PieceSlot> lar_PieceSlotL = GameModule.getGameModule().getAllDescendantComponentsOf(PieceSlot.class);

    for (PieceSlot l_objPieceSlot : lar_PieceSlotL) {
      mar_HashPieceSlot.put(l_objPieceSlot.getGpId(), l_objPieceSlot);
    }

    lar_PieceSlotL = null;

    for (QCConfiguration l_objQCConfiguration : mar_objListQCConfigurations) {
      Enumeration<TreeNode> l_objChildrenEnum = l_objQCConfiguration.children();

      while(l_objChildrenEnum.hasMoreElements()) {
        setPieceSlot((QCConfigurationEntry) l_objChildrenEnum.nextElement());
      }
    }
  }

  private void setPieceSlot(QCConfigurationEntry objConfigurationEntry) {
    PieceSlot l_objPieceSlot = null;

    if (objConfigurationEntry.getGpID() != null) {
      l_objPieceSlot = (PieceSlot) mar_HashPieceSlot.get(objConfigurationEntry.getGpID());

      if (l_objPieceSlot != null) {
        objConfigurationEntry.setPieceSlot(l_objPieceSlot);
      }
    }

    if (objConfigurationEntry.isMenu()) {
      Enumeration<TreeNode> l_objChildrenEnum = objConfigurationEntry.children();

      while(l_objChildrenEnum.hasMoreElements()) {
        setPieceSlot((QCConfigurationEntry) l_objChildrenEnum.nextElement());
      }
    }
  }

  public void add(Buildable b) {}

  private void RebuildToolBar() {
    JToolBar l_objToolBar = m_objMap.getToolBar();
    boolean l_bEndElementNotFound = true;
    int l_iStartPos = 0;

    if (m_objQCWorkingConfiguration != null) {
      // remove the old element
      for (int l_i = l_objToolBar.getComponents().length - 1; l_i >= 0; l_i--) {
        Component l_objComponent = l_objToolBar.getComponent(l_i);

        if (l_bEndElementNotFound) {
          if (l_objComponent instanceof QCEndToolBarItem) { 
            l_bEndElementNotFound = false;                
          }
        }
        else {
          if (l_objComponent instanceof QCStartToolBarItem) {
            l_iStartPos = l_i + 1;
            break;
          }
          else {
            l_objToolBar.remove(l_i);               
          }
        }
      }

      Enumeration<TreeNode> l_objChildrenEnum = m_objQCWorkingConfiguration.children();

      while (l_objChildrenEnum.hasMoreElements()) {                
        Component l_objComponent = CreateToolBarItem((QCConfigurationEntry) l_objChildrenEnum.nextElement());

        if (l_objComponent != null) {
          l_objToolBar.add(l_objComponent, l_iStartPos++);                
        }
      }

      l_objToolBar.revalidate();
      l_objToolBar.repaint();
    }
  }

  private Component CreateToolBarItem(QCConfigurationEntry objConfigurationEntry) {
    if (objConfigurationEntry.isMenu()) {
      // submenu
      QCButtonMenu l_objQCButtonMenu = new QCButtonMenu(objConfigurationEntry.getPieceSlot());

      try {
        if (objConfigurationEntry.getText() != null) {
          l_objQCButtonMenu.setToolTipText(objConfigurationEntry.getText());
        }
        else {
          l_objQCButtonMenu.setToolTipText(QCConfiguration.EmptyMenuTitle());
        }
        l_objQCButtonMenu.setIcon(new MenuSizedImageIcon(objConfigurationEntry.CreateButtonMenuIcon(), 30, 30));
        l_objQCButtonMenu.setMargin(new Insets(0, 0, 0, 0));

        CreatePopupMenu(objConfigurationEntry, l_objQCButtonMenu);
      } 
      catch (Exception ex) {
        ex.printStackTrace();
      }

      l_objQCButtonMenu.setAlignmentY(0.0F);

      return l_objQCButtonMenu;
    }
    else {
      // button standard
      if (objConfigurationEntry.getPieceSlot() != null) {
        QCButton l_objQCButton = new QCButton(objConfigurationEntry.getPieceSlot());

        try {
          l_objQCButton.InitDragDrop();
          l_objQCButton.setIcon(new SizedImageIcon(objConfigurationEntry.CreateButtonIcon(), 30, 30));
          l_objQCButton.setMargin(new Insets(0, 0, 0, 0));
        } 
        catch (Exception ex) {
          ex.printStackTrace();
        }

        l_objQCButton.setAlignmentY(0.0F);

        return l_objQCButton;
      }
    } 

    return null;
  }

  private void CreatePopupMenu(QCConfigurationEntry objConfigurationEntry, QCButtonMenu objQCButtonMenu) {
    JPopupMenu l_objPopupMenu = objQCButtonMenu.getPopupMenu();

    Enumeration<TreeNode> l_objChildrenEnum = objConfigurationEntry.children();

    while (l_objChildrenEnum.hasMoreElements()) {
      JMenuItem l_objMenuItem = CreateMenuItem((QCConfigurationEntry) l_objChildrenEnum.nextElement(), l_objPopupMenu);

      if (l_objMenuItem != null) {
        l_objPopupMenu.add(l_objMenuItem);
      }
    }
  }

  private JMenuItem CreateMenuItem(QCConfigurationEntry objConfigurationEntry, JPopupMenu objPopupMenu) {
    if (objConfigurationEntry.isMenu()) {
      //submenu
      JMenu l_objMenu = new JMenu();

      try {
        if (objConfigurationEntry.getText() != null) {
          l_objMenu.setText(objConfigurationEntry.getText()); 
        }
        else {
          l_objMenu.setText(QCConfiguration.EmptyMenuTitle());
        }

        l_objMenu.setIcon(new MenuSizedImageIcon(objConfigurationEntry.CreateButtonMenuIcon(), 30, 30));
      } 
      catch (Exception ex) {
        ex.printStackTrace();
      }

      Enumeration<TreeNode> l_objChildrenEnum = objConfigurationEntry.children();

      while(l_objChildrenEnum.hasMoreElements()) {
        JMenuItem l_objMenuItem = CreateMenuItem((QCConfigurationEntry) l_objChildrenEnum.nextElement(), objPopupMenu);

        if (l_objMenuItem != null) {
          l_objMenu.add(l_objMenuItem);
        }
      }

      return l_objMenu;
    }
    else {
      if (objConfigurationEntry.getPieceSlot() != null) {
        QCMenuItem l_MenuItem = new QCMenuItem(objConfigurationEntry.getPieceSlot(), objPopupMenu);

        try {
          l_MenuItem.setText(objConfigurationEntry.getPieceSlot().getPiece().getName());
          l_MenuItem.setIcon(new SizedImageIcon(objConfigurationEntry.CreateButtonIcon(), 30, 30));
          l_MenuItem.InitDragDrop();
        } 
        catch (Exception ex) {
          ex.printStackTrace();
        }

        return l_MenuItem;
      }
    }            

    return null;
  }

  private void RebuildPopupMenu() {
    JPopupMenu l_objPopupMenu = ((ASLMap)m_objMap).getPopupMenu();
    final JMenuItem l_objCopyConfigurationMenuItem  = new JMenuItem(), l_objRemoveConfigurationMenuItem  = new JMenuItem(), l_objEditConfigurationMenuItem = new JMenuItem();;
    int l_iStartPos = 0;
    boolean l_bEndElementNotFound = true;

    // remove the old element
    for (int l_i = l_objPopupMenu.getComponents().length - 1; l_i >= 0; l_i--) {
      Component l_objComponent = l_objPopupMenu.getComponent(l_i);

      if (l_bEndElementNotFound) {
        if (l_objComponent instanceof QCEndMenuItem) { 
          l_bEndElementNotFound = false;                
        }
      }
      else {
        if (l_objComponent instanceof QCStartMenuItem) {
          l_iStartPos = l_i + 1;
          break;
        }
        else {
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

    for (QCConfiguration l_objQCConfiguration : mar_objListQCConfigurations) {
      QCRadioButtonMenuItem l_objQCRadioButtonMenuItem = new QCRadioButtonMenuItem(l_objQCConfiguration);

      l_objQCRadioButtonMenuItem.addActionListener(evt -> {
        if (evt.getSource() instanceof QCRadioButtonMenuItem) {
          m_objQCWorkingConfiguration = ((QCRadioButtonMenuItem)evt.getSource()).getQCConfiguration();
          saveWorkingConfiguration();
          RebuildToolBar();

          if (m_objQCWorkingConfiguration != null && !m_bEditing) {
            l_objCopyConfigurationMenuItem.setEnabled(true);

            if (m_objQCWorkingConfiguration.isBuiltinConfiguration()) {
              l_objRemoveConfigurationMenuItem.setEnabled(false);
              l_objEditConfigurationMenuItem.setEnabled(false);
            }
            else {
              l_objRemoveConfigurationMenuItem.setEnabled(true);
              l_objEditConfigurationMenuItem.setEnabled(true);
            }
          }
          else {
            l_objCopyConfigurationMenuItem.setEnabled(false);
            l_objRemoveConfigurationMenuItem.setEnabled(false);
            l_objEditConfigurationMenuItem.setEnabled(false);
          }
        }
      });

      l_Group.add(l_objQCRadioButtonMenuItem);
      l_objPopupMenu.add(l_objQCRadioButtonMenuItem, l_iStartPos++);

      l_objQCRadioButtonMenuItem.setSelected(l_objQCConfiguration == m_objQCWorkingConfiguration);
    }

    l_objPopupMenu.add(new JPopupMenu.Separator(), l_iStartPos++);

    // copy configuration
    l_objCopyConfigurationMenuItem.setText("Copy current QC configuration");

    if ((m_objQCWorkingConfiguration != null) && (!m_bEditing)) {
      l_objCopyConfigurationMenuItem.setEnabled(true);
    }
    else {
      l_objCopyConfigurationMenuItem.setEnabled(false);
    }

    try {
      l_objCopyConfigurationMenuItem.setIcon(new ImageIcon(Op.load("QC/copy.png").getImage(null)));
    }
    catch (Exception ex) {
      ex.printStackTrace();
    }

    l_objCopyConfigurationMenuItem.addActionListener(evt -> {
      if (m_objQCWorkingConfiguration != null) {
        QCConfiguration l_objNewConfiguration = new QCConfiguration(m_objQCWorkingConfiguration);

        if (l_objNewConfiguration.SaveXML()) {
          mar_objListQCConfigurations.add(l_objNewConfiguration);
          m_objQCWorkingConfiguration = l_objNewConfiguration;
          saveWorkingConfiguration();

          ResortConfigurations();
  
          RebuildPopupMenu();
          RebuildToolBar();
        }
      }
    });

    l_objPopupMenu.add(l_objCopyConfigurationMenuItem, l_iStartPos++);

    // remove configuration
    l_objRemoveConfigurationMenuItem.setText("Delete current QC configuration");

    if (m_objQCWorkingConfiguration != null && !m_bEditing) {
      if (m_objQCWorkingConfiguration.isBuiltinConfiguration()) {
        l_objRemoveConfigurationMenuItem.setEnabled(false);
      }
      else {
        l_objRemoveConfigurationMenuItem.setEnabled(true);
      }
    }
    else {
      l_objRemoveConfigurationMenuItem.setEnabled(false);
    }

    try {
      l_objRemoveConfigurationMenuItem.setIcon(new ImageIcon(Op.load("QC/delete.png").getImage(null))); 
    }
    catch (Exception ex) {
      ex.printStackTrace();
    }

    l_objRemoveConfigurationMenuItem.addActionListener(evt -> {
      if (m_objQCWorkingConfiguration != null) {
        int l_iSelectedOption = JOptionPane.showConfirmDialog(m_objMap.getView(), 
            "Do you really want to delete the '" + m_objQCWorkingConfiguration.getDescription() + "' QC configuration? This is NOT undoable or reversible!", 
            "Delete the current QC configuration", 
            JOptionPane.YES_NO_OPTION,
            JOptionPane.QUESTION_MESSAGE); 

        if (l_iSelectedOption == JOptionPane.YES_OPTION) {
          m_objQCWorkingConfiguration.DeleteXML();

          int l_iIndex = mar_objListQCConfigurations.indexOf(m_objQCWorkingConfiguration);

          if (l_iIndex != -1) {
            if (l_iIndex > 0) {
              mar_objListQCConfigurations.remove(m_objQCWorkingConfiguration);
              m_objQCWorkingConfiguration = mar_objListQCConfigurations.get(l_iIndex - 1);
            }
          }

          saveWorkingConfiguration();

          RebuildPopupMenu();
          RebuildToolBar();
        }
      }
    });

    l_objPopupMenu.add(l_objRemoveConfigurationMenuItem, l_iStartPos++);

    // separator
    l_objPopupMenu.add(new JPopupMenu.Separator(), l_iStartPos++);

    // edit configuration
    l_objEditConfigurationMenuItem.setText("Modify current QC configuration");

    if (m_objQCWorkingConfiguration != null && !m_bEditing) {
      if (m_objQCWorkingConfiguration.isBuiltinConfiguration()) {
        l_objEditConfigurationMenuItem.setEnabled(false);
      }
      else {
        l_objEditConfigurationMenuItem.setEnabled(true);
      }
    }
    else {
      l_objEditConfigurationMenuItem.setEnabled(false);
    }

    try {
      l_objEditConfigurationMenuItem.setIcon(new ImageIcon(Op.load("QC/edit.png").getImage(null)));
    }
    catch (Exception ex) {
      ex.printStackTrace();
    }

    l_objEditConfigurationMenuItem.addActionListener(evt -> {
      if (m_objQCWorkingConfiguration != null) {
        //open the configuration window
        if (m_objQCConfig == null) {
          m_objQCConfig = new QCConfig();
        }

        m_objQCConfig.setConfiguration(m_objQCWorkingConfiguration);

        m_bEditing = true;
        RebuildPopupMenu();
      }
    });

    l_objPopupMenu.add(l_objEditConfigurationMenuItem, l_iStartPos++);
  }

  public void UpdateQC(boolean bClosing, boolean bSaving) {
    if (bClosing) {
      m_bEditing = false;
    }

    if (bSaving) {
      ResortConfigurations();
    }

    RebuildPopupMenu();

    if (bSaving) {
      RebuildToolBar();            
    }
  }

  private void ResortConfigurations() {
    QCConfiguration l_objBuiltinConfiguration = mar_objListQCConfigurations.get(0);
    mar_objListQCConfigurations.remove(l_objBuiltinConfiguration);

    Collections.sort(mar_objListQCConfigurations, new QCConfigurationComparator());
    mar_objListQCConfigurations.add(0, l_objBuiltinConfiguration);
  }

  public PieceSlot getPieceSlot(String strGpID) {
    PieceSlot l_objPieceSlot = null;

    if (strGpID != null) {
      l_objPieceSlot = (PieceSlot) mar_HashPieceSlot.get(strGpID);
    }

    return l_objPieceSlot;
  }

  public void setup(boolean bln) {
    if (bln) {
      if (m_objUndoButton != null && m_objUndoButton.getAction() == null) {
        for (int l_i = 0; l_i < GameModule.getGameModule().getToolBar().getComponentCount(); l_i++) {
          if (GameModule.getGameModule().getToolBar().getComponent(l_i) instanceof JButton) {
            JButton l_objB = ((JButton)(GameModule.getGameModule().getToolBar().getComponent(l_i)));

            if (l_objB.getAction() instanceof BasicLogger.UndoAction) {
              CopyActionButton(m_objUndoButton, l_objB, true);
              break;
            }                        
          }
        }
      }

      if (m_objStepButton != null && m_objStepButton.getAction() == null) {
        for (int l_i = 0; l_i < GameModule.getGameModule().getToolBar().getComponentCount(); l_i++) {
          if (GameModule.getGameModule().getToolBar().getComponent(l_i) instanceof JButton) {
            JButton l_objB = ((JButton)(GameModule.getGameModule().getToolBar().getComponent(l_i)));

            if (l_objB.getAction() instanceof BasicLogger.StepAction) {
              CopyActionButton(m_objStepButton, l_objB, true);
              break;
            }                        
          }
        }
      }           

      if (m_objCountersWindowButton != null && m_objCountersWindowButton.getActionListeners().length == 0) {
        for (int l_i = 0; l_i < GameModule.getGameModule().getToolBar().getComponentCount(); l_i++) {
          if (GameModule.getGameModule().getToolBar().getComponent(l_i) instanceof JButton) {
            JButton l_objB = ((JButton)(GameModule.getGameModule().getToolBar().getComponent(l_i)));

            if (l_objB.getToolTipText().contains("VASL Counters window")) {
              CopyActionButton(m_objCountersWindowButton, l_objB, false);
              break;
            }                        
          }
        }
      }

      if (m_objDraggableOverlaysWindowButton != null && m_objDraggableOverlaysWindowButton.getActionListeners().length == 0) {
        for (int l_i = 0; l_i < GameModule.getGameModule().getToolBar().getComponentCount(); l_i++) {
          if (GameModule.getGameModule().getToolBar().getComponent(l_i) instanceof JButton) {
            JButton l_objB = ((JButton)(GameModule.getGameModule().getToolBar().getComponent(l_i)));

            if (l_objB.getToolTipText().contains("the Draggable Overlays window")) {
              CopyActionButton(m_objDraggableOverlaysWindowButton, l_objB, false);
              break;
            }                        
          }
        }
      }

      if (m_objDeluxeDraggableOverlaysWindowButton != null && m_objDeluxeDraggableOverlaysWindowButton.getActionListeners().length == 0) {
        for (int l_i = 0; l_i < GameModule.getGameModule().getToolBar().getComponentCount(); l_i++) {
          if (GameModule.getGameModule().getToolBar().getComponent(l_i) instanceof JButton) {
            JButton l_objB = ((JButton)(GameModule.getGameModule().getToolBar().getComponent(l_i)));

            if (l_objB.getToolTipText().contains("Deluxe Draggable Overlays")) {
              CopyActionButton(m_objDeluxeDraggableOverlaysWindowButton, l_objB, false);
              break;
            }                        
          }
        }
      }

      if (m_objBrokenFinderButton != null && m_objBrokenFinderButton.getActionListeners().length == 0) {
        try {
          m_objBrokenFinderButton.setIcon(new ImageIcon(Op.load("malf").getImage(null)));
        }
        catch (Exception ex) {
        }

        ActionListener l_objAL = e -> { 
          try {
            ASLBrokenFinder l_objBrokenFinder = m_objMap.getComponentsOf(ASLBrokenFinder.class).iterator().next();

            if (l_objBrokenFinder != null) {
              l_objBrokenFinder.findBrokenPiece(m_objBrokenFinderButton.isSelected());
            }
          }
          catch (Exception ex) {
          }
        };

        m_objBrokenFinderButton.addActionListener(l_objAL);
        m_objBrokenFinderButton.setToolTipText("Turn on/off the highlighting of broken units/weapons");
      }

      if (sniperFinderButton != null && sniperFinderButton.getActionListeners().length == 0) {
        try {
          sniperFinderButton.setIcon(new ImageIcon(Op.load("sniper").getImage(null)));
        }
        catch (Exception e) {
          e.printStackTrace();
        }

        ActionListener al = e -> { 
          ASLSniperFinder sniperFinder = m_objMap.getComponentsOf(ASLSniperFinder.class).iterator().next();

          if (sniperFinder != null) {
            sniperFinder.findSniper(sniperFinderButton.isSelected());
          }
        };

        sniperFinderButton.addActionListener(al);
        sniperFinderButton.setToolTipText("Turn on/off the highlighting of sniperF counters");
      }
      // ASLCasbn
      if (m_objASLCasbinButton != null && m_objASLCasbinButton.getActionListeners().length == 0) {
        try {
          m_objASLCasbinButton.setIcon(new ImageIcon(Op.load("cpv").getImage(null)));
        }
        catch (Exception ex) {
        }

        ActionListener l_objAL = e -> { 
          try {
            ASLCasbin l_objCasbin = m_objMap.getComponentsOf(ASLCasbin.class).iterator().next();

            if (l_objCasbin != null) {
              l_objCasbin.startcasbin(m_objASLCasbinButton.isSelected());
            }
          }
          catch (Exception ex) {
          }
        };

        m_objASLCasbinButton.addActionListener(l_objAL);
        m_objASLCasbinButton.setToolTipText("Report CVP Totals");
      }

      if (mar_HashPieceSlot.isEmpty()) {
        ReadPiecesSlot();
        RebuildToolBar();
      }
    }
  }

  public Command getRestoreCommand() {
    return null;
  }

  private void CopyActionButton(JButton objDestButton, JButton objSourceButton, boolean bAction) {
    objDestButton.setText(objSourceButton.getText());

    try {
      if (objSourceButton.getIcon() != null) {
        objDestButton.setIcon(objSourceButton.getIcon());
      }
    }
    catch (Exception ex) {
    }

    if (bAction) {
      if (objSourceButton.getAction() != null) {
        objDestButton.setAction(objSourceButton.getAction());            
      }
    }
    else {
      for (int l_i = 0; l_i < objSourceButton.getActionListeners().length; l_i++) {
        objDestButton.addActionListener(objSourceButton.getActionListeners()[l_i]);
      }
    }

    objDestButton.setToolTipText(objSourceButton.getToolTipText());
    objSourceButton.setVisible(false);
  }
}
