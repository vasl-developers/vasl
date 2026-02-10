package VASL.build.module;

import VASSAL.build.AbstractBuildable;
import VASSAL.build.Buildable;
import VASSAL.build.GameModule;
import VASSAL.build.module.GameState;
import VASSAL.configure.BooleanConfigurer;
import VASSAL.configure.DirectoryConfigurer;
import VASSAL.preferences.Prefs;
import VASSAL.tools.io.ZipArchive;

import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Timer;
import java.util.TimerTask;

public class ASLAutoSave extends AbstractBuildable {
    private static final String AUTOSAVE_OPTION = "autosave";
    private static final String AUTOSAVE_DIR_OPTION = "autosaveDir";
    private static final int AUTOSAVE_INTERVAL_MS = 10 * 60 * 1000; // Autosave interval in milliseconds (10 minutes)
    private static final int AUTOSAVE_DELAY_MS = 60 * 1000; // Initial delay before starting autosave (1 minute)
    private static final int MAX_AUTOSAVE_FILES = 20; // Maximum number of autosave files to retain
    private static final DateTimeFormatter DATE_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HHmm");

    private final Timer timer;
    private boolean usingAutosave = false;
    private final GameModule gameModule;
    private final GameState gameState;
    private String directory;

    public ASLAutoSave() {
        this.gameModule = GameModule.getGameModule();
        this.gameState = gameModule.getGameState();
        this.timer = new Timer(true); // Daemon timer to handle autosave
        this.directory = initializeDirectory();
    }

    /**
     * Initializes the autosave directory based on game preferences.
     * @return the autosave directory path
     */
    private String initializeDirectory() {
        // Check if there's a stored value for the autosave directory
        String dirPath = gameModule.getPrefs().getStoredValue(AUTOSAVE_DIR_OPTION);

        // If no stored value, use the board directory + game name as default
        if (dirPath == null) {
            dirPath = gameModule.getPrefs().getStoredValue("boardURL") + File.separator + gameModule.getGameName();
        }

        File dir = new File(dirPath);
        if (!VASL.build.module.FileHelper.FileExists(dir) && !dir.mkdirs()) {
            gameModule.warn("Failed to create autosave directory: " + dirPath); // Log a warning if directory creation fails
        }
        return dirPath;
    }

    /**
     * Sets the directory where autosave files will be stored.
     * @param dir the directory
     */
    public void setDirectory(File dir) {
        if (dir != null) {
            directory = dir.getAbsolutePath();
            // Ensure the directory exists
            File autoSaveFolder = new File(directory);
            if (!VASL.build.module.FileHelper.FileExists(autoSaveFolder) && !autoSaveFolder.mkdirs()) {
                gameModule.warn("Failed to create autosave directory: " + directory);
            }
        }
    }

    /**
     * Starts the autosave timer to periodically save the game.
     */
    public void startAutoSave() {
        timer.scheduleAtFixedRate(new TimerTask() {
            @Override
            public void run() {
                if (!usingAutosave || !gameState.isGameStarted()) {
                    return; // Exit if autosave is disabled or game is not started
                }
                deleteOldestFilesIfNeeded();
                saveGame();
            }
        }, AUTOSAVE_DELAY_MS, AUTOSAVE_INTERVAL_MS);
    }

    /**
     * Saves the current game state to a file with a timestamped filename.
     * Uses saveGameRefresh to avoid updating the recent games list.
     */
    private void saveGame() {
        String autosaveFileName = directory + File.separator + "autosave " + getCurrentDateTime() + ".vsav";
        GameState gameState = gameModule.getGameState();
        try {
            gameModule.warn("Autosaving game to: " + autosaveFileName);

            // Create a temporary file and ZipArchive for saving
            File tmpFile = File.createTempFile("vassal", null);
            ZipArchive tmpZip = new ZipArchive(tmpFile);

            // Use saveGameRefresh instead of saveGame to avoid updating recent games list
            gameState.saveGameRefresh(tmpZip);

            // Copy the temporary file to the final location
            File autosaveFile = new File(autosaveFileName);
            Files.copy(tmpFile.toPath(), autosaveFile.toPath(), StandardCopyOption.REPLACE_EXISTING);

            gameModule.warn("Saved autosave file to: " + autosaveFileName);

            // Delete the temporary file
            if (VASL.build.module.FileHelper.FileExists(tmpFile)) {
                tmpFile.delete();
            }
        } catch (IOException e) {
            gameModule.warn("Failed to save autosave file: " + autosaveFileName + "\n" + e.getMessage());
        }
    }

    /**
     * Deletes the oldest autosave files if the maximum limit is exceeded.
     */
    private void deleteOldestFilesIfNeeded() {
        File dir = new File(directory);
        File[] files = dir.listFiles((d, name) -> 
            name.startsWith("autosave") && name.endsWith(".vsav"));

        if (files != null && files.length > MAX_AUTOSAVE_FILES) {
            Arrays.sort(files, Comparator.comparingLong(File::lastModified));
            for (int i = 0; i < files.length - MAX_AUTOSAVE_FILES; i++) {
                if (!files[i].delete()) {
                    gameModule.warn("Failed to delete old autosave file: " + files[i].getName());
                }
            }
        }
    }

    /**
     * Retrieves the current date and time as a formatted string.
     * @return current date and time as string
     */
    private String getCurrentDateTime() {
        return LocalDateTime.now().format(DATE_TIME_FORMATTER);
    }

    /**
     * Stops the autosave timer, canceling any future scheduled saves.
     */
    public void stopAutoSave() {
        timer.cancel();
    }

    @Override
    public void addTo(Buildable parent) {
        final Prefs modulePrefs = ((GameModule) parent).getPrefs();

        // Set up the autosave enable/disable option
        BooleanConfigurer autoSaveOption = (BooleanConfigurer) modulePrefs.getOption(AUTOSAVE_OPTION);
        if (autoSaveOption == null) {
            autoSaveOption = new BooleanConfigurer(AUTOSAVE_OPTION, "AutoSave game every 10 minutes", Boolean.TRUE);
            modulePrefs.addOption("VASL", autoSaveOption);
        }

        // Initialize usingAutosave based on saved preferences
        usingAutosave = (Boolean) modulePrefs.getValue(AUTOSAVE_OPTION);

        // Listen for changes to the autosave preference, updating usingAutosave in real-time
        autoSaveOption.addPropertyChangeListener(e -> usingAutosave = (Boolean) e.getNewValue());

        // Set up the autosave directory option
        DirectoryConfigurer dirConfig = (DirectoryConfigurer) modulePrefs.getOption(AUTOSAVE_DIR_OPTION);
        if (dirConfig == null) {
            dirConfig = new DirectoryConfigurer(AUTOSAVE_DIR_OPTION, "AutoSave Directory");
            modulePrefs.addOption("VASL", dirConfig);

            // Set default value if not already set
            String storedValue = modulePrefs.getStoredValue(AUTOSAVE_DIR_OPTION);
            if (storedValue == null) {
                // Use the current directory as default
                dirConfig.setValue(new File(directory));
            }
        }

        // Update directory if preference exists
        if (modulePrefs.getValue(AUTOSAVE_DIR_OPTION) != null) {
            setDirectory((File) modulePrefs.getValue(AUTOSAVE_DIR_OPTION));
        }

        // Listen for changes to the directory preference
        dirConfig.addPropertyChangeListener(new PropertyChangeListener() {
            @Override
            public void propertyChange(PropertyChangeEvent evt) {
                setDirectory((File) evt.getNewValue());
            }
        });

        // Start the autosave timer (it will check usingAutosave each time it runs)
        startAutoSave();
    }

    @Override
    public String[] getAttributeNames() {
        return new String[0]; // No attributes to expose
    }

    @Override
    public void setAttribute(String key, Object value) {
        // Method intentionally left blank; no attributes to set
    }

    @Override
    public String getAttributeValueString(String key) {
        return null; // No attribute values to return
    }
}
