package com.nana.sms.util;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Properties;

/**
 * AppConfig - Application Configuration Manager
 */
public final class AppConfig {

    private static final Logger log = LoggerFactory.getLogger(AppConfig.class);

    public static final String KEY_MAX_STUDENTS_DISPLAY = "ui.max.students.display";
    public static final String KEY_DEFAULT_EXPORT_DIR   = "export.default.directory";
    public static final String KEY_DEFAULT_IMPORT_DIR   = "import.default.directory";
    public static final String KEY_CONFIRM_DELETE        = "ui.confirm.delete";
    public static final String KEY_LOG_LEVEL             = "logging.level";
    public static final String KEY_LOAD_SAMPLE_DATA      = "startup.load.sample.data";
    public static final String KEY_SAMPLE_DATA_COUNT     = "startup.sample.data.count";
    public static final String PREF_WINDOW_WIDTH         = "window.width";
    public static final String PREF_WINDOW_HEIGHT        = "window.height";
    public static final String PREF_LAST_DIR             = "last.directory";

    private static final Properties DEFAULTS = new Properties();

    static {
        DEFAULTS.setProperty(KEY_MAX_STUDENTS_DISPLAY, "1000");
        DEFAULTS.setProperty(KEY_DEFAULT_EXPORT_DIR,   getUserDocumentsPath());
        DEFAULTS.setProperty(KEY_DEFAULT_IMPORT_DIR,   getUserDownloadsPath());
        DEFAULTS.setProperty(KEY_CONFIRM_DELETE,        "true");
        DEFAULTS.setProperty(KEY_LOG_LEVEL,             "INFO");
        DEFAULTS.setProperty(KEY_LOAD_SAMPLE_DATA,      "false");
        DEFAULTS.setProperty(KEY_SAMPLE_DATA_COUNT,     "20");
    }

    private static AppConfig instance;

    public static synchronized AppConfig getInstance() {
        if (instance == null) {
            instance = new AppConfig();
        }
        return instance;
    }

    private final Properties props;
    private final Path propertiesFilePath;
    private final java.util.prefs.Preferences prefs;

    private AppConfig() {
        String appData = System.getenv("APPDATA");
        if (appData == null) appData = System.getProperty("user.home");
        propertiesFilePath = Paths.get(appData, "SMS_Plus", "sms_plus.properties");
        props = new Properties(DEFAULTS);
        loadUserProperties();
        prefs = java.util.prefs.Preferences.userNodeForPackage(AppConfig.class);
        log.info("AppConfig loaded. Properties file: {}", propertiesFilePath.toAbsolutePath());
    }

    public String getString(String key) {
        return props.getProperty(key);
    }

    public int getInt(String key, int defaultValue) {
        try {
            return Integer.parseInt(props.getProperty(key));
        } catch (NumberFormatException ex) {
            return defaultValue;
        }
    }

    public boolean getBoolean(String key) {
        String val = props.getProperty(key, "false").toLowerCase().trim();
        return val.equals("true") || val.equals("yes") || val.equals("1");
    }

    public Path getDefaultExportDir() {
        String dirStr = getString(KEY_DEFAULT_EXPORT_DIR);
        Path dir = Paths.get(dirStr);
        if (!Files.isDirectory(dir)) {
            return Paths.get(System.getProperty("user.home"));
        }
        return dir;
    }

    public Path getDefaultImportDir() {
        String dirStr = getString(KEY_DEFAULT_IMPORT_DIR);
        Path dir = Paths.get(dirStr);
        if (!Files.isDirectory(dir)) {
            return Paths.get(System.getProperty("user.home"));
        }
        return dir;
    }

    public String getPref(String key, String defaultValue) {
        return prefs.get(key, defaultValue);
    }

    public int getPrefInt(String key, int defaultValue) {
        return prefs.getInt(key, defaultValue);
    }

    public void setPref(String key, String value) {
        prefs.put(key, value);
    }

    public void setPrefInt(String key, int value) {
        prefs.putInt(key, value);
    }

    public void saveUserProperties(String comment) {
        try {
            ensurePropertiesDirectory();
            Properties userOnly = new Properties();
            for (String key : props.stringPropertyNames()) {
                String userVal  = props.getProperty(key);
                String defltVal = DEFAULTS.getProperty(key, "");
                if (!userVal.equals(defltVal)) {
                    userOnly.setProperty(key, userVal);
                }
            }
            try (OutputStream out = Files.newOutputStream(propertiesFilePath)) {
                userOnly.store(out, comment);
            }
            log.info("User properties saved to: {}", propertiesFilePath);
        } catch (IOException ex) {
            log.error("Failed to save user properties.", ex);
        }
    }

    public void set(String key, String value) {
        props.setProperty(key, value);
    }

    private void loadUserProperties() {
        if (!Files.exists(propertiesFilePath)) {
            log.debug("User properties file not found; using defaults.");
            return;
        }
        try (InputStream in = Files.newInputStream(propertiesFilePath)) {
            props.load(in);
        } catch (IOException ex) {
            log.warn("Failed to load user properties: {}", ex.getMessage());
        }
    }

    private void ensurePropertiesDirectory() throws IOException {
        Path parent = propertiesFilePath.getParent();
        if (!Files.exists(parent)) {
            Files.createDirectories(parent);
        }
    }

    private static String getUserDocumentsPath() {
        String userProfile = System.getenv("USERPROFILE");
        if (userProfile != null) {
            Path docs = Paths.get(userProfile, "Documents");
            if (Files.isDirectory(docs)) return docs.toString();
        }
        return System.getProperty("user.home");
    }

    private static String getUserDownloadsPath() {
        String userProfile = System.getenv("USERPROFILE");
        if (userProfile != null) {
            Path downloads = Paths.get(userProfile, "Downloads");
            if (Files.isDirectory(downloads)) return downloads.toString();
        }
        return System.getProperty("user.home");
    }
}

