package VASL.build.module;

import VASSAL.build.AbstractBuildable;
import VASSAL.build.Buildable;
import VASSAL.build.GameModule;
import VASSAL.build.module.GameState;
import VASSAL.configure.BooleanConfigurer;
import VASSAL.preferences.Prefs;

import java.io.File;
import java.io.IOException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Timer;
import java.util.TimerTask;

public class ASLAutoSave extends AbstractBuildable {
    private static final String AUTOSAVE_OPTION = "autosave";
    private static final int AUTOSAVE_INTERVAL_MS = 10 * 60 * 1000; // Autosave interval in milliseconds (10 minutes)
    private static final int AUTOSAVE_DELAY_MS = 60 * 1000; // Initial delay before starting autosave (1 minute)
    private static final int MAX_AUTOSAVE_FILES = 20; // Maximum number of autosave files to retain
    private static final DateTimeFormatter DATE_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HHmm");

    private final Timer timer;
    private boolean usingAutosave = false;
    private final GameModule gameModule;
    private final GameState gameState;
    private final String directory;

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
        String dirPath = gameModule.getPrefs().getStoredValue("boardURL") + File.separator + gameModule.getGameName();
        File dir = new File(dirPath);
        if (!dir.exists() && !dir.mkdirs()) {
            gameModule.warn("Failed to create autosave directory: " + dirPath); // Log a warning if directory creation fails
        }
        return dirPath;
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
     */
    private void saveGame() {
        String autosaveFileName = directory + File.separator + "autosave " + getCurrentDateTime() + ".vsav";
        File autosaveFile = new File(autosaveFileName);
        try {
            gameModule.warn("Autosaving game to: " + directory);
            gameState.saveGame(autosaveFile);
        } catch (IOException e) {
            gameModule.warn("Failed to save autosave file: " + autosaveFileName + "\n" + e.getMessage());
        }
    }

    /**
     * Deletes the oldest autosave files if the maximum file limit is exceeded.
     */
    private void deleteOldestFilesIfNeeded() {
        File dir = new File(directory);
        File[] files = dir.listFiles((d, name) -> name.startsWith("autosave") && name.endsWith(".vsav"));
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
        BooleanConfigurer autoSaveOption = (BooleanConfigurer) modulePrefs.getOption(AUTOSAVE_OPTION);

        if (autoSaveOption == null) {
            autoSaveOption = new BooleanConfigurer(AUTOSAVE_OPTION, "AutoSave game every 10 minutes", Boolean.TRUE);
            modulePrefs.addOption("VASL", autoSaveOption);
        }

        // Initialize usingAutosave based on saved preferences
        usingAutosave = (Boolean) modulePrefs.getValue(AUTOSAVE_OPTION);

        // Listen for changes to the autosave preference, updating usingAutosave in real-time
        autoSaveOption.addPropertyChangeListener(e -> usingAutosave = (Boolean) e.getNewValue());

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
