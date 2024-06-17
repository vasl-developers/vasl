package VASL.build.module.map;

import java.awt.Color;
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
import java.awt.RenderingHints;
import java.awt.dnd.DnDConstants;
import java.awt.dnd.DragGestureListener;
import java.awt.dnd.DragSource;
import java.awt.dnd.DragSourceDragEvent;
import java.awt.dnd.DragSourceDropEvent;
import java.awt.dnd.DragSourceEvent;
import java.awt.dnd.DragSourceListener;
import java.awt.image.BaseMultiResolutionImage;
import java.awt.image.BufferedImage;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Enumeration;
import java.util.Hashtable;
import java.util.List;
import java.util.UUID;

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

class QCStartMenuItem extends JPopupMenu.Separator {}
class QCEndMenuItem extends JPopupMenu.Separator {}
class QCStartToolBarItem extends JToolBar.Separator {}
class QCEndToolBarItem extends JToolBar.Separator {}

class QCRadioButtonMenuItem extends JRadioButtonMenuItem {
  private final QCConfiguration qcConfiguration;

  QCRadioButtonMenuItem(QCConfiguration configuration) {
    super(configuration.getDescription());

    qcConfiguration = configuration;
  }

  /**
   * @return the qcConfiguration
   */
  public QCConfiguration getQCConfiguration() {
    return qcConfiguration;
  }
}

class QCButton extends JButton {
  PieceSlot pieceSlot;

  public QCButton(PieceSlot slot) {
    super();
    pieceSlot = slot;
  }

  public void InitDragDrop() {
    DragGestureListener dragGestureListener = dge -> {
      startDrag();
      PieceMover.AbstractDragHandler.getTheDragHandler().dragGestureRecognized(dge);
    };
    DragSource.getDefaultDragSource().createDefaultDragGestureRecognizer(this, DnDConstants.ACTION_MOVE, dragGestureListener);
  }

  // Puts counter in DragBuffer. Call when mouse gesture recognized
  protected void startDrag() {
    if (pieceSlot != null) {
      pieceSlot.getPiece().setPosition(new Point(0, 0));

      // Erase selection border to avoid leaving selected after mouse dragged out
      pieceSlot.getPiece().setProperty(Properties.SELECTED, null);

      if (pieceSlot.getPiece() != null) {
        KeyBuffer.getBuffer().clear();
        DragBuffer.getBuffer().clear();
        GamePiece gamePiece = PieceCloner.getInstance().clonePiece(pieceSlot.getPiece());
        gamePiece.setProperty(Properties.PIECE_ID, pieceSlot.getGpId());
        DragBuffer.getBuffer().add(gamePiece);
      }
    }
  }
}

class QCButtonMenu extends JButton {
  private final JPopupMenu popupMenu = new JPopupMenu();

  public QCButtonMenu() {

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
   * @return the popupMenu
   */
  public JPopupMenu getPopupMenu() {
    return popupMenu;
  }
}

class QCMenuItem extends JMenuItem implements DragSourceListener {
  PieceSlot pieceSlot;
  JPopupMenu parentPopupMenu;

  public QCMenuItem(PieceSlot slot, JPopupMenu popupMenu) {
    super();
    pieceSlot = slot;
    parentPopupMenu = popupMenu;
  }

  public void InitDragDrop() {
    DragGestureListener dragGestureListener = e -> {
      /*
       FredKors 18.11.2015 Seems to be obsolete
       if ((dge.getComponent() != null) && (dge.getComponent() instanceof QCMenuItem))
       DragSource.getDefaultDragSource().addDragSourceListener(((QCMenuItem)dge.getComponent()));
      */

        startDrag();
      PieceMover.AbstractDragHandler.getTheDragHandler().dragGestureRecognized(e);

      // FredKors 18.11.2015 Move the code after the dragGestureRecognized to avoid exception
      // FredKors 29.03.2015 Fix a small annoying thing dragging a counter from a menu
      if (parentPopupMenu != null) {
        parentPopupMenu.setVisible(false);
      }
    };

    DragSource.getDefaultDragSource().createDefaultDragGestureRecognizer(this, DnDConstants.ACTION_MOVE, dragGestureListener);
  }

  // Puts counter in DragBuffer. Call when mouse gesture recognized
  protected void startDrag() {
    if (pieceSlot != null) {
      pieceSlot.getPiece().setPosition(new Point(0, 0));

      // Erase selection border to avoid leaving selected after mouse dragged out
      pieceSlot.getPiece().setProperty(Properties.SELECTED, null);

      if (pieceSlot.getPiece() != null) {
        KeyBuffer.getBuffer().clear();
        DragBuffer.getBuffer().clear();
        GamePiece l_objNewPiece = PieceCloner.getInstance().clonePiece(pieceSlot.getPiece());
        l_objNewPiece.setProperty(Properties.PIECE_ID, pieceSlot.getGpId());
        DragBuffer.getBuffer().add(l_objNewPiece);
      }
      /*
       FredKors 18.11.2015 Move the code after the dragGestureRecognized to avoid exception
       FredKors 29.03.2015 Fix a small annoying thing dragging a counter from a menu
       if (m_objParentPopupMenu != null)
       m_objParentPopupMenu.setVisible(false);
      */
    }
  }

  public void dragEnter(DragSourceDragEvent dsde) {}
  public void dragOver(DragSourceDragEvent dsde) {}
  public void dropActionChanged(DragSourceDragEvent dsde) {}
  public void dragExit(DragSourceEvent dse) {}

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
  private final QCConfiguration qcConfiguration;
  private TreeNode currentSubMenu;

  public QCConfigurationParser(QCConfiguration configuration) {
    qcConfiguration = configuration;
    currentSubMenu = configuration;
  }

  @Override
  public void startElement(String uri, String localName, String qName, Attributes attributes) {
    if (qName.equalsIgnoreCase("qcconfig")) {
      qcConfiguration.setDescription(attributes.getValue("descr"));
    }
    else if (qName.equalsIgnoreCase("qcsubmenu")) {

      QCConfigurationEntry newSubmenu = new QCConfigurationEntry(qcConfiguration.getQC());

      newSubmenu.setMenu(true);
      newSubmenu.setEntryText(attributes.getValue("text"));

      if (newSubmenu.getEntryText() == null) {
        newSubmenu.setEntryText(QCConfiguration.EmptyMenuTitle());
      }

      if (currentSubMenu != qcConfiguration) {
        ((DefaultMutableTreeNode) currentSubMenu).add(newSubmenu);
      }
      else {
        qcConfiguration.add(newSubmenu);
      }

      currentSubMenu = newSubmenu;
    }
    else if (qName.equalsIgnoreCase("qcentry")) {
      QCConfigurationEntry newEntry = new QCConfigurationEntry(qcConfiguration.getQC());

      newEntry.setPieceId(attributes.getValue("slot"));

      if (currentSubMenu != qcConfiguration) {
        ((DefaultMutableTreeNode) currentSubMenu).add(newEntry);
      }
      else {
        qcConfiguration.add(newEntry);
      }
    }
  }

  @Override
  public void endElement (String uri, String localName, String qName) {
    if (qName.equalsIgnoreCase("qcsubmenu"))
      currentSubMenu = currentSubMenu.getParent();
  }
}

class QCConfiguration extends DefaultMutableTreeNode {
  private static final String EMPTY_MENU_TITLE = "Empty menu title";

  /**
   * @return the EMPTY_MENU_TITLE
   */
  public static String EmptyMenuTitle() {
    return EMPTY_MENU_TITLE;
  }

  private boolean isBuiltinConfiguration;
  private QC qc;
  private final File xmlConfigurationFile;
  private String description;

  public QCConfiguration(QC configuration, File file) {
    isBuiltinConfiguration = false;

    qc = configuration;
    xmlConfigurationFile = file;
    description = "";
  }

  // copy object
  public QCConfiguration(QCConfiguration source) {
    isBuiltinConfiguration = false;

    qc = source.getQC();
    description = source.getDescription() + " (copy)";

    Enumeration<TreeNode> childNodes = source.children();

    while (childNodes.hasMoreElements()) {
      add(new QCConfigurationEntry((QCConfigurationEntry) childNodes.nextElement()));
    }

    File configurationDirectory = new File(Info.getConfDir() + System.getProperty("file.separator","\\") + "qcconfigs");

    if (!configurationDirectory.exists()) {
      configurationDirectory.mkdir();
    }
    else if (!configurationDirectory.isDirectory()) {
        configurationDirectory.delete();
        configurationDirectory.mkdir();
    }

    xmlConfigurationFile = new File(configurationDirectory.getPath() + System.getProperty("file.separator","\\") + UUID.randomUUID() + ".xml");
  }

  public void ReadDataFrom(QCConfiguration source) {
    isBuiltinConfiguration = source.isBuiltinConfiguration();
    qc = source.getQC();
    description = source.getDescription();

    FreeAllNodes(this);

    Enumeration<TreeNode> childNodes = source.children();

    while (childNodes.hasMoreElements()) {
      add(new QCConfigurationEntry((QCConfigurationEntry) childNodes.nextElement()));
    }
  }

  private void FreeAllNodes(DefaultMutableTreeNode node) {
    Enumeration<TreeNode> childNodes = node.children();

    while (childNodes.hasMoreElements()) {
      FreeAllNodes((DefaultMutableTreeNode) childNodes.nextElement());
    }

    node.removeAllChildren();
  }

  /**
   * @return the QC
   */
  public QC getQC() {
    return qc;
  }

  /**
   * @return the name of the file
   */
  public String getName() {
    return xmlConfigurationFile != null ? xmlConfigurationFile.getName() : "";
  }

  /**
   * @return the m_strDescription
   */
  public String getDescription() {
    return description;
  }

  /**
   * @param descr the Description to set
   */
  public void setDescription(String descr) {
    if (descr != null) {
      description = descr;
    }
  }

  public boolean SaveXML() {
    try {
      if (xmlConfigurationFile != null) {
        if (!xmlConfigurationFile.exists()) {
          xmlConfigurationFile.createNewFile();
        }

        DocumentBuilderFactory builderFactory = DocumentBuilderFactory.newInstance();
        DocumentBuilder documentBuilder = builderFactory.newDocumentBuilder();

        // root elements
        Document document = documentBuilder.newDocument();
        Element rootElement = document.createElement("qcconfig");

        Attr attributes = document.createAttribute("descr");
        attributes.setValue(getDescription());
        rootElement.setAttributeNode(attributes);

        document.appendChild(rootElement);

        Enumeration<TreeNode> childNodes = children();

        while (childNodes.hasMoreElements()) {
          ((QCConfigurationEntry) childNodes.nextElement()).WriteXML(document, rootElement);
        }

        // write the content into xml file
        TransformerFactory transformerFactory = TransformerFactory.newInstance();
        Transformer transformer = transformerFactory.newTransformer();
        DOMSource domSource = new DOMSource(document);

        FileOutputStream fileOutputStream = new FileOutputStream(xmlConfigurationFile);

        transformer.transform(domSource, new StreamResult(fileOutputStream));
        fileOutputStream.close();
      }

      return true;
    }
    catch (Exception ignored) {
    }

    return false;
  }

  /**
   * @return the isBuiltinConfiguration
   */
  public Boolean isBuiltinConfiguration() {
    return isBuiltinConfiguration;
  }

  /**
   * @param isBuiltin the isBuiltin to set
   */
  public void setBuiltinConfiguration(Boolean isBuiltin) {
    this.isBuiltinConfiguration = isBuiltin;
  }

  public void DeleteXML() {
    if (xmlConfigurationFile != null && xmlConfigurationFile.exists()) {
      xmlConfigurationFile.delete();
    }
  }
}

class QCConfigurationEntry extends DefaultMutableTreeNode {
  private final QC qcToolbar;
  private boolean isMenu;
  private String pieceId, entryText;
  private PieceSlot pieceSlot;

  public QCConfigurationEntry(QC objQC) {
    super();

    qcToolbar = objQC;
    isMenu = false;
    pieceId = null;
    entryText = null;
    pieceSlot = null;
  }

  public QCConfigurationEntry(QCConfigurationEntry source) {
    super();

    qcToolbar = source.getQC();
    isMenu = source.isMenu();

    if (isMenu) {
      Enumeration<TreeNode> childNodes = source.children();

      while(childNodes.hasMoreElements()) {
        add(new QCConfigurationEntry((QCConfigurationEntry) childNodes.nextElement()));
      }
    }

    pieceId = source.getPieceId();
    entryText = source.getEntryText();
    pieceSlot = source.getPieceSlot();
  }

  /**
   * @return the isMenu
   */
  public boolean isMenu() {
    return isMenu;
  }

  /**
   * @param isMenu the isMenu to set
   */
  public void setMenu(boolean isMenu) {
    this.isMenu = isMenu;
  }

  /**
   * @return the gpID
   */
  public String getPieceId() {
    return pieceId;
  }

  /**
   * @param id the id to set
   */
  public void setPieceId(String id) {
    this.pieceId = id;
  }

  /**
   * @return the qc
   */
  public QC getQC() {
    return qcToolbar;
  }

  void WriteXML(Document document, Element element) {

    if (isMenu()) {
      Element entry = document.createElement("qcsubmenu");

      if (getEntryText() != null) {
        Attr attribute = document.createAttribute("text");
        attribute.setValue(getEntryText());
        entry.setAttributeNode(attribute);
      }

      element.appendChild(entry);

      Enumeration<TreeNode> childNodes = children();

      while(childNodes.hasMoreElements()) {
        ((QCConfigurationEntry) childNodes.nextElement()).WriteXML(document, entry);
      }
    }
    else {
      Element entry = document.createElement("qcentry");

      if (getPieceId() != null) {
        Attr l_objAttribute = document.createAttribute("slot");
        l_objAttribute.setValue(getPieceId());
        entry.setAttributeNode(l_objAttribute);
      }

      element.appendChild(entry);
    }
  }

  /**
   * @return the pieceSlot
   */
  public PieceSlot getPieceSlot() {
    return pieceSlot;
  }

  /**
   * @param slot the slot to set
   */
  public void setPieceSlot(PieceSlot slot) {
    this.pieceSlot = slot;
  }

  /**
   * @return the text
   */
  public String getEntryText() {
    return entryText;
  }

  /**
   * @param text the text to set
   */
  public void setEntryText(String text) {
    this.entryText = text;
  }

  public Image CreateButtonIcon() {
    final GamePiece piece = getPieceSlot().getPiece();
    final ImageOp pop = Op.piece(piece);
    final Dimension dimension = pop.getSize();
    final int min = Math.min(dimension.width, dimension.height);
    return new OpMultiResolutionImage(Op.crop(pop, 0, 0, min, min));
  }

  public Image CreateButtonMenuIcon() {
    QCConfigurationEntry configurationEntry = null;

    if (getPieceSlot() != null) {
      configurationEntry = this;
    }
    else {
      Enumeration<TreeNode> childNodes = children();

      while (childNodes.hasMoreElements()) {
        QCConfigurationEntry entry = (QCConfigurationEntry) childNodes.nextElement();

        if (entry.getPieceSlot() != null) {
          configurationEntry = entry;
          break;
        }
      }
    }

    if (configurationEntry != null) {
      return configurationEntry.CreateButtonIcon();
    }
    else {
      final int size = 30;
      final BufferedImage bufferedImage = ImageUtils.createCompatibleTranslucentImage(size, size);
      final Graphics2D graph2D = bufferedImage.createGraphics();

      graph2D.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

      graph2D.setColor(Color.WHITE);
      graph2D.fillRect(0, 0, size, size);

      graph2D.dispose();

      return new BaseMultiResolutionImage(bufferedImage);
    }
  }
}

/**
 * A class to represent the counters toolbar
 */
public class QC implements Buildable, GameComponent {

  private JButton undoButton = null;
  private JButton stepButton = null;
  private JButton countersWindowButton = null;
  private JButton draggableOverlaysWindowButton = null;
  private JButton deluxeDraggableOverlaysWindowButton = null;
  private JToggleButton brokenFinderButton = null;
  private JToggleButton sniperFinderButton = null;
  //ASLCasbin
  private JToggleButton aslCasbinButton = null;
  private Map map;
  private final ArrayList<QCConfiguration> qcConfigurations = new ArrayList<>();
  private QCConfiguration qcWorkingConfiguration = null;
  private QCConfig qcConfig = null;
  private boolean isEditing = false;
  private final Hashtable<String, PieceSlot> hashPieceSlot = new Hashtable<>();
  private final String LAST_CONFIGURATION_USED = "QCLastConfigurationUsed"; //$NON-NLS-1$

  public void loadConfigurations() {
    SAXParser saxParser;
    File configurationDirectory = new File(Info.getConfDir() + System.getProperty("file.separator","\\") + "qcconfigs");

    try {
      SAXParserFactory saxParserFactory = SAXParserFactory.newInstance();
      saxParser = saxParserFactory.newSAXParser();
    }
    catch (Exception ex) {
      saxParser = null;
    }

    if (saxParser != null) {
      // clear the old configurations (if any)
      qcConfigurations.clear();

      // read built-in configuration
      qcWorkingConfiguration = new QCConfiguration(this, null); // null file for the built-in configuration

      try {
        QCConfigurationParser qcConfigurationParser = new QCConfigurationParser(qcWorkingConfiguration);
        // parse the built-in configuration
          String builtinConfig = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" +
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

          saxParser.parse(new InputSource(new StringReader(builtinConfig)), qcConfigurationParser);
      }
      catch (Exception ex) {
        qcWorkingConfiguration = null;
      }

      if (qcWorkingConfiguration != null) {
        qcWorkingConfiguration.setBuiltinConfiguration(true);
      }

      // now read the custom configuration files
      // check for configs dir
      if (!configurationDirectory.exists()) {
        configurationDirectory.mkdir();
      }
      else if (!configurationDirectory.isDirectory()) {
        configurationDirectory.delete();
        configurationDirectory.mkdir();
      }
      else {
        // browsing configs files
        File[] configurationFiles = configurationDirectory.listFiles(
          (file, name) -> name.toLowerCase().endsWith(".xml")
        );

        if (configurationFiles != null) {
          for (File configurationFile : configurationFiles) {
            //Create an instance of this class; it defines all the handler methods
            QCConfiguration qcConfiguration = new QCConfiguration(this, configurationFile);

            try {
              QCConfigurationParser qcConfigurationParser = new QCConfigurationParser(qcConfiguration);
              // parse the config file
              saxParser.parse(configurationFile, qcConfigurationParser);
            } catch (Exception ex) {
              qcConfiguration = null;
            }

            if (qcConfiguration != null) {
              qcConfigurations.add(qcConfiguration);
            } else {
              configurationFile.delete();
            }
          }
        }

        qcConfigurations.sort(new QCConfigurationComparator());
      }

      if (qcWorkingConfiguration != null) {
        qcConfigurations.add(0, qcWorkingConfiguration);
      }
    }
  }

  public void readWorkingConfiguration() {
    String workingConfigurationName = (String)GameModule.getGameModule().getPrefs().getValue(LAST_CONFIGURATION_USED);

    if (workingConfigurationName == null) workingConfigurationName = "";

    for (QCConfiguration qcConfiguration : qcConfigurations) {
      if (workingConfigurationName.equalsIgnoreCase(qcConfiguration.getName())) {
        qcWorkingConfiguration = qcConfiguration;
        break;
      }
    }
  }	

  public void saveWorkingConfiguration() {
    if (qcWorkingConfiguration != null) {
      GameModule.getGameModule().getPrefs().setValue(LAST_CONFIGURATION_USED, qcWorkingConfiguration.getName());

      try {
        GameModule.getGameModule().getPrefs().save();
      }
      catch (Exception ex) {
        ex.printStackTrace();
      }
    }
  }

  public void build(Element e) {
    if (GameModule.getGameModule().getPrefs().getOption(LAST_CONFIGURATION_USED) == null) {
      GameModule.getGameModule().getPrefs().addOption(null, new StringConfigurer(LAST_CONFIGURATION_USED, null));
    }

    loadConfigurations();
    readWorkingConfiguration();
  }

  public org.w3c.dom.Element getBuildElement(org.w3c.dom.Document doc) {
    return doc.createElement(getClass().getName());
  }

  public void addTo(Buildable b) {
    map = (Map) b;

    ((ASLMap) map).getPopupMenu().add(new QCStartMenuItem());
    ((ASLMap) map).getPopupMenu().add(new QCEndMenuItem());

    RebuildPopupMenu();

    if (!map.shouldDockIntoMainWindow()) {
      map.getToolBar().add(new JToolBar.Separator(), 1);
      undoButton = new JButton();
      map.getToolBar().add(undoButton, 2);

      stepButton = new JButton();
      map.getToolBar().add(stepButton, 3);
    }

    map.getToolBar().add(new JToolBar.Separator());

    if (!map.shouldDockIntoMainWindow()) {
      if (countersWindowButton == null) {
        countersWindowButton = new JButton();
        map.getToolBar().add(countersWindowButton);
      }

      if (draggableOverlaysWindowButton == null) {
        draggableOverlaysWindowButton = new JButton();
        map.getToolBar().add(draggableOverlaysWindowButton);
      }

      if (deluxeDraggableOverlaysWindowButton == null) {
        deluxeDraggableOverlaysWindowButton = new JButton();
        map.getToolBar().add(deluxeDraggableOverlaysWindowButton);
      }
    }

    if (brokenFinderButton == null) {
      brokenFinderButton = new JToggleButton();
      map.getToolBar().add(brokenFinderButton);
    }

    if (sniperFinderButton == null) {
      sniperFinderButton = new JToggleButton();
      map.getToolBar().add(sniperFinderButton);
    }
    //ASLCasbin
    if (aslCasbinButton == null) {
      aslCasbinButton = new JToggleButton();
      map.getToolBar().add(aslCasbinButton);
    }

    JButton markMovedButton = new JButton();
    markMovedButton.setName("MarkMovedPlaceHolder");
    map.getToolBar().add(markMovedButton);

    map.getToolBar().add(new QCStartToolBarItem());
    map.getToolBar().add(new QCEndToolBarItem());

    GameModule.getGameModule().getGameState().addGameComponent(this);
  }

  private void ReadPiecesSlot() {
    List<PieceSlot> pieceSlots = GameModule.getGameModule().getAllDescendantComponentsOf(PieceSlot.class);

    for (PieceSlot pieceSlot : pieceSlots) {
      hashPieceSlot.put(pieceSlot.getGpId(), pieceSlot);
    }

    for (QCConfiguration qcConfiguration : qcConfigurations) {
      Enumeration<TreeNode> childNodes = qcConfiguration.children();

      while(childNodes.hasMoreElements()) {
        setPieceSlot((QCConfigurationEntry) childNodes.nextElement());
      }
    }
  }

  private void setPieceSlot(QCConfigurationEntry qcConfigurationEntry) {
    PieceSlot pieceSlot;

    if (qcConfigurationEntry.getPieceId() != null) {
      pieceSlot = hashPieceSlot.get(qcConfigurationEntry.getPieceId());

      if (pieceSlot != null) {
        qcConfigurationEntry.setPieceSlot(pieceSlot);
      }
    }

    if (qcConfigurationEntry.isMenu()) {
      Enumeration<TreeNode> childNodes = qcConfigurationEntry.children();

      while(childNodes.hasMoreElements()) {
        setPieceSlot((QCConfigurationEntry) childNodes.nextElement());
      }
    }
  }

  public void add(Buildable b) {}

  private void RebuildToolBar() {
    JToolBar toolBar = map.getToolBar();
    boolean endElementNotFound = true;
    int startPos = 0;

    if (qcWorkingConfiguration != null) {
      // remove the old element
      for (int i = toolBar.getComponents().length - 1; i >= 0; i--) {
        Component component = toolBar.getComponent(i);

        if (endElementNotFound) {
          if (component instanceof QCEndToolBarItem) {
            endElementNotFound = false;
          }
        }
        else {
          if (component instanceof QCStartToolBarItem) {
            startPos = i + 1;
            break;
          }
          else {
            toolBar.remove(i);
          }
        }
      }

      Enumeration<TreeNode> childNodes = qcWorkingConfiguration.children();

      while (childNodes.hasMoreElements()) {
        Component component = CreateToolBarItem((QCConfigurationEntry) childNodes.nextElement());

        if (component != null) {
          toolBar.add(component, startPos++);
        }
      }

      toolBar.revalidate();
      toolBar.repaint();
    }
  }

  private Component CreateToolBarItem(QCConfigurationEntry configurationEntry) {
    JButton button = null;

    if (configurationEntry.isMenu()) {
      // submenu
      QCButtonMenu qcButtonMenu = new QCButtonMenu();

      String entryText = configurationEntry.getEntryText();
      if (entryText == null) {
        entryText = QCConfiguration.EmptyMenuTitle();
      }

      qcButtonMenu.setToolTipText(entryText);
      qcButtonMenu.setIcon(new MenuSizedImageIcon(configurationEntry.CreateButtonMenuIcon(), 30, 30));

      CreatePopupMenu(configurationEntry, qcButtonMenu);

      button = qcButtonMenu;
    }
    else if (configurationEntry.getPieceSlot() != null) {
      // button standard
      QCButton qcButton = new QCButton(configurationEntry.getPieceSlot());

      qcButton.InitDragDrop();
      qcButton.setIcon(new SizedImageIcon(configurationEntry.CreateButtonIcon(), 30, 30));

      button = qcButton;
    }

    if (button != null) {
      button.setMargin(new Insets(0, 0, 0, 0));
      button.setMaximumSize(new Dimension(32, 32));
      button.setAlignmentY(0.0F);
    }

    return button;
  }

  private void CreatePopupMenu(QCConfigurationEntry objConfigurationEntry, QCButtonMenu objQCButtonMenu) {
    JPopupMenu popupMenu = objQCButtonMenu.getPopupMenu();

    Enumeration<TreeNode> childNodes = objConfigurationEntry.children();

    while (childNodes.hasMoreElements()) {
      JMenuItem l_objMenuItem = CreateMenuItem((QCConfigurationEntry) childNodes.nextElement(), popupMenu);

      if (l_objMenuItem != null) {
        popupMenu.add(l_objMenuItem);
      }
    }
  }

  private JMenuItem CreateMenuItem(QCConfigurationEntry objConfigurationEntry, JPopupMenu popupMenu) {
    if (objConfigurationEntry.isMenu()) {
      //submenu
      JMenu menu = new JMenu();

      try {
        if (objConfigurationEntry.getEntryText() != null) {
          menu.setText(objConfigurationEntry.getEntryText());
        }
        else {
          menu.setText(QCConfiguration.EmptyMenuTitle());
        }

        menu.setIcon(new MenuSizedImageIcon(objConfigurationEntry.CreateButtonMenuIcon(), 30, 30));
      }
      catch (Exception ex) {
        ex.printStackTrace();
      }

      Enumeration<TreeNode> childNodes = objConfigurationEntry.children();

      while(childNodes.hasMoreElements()) {
        JMenuItem menuItem = CreateMenuItem((QCConfigurationEntry) childNodes.nextElement(), popupMenu);

        if (menuItem != null) {
          menu.add(menuItem);
        }
      }

      return menu;
    }
    else {
      if (objConfigurationEntry.getPieceSlot() != null) {
        QCMenuItem qcMenuItem = new QCMenuItem(objConfigurationEntry.getPieceSlot(), popupMenu);

        try {
          qcMenuItem.setText(objConfigurationEntry.getPieceSlot().getPiece().getName());
          qcMenuItem.setIcon(new SizedImageIcon(objConfigurationEntry.CreateButtonIcon(), 30, 30));
          qcMenuItem.InitDragDrop();
        }
        catch (Exception ex) {
          ex.printStackTrace();
        }

        return qcMenuItem;
      }
    }

    return null;
  }

  private void RebuildPopupMenu() {
    JPopupMenu popupMenu = ((ASLMap) map).getPopupMenu();
    final JMenuItem copyConfigurationMenuItem  = new JMenuItem(), removeConfigurationMenuItem  = new JMenuItem(), editConfigurationMenuItem = new JMenuItem();
    int startPos = 0;
    boolean endElementNotFound = true;

    // remove the old element
    for (int i = popupMenu.getComponents().length - 1; i >= 0; i--) {
      Component component = popupMenu.getComponent(i);

      if (endElementNotFound) {
        if (component instanceof QCEndMenuItem) {
          endElementNotFound = false;
        }
      }
      else {
        if (component instanceof QCStartMenuItem) {
          startPos = i + 1;
          break;
        }
        else {
          popupMenu.remove(i);
        }
      }
    }

    // title
    JMenuItem selectQCItem = new JMenuItem("Select QC configuration");
    selectQCItem.setBackground(new Color(255,255,255));
    popupMenu.add(selectQCItem, startPos++);

    popupMenu.add(new JPopupMenu.Separator(), startPos++);

    // button group
    ButtonGroup group = new ButtonGroup();

    for (QCConfiguration qcConfiguration : qcConfigurations) {
      QCRadioButtonMenuItem qcRadioButtonMenuItem = new QCRadioButtonMenuItem(qcConfiguration);

      qcRadioButtonMenuItem.addActionListener(evt -> {
        if (evt.getSource() instanceof QCRadioButtonMenuItem) {
          qcWorkingConfiguration = ((QCRadioButtonMenuItem)evt.getSource()).getQCConfiguration();
          saveWorkingConfiguration();
          RebuildToolBar();

          if (qcWorkingConfiguration != null && !isEditing) {
            copyConfigurationMenuItem.setEnabled(true);

            if (qcWorkingConfiguration.isBuiltinConfiguration()) {
              removeConfigurationMenuItem.setEnabled(false);
              editConfigurationMenuItem.setEnabled(false);
            }
            else {
              removeConfigurationMenuItem.setEnabled(true);
              editConfigurationMenuItem.setEnabled(true);
            }
          }
          else {
            copyConfigurationMenuItem.setEnabled(false);
            removeConfigurationMenuItem.setEnabled(false);
            editConfigurationMenuItem.setEnabled(false);
          }
        }
      });

      group.add(qcRadioButtonMenuItem);
      popupMenu.add(qcRadioButtonMenuItem, startPos++);

      qcRadioButtonMenuItem.setSelected(qcConfiguration == qcWorkingConfiguration);
    }

    popupMenu.add(new JPopupMenu.Separator(), startPos++);

    // copy configuration
    copyConfigurationMenuItem.setText("Copy current QC configuration");

    copyConfigurationMenuItem.setEnabled((qcWorkingConfiguration != null) && (!isEditing));

    copyConfigurationMenuItem.setIcon(new ImageIcon(Op.load("QC/copy.png").getImage()));

    copyConfigurationMenuItem.addActionListener(evt -> {
      if (qcWorkingConfiguration != null) {
        QCConfiguration newConfiguration = new QCConfiguration(qcWorkingConfiguration);

        if (newConfiguration.SaveXML()) {
          qcConfigurations.add(newConfiguration);
          qcWorkingConfiguration = newConfiguration;
          saveWorkingConfiguration();

          ResortConfigurations();
          RebuildPopupMenu();
          RebuildToolBar();
        }
      }
    });

    popupMenu.add(copyConfigurationMenuItem, startPos++);

    // remove configuration
    removeConfigurationMenuItem.setText("Delete current QC configuration");

    if (qcWorkingConfiguration != null && !isEditing) {
      removeConfigurationMenuItem.setEnabled(!qcWorkingConfiguration.isBuiltinConfiguration());
    }
    else {
      removeConfigurationMenuItem.setEnabled(false);
    }

    removeConfigurationMenuItem.setIcon(new ImageIcon(Op.load("QC/delete.png").getImage()));

    removeConfigurationMenuItem.addActionListener(evt -> {
      if (qcWorkingConfiguration != null) {
        int answer = JOptionPane.showConfirmDialog(map.getView(),
            "Do you really want to delete the '" + qcWorkingConfiguration.getDescription() + "' QC configuration? This is NOT undoable or reversible!",
            "Delete the current QC configuration",
            JOptionPane.YES_NO_OPTION,
            JOptionPane.QUESTION_MESSAGE);

        if (answer == JOptionPane.YES_OPTION) {
          qcWorkingConfiguration.DeleteXML();

          int index = qcConfigurations.indexOf(qcWorkingConfiguration);

          if (index != -1) {
            if (index > 0) {
              qcConfigurations.remove(qcWorkingConfiguration);
              qcWorkingConfiguration = qcConfigurations.get(index - 1);
            }
          }

          saveWorkingConfiguration();

          RebuildPopupMenu();
          RebuildToolBar();
        }
      }
    });

    popupMenu.add(removeConfigurationMenuItem, startPos++);

    // separator
    popupMenu.add(new JPopupMenu.Separator(), startPos++);

    // edit configuration
    editConfigurationMenuItem.setText("Modify current QC configuration");

      if (qcWorkingConfiguration == null || isEditing) {
        editConfigurationMenuItem.setEnabled(false);
      } else {
        editConfigurationMenuItem.setEnabled(!qcWorkingConfiguration.isBuiltinConfiguration());
      }

      editConfigurationMenuItem.setIcon(new ImageIcon(Op.load("QC/edit.png").getImage()));

    editConfigurationMenuItem.addActionListener(evt -> {
      if (qcWorkingConfiguration != null) {
        //open the configuration window
        if (qcConfig == null) {
          qcConfig = new QCConfig();
        }

        qcConfig.setConfiguration(qcWorkingConfiguration);

        isEditing = true;
        RebuildPopupMenu();
      }
    });

    popupMenu.add(editConfigurationMenuItem, startPos);
  }

  public void UpdateQC(boolean closing, boolean saving) {
    if (closing) {
      isEditing = false;
    }

    if (saving) {
      ResortConfigurations();
    }

    RebuildPopupMenu();

    if (saving) {
      RebuildToolBar();
    }
  }

  private void ResortConfigurations() {
    QCConfiguration builtinConfiguration = qcConfigurations.get(0);
    qcConfigurations.remove(builtinConfiguration);

    qcConfigurations.sort(new QCConfigurationComparator());
    qcConfigurations.add(0, builtinConfiguration);
  }

  public PieceSlot getPieceSlot(String pieceId) {
    PieceSlot pieceSlot = null;

    if (pieceId != null) {
      pieceSlot = hashPieceSlot.get(pieceId);
    }

    return pieceSlot;
  }

  public void setup(boolean bln) {
    if (bln) {
      if (undoButton != null && undoButton.getAction() == null) {
        for (int i = 0; i < GameModule.getGameModule().getToolBar().getComponentCount(); i++) {
          if (GameModule.getGameModule().getToolBar().getComponent(i) instanceof JButton) {
            JButton button = ((JButton)(GameModule.getGameModule().getToolBar().getComponent(i)));

            if (button.getAction() instanceof BasicLogger.UndoAction) {
              CopyActionButton(undoButton, button, true);
              break;
            }
          }
        }
      }

      if (stepButton != null && stepButton.getAction() == null) {
        for (int i = 0; i < GameModule.getGameModule().getToolBar().getComponentCount(); i++) {
          if (GameModule.getGameModule().getToolBar().getComponent(i) instanceof JButton) {
            JButton button = ((JButton)(GameModule.getGameModule().getToolBar().getComponent(i)));

            if (button.getAction() instanceof BasicLogger.StepAction) {
              CopyActionButton(stepButton, button, true);
              break;
            }
          }
        }
      }

      if (countersWindowButton != null && countersWindowButton.getActionListeners().length == 0) {
        for (int i = 0; i < GameModule.getGameModule().getToolBar().getComponentCount(); i++) {
          if (GameModule.getGameModule().getToolBar().getComponent(i) instanceof JButton) {
            JButton button = ((JButton)(GameModule.getGameModule().getToolBar().getComponent(i)));

            if (button.getToolTipText().contains("VASL Counters window")) {
              CopyActionButton(countersWindowButton, button, false);
              break;
            }
          }
        }
      }

      if (draggableOverlaysWindowButton != null && draggableOverlaysWindowButton.getActionListeners().length == 0) {
        for (int i = 0; i < GameModule.getGameModule().getToolBar().getComponentCount(); i++) {
          if (GameModule.getGameModule().getToolBar().getComponent(i) instanceof JButton) {
            JButton button = ((JButton)(GameModule.getGameModule().getToolBar().getComponent(i)));

            if (button.getToolTipText().contains("the Draggable Overlays window")) {
              CopyActionButton(draggableOverlaysWindowButton, button, false);
              break;
            }
          }
        }
      }

      if (deluxeDraggableOverlaysWindowButton != null && deluxeDraggableOverlaysWindowButton.getActionListeners().length == 0) {
        for (int i = 0; i < GameModule.getGameModule().getToolBar().getComponentCount(); i++) {
          if (GameModule.getGameModule().getToolBar().getComponent(i) instanceof JButton) {
            JButton button = ((JButton)(GameModule.getGameModule().getToolBar().getComponent(i)));

            if (button.getToolTipText().contains("Deluxe Draggable Overlays")) {
              CopyActionButton(deluxeDraggableOverlaysWindowButton, button, false);
              break;
            }
          }
        }
      }

      if (brokenFinderButton != null && brokenFinderButton.getActionListeners().length == 0) {
        brokenFinderButton.setIcon(new ImageIcon(Op.load("malf").getImage()));

        ActionListener al = e -> {
          try {
            ASLBrokenFinder brokenFinder = map.getComponentsOf(ASLBrokenFinder.class).iterator().next();

            if (brokenFinder != null) {
              brokenFinder.findBrokenPiece(brokenFinderButton.isSelected());
            }
          }
          catch (Exception ignored) {
          }
        };

        brokenFinderButton.addActionListener(al);
        brokenFinderButton.setToolTipText("Turn on/off the highlighting of broken units/weapons");
      }

      if (sniperFinderButton != null && sniperFinderButton.getActionListeners().length == 0) {
        sniperFinderButton.setIcon(new ImageIcon(Op.load("sniper").getImage()));

        ActionListener al = e -> {
          ASLSniperFinder sniperFinder = map.getComponentsOf(ASLSniperFinder.class).iterator().next();

          if (sniperFinder != null) {
            sniperFinder.findSniper(sniperFinderButton.isSelected());
          }
        };

        sniperFinderButton.addActionListener(al);
        sniperFinderButton.setToolTipText("Turn on/off the highlighting of sniperF counters");
      }
      // ASLCasbn
      if (aslCasbinButton != null && aslCasbinButton.getActionListeners().length == 0) {
        aslCasbinButton.setIcon(new ImageIcon(Op.load("cpv").getImage()));

        ActionListener al = e -> {
          try {
            ASLCasbin aslCasbin = map.getComponentsOf(ASLCasbin.class).iterator().next();

            if (aslCasbin != null) {
              aslCasbin.startcasbin(aslCasbinButton.isSelected());
            }
          }
          catch (Exception ignored) {
          }
        };

        aslCasbinButton.addActionListener(al);
        aslCasbinButton.setToolTipText("Report CVP Totals");
      }

      if (hashPieceSlot.isEmpty()) {
        ReadPiecesSlot();
        RebuildToolBar();
      }
    }
  }

  public Command getRestoreCommand() {
    return null;
  }

  private void CopyActionButton(JButton destination, JButton source, boolean actionToo) {
    destination.setText(source.getText());

    try {
      if (source.getIcon() != null) {
        destination.setIcon(source.getIcon());
      }
    }
    catch (Exception ignored) {
    }

    if (actionToo) {
      if (source.getAction() != null) {
        destination.setAction(source.getAction());
      }
    }
    else {
      for (int i = 0; i < source.getActionListeners().length; i++) {
        destination.addActionListener(source.getActionListeners()[i]);
      }
    }

    destination.setToolTipText(source.getToolTipText());
    source.setVisible(false);
  }

  private static class SizedImageIcon extends ImageIcon {
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

  private static class MenuSizedImageIcon extends SizedImageIcon {
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
}
