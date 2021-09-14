/*
 * $Id: CounterMetadataFile 3/30/14 davidsullivan1 $
 *
 * Copyright (c) 2014 by David Sullivan
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Library General Public
 * License (LGPL) as published by the Free Software Foundation.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Library General Public License for more details.
 *
 * You should have received a copy of the GNU Library General Public
 * License along with this library; if not, copies are available
 * at http://www.opensource.org.
 */
package VASL.LOS.counters;

import VASL.LOS.counters.CounterMetadata;
import VASSAL.build.GameModule;
import VASSAL.tools.DataArchive;
import VASSAL.tools.ErrorDialog;
import org.apache.commons.io.IOUtils;
import org.jdom2.Document;
import org.jdom2.Element;
import org.jdom2.JDOMException;
import org.jdom2.input.SAXBuilder;

import java.io.IOException;
import java.io.InputStream;
import java.util.LinkedHashMap;

/**
 * This class provides access to the counter metadata file in the module archive
 */
public class CounterMetadataFile {

    // name of the counter metadata file in the module archive
    private final static String counterMetadataFileName = "CounterMetadata.xml";

    // XML element and attribute names
    protected static final String counterMetadataElement = "counterMetadata";
    protected static final String smokeCounterElement = "smoke";
    protected static final String OBACounterElement = "OBA";
    protected static final String terrainCounterElement = "terrain";
    protected static final String wreckCounterElement = "wreck";
    protected static final String ignoreCounterElement = "ignore";
    protected static final String bridgeCounterElement = "bridge";

    protected static final String buildingLevelCounterElement = "buildingLevel";
    protected static final String roofCounterElement = "roof";
    protected static final String entrenchmentCounterElement = "entrenchment";
    protected static final String crestCounterElement = "crest";
    protected static final String climbCounterElement = "climb";

    protected static final String locationCounterElement = "locationCounter";
    protected static final String counterNameAttribute = "name";
    protected static final String counterHindranceAttribute = "hindrance";
    protected static final String counterHeightAttribute = "height";
    protected static final String counterTerrainAttribute = "terrain";
    protected static final String counterLevelAttribute = "level";
    protected static final String counterPositionAttribute = "position";
    protected static final String counterCoveredArchAttribute = "ca";

    // constant values for the counter position attribute
    public static final String counterPositionAbove = "above";
    public static final String getCounterPositionBelow = "below";

    // List of the counter elements
    protected LinkedHashMap<String, CounterMetadata> metadataElements = new LinkedHashMap<String, CounterMetadata>(30);

    public CounterMetadataFile() {

        DataArchive archive = GameModule.getGameModule().getDataArchive();
        try (InputStream inputStream = archive.getInputStream(counterMetadataFileName)) {

            // counter metadata
            parseCounterMetadataFile(inputStream);

            // give up on any errors
        } catch (IOException e) {
            metadataElements = null;
            ErrorDialog.bug(e);
        } catch (JDOMException e) {
            metadataElements = null;
            ErrorDialog.bug(e);
        } catch (NullPointerException e) {
            metadataElements = null;
            ErrorDialog.bug(e);
        }
    }

    /**
     * Parses the counter metadata file
     * @param metadata an <code>InputStream</code> for the counter metadata XML file
     * @throws org.jdom2.JDOMException
     */
    public void parseCounterMetadataFile(InputStream metadata) throws JDOMException {

        SAXBuilder parser = new SAXBuilder();

        try {

            // the root element will be the counter metadata element
            Document doc = parser.build(metadata);
            Element root = doc.getRootElement();

            // read the counters
            if(root.getName().equals(counterMetadataElement)) {

                parseCounters(root);
            }

        } catch (IOException e) {
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
        assertElementName(element, counterMetadataElement);

        for(Element e: element.getChildren()) {

            CounterMetadata counterMetadata = null;
            String name = e.getAttributeValue(counterNameAttribute);

            // ignore any child elements that are not counter rules
            if(e.getName().equals(smokeCounterElement)) {

                // read the height and hindrance
                counterMetadata = new CounterMetadata(name, CounterMetadata.CounterType.SMOKE);
                counterMetadata.setHeight(e.getAttribute(counterHeightAttribute).getIntValue());
                counterMetadata.setHindrance(e.getAttribute(counterHindranceAttribute).getIntValue());

            }
            else if(e.getName().equals(terrainCounterElement)) {
                counterMetadata = new CounterMetadata(name, CounterMetadata.CounterType.TERRAIN);
                counterMetadata.setTerrain(e.getAttributeValue(counterTerrainAttribute));

            }
            else if(e.getName().equals(OBACounterElement)) {
                counterMetadata = new CounterMetadata(name, CounterMetadata.CounterType.OBA);

            }
            else if(e.getName().equals(wreckCounterElement)) {
                counterMetadata = new CounterMetadata(name, CounterMetadata.CounterType.WRECK);

            }
            else if(e.getName().equals(ignoreCounterElement)) {
                counterMetadata = new CounterMetadata(name, CounterMetadata.CounterType.IGNORE);
            }
            else if(e.getName().equals(bridgeCounterElement)) {
                counterMetadata = new CounterMetadata(name, CounterMetadata.CounterType.BRIDGE);
                counterMetadata.setHindrance(e.getAttribute(counterHindranceAttribute).getIntValue());
                counterMetadata.setPosition(e.getAttributeValue(counterPositionAttribute));
            }
            else if(e.getName().equals(buildingLevelCounterElement)) {
                counterMetadata = new CounterMetadata(name, CounterMetadata.CounterType.BUILDING_LEVEL);
                counterMetadata.setLevel(e.getAttribute(counterLevelAttribute).getIntValue());
                counterMetadata.setPosition(e.getAttributeValue(counterPositionAttribute));
            }
            else if(e.getName().equals(roofCounterElement)) {
                counterMetadata = new CounterMetadata(name, CounterMetadata.CounterType.ROOF);
                counterMetadata.setPosition(e.getAttributeValue(counterPositionAttribute));
            }
            else if(e.getName().equals(entrenchmentCounterElement)) {
                counterMetadata = new CounterMetadata(name, CounterMetadata.CounterType.ENTRENCHMENT);
                counterMetadata.setPosition(e.getAttributeValue(counterPositionAttribute));
                counterMetadata.setTerrain("Foxholes");
            }
            else if(e.getName().equals(crestCounterElement)) {
                counterMetadata = new CounterMetadata(name, CounterMetadata.CounterType.CREST);
                counterMetadata.setPosition(e.getAttributeValue(counterPositionAttribute));
                counterMetadata.setCoverArch(e.getAttribute(counterCoveredArchAttribute).getIntValue());
            }
            else if(e.getName().equals(climbCounterElement)) {
                counterMetadata = new CounterMetadata(name, CounterMetadata.CounterType.CLIMB);
                counterMetadata.setLevel(e.getAttribute(counterLevelAttribute).getIntValue());
                counterMetadata.setPosition(e.getAttributeValue(counterPositionAttribute));
                counterMetadata.setCoverArch(e.getAttribute(counterCoveredArchAttribute).getIntValue());
            }

            metadataElements.put(name, counterMetadata);
        }
    }

    /**
     * @return the list of LOS counter rules
     */
    public LinkedHashMap<String, CounterMetadata> getMetadataElements(){
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
