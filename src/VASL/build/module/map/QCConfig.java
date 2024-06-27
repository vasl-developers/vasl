/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */

package VASL.build.module.map;

import VASSAL.build.module.map.PieceMover;
import VASSAL.counters.DragBuffer;
import VASSAL.counters.GamePiece;
import VASSAL.counters.PieceIterator;
import VASSAL.counters.Properties;
import VASSAL.counters.Stack;
import VASSAL.tools.image.ImageUtils;
import VASSAL.tools.imageop.Op;

import java.awt.*;
import java.awt.dnd.DnDConstants;
import java.awt.dnd.DropTargetDragEvent;
import java.awt.dnd.DropTargetDropEvent;
import java.awt.dnd.DropTargetEvent;
import java.awt.dnd.DropTargetListener;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.WindowEvent;
import java.awt.event.WindowListener;
import java.awt.image.BufferedImage;
import java.util.Enumeration;
import javax.swing.GroupLayout;
import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JToolBar;
import javax.swing.JTree;
import javax.swing.SwingConstants;
import javax.swing.event.TreeSelectionListener;
import javax.swing.plaf.basic.BasicTreeUI;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeCellRenderer;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.TreeNode;
import javax.swing.tree.TreePath;
import javax.swing.tree.TreeSelectionModel;

class QCTreeCellRenderer extends DefaultTreeCellRenderer {

    QCTreeCellRenderer() {}

    @Override
    public Component getTreeCellRendererComponent(JTree tree, Object value, boolean selected, boolean expanded, boolean leaf, int row, boolean hasFocus) 
    {
        JLabel label = (JLabel)super.getTreeCellRendererComponent(
                            tree, value, selected,
                            expanded, leaf, row,
                            hasFocus); 
   
        DefaultMutableTreeNode treeNode = ((DefaultMutableTreeNode) value);
        
        if (treeNode instanceof QCConfiguration)
        {
            QCConfiguration qcConfiguration = (QCConfiguration) treeNode;
            label.setIcon(null);
            label.setText(qcConfiguration.getDescription());
        } 
        else if (treeNode instanceof QCConfigurationEntry)
        {
            QCConfigurationEntry qcConfigurationEntry = (QCConfigurationEntry) treeNode;
            
            if (qcConfigurationEntry.isMenu())
            {
                label.setIcon(new SizedImageIcon(qcConfigurationEntry.CreateButtonMenuIcon(), 30, 30));
                
                if (qcConfigurationEntry.getEntryText() != null)
                    label.setText(qcConfigurationEntry.getEntryText());
                else
                    label.setText(QCConfiguration.EmptyMenuTitle());
            }
            else
            {
                if (qcConfigurationEntry.getPieceSlot() != null)
                {
                    label.setIcon(new SizedImageIcon(qcConfigurationEntry.CreateButtonIcon(), 30, 30));
                    label.setText(qcConfigurationEntry.getPieceSlot().getPiece().getName());
                }
                else
                {
                    BufferedImage unknownImage = null;
                    try
                    {
                        unknownImage = Op.load("QC/unknown.png").getImage();
                    }
                    catch (Exception ex) 
                    {
                        ex.printStackTrace();
                    }
                    
                    if (unknownImage != null)
                        label.setIcon(new SizedImageIcon(unknownImage, 30, 30));
                    else
                        label.setIcon(null);
                    
                    label.setText("Unknown counter");
                }
            }
        }
        
        return label;
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
}

/**
 *
 * @author Federico
 */
public class QCConfig implements DropTargetListener
{
    private QCConfiguration qcConfiguration;
    private QCConfiguration qcOriginalConfiguration;
    private JFrame frame;
    final private QCConfig myself = this;
    DefaultTreeModel treeModel;
    
    public QCConfig() 
    {
        qcConfiguration = null;
        qcOriginalConfiguration = null;
        frame = null;
        treeModel = null;
    }
    
    private void SetButtonProperties(JButton button, String image, String tooltip, ActionListener actionListener)
    {
        try 
        {
            button.setIcon(new ImageIcon(Op.load(image).getImage(null))); // NOI18N
        } 
        catch (Exception ex) 
        {
            ex.printStackTrace();
        }
        
        button.setToolTipText(tooltip);
        button.setFocusable(false);
        button.setHorizontalTextPosition(SwingConstants.CENTER);
        button.setText("");
        button.setVerticalTextPosition(SwingConstants.BOTTOM);
        button.addActionListener(actionListener);
        
        toolBar.add(button);
    }
    
    
    /**
     * This method is called from within the constructor to initialize the form.
     * WARNING: Do NOT modify this code. The content of this method is always
     * regenerated by the Form Editor.
     */
    private void initComponents() {

        toolBar = new JToolBar();
        buttonMoveUp = new JButton();
        buttonMoveDown = new JButton();
        buttonMoveUpLevel = new JButton();
        buttonAddMenu = new JButton();
        buttonSetTitle = new JButton();
        buttonRemCounter = new JButton();
        buttonReset = new JButton();

        treeModel = new DefaultTreeModel(qcConfiguration);
        tree = new JTree(treeModel);
        tree.setCellRenderer(new QCTreeCellRenderer());
        ((BasicTreeUI) tree.getUI()).setLeftChildIndent(20);
        tree.setRowHeight(35);
    
        tree.addTreeSelectionListener(createSelectionListener());
        
        expandAll(tree);

        TreePath rootPath = new TreePath(((DefaultMutableTreeNode) treeModel.getRoot()).getPath());

        tree.scrollPathToVisible(rootPath);
        tree.setSelectionPath(rootPath);
        
        toolBar.setFloatable(false);
        toolBar.setOrientation(SwingConstants.VERTICAL);
        toolBar.setRollover(true);
        toolBar.setMaximumSize(new Dimension(64, 23));
        toolBar.setMinimumSize(new Dimension(50, 23));
        toolBar.setPreferredSize(new Dimension(50, 23));
        
        SetButtonProperties(buttonMoveUp, "QC/up.png", "Move up the selected node", this::ButtonMoveUpActionPerformed);
        SetButtonProperties(buttonMoveDown, "QC/down.png", "Move down the selected node", this::ButtonMoveDownActionPerformed);
        SetButtonProperties(buttonMoveUpLevel, "QC/uplev.png", "Move the selected node one level up", this::ButtonMoveUpLevelActionPerformed);
        SetButtonProperties(buttonAddMenu, "QC/submenu.png", "Add a new menu/submenu to the toolbar", this::ButtonAddMenuActionPerformed);
        SetButtonProperties(buttonSetTitle, "QC/title.png", "Edit the title of a menu/submenu or the description of the whole configuration", this::ButtonSetTitleActionPerformed);
        SetButtonProperties(buttonRemCounter, "QC/delnode.png", "Remove the selected node from the toolbar", this::ButtonRemCounterActionPerformed);
        SetButtonProperties(buttonReset, "QC/reset.png", "Reset the configuration to the starting state", this::ButtonResetActionPerformed);

        JScrollPane scrollPane = new JScrollPane();
        scrollPane.setName(""); // NOI18N
        scrollPane.setPreferredSize(new Dimension(330, 500));
        scrollPane.setRequestFocusEnabled(false);

        tree.setShowsRootHandles(true);
        tree.getSelectionModel().setSelectionMode(TreeSelectionModel.SINGLE_TREE_SELECTION);
        scrollPane.setViewportView(tree);

        JPanel addPanel = new JPanel();
        addPanel.setBackground(new java.awt.Color(255, 255, 204));
        addPanel.setBorder(javax.swing.BorderFactory.createLineBorder(new java.awt.Color(204, 204, 204)));

        JLabel addLabel = new JLabel();
        addLabel.setBackground(new java.awt.Color(255, 255, 255));
        try 
        {
            addLabel.setIcon(new ImageIcon(Op.load("QC/add.png").getImage(null))); // NOI18N
        } 
        catch (Exception ex) 
        {
            ex.printStackTrace();
        }
        addLabel.setText("<html><p align=justify style=\"padding: 5px\">To add a new node drag a <b>counter</b> or a <b>draggable overlay</b> and drop it on the tree.</p></html>");
        addLabel.setIconTextGap(4);
        addLabel.setPreferredSize(new java.awt.Dimension(330, 75));
        
        javax.swing.GroupLayout addPanelLayout = new javax.swing.GroupLayout(addPanel);
        addPanel.setLayout(addPanelLayout);
        addPanelLayout.setHorizontalGroup(
            addPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                .addGroup(javax.swing.GroupLayout.Alignment.TRAILING, addPanelLayout.createSequentialGroup()
                .addContainerGap()
                .addComponent(addLabel, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
        );
        addPanelLayout.setVerticalGroup(
            addPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                .addComponent(addLabel, javax.swing.GroupLayout.DEFAULT_SIZE, 68, Short.MAX_VALUE)
        );
        
        GroupLayout layout = new GroupLayout(frame.getContentPane());
        frame.getContentPane().setLayout(layout);
        layout.setHorizontalGroup(
            layout.createParallelGroup(GroupLayout.Alignment.LEADING)
            .addGroup(layout.createSequentialGroup()
                .addComponent(scrollPane, GroupLayout.DEFAULT_SIZE, GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                .addComponent(toolBar, GroupLayout.PREFERRED_SIZE, GroupLayout.DEFAULT_SIZE, GroupLayout.PREFERRED_SIZE))
            .addComponent(addPanel, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
        );
        layout.setVerticalGroup(
            layout.createParallelGroup(GroupLayout.Alignment.LEADING)
            .addGroup(layout.createSequentialGroup()
                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addComponent(toolBar, GroupLayout.DEFAULT_SIZE, 750, Short.MAX_VALUE)
                    .addComponent(scrollPane, GroupLayout.DEFAULT_SIZE, GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
                .addComponent(addPanel, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE))
        );

        scrollPane.getAccessibleContext().setAccessibleName("");

        frame.pack();
        
        tree.setDropTarget(PieceMover.DragHandler.makeDropTarget(tree, DnDConstants.ACTION_MOVE, this));
        
    }    

    private TreeSelectionListener createSelectionListener() 
    {
        return e -> {
            TreePath newPath = e.getNewLeadSelectionPath();

            if (newPath != null)
            {
                DefaultMutableTreeNode node = (DefaultMutableTreeNode) newPath.getLastPathComponent();
                EnableButtons(node);
            }
            else
            {
                EnableButtons(null);
            }
        };
    }

    private void ButtonMoveDownActionPerformed(ActionEvent evt)
    {
        TreePath treePath = tree.getSelectionPath();        // get path of selected node.

        if (treePath == null)
            return;

        boolean isExpanded = tree.isExpanded(treePath);

        DefaultMutableTreeNode node = (DefaultMutableTreeNode) treePath.getLastPathComponent();
        DefaultMutableTreeNode nodeParent = (DefaultMutableTreeNode) node.getParent();

        //now get the index of the selected node in the DefaultTreeModel
        int index = treeModel.getIndexOfChild(nodeParent, node);
        int maxIndex = treeModel.getChildCount(nodeParent) - 1;

        // if selected node is first, return (can't move it up)
        if (index < maxIndex)
        {
            treeModel.removeNodeFromParent(node);
            treeModel.insertNodeInto(node, nodeParent, index + 1);    // move the node

            TreePath newNodePath = new TreePath(node.getPath());

            if (isExpanded)
                tree.expandPath(newNodePath);

            tree.scrollPathToVisible(newNodePath);
            tree.setSelectionPath(newNodePath);
        
            SaveDataModified();
        }
    }                                               

    private void ButtonMoveUpActionPerformed(ActionEvent evt)
    {
        TreePath treePath = tree.getSelectionPath();        // get path of selected node.

        if (treePath == null)
            return;

        boolean isExpanded = tree.isExpanded(treePath);

        DefaultMutableTreeNode node = (DefaultMutableTreeNode) treePath.getLastPathComponent();
        DefaultMutableTreeNode nodeParent = (DefaultMutableTreeNode) node.getParent();

        //now get the index of the selected node in the DefaultTreeModel
        int index = treeModel.getIndexOfChild(nodeParent, node);

        // if selected node is first, return (can't move it up)
        if (index != 0)
        {
            treeModel.removeNodeFromParent(node);
            treeModel.insertNodeInto(node, nodeParent, index - 1);    // move the node

            TreePath newNodePath = new TreePath(node.getPath());

            if (isExpanded)
                tree.expandPath(newNodePath);

            tree.scrollPathToVisible(newNodePath);
            tree.setSelectionPath(newNodePath);
        
            SaveDataModified();
        }
    }                                              
    
    private void ButtonMoveUpLevelActionPerformed(ActionEvent evt)
    {
        TreePath treePath = tree.getSelectionPath();        // get path of selected node.

        if (treePath == null)
            return;

        boolean isExpanded = tree.isExpanded(treePath);

        DefaultMutableTreeNode node = (DefaultMutableTreeNode) treePath.getLastPathComponent();
        DefaultMutableTreeNode nodeParent = (DefaultMutableTreeNode) node.getParent();
        DefaultMutableTreeNode nodeParentParent = (DefaultMutableTreeNode) nodeParent.getParent();
        
        if (nodeParentParent == null)
            return;

         treeModel.removeNodeFromParent(node);
         treeModel.insertNodeInto(node, nodeParentParent, treeModel.getChildCount(nodeParentParent));

        TreePath newNodePath = new TreePath(node.getPath());

        if (isExpanded)
            tree.expandPath(newNodePath);
         
        tree.scrollPathToVisible(newNodePath);
        tree.setSelectionPath(newNodePath);
        
        SaveDataModified();
    }
    
    private void ButtonAddMenuActionPerformed(ActionEvent evt)
    {
        DefaultMutableTreeNode selectedNode;
        QCConfigurationEntry newNode = new QCConfigurationEntry(qcConfiguration.getQC());
        
        newNode.setMenu(true);
        newNode.setEntryText(QCConfiguration.EmptyMenuTitle());
            
        TreePath newTreePath = tree.getSelectionPath();        // get path of selected node.

        if (newTreePath == null)
            selectedNode = (DefaultMutableTreeNode) treeModel.getRoot();
        else
            selectedNode = (DefaultMutableTreeNode) newTreePath.getLastPathComponent();
        
        if (selectedNode.isRoot())
        {
             treeModel.insertNodeInto(newNode, selectedNode, treeModel.getChildCount(selectedNode));
        }
        else if (((QCConfigurationEntry)selectedNode).isMenu())
        {
             treeModel.insertNodeInto(newNode, selectedNode, treeModel.getChildCount(selectedNode));
        }
        else
        {
            DefaultMutableTreeNode nodeParent = (DefaultMutableTreeNode) selectedNode.getParent();

            int index = treeModel.getIndexOfChild(nodeParent, selectedNode);
             treeModel.insertNodeInto(newNode, nodeParent, index + 1);
        }

        TreePath newNodePath = new TreePath(newNode.getPath());

        tree.expandPath(newNodePath);
        tree.scrollPathToVisible(newNodePath);
        tree.setSelectionPath(newNodePath);
        
        SaveDataModified();
    }
    
    private void ButtonSetTitleActionPerformed(ActionEvent evt)
    {
        TreePath treePath = tree.getSelectionPath();        // get path of selected node.

        if (treePath == null)
            return;
        
        DefaultMutableTreeNode node = (DefaultMutableTreeNode) treePath.getLastPathComponent();
        
        if (node.isRoot())
        {
            String title = JOptionPane.showInputDialog (self().frame, "Enter the configuration description", qcConfiguration.getDescription());
            
            if (title != null)
            {
                title = title.trim();
                
                if (!title.isEmpty())
                {
                    ((QCConfiguration)node).setDescription(title);
                    treeModel.nodeChanged(node);

                    SaveDataModified();
                }
            }         
        }
        else if (((QCConfigurationEntry)node).isMenu())
        {
            String title = JOptionPane.showInputDialog (self().frame, "Enter the menu/submenu title", ((QCConfigurationEntry)node).getEntryText());

            if (title != null)
            {
                title = title.trim();
                
                if (!title.isEmpty())
                {
                    ((QCConfigurationEntry)node).setEntryText(title);
                    treeModel.nodeChanged(node);

                    SaveDataModified();
                }         
            }
        }
    }
    
    private void ButtonRemCounterActionPerformed(ActionEvent evt)
    {
        int selectedOption = JOptionPane.YES_OPTION;
        TreePath treePath = tree.getSelectionPath();        // get path of selected node.

        if (treePath == null)
            return;

        DefaultMutableTreeNode node = (DefaultMutableTreeNode) treePath.getLastPathComponent();
        DefaultMutableTreeNode nodeParent = (DefaultMutableTreeNode) node.getParent();
        
        if (node.isRoot())
            return;
        
        if ((((QCConfigurationEntry)node).isMenu()) && (treeModel.getChildCount(node) > 0))
        {
            selectedOption = JOptionPane.showConfirmDialog(self().frame,
                                              "Are you sure? All the child items also will be removed...", 
                                              "Do you want to remove this menu/submenu?", 
                                              JOptionPane.YES_NO_OPTION,
                                              JOptionPane.QUESTION_MESSAGE);
        }
        
        if (selectedOption == JOptionPane.YES_OPTION)
        {
            int index = treeModel.getIndexOfChild(nodeParent, node);
            
            treeModel.removeNodeFromParent(node);
            FreeAllNodes(node);
            
            while (true)
            {
                DefaultMutableTreeNode selectedNode = null;
                
                if (index < treeModel.getChildCount(nodeParent))
                    selectedNode = (DefaultMutableTreeNode) treeModel.getChild(nodeParent, index);
                
                if (selectedNode != null)
                {
                    TreePath newNodePath = new TreePath(selectedNode.getPath());

                    tree.scrollPathToVisible(newNodePath);
                    tree.setSelectionPath(newNodePath);

                    break;
                }
                else
                {
                    index--;
                   
                    if (index < 0)
                    {
                        TreePath newNodePath = new TreePath(nodeParent.getPath());

                        tree.scrollPathToVisible(newNodePath);
                        tree.setSelectionPath(newNodePath);
                       
                        break;
                    }
                }
            }
            
            SaveDataModified();
        }
    }
    
    private void FreeAllNodes(DefaultMutableTreeNode node)
    {
        Enumeration<TreeNode> childNodes = node.children();

        while(childNodes.hasMoreElements())
            FreeAllNodes((QCConfigurationEntry) childNodes.nextElement());
        
        node.removeAllChildren();
    }    
    
    private void ButtonResetActionPerformed(ActionEvent evt)
    {
        if (JOptionPane.showConfirmDialog(self().frame,
                                          "Are you sure? All the changes you made will be lost...", 
                                          "Do you want to reset the configuration?", 
                                          JOptionPane.YES_NO_OPTION,
                                          JOptionPane.QUESTION_MESSAGE) == JOptionPane.YES_OPTION) 
        {
            qcConfiguration.ReadDataFrom(qcOriginalConfiguration);

            if (qcConfiguration.SaveXML())
            {
                frame.setTitle("Editing : " + qcConfiguration.getDescription());

                treeModel = new DefaultTreeModel(qcConfiguration);
                tree.setModel(treeModel);

                expandAll(tree);

                TreePath newRootPath = new TreePath(((DefaultMutableTreeNode) treeModel.getRoot()).getPath());

                tree.scrollPathToVisible(newRootPath);
                tree.setSelectionPath(newRootPath);
                
                self().getConfiguration().getQC().UpdateQC(false, true);
            }
            else
                JOptionPane.showMessageDialog(self().frame,
                                              "Oooops, error saving the configuration xml file... Sorry!", 
                                              "Error!", 
                                              JOptionPane.ERROR_MESSAGE);                 
        }
    }
    
    public void expandAll(JTree tree)
    {
        TreeNode objRootNode = (TreeNode) tree.getModel().getRoot();
        
        expandAll(tree, new TreePath(objRootNode));
    }

    private void expandAll(JTree tree, TreePath parentPath)
    {
        TreeNode node = (TreeNode) parentPath.getLastPathComponent();

        if (node.getChildCount() >= 0)
        {
            Enumeration<? extends TreeNode> childNodes = node.children();

            while (childNodes.hasMoreElements())
            {
              TreeNode currentNode = childNodes.nextElement();
              TreePath treePath = parentPath.pathByAddingChild(currentNode);
              expandAll(tree, treePath);
            }
        }
        
        tree.expandPath(parentPath);
    }
    
    // Variables declaration - do not modify                     
    private JToolBar toolBar;
    private JTree tree;
    private JButton buttonMoveUp;
    private JButton buttonMoveDown;
    private JButton buttonMoveUpLevel;
    private JButton buttonAddMenu;
    private JButton buttonSetTitle;
    private JButton buttonRemCounter;
    private JButton buttonReset;
    // End of variables declaration

    /**
     * @return the qcConfiguration
     */
    QCConfiguration getConfiguration()
    {
        return qcConfiguration;
    }

    /**
     * @param configuration the configuration to set
     */
    void setConfiguration(QCConfiguration configuration)
    {
        qcConfiguration = configuration;
        qcOriginalConfiguration = new QCConfiguration(this.qcConfiguration.getQC(), null);
        
        qcOriginalConfiguration.ReadDataFrom(this.qcConfiguration);
        
        if (frame == null)
        {
            frame = new JFrame();
            
            try
            {
                frame.setIconImage(new ImageIcon(Op.load("QC/edit.png").getImage(null)).getImage());
            }
            catch (Exception ex) 
            {
                ex.printStackTrace();
            }
            
            initComponents();
            
            Dimension screenSize = Toolkit.getDefaultToolkit().getScreenSize();
            frame.setLocation(screenSize.width/2- frame.getWidth()/2, screenSize.height/2- frame.getHeight()/2);

            frame.setDefaultCloseOperation(JFrame.DO_NOTHING_ON_CLOSE);
            frame.addWindowListener(new WindowListener()
            {
                @Override
                public void windowClosing(WindowEvent e) 
                {
                    self().getConfiguration().getQC().UpdateQC(true, false);
                    self().frame.setVisible(false);
                }

                public void windowOpened(WindowEvent e) {}
                public void windowClosed(WindowEvent e) {}
                public void windowIconified(WindowEvent e) {}
                public void windowDeiconified(WindowEvent e) {}
                public void windowActivated(WindowEvent e) {}
                public void windowDeactivated(WindowEvent e) {}
            });
        }
        else        
        {
            treeModel = new DefaultTreeModel(this.qcConfiguration);
            tree.setModel(treeModel);
            
            expandAll(tree);
            
            TreePath newRootPath = new TreePath(((DefaultMutableTreeNode) treeModel.getRoot()).getPath());
            
            tree.scrollPathToVisible(newRootPath);
            tree.setSelectionPath(newRootPath);
        }
        
        frame.setTitle("Editing : " + this.qcConfiguration.getDescription());
        frame.setVisible(true);
        
        EnableButtons();
    }

    /**
     * @return myself
     */
    public QCConfig self() {
        return myself;
    }

    public void SaveDataModified() 
    {
        if (qcConfiguration.SaveXML())
        {
            frame.setTitle("Editing : " + qcConfiguration.getDescription());

            self().getConfiguration().getQC().UpdateQC(false, true);
        }
        else
            JOptionPane.showMessageDialog(self().frame,
                                          "Oooops, error saving the configuration xml file... Sorry!", 
                                          "Error!", 
                                          JOptionPane.ERROR_MESSAGE);                 
    }

    private void EnableButtons() 
    {
        TreePath treePath = tree.getSelectionPath();        // get path of selected node.

        if (treePath == null)
            EnableButtons(null);
        else
        {
            DefaultMutableTreeNode node = (DefaultMutableTreeNode) treePath.getLastPathComponent();
            
            EnableButtons(node);
        }
    }

    private void EnableButtons(DefaultMutableTreeNode selectedNode)
    {
        if (selectedNode != null)
        {
            DefaultMutableTreeNode nodeParent = (DefaultMutableTreeNode) selectedNode.getParent();
            int index = -1;
            int maxIndex = -1;
            
            if (nodeParent != null)
            {
                index = treeModel.getIndexOfChild(nodeParent, selectedNode);
                maxIndex = treeModel.getChildCount(nodeParent) - 1;
            }

            buttonMoveUp.setEnabled(index > 0);
            buttonMoveDown.setEnabled((index != -1) && (index < maxIndex));
            buttonMoveUpLevel.setEnabled((nodeParent != null) && (!nodeParent.isRoot()));
            buttonAddMenu.setEnabled(true);
            buttonSetTitle.setEnabled(((selectedNode.isRoot()) || ((selectedNode instanceof QCConfigurationEntry) && ((QCConfigurationEntry)selectedNode).isMenu())));
            buttonRemCounter.setEnabled(!selectedNode.isRoot());
            buttonReset.setEnabled(true);
        }
        else
        {
            buttonMoveUp.setEnabled(false);
            buttonMoveDown.setEnabled(false);
            buttonMoveUpLevel.setEnabled(false);
            buttonAddMenu.setEnabled(true);
            buttonSetTitle.setEnabled(false);
            buttonRemCounter.setEnabled(false);
            buttonReset.setEnabled(true);
        }
    }

    public void dragEnter(DropTargetDragEvent dtde) {
    }

    public void dragOver(DropTargetDragEvent dtde) {
    }

    public void dropActionChanged(DropTargetDragEvent dtde) {
    }

    public void dragExit(DropTargetEvent dte) {
    }

    public void drop(DropTargetDropEvent dtde) 
    {
        final PieceIterator it = DragBuffer.getBuffer().getIterator();
        
        if (it.hasMoreElements())
        {
            GamePiece piece = it.nextPiece();
            
            if (piece instanceof Stack)
            {
                int pieceCount = ((Stack) piece).getPieceCount();
                piece = ((Stack) piece).getPieceAt(pieceCount - 1);
            }
            
            CreateNewNode(piece);
        }
        
        dtde.dropComplete(true);
    }

    private void CreateNewNode(GamePiece piece)
    {
        if ((!Boolean.TRUE.equals(piece.getProperty(Properties.INVISIBLE_TO_ME)))
            && (!Boolean.TRUE.equals(piece.getProperty(Properties.OBSCURED_TO_ME))))
        {
            DefaultMutableTreeNode selectedNode;
            QCConfigurationEntry newNode = new QCConfigurationEntry(qcConfiguration.getQC());

            newNode.setMenu(false);
            newNode.setPieceId((String)piece.getProperty(Properties.PIECE_ID));
            newNode.setEntryText(piece.getName());
            newNode.setPieceSlot(qcConfiguration.getQC().getPieceSlot(newNode.getPieceId()));

            TreePath treePath = tree.getSelectionPath();        // get path of selected node.

            if (treePath == null)
                selectedNode = (DefaultMutableTreeNode) treeModel.getRoot();
            else
                selectedNode = (DefaultMutableTreeNode) treePath.getLastPathComponent();

            if (selectedNode.isRoot())
            {
                treeModel.insertNodeInto(newNode, selectedNode, treeModel.getChildCount(selectedNode));
            }
            else if (((QCConfigurationEntry)selectedNode).isMenu())
            {
                treeModel.insertNodeInto(newNode, selectedNode, treeModel.getChildCount(selectedNode));
            }
            else
            {
                DefaultMutableTreeNode nodeParent = (DefaultMutableTreeNode) selectedNode.getParent();

                int index = treeModel.getIndexOfChild(nodeParent, selectedNode);
                treeModel.insertNodeInto(newNode, nodeParent, index + 1);
            }

            TreePath newNodePath = new TreePath(newNode.getPath());

            tree.expandPath(newNodePath);
            tree.scrollPathToVisible(newNodePath);
            tree.setSelectionPath(newNodePath);

            SaveDataModified();
        }
    }
}
