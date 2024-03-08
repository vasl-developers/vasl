package VASL.build.module.map;

import org.jdom2.Document;
import org.jdom2.JDOMException;
import org.jdom2.input.SAXBuilder;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.net.URLConnection;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;

import static java.time.temporal.ChronoUnit.DAYS;

public class BoardDataReader {
    
    // implement using xml file for board versions
    private static final String boardsFileElement = "boardsMetadata";
    private static final String coreboardElement = "coreBoards";
    private static final String boarddataType = "boarddata";
    private static final String coreboardNameAttr = "name";
    private static final String otherboardElement = "otherBoards";
    private static final String otherboardNameAttr = "name";
    private static final String dateofissue = "issued";

    public BoardDataReader() {}

    public static ArrayList<String> getAllBoardNamesList() {
        ArrayList<String> bdList = getallboards();
        for (String bd: getnewboards()) {
            bdList.add(bd);
        }
        ArrayList<String> namesList = new ArrayList<>();
        for (String bd: bdList) {
            namesList.add(bd.split(" ")[0]);
        }
        return namesList;
    }

    public static ArrayList<String> getDeluxeBoardNamesList() {
        //Only return "Deluxe" boards
        ArrayList<String> bdList = getallboards();
        for (String bd: getnewboards()) {
            bdList.add(bd);
        }
        ArrayList<String> namesList = new ArrayList<>();
        for (String bd: bdList) {
            if (bd.contains("Deluxe") || bd.contains("deluxe")) {
                namesList.add(bd.split(" ")[0]);
            }
        }
        if (namesList.size() == 0) {
            //Server offline, provide default list
            namesList.add("00dx");
            namesList.add("a");
            namesList.add("b");
            namesList.add("c");
            namesList.add("d");
            namesList.add("e");
            namesList.add("f");
            namesList.add("g");
            namesList.add("h");
            namesList.add("i");
            namesList.add("j");
            namesList.add("k");
            namesList.add("l");
            namesList.add("mdx");
            namesList.add("ndx");
            namesList.add("odx");
            namesList.add("pdx");
            namesList.add("qdx");
            namesList.add("rdx");
            namesList.add("sdx");
            namesList.add("tdx");
            namesList.add("udx");
            namesList.add("vdx");
            namesList.add("wdx");
            namesList.add("xdx");
            namesList.add("ydx");
            namesList.add("zdx");
            namesList.add("d1");
            namesList.add("d2");
            namesList.add("d3");
            namesList.add("d4");
            namesList.add("d5");
            namesList.add("d6");
            namesList.add("d7");
            namesList.add("DeluxeBlank");
        }
        return namesList;
    }

    private static ArrayList<String> getallboards() {
        ArrayList<String> allboardslist = new ArrayList<String>();
        try {
            URL base = new URL(BoardVersionChecker.getboardVersionURL());
            URLConnection conn = base.openConnection();
            conn.setUseCaches(false);
            try (InputStream inputStream = conn.getInputStream()) {
                allboardslist = parseboardversionFile(inputStream);
            }
        } catch (IOException e){
        } catch (JDOMException e) {
            // throw new JDOMException("Cannot read the shared metadata file", e);
        }
        return allboardslist;
    }

    private static ArrayList<String> getnewboards() {
        ArrayList<String> newboardslist = new ArrayList<String>();
        try {
            URL base = new URL(BoardVersionChecker.getboardVersionURL());
            URLConnection conn = base.openConnection();
            conn.setUseCaches(false);
            try (InputStream inputStream = conn.getInputStream()) {
                newboardslist = newboardsparseboardversionFile(inputStream);
            }
        } catch (IOException e){
        } catch (JDOMException e) {
            // throw new JDOMException("Cannot read the shared metadata file", e);
        }
        return newboardslist;
    }

    private static ArrayList<String> parseboardversionFile(InputStream metadata) throws JDOMException {

        ArrayList<String> addtoboardlist = new ArrayList<String>();
        SAXBuilder parser = new SAXBuilder();

        try {
            // the root element will be the boardsMetadata element
            Document doc = parser.build(metadata);
            org.jdom2.Element root = doc.getRootElement();

            // read the shared metadata
            if(root.getName().equals(boardsFileElement)) {

                for(org.jdom2.Element e: root.getChildren()) {

                    //add coreBoards
                    if(e.getName().equals(coreboardElement)){
                        for(org.jdom2.Element f: e.getChildren()) {
                            if(f.getName().equals(boarddataType)) {
                                // read the coreBoards attributes
                                addtoboardlist.add(f.getAttribute(coreboardNameAttr).getValue() + "    " + f.getAttribute("boardType").getValue());
                            }
                        }
                    }
                    // add all other boards
                    if(e.getName().equals(otherboardElement)){
                        for(org.jdom2.Element f: e.getChildren()) {
                            if(f.getName().equals(boarddataType)) {
                                // read the coreBoards attributes
                                addtoboardlist.add(f.getAttribute(otherboardNameAttr).getValue() + "    " + f.getAttribute("boardType").getValue());
                            }
                        }
                    }
                }
            }

        } catch (IOException e) {
            e.printStackTrace(System.err);
            throw new JDOMException("Error reading the v5boardVersions.xml metadata", e);
        }
        return addtoboardlist;
    }

    private static ArrayList<String> newboardsparseboardversionFile(InputStream metadata) throws JDOMException {

        ArrayList<String> newboardlist = new ArrayList<String>();
        SAXBuilder parser = new SAXBuilder();
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy/MM/dd");
        LocalDate dateissued;
        LocalDate datenow = LocalDate.now();
        try {
            // the root element will be the boardsMetadata element
            Document doc = parser.build(metadata);
            org.jdom2.Element root = doc.getRootElement();

            // read the shared metadata
            if(root.getName().equals(boardsFileElement)) {

                for(org.jdom2.Element e: root.getChildren()) {

                    //add new coreBoards
                    if(e.getName().equals(coreboardElement)){
                        for(org.jdom2.Element f: e.getChildren()) {
                            if(f.getName().equals(boarddataType)) {
                                if (!f.getAttribute(dateofissue).getValue().equals("")) {
                                    dateissued = LocalDate.parse(f.getAttribute(dateofissue).getValue(), formatter);
                                    if (DAYS.between(dateissued, datenow) < 91) {
                                        // read the newBoards attributes
                                        newboardlist.add(f.getAttribute(coreboardNameAttr).getValue() + "  " + f.getAttribute("boardType").getValue() + "  NEW");
                                    }
                                }
                            }
                        }
                    }
                    // add all other boards
                    if(e.getName().equals(otherboardElement)){
                        for(org.jdom2.Element f: e.getChildren()) {
                            if(f.getName().equals(boarddataType)) {
                                if (!f.getAttribute(dateofissue).getValue().equals("")) {
                                    dateissued = LocalDate.parse(f.getAttribute(dateofissue).getValue(), formatter);
                                    if (DAYS.between(dateissued, datenow) < 91) {
                                        // read the newBoards attributes
                                        newboardlist.add(f.getAttribute(otherboardNameAttr).getValue() + "  " + f.getAttribute("boardType").getValue()  + "  NEW");
                                    }
                                }
                            }
                        }
                    }
                }
            }

        } catch (IOException e) {
            e.printStackTrace(System.err);
            throw new JDOMException("Error reading the v5boardVersions.xml metadata", e);
        }
        return newboardlist ;
    }

}
