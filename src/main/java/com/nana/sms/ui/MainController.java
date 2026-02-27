package com.nana.sms.ui;

import com.nana.sms.service.ReportGenerator;
import com.nana.sms.service.StudentService;
import com.nana.sms.util.CsvExporter;
import com.nana.sms.util.CsvImporter;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.Separator;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * MainController â€” Root Layout Controller
 *
 * <p>WHY THIS CLASS EXISTS:
 * The application uses a classic sidebar navigation layout:
 * <pre>
 *   +------------------+----------------------------+
 *   |  NAVIGATION      |  CONTENT AREA              |
 *   |  [Dashboard]     |                            |
 *   |  [Students]      |  (active screen here)      |
 *   |  [Reports]       |                            |
 *   |  [Import/Export] |                            |
 *   |  [Settings]      |                            |
 *   +------------------+----------------------------+
 * </pre>
 *
 * <p>This controller owns the outer shell (BorderPane with sidebar + content
 * area). Each navigation button swaps the content area by calling the
 * appropriate sub-controller's {@code buildView()} method.
 *
 * <p>CONTROLLERS DO NOT CONTAIN BUSINESS LOGIC:
 * Each navigation handler calls a service or sub-controller, never
 * performs validation or SQL. The service layer handles all logic.
 */
public class MainController {

    private static final Logger log = LoggerFactory.getLogger(MainController.class);

    // -----------------------------------------------------------------------
    // DEPENDENCIES (injected via constructor)
    // -----------------------------------------------------------------------

    private final StudentService    studentService;
    private final ReportGenerator   reportGenerator;
    private final CsvExporter       csvExporter;
    private final CsvImporter       csvImporter;

    // -----------------------------------------------------------------------
    // UI COMPONENTS
    // -----------------------------------------------------------------------

    /** The content area where sub-screens are swapped in and out. */
    private StackPane contentArea;

    // -----------------------------------------------------------------------
    // SUB-CONTROLLERS
    // -----------------------------------------------------------------------

    private DashboardController    dashboardController;
    private StudentsController     studentsController;
    private ReportsController      reportsController;
    private ImportExportController importExportController;
    private SettingsController     settingsController;

    // -----------------------------------------------------------------------
    // CONSTRUCTOR
    // -----------------------------------------------------------------------

    /**
     * Creates the MainController with all required dependencies.
     *
     * @param studentService  the service for student operations
     * @param reportGenerator the report generation service
     * @param csvExporter     the CSV export utility
     * @param csvImporter     the CSV import utility
     */
    public MainController(StudentService studentService,
                           ReportGenerator reportGenerator,
                           CsvExporter csvExporter,
                           CsvImporter csvImporter) {
        this.studentService  = studentService;
        this.reportGenerator = reportGenerator;
        this.csvExporter     = csvExporter;
        this.csvImporter     = csvImporter;

        // Instantiate sub-controllers
        this.dashboardController    = new DashboardController(studentService);
        this.studentsController     = new StudentsController(studentService);
        this.reportsController      = new ReportsController(
                reportGenerator, csvExporter);
        this.importExportController = new ImportExportController(
                csvImporter, csvExporter, studentService);
        this.settingsController     = new SettingsController();
    }

    // -----------------------------------------------------------------------
    // BUILD ROOT LAYOUT
    // -----------------------------------------------------------------------

    /**
     * Builds and returns the complete root layout as a {@link BorderPane}.
     *
     * <p>Called once by {@link MainApp#start} to create the initial scene.
     *
     * @return the fully wired root layout pane
     */
    public BorderPane buildRootLayout() {
        BorderPane root = new BorderPane();
        root.setStyle("-fx-background-color: #f0f2f5;");

        // Left: navigation sidebar
        root.setLeft(buildSidebar());

        // Centre: content area (starts on Dashboard)
        contentArea = new StackPane();
        contentArea.setPadding(new Insets(20));
        root.setCenter(contentArea);

        // Show dashboard by default
        showDashboard();

        return root;
    }

    // -----------------------------------------------------------------------
    // SIDEBAR
    // -----------------------------------------------------------------------

    /**
     * Builds the left-side navigation panel.
     *
     * @return a styled {@link VBox} containing nav buttons
     */
    private VBox buildSidebar() {
        VBox sidebar = new VBox(4);
        sidebar.setPrefWidth(200);
        sidebar.setPadding(new Insets(0));
        sidebar.setStyle(
                "-fx-background-color: #1e293b;"
                + "-fx-effect: dropshadow(gaussian, rgba(0,0,0,0.3), 8, 0, 2, 0);");

        // App name header
        Label appName = new Label("SMS Plus");
        appName.setStyle(
                "-fx-text-fill: white;"
                + "-fx-font-size: 18px;"
                + "-fx-font-weight: bold;"
                + "-fx-padding: 20 16 8 16;");

        Label version = new Label("v1.0.0");
        version.setStyle(
                "-fx-text-fill: #94a3b8;"
                + "-fx-font-size: 11px;"
                + "-fx-padding: 0 16 16 16;");

        Separator sep = new Separator();
        sep.setStyle("-fx-background-color: #334155;");

        // Navigation buttons
        Button btnDashboard  = buildNavButton("\uD83C\uDFE0  Dashboard");
        Button btnStudents   = buildNavButton("\uD83D\uDC65  Students");
        Button btnReports    = buildNavButton("\uD83D\uDCCA  Reports");
        Button btnImport     = buildNavButton("\uD83D\uDCCB  Import / Export");
        Button btnSettings   = buildNavButton("\u2699\uFE0F  Settings");

        // Spacer to push Settings to bottom
        VBox spacer = new VBox();
        VBox.setVgrow(spacer, javafx.scene.layout.Priority.ALWAYS);

        Separator sepBottom = new Separator();
        sepBottom.setStyle("-fx-background-color: #334155;");

        // Wire navigation actions
        btnDashboard.setOnAction(e -> showDashboard());
        btnStudents.setOnAction(e  -> showStudents());
        btnReports.setOnAction(e   -> showReports());
        btnImport.setOnAction(e    -> showImportExport());
        btnSettings.setOnAction(e  -> showSettings());

        sidebar.getChildren().addAll(
                appName, version, sep,
                btnDashboard, btnStudents, btnReports, btnImport,
                spacer, sepBottom, btnSettings);

        return sidebar;
    }

    /**
     * Creates a styled navigation button for the sidebar.
     *
     * @param text the button label (with emoji icon prefix)
     * @return the styled {@link Button}
     */
    private Button buildNavButton(String text) {
        Button btn = new Button(text);
        btn.setMaxWidth(Double.MAX_VALUE);
        btn.setAlignment(Pos.CENTER_LEFT);
        btn.setPadding(new Insets(12, 16, 12, 16));
        btn.setStyle(
                "-fx-background-color: transparent;"
                + "-fx-text-fill: #cbd5e1;"
                + "-fx-font-size: 13px;"
                + "-fx-cursor: hand;"
                + "-fx-background-radius: 0;");

        // Hover effect via mouse events
        btn.setOnMouseEntered(e -> btn.setStyle(
                "-fx-background-color: #334155;"
                + "-fx-text-fill: white;"
                + "-fx-font-size: 13px;"
                + "-fx-cursor: hand;"
                + "-fx-background-radius: 0;"));
        btn.setOnMouseExited(e -> btn.setStyle(
                "-fx-background-color: transparent;"
                + "-fx-text-fill: #cbd5e1;"
                + "-fx-font-size: 13px;"
                + "-fx-cursor: hand;"
                + "-fx-background-radius: 0;"));

        return btn;
    }

    // -----------------------------------------------------------------------
    // NAVIGATION METHODS
    // -----------------------------------------------------------------------

    /**
     * Replaces the content area with the given content node.
     *
     * @param content the UI node to display
     */
    private void setContent(javafx.scene.Node content) {
        contentArea.getChildren().setAll(content);
    }

    /** Shows the Dashboard screen. */
    private void showDashboard() {
        log.debug("Navigating to Dashboard.");
        setContent(dashboardController.buildView());
    }

    /** Shows the Students management screen. */
    private void showStudents() {
        log.debug("Navigating to Students.");
        setContent(studentsController.buildView());
    }

    /** Shows the Reports screen. */
    private void showReports() {
        log.debug("Navigating to Reports.");
        setContent(reportsController.buildView());
    }

    /** Shows the Import/Export screen. */
    private void showImportExport() {
        log.debug("Navigating to Import/Export.");
        setContent(importExportController.buildView());
    }

    /** Shows the Settings screen. */
    private void showSettings() {
        log.debug("Navigating to Settings.");
        setContent(settingsController.buildView());
    }
}

