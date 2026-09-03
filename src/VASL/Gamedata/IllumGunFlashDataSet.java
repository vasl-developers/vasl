package VASL.Gamedata;

import VASSAL.build.GameModule;
import VASSAL.tools.DataArchive;
import org.jdom2.Document;
import org.jdom2.Element;
import org.jdom2.JDOMException;
import org.jdom2.input.SAXBuilder;

import java.io.IOException;
import java.io.InputStream;
import java.util.LinkedHashMap;

import static VASSAL.build.GameModule.getGameModule;

/**
 * This class provides access to the Illumination and Gunflash counter metadata file in the module archive
 * equivalent to CounterMetadataFile.java
 */
public class IllumGunFlashDataSet {  //implements StandardXMLDataSet {

    // name of the counter metadata file in the module archive
    private final static String illgunMetadataFileName = "IlluminationCounterMetaData.xml";

    // XML element and attribute names
    protected static final String illuminationMetadataElement = "illuminationGunflashCounterMetadata";
    protected static final String starshellCounterElement = "starshell";
    protected static final String illumRoundCounterElement = "illumround";
    protected static final String blazeCounterElement = "blaze";
    protected static final String flameCounterElement = "flame";
    protected static final String tripflaresCounterElement = "tripflares";
    protected static final String searchlightsCounterElement = "searchlights";
    protected static final String gunflashCounterElement = "gunflash";

    protected static final String counterNameAttribute = "name";
    protected static final String counterHexAttribute = "hex";
    protected static final String counterRangeAttribute = "range";
    protected static final String counterZoneAttribute = "zone";


    // List of the counter elements
    protected LinkedHashMap<String, IllumGunFlashMetadata> metadataElements = new LinkedHashMap<String, IllumGunFlashMetadata>(30);

    public IllumGunFlashDataSet(){

        DataArchive archive = GameModule.getGameModule().getDataArchive();
        try (InputStream inputStream = archive.getInputStream(illgunMetadataFileName)) {

            // counter metadata
            parseIllgunCounterMetadataFile(inputStream);

            // give up on any errors
        }
        catch (IOException e) {
            metadataElements = null;
        }
        catch (JDOMException e) {
            metadataElements = null;
        }
        catch (NullPointerException e) {
            metadataElements = null;
        }
        final GameModule mod = getGameModule();
        if (metadataElements == null) {mod.warn("metadataElements are null");}
    }

        /**
         * Parses the counter metadata file
         * @param metadata an <code>InputStream</code> for the counter metadata XML file
         * @throws org.jdom2.JDOMException
         */
        public void parseIllgunCounterMetadataFile(InputStream metadata) throws JDOMException {

            SAXBuilder parser = new SAXBuilder();

            try {

                // the root element will be the counter metadata element
                Document doc = parser.build(metadata);
                Element root = doc.getRootElement();

                // read the counters
                if(root.getName().equals(illuminationMetadataElement)) {

                    parseCounters(root);
                }

            }
            catch (IOException e) {
                e.printStackTrace(System.err);
                throw new JDOMException("Error reading the counter metadata", e);
            }
        }

        /**
         * Parses the counter metadata element
         * @param element the counter metadata element
         * @throws org.jdom2.JDOMException
         */
        protected void parseCounters(Element element) throws JDOMException {

            // make sure we have the right element
            assertElementName(element, illuminationMetadataElement);

            for(Element e: element.getChildren()) {

                IllumGunFlashMetadata illumgunflashMetadata = null;
                String name = e.getAttributeValue(counterNameAttribute);

                // ignore any child elements that are not counter rules
                if(e.getName().equals(starshellCounterElement)) {
                    // read the attributes of the element
                    illumgunflashMetadata = new IllumGunFlashMetadata(name, IllumGunFlashMetadata.CounterType.STARSHELL);
                    illumgunflashMetadata.setRange(e.getAttribute(counterRangeAttribute).getIntValue());
                    //illumgunflashMetadata.setHindrance(e.getAttribute(counterHindranceAttribute).getIntValue());

                }
                else if(e.getName().equals(illumRoundCounterElement)) {
                    illumgunflashMetadata = new IllumGunFlashMetadata(name, IllumGunFlashMetadata.CounterType.ILLUMROUND);
                    illumgunflashMetadata.setRange(e.getAttribute(counterRangeAttribute).getIntValue());
                    //if (e.getAttribute(counterLevelAttribute) != null) {
                    //    illumgunflashMetadata.setLevel(e.getAttribute(counterLevelAttribute).getIntValue());
                    //}
                }
                else if(e.getName().equals(flameCounterElement)) {
                    illumgunflashMetadata = new IllumGunFlashMetadata(name, IllumGunFlashMetadata.CounterType.FLAME);
                    //illumgunflashMetadata.setTerrain(e.getAttributeValue(counterTerrainAttribute));
                    //if (e.getAttribute(counterCoveredArchAttribute) != null) {
                    illumgunflashMetadata.setRange(e.getAttribute(counterRangeAttribute).getIntValue());
                    //}
                    //if (e.getAttribute(counterHexsideAttribute) != null) {
                    //    illumgunflashMetadata.setHexside(e.getAttribute(counterHexsideAttribute).getIntValue());
                    //}
                }
                else if(e.getName().equals(blazeCounterElement)) {
                    illumgunflashMetadata = new IllumGunFlashMetadata(name, IllumGunFlashMetadata.CounterType.BLAZE);
                    illumgunflashMetadata.setRange(e.getAttribute(counterRangeAttribute).getIntValue());
                    //illumgunflashMetadata.setIsBarrage(e.getAttribute(counterIsBarrageAttribute).getBooleanValue());
                }
                else if(e.getName().equals(tripflaresCounterElement)) {
                    illumgunflashMetadata = new IllumGunFlashMetadata(name, IllumGunFlashMetadata.CounterType.TRIPFLARES);
                    illumgunflashMetadata.setRange(e.getAttribute(counterRangeAttribute).getIntValue());
                }
                else if(e.getName().equals(searchlightsCounterElement)) {
                    illumgunflashMetadata = new IllumGunFlashMetadata(name, IllumGunFlashMetadata.CounterType.SEARCHLIGHTS);
                }else if(e.getName().equals(gunflashCounterElement)) {
                    illumgunflashMetadata = new IllumGunFlashMetadata(name, IllumGunFlashMetadata.CounterType.GUNFLASH);
                    illumgunflashMetadata.setRange(e.getAttribute(counterRangeAttribute).getIntValue());
                }


                metadataElements.put(name, illumgunflashMetadata);
            }
        }

        /**
         * @return the list of LOS counter rules
         */
        public LinkedHashMap<String, IllumGunFlashMetadata> getMetadataElements(){
            return metadataElements;
        }

        /**
         * Assert the element has the given name otherwise throw an exception
         * @param element the element
         * @param elementName the element name
         * @throws org.jdom2.JDOMException
         */
        private void assertElementName(Element element, String elementName) throws JDOMException {

            // make sure we have the right element
            if(!element.getName().equals(elementName)) {
                throw new JDOMException("Invalid element passed to an element parser: " + elementName);
            }
        }

    }

