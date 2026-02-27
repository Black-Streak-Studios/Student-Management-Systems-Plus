package com.nana.sms.ui;

import com.nana.sms.repository.SqliteStudentRepository;
import com.nana.sms.repository.StudentRepository;
import com.nana.sms.service.ReportGenerator;
import com.nana.sms.service.StudentService;
import com.nana.sms.service.StudentServiceImpl;
import com.nana.sms.util.AppLogger;
import com.nana.sms.util.CsvExporter;
import com.nana.sms.util.CsvImporter;
import com.nana.sms.util.DatabaseManager;
import javafx.application.Application;
import javafx.scene.Scene;
import javafx.stage.Stage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDateTime;

/**
 * MainApp â€” JavaFX Application Entry Point
 *
 * <p>WHY THIS CLASS EXISTS:
 * Every JavaFX application must have exactly one class that extends
 * {@link Application}. This class is responsible for:
 * <ul>
 *   <li>Wiring together all dependencies (manual DI composition root).</li>
 *   <li>Creating the primary {@link Stage} and initial {@link Scene}.</li>
 *   <li>Calling {@link DatabaseManager} shutdown on exit.</li>
 *   <li>Logging the startup and shutdown banners.</li>
 * </ul>
 *
 * <p>COMPOSITION ROOT PATTERN:
 * All dependency wiring happens here â€” repository â†’ service â†’ UI controllers.
 * No controller creates its own dependencies; they are all injected.
 * This is manual dependency injection without a framework, appropriate
 * for a desktop application of this scale.
 */
public class MainApp extends Application {

    private static final Logger log = LoggerFactory.getLogger(MainApp.class);

    /** Records application start time for session duration calculation. */
    private final LocalDateTime startTime = LocalDateTime.now();

    // -----------------------------------------------------------------------
    // DEPENDENCY INSTANCES (composition root)
    // -----------------------------------------------------------------------

    private DatabaseManager   databaseManager;
    private StudentRepository repository;
    private StudentService    studentService;
    private ReportGenerator   reportGenerator;
    private CsvExporter       csvExporter;
    private CsvImporter       csvImporter;

    // -----------------------------------------------------------------------
    // JAVAFX LIFECYCLE
    // -----------------------------------------------------------------------

    /**
     * JavaFX entry point â€” called on the JavaFX Application Thread.
     *
     * <p>Initialises all dependencies, builds the UI, and shows the window.
     *
     * @param primaryStage the primary window provided by JavaFX
     */
    @Override
    public void start(Stage primaryStage) {
        // --- Logging setup ---
        AppLogger.setSessionContext(AppLogger.generateSessionId());
        AppLogger.logStartup();
        log.info("Application starting on JavaFX Application Thread.");

        try {
            // --- Dependency wiring ---
            wireDependencies();

            // --- Build root UI ---
            MainController mainController = new MainController(
                    studentService, reportGenerator,
                    csvExporter, csvImporter);

            Scene scene = new Scene(
                    mainController.buildRootLayout(), 1200, 750);

            // --- Apply stylesheet ---
            scene.getStylesheets().add(getInlineStylesheet());

            // --- Configure stage ---
            primaryStage.setTitle("Student Management System Plus v1.0");
            primaryStage.setScene(scene);
            primaryStage.setMinWidth(900);
            primaryStage.setMinHeight(600);
            primaryStage.show();

            log.info("Primary stage shown. Application ready.");
            AppLogger.logEvent("APP_STARTED",
                    "stage=" + primaryStage.getTitle());

        } catch (Exception ex) {
            log.error("Fatal error during application startup.", ex);
            AppLogger.logErrorEvent("APP_STARTUP_FAILED", "", ex);
            showFatalError(primaryStage, ex);
        }
    }

    /**
     * Called by JavaFX when the application is closing.
     * Performs graceful shutdown of the database connection.
     */
    @Override
    public void stop() {
        log.info("Application stop() called â€” shutting down.");
        AppLogger.logEvent("APP_STOPPING", "");

        if (databaseManager != null) {
            databaseManager.shutdown();
        }

        AppLogger.logShutdown(startTime);
        AppLogger.clearAllContext();
    }

    // -----------------------------------------------------------------------
    // PRIVATE HELPERS
    // -----------------------------------------------------------------------

    /**
     * Wires all application dependencies.
     *
     * <p>ORDER MATTERS:
     * <ol>
     *   <li>DatabaseManager (initialises SQLite)</li>
     *   <li>Repository (needs DatabaseManager)</li>
     *   <li>Service (needs Repository)</li>
     *   <li>ReportGenerator (needs Service)</li>
     *   <li>CSV utilities (need Service)</li>
     * </ol>
     */
    private void wireDependencies() {
        log.info("Wiring application dependencies...");
        databaseManager = DatabaseManager.getInstance();
        repository      = new SqliteStudentRepository();
        studentService  = new StudentServiceImpl(repository);
        reportGenerator = new ReportGenerator(studentService);
        csvExporter     = new CsvExporter();
        csvImporter     = new CsvImporter(studentService);
        log.info("Dependencies wired successfully.");
    }

    /**
     * Returns a CSS stylesheet as a data URI string.
     *
     * <p>WHY inline: Avoids the need for a separate .css resource file
     * that must be on the classpath. Everything is self-contained in the JAR.
     *
     * @return CSS data URI string for use with Scene.getStylesheets().add()
     */
    private String getInlineStylesheet() {
        // We return an empty data URI â€” styling is applied inline via
        // JavaFX API calls in each controller. A real production app
        // would load a CSS file from the classpath.
        return "data:text/css,";
    }

    /**
     * Shows a minimal fatal error dialog and terminates the application.
     *
     * @param stage the primary stage (may not be showing yet)
     * @param ex    the exception that caused the fatal error
     */
    private void showFatalError(Stage stage, Exception ex) {
        javafx.scene.control.Alert alert = new javafx.scene.control.Alert(
                javafx.scene.control.Alert.AlertType.ERROR);
        alert.setTitle("Fatal Startup Error");
        alert.setHeaderText("Student Management System Plus could not start.");
        alert.setContentText(
                "Error: " + ex.getMessage()
                + "\n\nPlease check the log file at:\n"
                + AppLogger.getLogFilePath());
        alert.showAndWait();
        javafx.application.Platform.exit();
    }

    // -----------------------------------------------------------------------
    // MAIN METHOD
    // -----------------------------------------------------------------------

    /**
     * Application entry point.
     *
     * <p>WHY NOT just {@code Application.launch()}:
     * On Java 11+ with modular JavaFX, calling {@code launch()} from a class
     * that extends {@code Application} requires the module system to be
     * configured correctly. Calling {@code launch(args)} explicitly from
     * {@code main()} is more portable and works correctly with the Maven
     * JavaFX plugin.
     *
     * @param args command-line arguments (passed to JavaFX launch)
     */
    public static void main(String[] args) {
        launch(args);
    }
}

