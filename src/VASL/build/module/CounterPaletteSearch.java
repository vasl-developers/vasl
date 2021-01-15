package VASL.build.module;

import VASL.build.module.map.boardArchive.AbstractMetadata;
import VASSAL.build.AbstractBuildable;
import VASSAL.build.Buildable;
import VASSAL.build.GameModule;
import VASSAL.build.module.GameComponent;
import VASSAL.command.Command;
import VASSAL.tools.DataArchive;
import VASSAL.tools.KeyStrokeListener;
import org.apache.commons.io.IOUtils;
import org.jdom2.Document;
import org.jdom2.Element;
import org.jdom2.JDOMException;
import org.jdom2.input.SAXBuilder;

import javax.swing.*;
import javax.swing.table.TableColumn;
import java.awt.*;
import java.awt.event.*;
import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.LinkedHashMap;

public class CounterPaletteSearch extends AbstractBuildable implements GameComponent {
    private JTextField Search, SearchResult;
    private JLabel Searchlabel, Scrolllabel;
    private JButton launch;
    private JFrame frame;

    private KeyStrokeListener keyListener;
    private AbstractAction launchAction;

    private static final String CounterMetadataFileName = "counterfinder.xml"; // name of the counter metadata file
    private static CounterFinderMetadata counterfinderMetadata = null;
    private HashMap<String, Countertype> counterlist;

    public CounterPaletteSearch() {
        frame = new JFrame("Counter Search");
        frame.setDefaultCloseOperation(WindowConstants.HIDE_ON_CLOSE);

        launch = new JButton("Ctr Search");
        launch.setAlignmentY(0.0F);
        launch.setToolTipText("Search Counter Palette [SHIFT-CTRL Q]");
        launchAction = new AbstractAction() {
            public void actionPerformed(ActionEvent e) {
                frame.setVisible(!frame.isShowing());
            }
        };
        launch.addActionListener(launchAction);
        launchAction.setEnabled(false);
        launch.setEnabled(false);

        keyListener = new KeyStrokeListener(launchAction);
        keyListener.setKeyStroke(KeyStroke.getKeyStroke(KeyEvent.VK_Q, InputEvent.SHIFT_MASK + InputEvent.CTRL_MASK));

        Search = new JTextField ("Enter Counter Name");
        Search.setAlignmentX(Component.LEFT_ALIGNMENT);
        SearchResult = new JTextField("Counter Location in Palette");
        SearchResult.setAlignmentX(Component.LEFT_ALIGNMENT);
        Searchlabel = new JLabel("1. Search is case insensitive");
        Searchlabel.setForeground(Color.blue);
        JButton searchButton = new JButton("Search");
        searchButton.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                search();

            }
        });
        searchButton.setAlignmentX(Component.LEFT_ALIGNMENT);
        JButton clearButton = new JButton("Clear");
        clearButton.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                reset();

            }
        });
        clearButton.setAlignmentX(Component.LEFT_ALIGNMENT);
        Scrolllabel = new JLabel("2. Scroll through list to find counter");
        Scrolllabel.setForeground(Color.blue);

        JPanel p = new JPanel();
        //p.setSize(new Dimension(700,400));
        p.setLayout(new BoxLayout(p, BoxLayout.Y_AXIS));
        p.add(Box.createVerticalStrut(10));
        p.add(Searchlabel);
        p.add(Box.createVerticalStrut(5));
        p.add(Search);
        p.add(Box.createVerticalStrut(10));
        p.add(SearchResult);
        p.add(Box.createVerticalStrut(5));
        p.add(searchButton);
        p.add(Box.createVerticalStrut(5));
        p.add (clearButton);
        p.add(Box.createVerticalStrut(15));
        p.add(Scrolllabel);
        p.add(Box.createVerticalStrut(5));

        // read the countersearch metadata file into variables
        try {
            readMetadata();
        } catch (JDOMException e) {
            e.printStackTrace();
        }
        // add counter metadata to table
        createtabledisplay(p);

        frame.getContentPane().add(p);
        frame.pack();
    }
    private void search()  {
        // name of counter to find
        String searchterm = Search.getText();
        boolean match = false;
        // look for match between search term and name of counters
        for(Countertype c: counterlist.values()){
            if (c.getName().toLowerCase().contains(searchterm.toLowerCase())) {
                // if match show location of counter in search result text box
                showsearchresult(c);
                match = true;
            }
        }
        if(!match) {
            SearchResult.setText("No Result Found. Try different search term or use Scroll method");
            SearchResult.setForeground(Color.red);
        }
    }

    /**
     * read the countersearch metadata
     */
    private void readMetadata() throws JDOMException {

        InputStream inputStream = null;
        try {

            DataArchive archive = GameModule.getGameModule().getDataArchive();

            // counter finder metadata
            inputStream =  archive.getInputStream(CounterMetadataFileName);
            counterfinderMetadata = new CounterFinderMetadata();
            counterfinderMetadata.parseCounterFinderMetadataFile(inputStream);

            // give up on any errors
        } catch (IOException e) {
            counterfinderMetadata = null;
            throw new JDOMException("Cannot read counter finder metadata file", e);
        } catch (JDOMException e) {
            counterfinderMetadata = null;
            throw new JDOMException("Cannot read counter finder metadata file", e);
        } catch (NullPointerException e) {
            counterfinderMetadata = null;
            throw new JDOMException("Cannot read counter finder metadata file", e);
        }
        finally {
            IOUtils.closeQuietly(inputStream);
        }
        // create list of counters that can be searched for - unit, vehicle, gun, plane (most) counters are not searchable
        counterlist = counterfinderMetadata.getCounterTypes();

    }
    private void showsearchresult(Countertype c){
        // show location in counter palette in text box
        SearchResult.setText(c.getpalettelocation());
    }

    public void addTo(Buildable b) {
        GameModule.getGameModule().getToolBar().add(launch);
        GameModule.getGameModule().getGameState().addGameComponent(this);
        GameModule.getGameModule().addKeyStrokeListener(keyListener);
    }
    public SetInfo getRestoreCommand() {
        return null;
    }
    public void setup(boolean show) {
        launch.setEnabled(show);
        launchAction.setEnabled(show);
        if (!show) {
            reset();
            frame.setVisible(false);
        }
    }
    public void reset() {
        // sets search boxes back to default
        Search.setText("Enter Counter Name");
        SearchResult.setText("Counter Location in Palette");
        SearchResult.setForeground(Color.black);
        Search.requestFocusInWindow();
        Search.selectAll();
    }
    public String[] getAttributeNames() {
        return new String[0];
    }

    public void setAttribute(String name, Object value) {
    }

    public String getAttributeValueString(String name) {
        return null;
    }

    public String getState() {
        return null;
    }

    private void createtabledisplay(JPanel p){

        String[] columnNames = {"Counter", "Location in Counter Palette"};
        int tablesize = counterlist.size();
        Object[][] data = new Object[tablesize][tablesize];
        int n=0;
        // adds data for each counter to a new row in a table
        for(Countertype c: counterlist.values()){
            data[n][0] = c.getName();
            data[n][1] = c.getpalettelocation();
            n+=1;
        }
        final JTable table = new JTable(data, columnNames);
        //table.setPreferredScrollableViewportSize(new Dimension(350, 200));
        JScrollPane scrollPane = new JScrollPane(table);
        p.add(scrollPane);

        TableColumn column = null;
        column = table.getColumnModel().getColumn(0);
        column.setPreferredWidth(100);
        column = table.getColumnModel().getColumn(1);
        column.setPreferredWidth(200);

    }

        // this class does nothing but required by GameComponent
    public static class SetInfo extends Command {

        public SetInfo(String value) {   }

        public String getState() { return null;}

        protected void executeCommand() { }

        protected Command myUndoCommand() {return null;}
    }

    public class CounterFinderMetadata extends AbstractMetadata {

        private static final String counterfinderMetadataElement = "counterfinder";
        private static final String counterElement = "counter";
        private static final String CountertypeNameAttr = "name";
        private static final String CountertypepallocAttr = "palloc";
        // maps Counter names to Counter objects
        private LinkedHashMap<String, Countertype> countertypes = new LinkedHashMap<String, Countertype>(1024);


        /**
         * Parses a shared board metadata file
         * @param metadata an <code>InputStream</code> for the counter finder XML file
         * @throws JDOMException
         */
        public void parseCounterFinderMetadataFile(InputStream metadata) throws JDOMException {

            SAXBuilder parser = new SAXBuilder();

            try {

                // the root element will be the counterfinder element
                Document doc = parser.build(metadata);
                Element root = doc.getRootElement();

                // read the metadata
                if(root.getName().equals(counterfinderMetadataElement)) {

                    parsename(root);

                }

            } catch (IOException e) {
                e.printStackTrace(System.err);
                throw new JDOMException("Error reading counter finder metadata", e);
            }
        }

        /**
         * Parses the counter finder types
         * @param element the counterfinder element
         * @throws org.jdom2.JDOMException
         */
        protected void parsename(Element element) throws JDOMException {

            // make sure we have the right element
            assertElementName(element, counterfinderMetadataElement);

            for(Element e: element.getChildren()) {

                // ignore any child elements that are not counter
                if(e.getName().equals(counterElement)){

                    // read the counter attributes
                    Countertype countertype = new Countertype();
                    countertype.setName(e.getAttribute(CountertypeNameAttr).getValue());
                    countertype.setLocation(e.getAttribute(CountertypepallocAttr).getValue());

                    // add the counter type to the countertype list
                    countertypes.put(countertype.getName(), countertype);
                }
            }
        }

        /**
         * @return the list of counter types
         */
        public HashMap<String, Countertype> getCounterTypes() {

            return countertypes;
        }
    }
    public class Countertype {
        // private variables
        private String  name;
        private String palettelocation;

        /**
         * Set the Countertype name
        */
        public void setName(String name) {
            this.name = name;
        }

        /**
         * Set the Counter Palette location
         */
        public void setLocation(String palloc) {
            this.palettelocation = palloc;
        }

        /**
         * @return the name of the counter
         */
        public String getName() {
            return name;
        }

        /**
         * @return the location of the counter in the counter palette
         */
        public String getpalettelocation() {
            return palettelocation;
        }
    }

}
