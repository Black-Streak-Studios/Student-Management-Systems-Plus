package com.nana.sms.ui;

import com.nana.sms.service.ReportData;
import com.nana.sms.service.ReportGenerator;
import com.nana.sms.service.ReportType;
import com.nana.sms.util.CsvExporter;
import javafx.application.Platform;
import javafx.concurrent.Task;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.stage.FileChooser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.nio.file.Path;
import java.util.Map.Entry;

/**
 * ReportsController â€” Reports Screen Controller
 *
 * <p>Allows the user to select a report type, generate it on a background
 * thread, view summary statistics, and export to CSV.
 */
public class ReportsController {

    private static final Logger log = LoggerFactory.getLogger(ReportsController.class);

    private final ReportGenerator reportGenerator;
    private final CsvExporter     csvExporter;

    /** The most recently generated report â€” used for export. */
    private ReportData currentReport;

    /** Labels for displaying report stats. */
    private VBox statsArea;
    private Label reportTitleLabel;
    private Label reportSubtitleLabel;
    private Label statusLabel;
    private Button btnExportCsv;

    public ReportsController(ReportGenerator reportGenerator,
                              CsvExporter csvExporter) {
        this.reportGenerator = reportGenerator;
        this.csvExporter     = csvExporter;
    }

    /**
     * Builds the reports screen view.
     *
     * @return the root {@link VBox}
     */
    public VBox buildView() {
        VBox root = new VBox(16);
        root.setPadding(new Insets(10));

        // --- Title ---
        Label title = new Label("Reports");
        title.setStyle(
                "-fx-font-size: 24px; -fx-font-weight: bold; "
                + "-fx-text-fill: #1e293b;");

        // --- Controls bar ---
        HBox controls = new HBox(12);
        controls.setAlignment(Pos.CENTER_LEFT);

        ComboBox<ReportType> typePicker = new ComboBox<>();
        typePicker.getItems().addAll(ReportType.values());
        typePicker.setValue(ReportType.FULL_ROSTER);
        typePicker.setPrefWidth(250);
        typePicker.setStyle(buildFieldStyle());

        Button btnGenerate = new Button("\uD83D\uDCCA Generate Report");
        btnGenerate.setStyle(
                "-fx-background-color: #3b82f6; -fx-text-fill: white;"
                + "-fx-background-radius: 6; -fx-padding: 8 16;"
                + "-fx-cursor: hand;");

        btnExportCsv = new Button("\uD83D\uDCBE Export to CSV");
        btnExportCsv.setStyle(
                "-fx-background-color: #22c55e; -fx-text-fill: white;"
                + "-fx-background-radius: 6; -fx-padding: 8 16;"
                + "-fx-cursor: hand;");
        btnExportCsv.setDisable(true);

        statusLabel = new Label("Select a report type and click Generate.");
        statusLabel.setStyle("-fx-text-fill: #64748b; -fx-font-size: 12px;");

        controls.getChildren().addAll(
                typePicker, btnGenerate, btnExportCsv, statusLabel);

        // --- Report display area ---
        reportTitleLabel = new Label();
        reportTitleLabel.setStyle(
                "-fx-font-size: 18px; -fx-font-weight: bold; "
                + "-fx-text-fill: #1e293b;");

        reportSubtitleLabel = new Label();
        reportSubtitleLabel.setStyle(
                "-fx-text-fill: #64748b; -fx-font-size: 12px;");

        statsArea = new VBox(8);
        statsArea.setPadding(new Insets(16));
        statsArea.setStyle(
                "-fx-background-color: white; -fx-background-radius: 8;"
                + "-fx-border-color: #e2e8f0; -fx-border-radius: 8;");

        ScrollPane scroll = new ScrollPane(statsArea);
        scroll.setFitToWidth(true);
        scroll.setStyle("-fx-background-color: transparent;");
        VBox.setVgrow(scroll, Priority.ALWAYS);

        // Wire generate button
        btnGenerate.setOnAction(e ->
                generateReport(typePicker.getValue()));

        btnExportCsv.setOnAction(e ->
                exportCurrentReport(
                        btnExportCsv.getScene().getWindow()));

        root.getChildren().addAll(
                title, controls,
                reportTitleLabel, reportSubtitleLabel,
                scroll);

        return root;
    }

    // -----------------------------------------------------------------------
    // REPORT GENERATION
    // -----------------------------------------------------------------------

    /**
     * Generates the selected report on a background thread.
     *
     * @param type the report type to generate
     */
    private void generateReport(ReportType type) {
        if (type == null) return;

        statusLabel.setText("Generating report...");
        statsArea.getChildren().clear();
        statsArea.getChildren().add(new ProgressIndicator());

        Task<ReportData> task = new Task<>() {
            @Override
            protected ReportData call() {
                return reportGenerator.generate(type);
            }
        };

        task.setOnSucceeded(e -> {
            currentReport = task.getValue();
            displayReport(currentReport);
            btnExportCsv.setDisable(false);
            statusLabel.setText(
                    "Generated at " + currentReport.getGeneratedAtFormatted()
                    + " â€” " + currentReport.getStudentCount() + " student(s).");
            log.info("Report generated: {}", currentReport);
        });

        task.setOnFailed(e -> {
            log.error("Report generation failed.", task.getException());
            statusLabel.setText("Report generation failed: "
                    + task.getException().getMessage());
            statsArea.getChildren().setAll(
                    errorLabel("Report generation failed."));
        });

        Thread thread = new Thread(task);
        thread.setDaemon(true);
        thread.setName("report-generator");
        thread.start();
    }

    /**
     * Populates the stats area with the report's summary statistics
     * and course breakdown (if present).
     *
     * @param report the generated report
     */
    private void displayReport(ReportData report) {
        statsArea.getChildren().clear();
        reportTitleLabel.setText(report.getTitle());
        reportSubtitleLabel.setText(report.getSubtitle()
                + "  |  Generated: " + report.getGeneratedAtFormatted());

        // --- Summary stats ---
        Label statsHeader = new Label("Summary Statistics");
        statsHeader.setStyle(
                "-fx-font-weight: bold; -fx-font-size: 14px; "
                + "-fx-text-fill: #374151;");
        statsArea.getChildren().add(statsHeader);

        GridPane statsGrid = new GridPane();
        statsGrid.setVgap(6);
        statsGrid.setHgap(20);
        int row = 0;
        for (Entry<String, String> entry :
                report.getSummaryStats().entrySet()) {
            Label key = new Label(entry.getKey() + ":");
            key.setStyle(
                    "-fx-text-fill: #64748b; -fx-font-size: 13px;");
            Label val = new Label(entry.getValue());
            val.setStyle(
                    "-fx-font-weight: bold; -fx-text-fill: #1e293b;"
                    + "-fx-font-size: 13px;");
            statsGrid.add(key, 0, row);
            statsGrid.add(val, 1, row);
            row++;
        }
        statsArea.getChildren().add(statsGrid);

        // --- Course breakdown (if present) ---
        if (!report.getCourseStats().isEmpty()) {
            Separator sep = new Separator();
            sep.setPadding(new Insets(8, 0, 8, 0));

            Label courseHeader = new Label("Course Breakdown");
            courseHeader.setStyle(
                    "-fx-font-weight: bold; -fx-font-size: 14px; "
                    + "-fx-text-fill: #374151;");

            TableView<ReportData.CourseStatRow> courseTable =
                    buildCourseTable();
            courseTable.getItems().setAll(report.getCourseStats());
            courseTable.setPrefHeight(
                    Math.min(report.getCourseStats().size() * 35 + 35, 300));

            statsArea.getChildren().addAll(sep, courseHeader, courseTable);
        }

        // --- Student count footer ---
        Label footer = new Label("Total students in report: "
                + report.getStudentCount());
        footer.setStyle("-fx-text-fill: #94a3b8; -fx-font-size: 11px;");
        statsArea.getChildren().add(footer);
    }

    /**
     * Builds a small table for course statistics rows.
     *
     * @return a configured {@link TableView}
     */
    @SuppressWarnings("unchecked")
    private TableView<ReportData.CourseStatRow> buildCourseTable() {
        TableView<ReportData.CourseStatRow> table = new TableView<>();
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);

        TableColumn<ReportData.CourseStatRow, String> colCourse =
                new TableColumn<>("Course");
        colCourse.setCellValueFactory(d ->
                new javafx.beans.property.SimpleStringProperty(
                        d.getValue().getCourseName()));

        TableColumn<ReportData.CourseStatRow, String> colCount =
                new TableColumn<>("Students");
        colCount.setCellValueFactory(d ->
                new javafx.beans.property.SimpleStringProperty(
                        String.valueOf(d.getValue().getStudentCount())));
        colCount.setPrefWidth(80);

        TableColumn<ReportData.CourseStatRow, String> colAvg =
                new TableColumn<>("Avg GPA");
        colAvg.setCellValueFactory(d ->
                new javafx.beans.property.SimpleStringProperty(
                        d.getValue().getAverageGpaFormatted()));
        colAvg.setPrefWidth(80);

        TableColumn<ReportData.CourseStatRow, String> colActive =
                new TableColumn<>("Active");
        colActive.setCellValueFactory(d ->
                new javafx.beans.property.SimpleStringProperty(
                        String.valueOf(d.getValue().getActiveCount())));
        colActive.setPrefWidth(70);

        table.getColumns().addAll(colCourse, colCount, colAvg, colActive);
        return table;
    }

    // -----------------------------------------------------------------------
    // CSV EXPORT
    // -----------------------------------------------------------------------

    /**
     * Exports the current report's student list to a CSV file chosen by
     * the user via a file chooser dialog.
     *
     * @param owner the parent window for the file chooser
     */
    private void exportCurrentReport(javafx.stage.Window owner) {
        if (currentReport == null) return;

        FileChooser chooser = new FileChooser();
        chooser.setTitle("Export Report to CSV");
        chooser.setInitialFileName(
                currentReport.getReportType().name() + "_report.csv");
        chooser.getExtensionFilters().add(
                new FileChooser.ExtensionFilter("CSV Files", "*.csv"));

        File file = chooser.showSaveDialog(owner);
        if (file == null) return;

        Task<CsvExporter.ExportResult> task = new Task<>() {
            @Override
            protected CsvExporter.ExportResult call() {
                return csvExporter.exportAll(
                        currentReport.getStudents(), file.toPath());
            }
        };

        task.setOnSucceeded(e -> {
            CsvExporter.ExportResult result = task.getValue();
            if (result.isSuccess()) {
                statusLabel.setText("Exported: " + result.getSummary());
            } else {
                showError("Export Failed", result.getErrorMessage());
            }
        });

        task.setOnFailed(e ->
                showError("Export Error", task.getException().getMessage()));

        Thread thread = new Thread(task);
        thread.setDaemon(true);
        thread.setName("report-csv-export");
        thread.start();
    }

    private Label errorLabel(String msg) {
        Label l = new Label(msg);
        l.setStyle("-fx-text-fill: #ef4444;");
        return l;
    }

    private void showError(String title, String msg) {
        Platform.runLater(() -> {
            Alert alert = new Alert(Alert.AlertType.ERROR);
            alert.setTitle(title);
            alert.setContentText(msg);
            alert.showAndWait();
        });
    }

    private String buildFieldStyle() {
        return "-fx-background-radius: 6; -fx-border-radius: 6;"
                + "-fx-border-color: #e2e8f0; -fx-border-width: 1;"
                + "-fx-padding: 6 10;";
    }

    // Needed for stats display
    private static class Map {
        static <K,V> java.util.Set<java.util.Map.Entry<K,V>> entrySet(
                java.util.Map<K,V> map) {
            return map.entrySet();
        }
    }
}

