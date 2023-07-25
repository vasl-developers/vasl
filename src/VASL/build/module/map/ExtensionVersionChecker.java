package VASL.build.module.map;
import VASSAL.build.AbstractBuildable;
import VASSAL.build.Buildable;
import VASSAL.build.GameModule;
import VASSAL.build.module.*;

import VASSAL.build.module.Map;
import VASSAL.command.Command;
import VASSAL.configure.StringConfigurer;
import VASSAL.tools.PropertiesEncoder;

import org.jdom2.Document;
import org.jdom2.JDOMException;
import org.jdom2.input.SAXBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.io.*;
import java.net.URI;
import java.net.URL;
import java.net.URLConnection;
import java.nio.channels.Channels;
import java.nio.channels.ReadableByteChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;

import static java.nio.file.StandardCopyOption.REPLACE_EXISTING;

public class ExtensionVersionChecker extends AbstractBuildable implements GameComponent, PropertyChangeListener {
    private static String extensionsVersionURL;

    //private static Properties extensionsVersions;

    //private String extensionPageURL;
    private static String extensionsRepositoryURL;

    private String localextension_dir;
    public static String EXTENSIONS_VERSION_PROPERTY_KEY = "extensionsVersions";

    private Map map;
    private static final String EXTENSIONS_VERSION_URL = "extensionsVersionURL";

    //private static final String EXTENSION_PAGE_URL = "extensionPageURL";
    private static final String EXTENSIONS_VERSIONS = EXTENSIONS_VERSION_PROPERTY_KEY;
    private static final String EXTENSIONS_REPOSITORY_URL = "extensionsRepositoryURL";

    private static final String extensionsFileElement = "extensionsMetadata";

    private static final String extensiondataType = "extensiondata";
    private static final String extensionNameAttr = "name";
    private static final String extensionversionAttr = "version";
    private static final String extensionversiondateAttr = "versionDate";
    private static final String extensiondescAttr = "description";
    private static LinkedHashMap<String, ExtensionVersionChecker.ExtensionVersions> extensionsversions = new LinkedHashMap<String, ExtensionVersionChecker.ExtensionVersions>(500);
    private static final Logger logger = LoggerFactory.getLogger(ASLBoardPicker.class);
    public String[] getAttributeNames() {
        return new String[]{EXTENSIONS_VERSION_URL, EXTENSIONS_REPOSITORY_URL};
    }

    public String getAttributeValueString(String key) {
        if (EXTENSIONS_VERSION_URL.equals(key)) {
            return extensionsVersionURL;
        } else if (EXTENSIONS_REPOSITORY_URL.equals(key)) {
            return extensionsRepositoryURL;
        }
        return null;
    }

    public void setAttribute(String key, Object value) {
        if (EXTENSIONS_VERSION_URL.equals(key)) {
            extensionsVersionURL = (String) value;
        } else if (EXTENSIONS_REPOSITORY_URL.equals(key)) {
            extensionsRepositoryURL = (String) value;
        }
    }
    public static String getextensionsVersionURL(){
        return extensionsVersionURL;
    }
    public void addTo(Buildable parent)  {

        map = (Map) parent;
        GameModule.getGameModule().getGameState().addGameComponent(this);
        GameModule.getGameModule().getServer().addPropertyChangeListener(ServerConnection.CONNECTED, this);
        GameModule.getGameModule().getPrefs().addOption(null, new StringConfigurer(EXTENSIONS_VERSIONS, null));

        //TODO property change listener not firing - force update
        readVersionFiles();

        //localextension_dir= GameModule.getGameModule().getPrefs().getStoredValue("extensionDIR");
        //boolean reg =true;
    }

    public Command getRestoreCommand() {
        return null;
    }

    public void setup(boolean gameStarting) {

        if (gameStarting) {

        }
    }

    private Properties readVersionList(String s) {
        Properties p = null;
        if (s != null
                && s.length() > 0) {
            try {
                p = new PropertiesEncoder(s).getProperties();
            } catch (IOException e) {
                // Fail silently if we can't contact the server
            }
        }
        return p;
    }

    private static void logException(Throwable error) {
        logger.info("", error);
    }

    public void propertyChange(PropertyChangeEvent evt)  {
        if (Boolean.TRUE.equals(evt.getNewValue())) {

            readVersionFiles();
        }
    }

    /**
     * Reads the extensions versions using the URLs in the build file
     */
    private void readVersionFiles()  {

        // Need to disable SNI to read from Github
        //System.setProperty("jsse.enableSNIExtension", "false");

        try {
            URL base = new URL(extensionsVersionURL);
            URLConnection conn = base.openConnection();
            conn.setUseCaches(false);


            try (InputStream input = conn.getInputStream()){
                parseextensionversionFile(input);

            }


        } catch (IOException e) {
            // Fail silently if we can't contact the server
        }

    }

    private void parseextensionversionFile(InputStream metadata) {

        ArrayList<String> addtoextensionlist = new ArrayList<String>();
        SAXBuilder parser = new SAXBuilder();

        try {
            // the root element will be the extensionsMetadata element
            Document doc = parser.build(metadata);
            org.jdom2.Element root = doc.getRootElement();

            // read the shared metadata
            if(root.getName().equals(extensionsFileElement)) {

                for(org.jdom2.Element e: root.getChildren()) {

                    //for(org.jdom2.Element f: e.getChildren()) {
                        if(e.getName().equals(extensiondataType)) {
                            // read the extensions attributes
                            ExtensionVersionChecker.ExtensionVersions extversion = new ExtensionVersionChecker.ExtensionVersions();
                            extversion.setName(e.getAttribute(extensionNameAttr).getValue());
                            extversion.setboardversion(e.getAttribute(extensionversionAttr).getValue());
                            extversion.setversiondate(e.getAttribute(extensionversiondateAttr).getValue());
                            extversion.setdescription(e.getAttribute(extensiondescAttr).getValue());

                            // add the extension version to the extensions version list
                            extensionsversions.put(extversion.getName(), extversion);
                        }
                    //}
                }
            }

        } catch (IOException e) {
            e.printStackTrace(System.err);
        } catch (JDOMException e) {

        }

    }

    /**
     * Copies a extension from the GitHub extension repository to the extension directory
     * @param extensionName the extension name
     * @return true if the extension was successfully copied, otherwise false
     */
    public static boolean updateextension(String extensionName) {

        String qualifiedExtensionName =
                GameModule.getGameModule().getPrefs().getStoredValue("extensionDIR") +
                        System.getProperty("file.separator", "\\") + extensionName +".vmdx";
        String testExtensionName =
                GameModule.getGameModule().getPrefs().getStoredValue("extensionDIR") +
                        System.getProperty("file.separator", "\\") + "testextension";
        String url = extensionsRepositoryURL + "/" + extensionName +".vmdx";

        // code changes to improve auto-sync workflow -if testextension is valid, copy over existing extension file; whether valid or not, delete temp file testextension
        final Path testpath =  Paths.get(testExtensionName);
        if(Boolean.TRUE.equals(getRepositoryFile(url, testExtensionName))){
            final Path qualifiedpath = Paths.get(qualifiedExtensionName);
            try {
                // need to unload older version before can replace it
                ModuleExtension removeext = null;
                for (final ModuleExtension ext : GameModule.getGameModule().getComponentsOf(ModuleExtension.class)) {
                    if (ext.getName().equals(extensionName)) {
                        removeext = ext;
                        break;
                    }
                }
                removeext.getDataArchive().close();
                GameModule.getGameModule().remove(removeext);
                Files.move(testpath, qualifiedpath, REPLACE_EXISTING);

            }  catch (IOException e) {
                logException(e);
                return false;
            }
            return true;
        } else {
            try {
                Files.delete(testpath);
            }  catch (IOException e) {
                logException(e);
                GameModule.getGameModule().warn("testextension deletion failed; remove manually");
                return false;
            }
            return false;
        }
    }


    /**
     * Copy a file from a website to local disk
     * Assumes URL is on github.com
     * NOTE - will overwrite existing file
     * @param url URL to the file on the website
     * @param fileName fully qualified file name
     * @return true if copy succeeded, otherwise false
     */
    private static boolean getRepositoryFile(String url, String fileName)  {

        // Need to disable SNI to read from Github
        System.setProperty("jsse.enableSNIExtension", "false");

        try {

            URL website = new URL( encodeUrl(url) );
            URLConnection conn = website.openConnection();
            conn.setUseCaches(false);
            try (FileOutputStream outFile = new FileOutputStream(fileName);
                 InputStream in = conn.getInputStream()) {
                ReadableByteChannel rbc = Channels.newChannel(in);
                outFile.getChannel().transferFrom(rbc, 0, Long.MAX_VALUE);
            }
            return true;

        } catch (IOException e) {
            // Fail silently on any error
            return false;
        }
    }

    // do not use on an already-encoded URL. It will double-encode it.
    private static String encodeUrl( String unencodedURL ) {
        try {
            URL url = new URL(unencodedURL);
            URI uri = new URI(url.getProtocol(), url.getUserInfo(), url.getHost(), url.getPort(), url.getPath(), url.getQuery(), url.getRef());
            return uri.toURL().toString();
        }
        catch( java.net.URISyntaxException ex ) {
            return unencodedURL;
        }
        catch( java.net.MalformedURLException ex ) {
            return unencodedURL;
        }
    }


    // new method to get latest version number from xml file instead of .txt
    public static String getlatestVersionnumberfromwebrepository(String extensionName){
        ExtensionVersionChecker.ExtensionVersions findversion = extensionsversions.get(extensionName);
        return findversion.getExtensionversion();

    }

    public class ExtensionVersions{
        private String extensionname;
        private String extensionversion;
        private String versiondate;
        private String description;

        public String getName(){return extensionname;}
        public void setName(String value ) {extensionname = value;}
        public String getExtensionversion() {return extensionversion;}
        public void setboardversion(String value){extensionversion = value;}
        public String getversiondate(){return versiondate;}
        public void setversiondate(String value){versiondate = value;}
        public String getdescription(){return description;}
        public void setdescription(String value){description = value;}
    }

    public static class ASLExtensionsLoader extends ExtensionsLoader{

    }
}
