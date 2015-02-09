package VASL;

import java.io.*;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

public class BoardBuilder {

    private static String INPUT_FOLDER = "boards/src";
    private static String OUTPUT_FOLDER = "boardsOut";
    private static String BOARD_NAME = null;

    public static void main(String[] args) {

        // read the input/output folders - note board name is optional parameter used to build a single board
        if (args.length < 2 || args.length > 3) {
            System.out.println("Usage: BoardBuilder <board source folder>  <output folder> <board name>");
            System.exit(1);
        }
        INPUT_FOLDER = args[0];
        OUTPUT_FOLDER = args[1];

        if(args.length == 3) {
            BOARD_NAME = args[2];
            System.out.println("Building single board: " + BOARD_NAME);
        }

	    BoardBuilder program = new BoardBuilder();
        program.run();
    }

    private void run() {

        File baseFolder = new File(INPUT_FOLDER);

        if (!baseFolder.exists()) {
            System.out.println(String.format("Could not find %s.", baseFolder));
            return;
        }

        File outFolder = new File(OUTPUT_FOLDER);

        System.out.println(String.format("Writing to %s", outFolder));

        if (outFolder.mkdirs()) {
            System.out.println("Output folder created.");
        } else {
            System.out.println("Output folder already exists.");
        }

        FilenameFilter boardFolderFilter = new FilenameFilter() {

            public boolean accept(File dir, String name) {
                return name.startsWith("bd");
            }
        };

        FilenameFilter boardContentFilter = new FilenameFilter() {

            public boolean accept(File dir, String name) {
                return !name.endsWith(".psd") && !name.startsWith(".");
            }
        };

        File[] allBoards = baseFolder.listFiles(boardFolderFilter);
        if (allBoards == null) {
            System.out.println(String.format("No board folders found in %s. Exiting.", baseFolder));
            return;
        } else {
            System.out.println(String.format("Found %d board folders in %s", allBoards.length, baseFolder));
        }


        byte[] buffer = new byte[1024];

        for (File aBoard : allBoards) {

            if(BOARD_NAME == null || BOARD_NAME.equals(aBoard.getName())) {

                String boardName = aBoard.getName();
                ZipOutputStream boardArchive;

                try {
                    boardArchive = new ZipOutputStream(new FileOutputStream(new File(outFolder, boardName)));
                } catch (FileNotFoundException e) {
                    e.printStackTrace();
                    return;
                }

                File[] contents = aBoard.listFiles(boardContentFilter);
                if (contents == null) {
                    System.out.println(String.format("No acceptable content found in %s. Skipping.", aBoard));
                    continue;
                } else {
                    System.out.println(String.format("Adding %d files to archive %s.", contents.length, boardName));
                }

                try {
                    for (File file : contents) {
                        ZipEntry zipEntry = new ZipEntry(file.getName());
                        System.out.println(String.format("Adding %s to archive %s.", file.getName(), boardName));
                        boardArchive.putNextEntry(zipEntry);
                        FileInputStream in = null;
                        try {
                            in = new FileInputStream(file);
                            int len;
                            while ((len = in.read(buffer)) > 0) {
                                boardArchive.write(buffer, 0, len);
                            }
                            in.close();
                            boardArchive.closeEntry();
                        } catch (FileNotFoundException e) {
                            e.printStackTrace();
                        } finally {
                            if (in != null) in.close();
                        }
                    }
                } catch (IOException e) {
                    e.printStackTrace();
                } finally {
                    try { boardArchive.close(); } catch (IOException ignored) {}
                }
                System.out.println(String.format("Archive %s completed.", boardName));
            }
         }

        System.out.println(String.format("Done."));
    }

}
