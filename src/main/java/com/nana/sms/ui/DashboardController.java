package com.nana.sms.ui;

import com.nana.sms.domain.StudentStatus;
import com.nana.sms.service.StudentService;
import javafx.application.Platform;
import javafx.concurrent.Task;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressIndicator;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * DashboardController â€” Dashboard Screen Controller
 *
 * <p>WHY THIS CLASS EXISTS:
 * The dashboard provides an at-a-glance summary of the student population.
 * It displays statistic cards for total students, status breakdowns, and
 * average GPA. All data is loaded on a background thread to prevent UI
 * freezing while database queries execute.
 *
 * <p>NO BUSINESS LOGIC HERE:
 * All data retrieval is delegated to {@link StudentService}. This controller
 * only translates service results into JavaFX UI nodes.
 */
public class DashboardController {

    private static final Logger log = LoggerFactory.getLogger(DashboardController.class);

    private final StudentService studentService;

    public DashboardController(StudentService studentService) {
        this.studentService = studentService;
    }

    /**
     * Builds the dashboard view.
     *
     * <p>Returns immediately with a loading spinner, then populates
     * the stats on a background thread. On completion, updates the UI
     * on the JavaFX Application Thread via {@link Platform#runLater}.
     *
     * @return the dashboard root node
     */
    public VBox buildView() {
        VBox root = new VBox(20);
        root.setPadding(new Insets(10));

        // --- Title ---
        Label title = new Label("Dashboard");
        title.setStyle(
                "-fx-font-size: 24px;"
                + "-fx-font-weight: bold;"
                + "-fx-text-fill: #1e293b;");

        Label subtitle = new Label("Student population overview");
        subtitle.setStyle("-fx-text-fill: #64748b; -fx-font-size: 13px;");

        // --- Stats grid placeholder ---
        GridPane statsGrid = new GridPane();
        statsGrid.setHgap(16);
        statsGrid.setVgap(16);

        // Loading indicator shown while background task runs
        StackPane loadingPane = new StackPane(new ProgressIndicator());
        loadingPane.setPrefHeight(120);

        statsGrid.add(loadingPane, 0, 0);

        root.getChildren().addAll(title, subtitle, statsGrid);

        // Load stats asynchronously
        loadDashboardStats(statsGrid);

        return root;
    }

    /**
     * Loads all dashboard statistics on a background thread using a
     * JavaFX {@link Task}.
     *
     * <p>WHY A TASK:
     * Database queries (even fast ones) should never run on the JavaFX
     * Application Thread. If a query takes longer than ~16ms, the UI
     * will stutter. Using a Task keeps the UI responsive and shows a
     * loading indicator while data is being fetched.
     *
     * @param statsGrid the grid to populate with stat cards on completion
     */
    private void loadDashboardStats(GridPane statsGrid) {
        // Capture all stats as local variables in the background thread
        Task<DashboardStats> task = new Task<>() {
            @Override
            protected DashboardStats call() {
                log.debug("Loading dashboard stats on background thread.");
                DashboardStats stats = new DashboardStats();
                stats.total      = studentService.getTotalStudentCount();
                stats.active     = studentService.getStudentCountByStatus(
                                        StudentStatus.ACTIVE);
                stats.inactive   = studentService.getStudentCountByStatus(
                                        StudentStatus.INACTIVE);
                stats.graduated  = studentService.getStudentCountByStatus(
                                        StudentStatus.GRADUATED);
                stats.suspended  = studentService.getStudentCountByStatus(
                                        StudentStatus.SUSPENDED);
                stats.avgGpa     = studentService.getOverallAverageGpa();
                stats.courseCount = studentService.getDistinctCourses().size();
                return stats;
            }
        };

        task.setOnSucceeded(e -> {
            // Back on JavaFX Application Thread â€” safe to update UI
            DashboardStats stats = task.getValue();
            populateStatsGrid(statsGrid, stats);
            log.debug("Dashboard stats loaded and displayed.");
        });

        task.setOnFailed(e -> {
            log.error("Dashboard stats load failed.", task.getException());
            statsGrid.getChildren().setAll(
                    buildErrorLabel("Failed to load statistics."));
        });

        // Run on a daemon background thread
        Thread thread = new Thread(task);
        thread.setDaemon(true);
        thread.setName("dashboard-stats-loader");
        thread.start();
    }

    /**
     * Populates the statistics grid with stat cards.
     * Called on the JavaFX Application Thread after background load.
     *
     * @param grid  the grid to populate
     * @param stats the loaded statistics
     */
    private void populateStatsGrid(GridPane grid, DashboardStats stats) {
        grid.getChildren().clear();

        // Row 0: primary stats
        grid.add(buildStatCard("Total Students",  String.valueOf(stats.total),
                "#3b82f6", "#eff6ff"), 0, 0);
        grid.add(buildStatCard("Active",          String.valueOf(stats.active),
                "#22c55e", "#f0fdf4"), 1, 0);
        grid.add(buildStatCard("Graduated",       String.valueOf(stats.graduated),
                "#8b5cf6", "#f5f3ff"), 2, 0);
        grid.add(buildStatCard("Courses",
                String.valueOf(stats.courseCount),
                "#f59e0b", "#fffbeb"), 3, 0);

        // Row 1: secondary stats
        grid.add(buildStatCard("Inactive",        String.valueOf(stats.inactive),
                "#94a3b8", "#f8fafc"), 0, 1);
        grid.add(buildStatCard("Suspended",       String.valueOf(stats.suspended),
                "#ef4444", "#fef2f2"), 1, 1);
        grid.add(buildStatCard("Avg GPA",
                String.format("%.2f", stats.avgGpa),
                "#0ea5e9", "#f0f9ff"), 2, 1);
    }

    /**
     * Builds a single statistic card with a coloured accent border.
     *
     * @param label       the statistic label
     * @param value       the statistic value as a display string
     * @param accentColor CSS colour for the left border accent
     * @param bgColor     CSS background colour for the card
     * @return the assembled card node
     */
    private VBox buildStatCard(String label, String value,
                                String accentColor, String bgColor) {
        VBox card = new VBox(6);
        card.setPrefWidth(180);
        card.setPrefHeight(90);
        card.setPadding(new Insets(16, 16, 16, 20));
        card.setStyle(
                "-fx-background-color: " + bgColor + ";"
                + "-fx-background-radius: 8;"
                + "-fx-border-color: " + accentColor + ";"
                + "-fx-border-width: 0 0 0 4;"
                + "-fx-border-radius: 0 8 8 0;"
                + "-fx-effect: dropshadow(gaussian, rgba(0,0,0,0.08), 4, 0, 0, 2);");

        Label valueLabel = new Label(value);
        valueLabel.setStyle(
                "-fx-font-size: 28px;"
                + "-fx-font-weight: bold;"
                + "-fx-text-fill: " + accentColor + ";");

        Label nameLabel = new Label(label);
        nameLabel.setStyle(
                "-fx-font-size: 12px;"
                + "-fx-text-fill: #64748b;");

        card.getChildren().addAll(valueLabel, nameLabel);
        return card;
    }

    /** Builds a simple error label for the grid. */
    private Label buildErrorLabel(String message) {
        Label lbl = new Label(message);
        lbl.setStyle("-fx-text-fill: #ef4444; -fx-font-size: 13px;");
        return lbl;
    }

    /** Simple data transfer object for background stats loading. */
    private static class DashboardStats {
        int    total, active, inactive, graduated, suspended, courseCount;
        double avgGpa;
    }
}

