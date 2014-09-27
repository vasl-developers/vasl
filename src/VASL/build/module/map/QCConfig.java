/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */

package VASL.build.module.map;

import VASSAL.tools.image.ImageUtils;
import VASSAL.tools.imageop.Op;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.Toolkit;
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
import static javax.swing.JOptionPane.INFORMATION_MESSAGE;
import javax.swing.JScrollPane;
import javax.swing.JToolBar;
import javax.swing.JTree;
import javax.swing.SwingConstants;
import javax.swing.plaf.basic.BasicTreeUI;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeCellRenderer;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.TreeNode;
import javax.swing.tree.TreePath;
import javax.swing.tree.TreeSelectionModel;

//class QCTreeCellRenderer implements TreeCellRenderer {
class QCTreeCellRenderer extends DefaultTreeCellRenderer {

    QCTreeCellRenderer() {}

    public BufferedImage CreateIcon(BufferedImage objCounterIcon) 
    {
        final int l_iBorder = 3;
        final int l_iSizeOrigin = objCounterIcon.getWidth();
        final int l_iSize = l_iSizeOrigin + 2 * l_iBorder;
        final BufferedImage l_objBI = ImageUtils.createCompatibleTranslucentImage(l_iSize, l_iSize);
        final Graphics2D l_objGraphics = l_objBI.createGraphics();

        l_objGraphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        l_objGraphics.setColor(Color.WHITE);
        l_objGraphics.fillRect(0, 0, l_iSize, l_iSize);            
        l_objGraphics.setColor(Color.BLACK);
        l_objGraphics.drawRect(l_iBorder - 1, l_iBorder - 1, l_iSizeOrigin + 1, l_iSizeOrigin + 1);

        l_objGraphics.drawImage(objCounterIcon, l_iBorder, l_iBorder, null);

        l_objGraphics.dispose();

        return l_objBI;        
    }
    
    @Override
    public Component getTreeCellRendererComponent(JTree tree, Object value, boolean selected, boolean expanded, boolean leaf, int row, boolean hasFocus) 
    {
        JLabel l_objLabel = (JLabel)super.getTreeCellRendererComponent(
                            tree, value, selected,
                            expanded, leaf, row,
                            hasFocus); 
   
        DefaultMutableTreeNode l_objObject = ((DefaultMutableTreeNode) value);
        
        if (l_objObject instanceof QCConfiguration) 
        {
            QCConfiguration l_objQCConfiguration = (QCConfiguration) l_objObject;
            l_objLabel.setIcon(null);
            l_objLabel.setText(l_objQCConfiguration.getDescription());
        } 
        else if (l_objObject instanceof QCConfigurationEntry) 
        {
            QCConfigurationEntry l_objConfigurationEntry = (QCConfigurationEntry) l_objObject;
            
            if (l_objConfigurationEntry.isMenu())
            {
                l_objLabel.setIcon(new ImageIcon(CreateIcon(l_objConfigurationEntry.CreateButtonMenuIcon())));
                
                if (l_objConfigurationEntry.getText() != null)
                    l_objLabel.setText(l_objConfigurationEntry.getText());
                else
                    l_objLabel.setText("submenu");
            }
            else
            {
                if (l_objConfigurationEntry.getPieceSlot() != null)
                {
                    l_objLabel.setIcon(new ImageIcon(CreateIcon(l_objConfigurationEntry.CreateButtonIcon())));
                    l_objLabel.setText(l_objConfigurationEntry.getPieceSlot().getPiece().getName());
                }
                else
                {
                    BufferedImage l_objUnknown = null;
                    try
                    {
                        l_objUnknown = Op.load("QC/unknown.png").getImage();
                    }
                    catch (Exception ex) 
                    {
                        ex.printStackTrace();
                    }
                    
                    if (l_objUnknown != null)
                        l_objLabel.setIcon(new ImageIcon(CreateIcon(l_objUnknown)));
                    else
                        l_objLabel.setIcon(null);
                    
                    l_objLabel.setText("Unknown counter");
                }
            }
        }
        
        return l_objLabel;
    }
}

/**
 *
 * @author Federico
 */
public class QCConfig 
{
    private boolean m_bDataModified;
    private QCConfiguration m_objConfiguration;
    private QCConfiguration m_objWorkingConfiguration;
    private JFrame m_objFrame;
    final private QCConfig m_objSelf = this;
    DefaultTreeModel m_objModelTree;
    
    public QCConfig() 
    {
        m_objConfiguration  = null;
        m_objWorkingConfiguration = null;
        m_objFrame = null;       
        m_objModelTree = null;
        m_bDataModified = false;
    }
    
    private void SetButtonProperties(JButton objButton, String strImage, String strTooltip, ActionListener objActionListener)
    {
        try 
        {
            objButton.setIcon(new ImageIcon(Op.load(strImage).getImage(null))); // NOI18N
        } 
        catch (Exception ex) 
        {
            ex.printStackTrace();
        }
        
        objButton.setToolTipText(strTooltip);
        objButton.setFocusable(false);
        objButton.setHorizontalTextPosition(SwingConstants.CENTER);
        objButton.setText("");
        objButton.setVerticalTextPosition(SwingConstants.BOTTOM);
        objButton.addActionListener(objActionListener);
        
        m_objToolbar.add(objButton);
    }
    
    
    /**
     * This method is called from within the constructor to initialize the form.
     * WARNING: Do NOT modify this code. The content of this method is always
     * regenerated by the Form Editor.
     */
    @SuppressWarnings("unchecked")
    // <editor-fold defaultstate="collapsed" desc="Generated Code">                          
    private void initComponents() {

        m_objToolbar = new JToolBar();
        m_objBtnMoveUp = new JButton();
        m_objBtnMoveDown = new JButton();
        m_objBtnAddCounter = new JButton();
        m_objBtnAddMenu = new JButton();
        m_objBtnSetTitle = new JButton();
        m_objBtnRemCounter = new JButton();
        m_objBtnSave = new JButton();
        m_objBtnExit = new JButton();
        QCSP = new JScrollPane();
        
        m_objModelTree = new DefaultTreeModel(m_objWorkingConfiguration);
        m_objTree = new JTree(m_objModelTree);
        m_objTree.setCellRenderer(new QCTreeCellRenderer());
        ((BasicTreeUI)m_objTree.getUI()).setLeftChildIndent(20);        
    
        expandAll(m_objTree);

        TreePath l_objRootPath = new TreePath(((DefaultMutableTreeNode)m_objModelTree.getRoot()).getPath());

        m_objTree.scrollPathToVisible(l_objRootPath);
        m_objTree.setSelectionPath(l_objRootPath);
        
        m_objToolbar.setFloatable(false);
        m_objToolbar.setOrientation(SwingConstants.VERTICAL);
        m_objToolbar.setRollover(true);
        m_objToolbar.setMaximumSize(new Dimension(64, 23));
        m_objToolbar.setMinimumSize(new Dimension(50, 23));
        m_objToolbar.setPreferredSize(new Dimension(50, 23));
        
        SetButtonProperties(m_objBtnMoveUp, "QC/up.png", "Move up the selected node", new ActionListener() {
            public void actionPerformed(ActionEvent evt) 
            {
                m_objBtnMoveUpActionPerformed(evt);
            }
        });

        SetButtonProperties(m_objBtnMoveDown, "QC/down.png", "Move down the selected node", new ActionListener() {
            public void actionPerformed(ActionEvent evt) 
            {
                m_objBtnMoveDownActionPerformed(evt);
            }
        });
        
        SetButtonProperties(m_objBtnAddCounter, "QC/add.png", "Add a new button to the toolbar", new ActionListener() {
            public void actionPerformed(ActionEvent evt) 
            {
                m_objBtnAddCounterActionPerformed(evt);
            }
        });
        
        SetButtonProperties(m_objBtnAddMenu, "QC/submenu.png", "Add a new menu/submenu to the toolbar", new ActionListener() {
            public void actionPerformed(ActionEvent evt) 
            {
                m_objBtnAddMenuActionPerformed(evt);
            }
        });
        
        SetButtonProperties(m_objBtnSetTitle, "QC/title.png", "Edit the title of a menu or the description of the whole configuration", new ActionListener() {
            public void actionPerformed(ActionEvent evt) 
            {
                m_objBtnSetTitleActionPerformed(evt);
            }
        });
        
        SetButtonProperties(m_objBtnRemCounter, "QC/delnode.png", "Remove the selected node from the toolbar", new ActionListener() {
            public void actionPerformed(ActionEvent evt) 
            {
                m_objBtnRemCounterActionPerformed(evt);
            }
        });
        
        SetButtonProperties(m_objBtnSave, "QC/save.png", "Save the configuration and apply the changes", new ActionListener() {
            public void actionPerformed(ActionEvent evt) 
            {
                m_objBtnSaveActionPerformed(evt);
            }
        });
        
        SetButtonProperties(m_objBtnExit, "QC/exit.png", "Exit the window and discard the unsaved changes", new ActionListener() {
            public void actionPerformed(ActionEvent evt) 
            {
                m_objBtnExitActionPerformed(evt);
            }
        });
        
        QCSP.setName(""); // NOI18N
        QCSP.setPreferredSize(new Dimension(300, 500));
        QCSP.setRequestFocusEnabled(false);

        m_objTree.setShowsRootHandles(true);
        m_objTree.getSelectionModel().setSelectionMode(TreeSelectionModel.SINGLE_TREE_SELECTION);
        QCSP.setViewportView(m_objTree);

        GroupLayout layout = new GroupLayout(m_objFrame.getContentPane());
        m_objFrame.getContentPane().setLayout(layout);
        layout.setHorizontalGroup(
            layout.createParallelGroup(GroupLayout.Alignment.LEADING)
            .addGroup(layout.createSequentialGroup()
                .addComponent(QCSP, GroupLayout.DEFAULT_SIZE, GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                .addComponent(m_objToolbar, GroupLayout.PREFERRED_SIZE, GroupLayout.DEFAULT_SIZE, GroupLayout.PREFERRED_SIZE))
        );
        layout.setVerticalGroup(
            layout.createParallelGroup(GroupLayout.Alignment.LEADING)
            .addComponent(m_objToolbar, GroupLayout.DEFAULT_SIZE, 750, Short.MAX_VALUE)
            .addComponent(QCSP, GroupLayout.DEFAULT_SIZE, GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
        );

        QCSP.getAccessibleContext().setAccessibleName("");

        m_objFrame.pack();
    }    
    private void m_objBtnMoveDownActionPerformed(ActionEvent evt) 
    {
        TreePath l_objTreePath = m_objTree.getSelectionPath();        // get path of selected node.

        if (l_objTreePath == null) 
            return;

        boolean l_bIsExpanded = m_objTree.isExpanded(l_objTreePath);

        DefaultMutableTreeNode l_objNode = (DefaultMutableTreeNode) l_objTreePath.getLastPathComponent();
        DefaultMutableTreeNode l_objParentNode = (DefaultMutableTreeNode) l_objNode.getParent();

        //now get the index of the selected node in the DefaultTreeModel
        int l_iIndex = m_objModelTree.getIndexOfChild(l_objParentNode, l_objNode);
        int l_iMaxIndex = m_objModelTree.getChildCount(l_objParentNode) - 1;

        // if selected node is first, return (can't move it up)
        if (l_iIndex < l_iMaxIndex) 
        {
            m_objModelTree.removeNodeFromParent(l_objNode);
            m_objModelTree.insertNodeInto(l_objNode, l_objParentNode, l_iIndex + 1);    // move the node

            TreePath l_objNodePath = new TreePath(l_objNode.getPath());

            if (l_bIsExpanded) 
                m_objTree.expandPath(l_objNodePath);

            m_objTree.scrollPathToVisible(l_objNodePath);
            m_objTree.setSelectionPath(l_objNodePath);
        
            setDataModified(true);
        }
    }                                               

    private void m_objBtnMoveUpActionPerformed(ActionEvent evt) 
    {
        TreePath l_objTreePath = m_objTree.getSelectionPath();        // get path of selected node.

        if (l_objTreePath == null) 
            return;

        boolean l_bIsExpanded = m_objTree.isExpanded(l_objTreePath);

        DefaultMutableTreeNode l_objNode = (DefaultMutableTreeNode) l_objTreePath.getLastPathComponent();
        DefaultMutableTreeNode l_objParentNode = (DefaultMutableTreeNode) l_objNode.getParent();

        //now get the index of the selected node in the DefaultTreeModel
        int l_iIndex = m_objModelTree.getIndexOfChild(l_objParentNode, l_objNode);

        // if selected node is first, return (can't move it up)
        if (l_iIndex != 0) 
        {
            m_objModelTree.removeNodeFromParent(l_objNode);
            m_objModelTree.insertNodeInto(l_objNode, l_objParentNode, l_iIndex - 1);    // move the node

            TreePath l_objNodePath = new TreePath(l_objNode.getPath());

            if (l_bIsExpanded) 
                m_objTree.expandPath(l_objNodePath);

            m_objTree.scrollPathToVisible(l_objNodePath);
            m_objTree.setSelectionPath(l_objNodePath);
        
            setDataModified(true);
        }
    }                                              
    
    private void m_objBtnAddCounterActionPerformed(ActionEvent evt) 
    {
        JOptionPane.showMessageDialog(self().m_objFrame, 
            "To add a button to the toolbar you must simply do a drag & drop from the VASL counters window to the tree." 
                + "\nThe new button will be inserted after the currently selected node (if there is no node selected or if the selected \nnode represents "
                + "a submenu, it will be added as last child).", 
            "To add a button to the toolbar", 
            INFORMATION_MESSAGE);
    }
    
    private void m_objBtnAddMenuActionPerformed(ActionEvent evt) 
    {
    }
    
    private void m_objBtnSetTitleActionPerformed(ActionEvent evt) 
    {
    }
    
    private void m_objBtnRemCounterActionPerformed(ActionEvent evt) 
    {
        int l_iSelectedOption = JOptionPane.YES_OPTION;
        TreePath l_objTreePath = m_objTree.getSelectionPath();        // get path of selected node.

        if (l_objTreePath == null) 
            return;

        DefaultMutableTreeNode l_objNode = (DefaultMutableTreeNode) l_objTreePath.getLastPathComponent();
        DefaultMutableTreeNode l_objParentNode = (DefaultMutableTreeNode) l_objNode.getParent();
        
        if (l_objNode.isRoot())
            return;
        
        if ((((QCConfigurationEntry)l_objNode).isMenu()) && (m_objModelTree.getChildCount(l_objNode) > 0))
        {
            l_iSelectedOption = JOptionPane.showConfirmDialog(self().m_objFrame, 
                                              "Are you sure? All the child items also will be removed...", 
                                              "Do you want to remove this menu?", 
                                              JOptionPane.YES_NO_OPTION,
                                              JOptionPane.QUESTION_MESSAGE);
        }
        
        if (l_iSelectedOption == JOptionPane.YES_OPTION)
        {
            int l_iIndex = m_objModelTree.getIndexOfChild(l_objParentNode, l_objNode);
            
            m_objModelTree.removeNodeFromParent(l_objNode);            
            
            while (true)
            {
                DefaultMutableTreeNode l_objSelectedNode = null;
                
                if (l_iIndex < m_objModelTree.getChildCount(l_objParentNode))
                    l_objSelectedNode = (DefaultMutableTreeNode) m_objModelTree.getChild(l_objParentNode, l_iIndex);
                
                if (l_objSelectedNode != null)
                {
                    TreePath l_objNodePath = new TreePath(l_objSelectedNode.getPath());

                    m_objTree.scrollPathToVisible(l_objNodePath);
                    m_objTree.setSelectionPath(l_objNodePath);

                    break;
                }
                else
                {
                    l_iIndex--;
                   
                    if (l_iIndex < 0)
                    {
                        TreePath l_objNodePath = new TreePath(l_objParentNode.getPath());

                        m_objTree.scrollPathToVisible(l_objNodePath);
                        m_objTree.setSelectionPath(l_objNodePath);
                       
                        break;
                    }
                }
            }
            
            setDataModified(true);
        }
    }
    
    private void m_objBtnSaveActionPerformed(ActionEvent evt) 
    {
        
    }
    
    private void m_objBtnExitActionPerformed(ActionEvent evt) 
    {
        m_objFrame.dispatchEvent(new WindowEvent(m_objFrame, WindowEvent.WINDOW_CLOSING));         
    }
    
    public void expandAll(JTree objTree) 
    {
        TreeNode objRootNode = (TreeNode) objTree.getModel().getRoot();
        
        expandAll(objTree, new TreePath(objRootNode));
    }

    private void expandAll(JTree objTree, TreePath objParentPath) 
    {
        TreeNode l_objNode = (TreeNode) objParentPath.getLastPathComponent();

        if (l_objNode.getChildCount() >= 0) 
        {
            for (Enumeration l_en = l_objNode.children(); l_en.hasMoreElements();) 
            {
              TreeNode l_objCurrentNode = (TreeNode) l_en.nextElement();
              TreePath l_objTreePath = objParentPath.pathByAddingChild(l_objCurrentNode);
              expandAll(objTree, l_objTreePath);
            }
        }
        
        objTree.expandPath(objParentPath);
    }
    
    // Variables declaration - do not modify                     
    private JToolBar m_objToolbar;
    private JScrollPane QCSP;
    private JTree m_objTree;
    private JButton m_objBtnMoveUp;
    private JButton m_objBtnMoveDown;
    private JButton m_objBtnAddCounter;
    private JButton m_objBtnAddMenu;
    private JButton m_objBtnSetTitle;
    private JButton m_objBtnRemCounter;
    private JButton m_objBtnSave;
    private JButton m_objBtnExit;
    // End of variables declaration                   

    /**
     * @return the m_objConfiguration
     */
    public QCConfiguration getConfiguration() 
    {
        return m_objConfiguration;
    }

    /**
     * @param m_objConfiguration the m_objConfiguration to set
     */
    public void setConfiguration(QCConfiguration objConfiguration) 
    {
        m_objConfiguration = objConfiguration;
        m_objWorkingConfiguration = new QCConfiguration(m_objConfiguration.getQC(), null);
        
        m_objWorkingConfiguration.ReadDataFrom(m_objConfiguration);
        
        if (m_objFrame == null)
        {
            m_objFrame = new JFrame();
            
            try
            {
                m_objFrame.setIconImage(new ImageIcon(Op.load("QC/edit.png").getImage(null)).getImage());
            }
            catch (Exception ex) 
            {
                ex.printStackTrace();
            }
            
            initComponents();
            
            Dimension l_objScreenDimension = Toolkit.getDefaultToolkit().getScreenSize();
            m_objFrame.setLocation(l_objScreenDimension.width/2-m_objFrame.getWidth()/2, l_objScreenDimension.height/2-m_objFrame.getHeight()/2);            

            m_objFrame.setDefaultCloseOperation(JFrame.DO_NOTHING_ON_CLOSE);
            m_objFrame.addWindowListener(new WindowListener() 
            {
                @Override
                public void windowClosing(WindowEvent e) 
                {
                    int l_iSelectedOption = JOptionPane.YES_OPTION;
                    
                    if (isDataModified())
                    {
                        l_iSelectedOption = JOptionPane.showConfirmDialog(self().m_objFrame, 
                                                          "Are you sure? Any changes not saved will be lost...", 
                                                          "Did you save the changes?", 
                                                          JOptionPane.YES_NO_OPTION,
                                                          JOptionPane.QUESTION_MESSAGE); 
                    }

                    if (l_iSelectedOption == JOptionPane.YES_OPTION) 
                    {
                        self().getConfiguration().getQC().UpdateQC(true, false);
                        self().m_objFrame.setVisible(false);
                    }
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
            m_objModelTree = new DefaultTreeModel(m_objWorkingConfiguration);
            m_objTree.setModel(m_objModelTree);
            
            expandAll(m_objTree);
            
            TreePath l_objRootPath = new TreePath(((DefaultMutableTreeNode)m_objModelTree.getRoot()).getPath());
            
            m_objTree.scrollPathToVisible(l_objRootPath);
            m_objTree.setSelectionPath(l_objRootPath);
        }
        
        m_objFrame.setTitle("Editing : " + m_objWorkingConfiguration.getDescription());
        m_objFrame.setVisible(true);
        
        setDataModified(false);
    }

    /**
     * @return the m_objself
     */
    public QCConfig self() {
        return m_objSelf;
    }

    /**
     * @return the m_bDataModified
     */
    public boolean isDataModified() {
        return m_bDataModified;
    }

    /**
     * @param m_bDataModified the m_bDataModified to set
     */
    public void setDataModified(boolean m_bDataModified) {
        this.m_bDataModified = m_bDataModified;
        EnableButtons();
    }

    private void EnableButtons() 
    {
        
    }
}
