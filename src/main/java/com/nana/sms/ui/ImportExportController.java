package com.nana.sms.ui;

import com.nana.sms.service.StudentService;
import com.nana.sms.util.CsvExporter;
import com.nana.sms.util.CsvImporter;
import com.nana.sms.util.ImportReport;
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

/**
 * ImportExportController â€” CSV Import and Export Screen
 *
 * <p>Provides a single screen for both import and export operations.
 * Import shows a file picker, mode selector, and displays the ImportReport
 * on completion. Export shows a file picker and column selection.
 */
public class ImportExportController {

    private static final Logger log =
            LoggerFactory.getLogger(ImportExportController.class);

    private final CsvImporter   csvImporter;
    private final CsvExporter   csvExporter;
    private final StudentService studentService;

    private TextArea  resultArea;
    private Label     statusLabel;

    public ImportExportController(CsvImporter csvImporter,
                                   CsvExporter csvExporter,
                                   StudentService studentService) {
        this.csvImporter   = csvImporter;
        this.csvExporter   = csvExporter;
        this.studentService = studentService;
    }

    /**
     * Builds the Import/Export screen view.
     *
     * @return the root {@link VBox}
     */
    public VBox buildView() {
        VBox root = new VBox(20);
        root.setPadding(new Insets(10));

        // --- Title ---
        Label title = new Label("Import / Export");
        title.setStyle(
                "-fx-font-size: 24px; -fx-font-weight: bold; "
                + "-fx-text-fill: #1e293b;");

        // --- Two-panel layout ---
        HBox panels = new HBox(20);
        panels.setAlignment(Pos.TOP_LEFT);

        panels.getChildren().addAll(
                buildImportPanel(),
                buildExportPanel());

        // --- Result area ---
        Label resultTitle = new Label("Operation Log:");
        resultTitle.setStyle(
                "-fx-font-weight: bold; -fx-text-fill: #374151;");

        resultArea = new TextArea();
        resultArea.setEditable(false);
        resultArea.setPrefHeight(200);
        resultArea.setStyle(
                "-fx-font-family: monospace; -fx-font-size: 12px;"
                + "-fx-background-radius: 6;");
        VBox.setVgrow(resultArea, Priority.ALWAYS);

        statusLabel = new Label("Ready.");
        statusLabel.setStyle(
                "-fx-text-fill: #64748b; -fx-font-size: 12px;");

        root.getChildren().addAll(
                title, panels, resultTitle, resultArea, statusLabel);

        return root;
    }

    // -----------------------------------------------------------------------
    // IMPORT PANEL
    // -----------------------------------------------------------------------

    /**
     * Builds the import panel card.
     *
     * @return the import panel {@link VBox}
     */
    private VBox buildImportPanel() {
        VBox panel = new VBox(12);
        panel.setPrefWidth(380);
        panel.setPadding(new Insets(20));
        panel.setStyle(
                "-fx-background-color: white; -fx-background-radius: 8;"
                + "-fx-border-color: #e2e8f0; -fx-border-radius: 8;");

        Label header = new Label("\uD83D\uDCCB Import Students from CSV");
        header.setStyle(
                "-fx-font-size: 15px; -fx-font-weight: bold; "
                + "-fx-text-fill: #1e293b;");

        Label desc = new Label(
                "Import student records from a CSV file.\n"
                + "The file must include headers: Student ID, First Name,\n"
                + "Last Name, Email, Phone, Course, Year Level, GPA, Status.");
        desc.setWrapText(true);
        desc.setStyle("-fx-text-fill: #64748b; -fx-font-size: 12px;");

        // Import mode
        Label modeLabel = new Label("Import Mode:");
        modeLabel.setStyle("-fx-font-weight: bold; -fx-font-size: 12px;");

        ToggleGroup modeGroup = new ToggleGroup();
        RadioButton appendBtn = new RadioButton(
                "Append â€” add new students (default)");
        appendBtn.setToggleGroup(modeGroup);
        appendBtn.setSelected(true);

        RadioButton replaceBtn = new RadioButton(
                "Replace All â€” delete existing and reimport");
        replaceBtn.setToggleGroup(modeGroup);
        replaceBtn.setStyle("-fx-text-fill: #ef4444;");

        Button btnChooseFile = new Button(
                "\uD83D\uDCC2 Choose CSV File and Import");
        btnChooseFile.setMaxWidth(Double.MAX_VALUE);
        btnChooseFile.setStyle(
                "-fx-background-color: #3b82f6; -fx-text-fill: white;"
                + "-fx-background-radius: 6; -fx-padding: 10 16;"
                + "-fx-cursor: hand; -fx-font-size: 13px;");

        btnChooseFile.setOnAction(e -> {
            boolean replaceMode = replaceBtn.isSelected();
            handleImport(btnChooseFile.getScene().getWindow(), replaceMode);
        });

        panel.getChildren().addAll(
                header, desc,
                new Separator(),
                modeLabel, appendBtn, replaceBtn,
                btnChooseFile);

        return panel;
    }

    // -----------------------------------------------------------------------
    // EXPORT PANEL
    // -----------------------------------------------------------------------

    /**
     * Builds the export panel card.
     *
     * @return the export panel {@link VBox}
     */
    private VBox buildExportPanel() {
        VBox panel = new VBox(12);
        panel.setPrefWidth(380);
        panel.setPadding(new Insets(20));
        panel.setStyle(
                "-fx-background-color: white; -fx-background-radius: 8;"
                + "-fx-border-color: #e2e8f0; -fx-border-radius: 8;");

        Label header = new Label("\uD83D\uDCBE Export Students to CSV");
        header.setStyle(
                "-fx-font-size: 15px; -fx-font-weight: bold; "
                + "-fx-text-fill: #1e293b;");

        Label desc = new Label(
                "Export all student records to a CSV file.\n"
                + "The file will be UTF-8 encoded with a BOM\n"
                + "for compatibility with Microsoft Excel.");
        desc.setWrapText(true);
        desc.setStyle("-fx-text-fill: #64748b; -fx-font-size: 12px;");

        Button btnExportAll = new Button(
                "\uD83D\uDCE4 Export All Students (Default Columns)");
        btnExportAll.setMaxWidth(Double.MAX_VALUE);
        btnExportAll.setStyle(
                "-fx-background-color: #22c55e; -fx-text-fill: white;"
                + "-fx-background-radius: 6; -fx-padding: 10 16;"
                + "-fx-cursor: hand; -fx-font-size: 13px;");

        Button btnExportImportable = new Button(
                "\uD83D\uDD04 Export Import-Compatible Format");
        btnExportImportable.setMaxWidth(Double.MAX_VALUE);
        btnExportImportable.setStyle(
                "-fx-background-color: #8b5cf6; -fx-text-fill: white;"
                + "-fx-background-radius: 6; -fx-padding: 10 16;"
                + "-fx-cursor: hand; -fx-font-size: 13px;");

        btnExportAll.setOnAction(e ->
                handleExport(btnExportAll.getScene().getWindow(), false));
        btnExportImportable.setOnAction(e ->
                handleExport(btnExportImportable.getScene().getWindow(), true));

        panel.getChildren().addAll(
                header, desc,
                new Separator(),
                btnExportAll,
                btnExportImportable);

        return panel;
    }

    // -----------------------------------------------------------------------
    // IMPORT HANDLER
    // -----------------------------------------------------------------------

    /**
     * Shows a file chooser and imports the chosen CSV file on a background thread.
     *
     * @param owner       the parent window
     * @param replaceAll  true for REPLACE_ALL mode, false for APPEND
     */
    private void handleImport(javafx.stage.Window owner, boolean replaceAll) {
        if (replaceAll) {
            Alert confirm = new Alert(Alert.AlertType.CONFIRMATION);
            confirm.setTitle("Confirm Replace All");
            confirm.setHeaderText("Delete ALL existing students?");
            confirm.setContentText(
                    "This will permanently delete all existing student records\n"
                    + "before importing. This cannot be undone.\n\n"
                    + "Are you sure you want to continue?");
            confirm.getButtonTypes().setAll(
                    new ButtonType("Yes, Replace All",
                            ButtonBar.ButtonData.YES),
                    ButtonType.CANCEL);
            var result = confirm.showAndWait();
            if (result.isEmpty() ||
                    result.get().getButtonData() != ButtonBar.ButtonData.YES) {
                return;
            }
        }

        FileChooser chooser = new FileChooser();
        chooser.setTitle("Select CSV Import File");
        chooser.getExtensionFilters().add(
                new FileChooser.ExtensionFilter("CSV Files", "*.csv", "*.txt"));

        File file = chooser.showOpenDialog(owner);
        if (file == null) return;

        Path csvPath = file.toPath();
        CsvImporter.ImportMode mode = replaceAll
                ? CsvImporter.ImportMode.REPLACE_ALL
                : CsvImporter.ImportMode.APPEND;

        statusLabel.setText("Importing from: " + file.getName() + "...");
        resultArea.clear();

        Task<ImportReport> task = new Task<>() {
            @Override
            protected ImportReport call() {
                return csvImporter.importFile(csvPath, mode);
            }
        };

        task.setOnSucceeded(e -> {
            ImportReport report = task.getValue();
            displayImportReport(report);
        });

        task.setOnFailed(e -> {
            log.error("Import task failed.", task.getException());
            appendResult("ERROR: " + task.getException().getMessage());
            statusLabel.setText("Import failed.");
        });

        Thread thread = new Thread(task);
        thread.setDaemon(true);
        thread.setName("csv-import");
        thread.start();
    }

    /**
     * Displays the import report in the result area.
     *
     * @param report the completed import report
     */
    private void displayImportReport(ImportReport report) {
        Platform.runLater(() -> {
            resultArea.setText(report.toReportText());
            statusLabel.setText(report.getSummary());

            if (report.isFullSuccess()) {
                statusLabel.setStyle(
                        "-fx-text-fill: #22c55e; -fx-font-size: 12px;"
                        + "-fx-font-weight: bold;");
            } else if (report.isFullFailure()) {
                statusLabel.setStyle(
                        "-fx-text-fill: #ef4444; -fx-font-size: 12px;"
                        + "-fx-font-weight: bold;");
            } else {
                statusLabel.setStyle(
                        "-fx-text-fill: #f59e0b; -fx-font-size: 12px;"
                        + "-fx-font-weight: bold;");
            }
        });
    }

    // -----------------------------------------------------------------------
    // EXPORT HANDLER
    // -----------------------------------------------------------------------

    /**
     * Loads all students and exports to a user-chosen CSV file.
     *
     * @param owner      the parent window
     * @param importable true for import-compatible column set
     */
    private void handleExport(javafx.stage.Window owner, boolean importable) {
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Save CSV Export File");
        chooser.setInitialFileName(importable
                ? "students_import_format.csv" : "students_export.csv");
        chooser.getExtensionFilters().add(
                new FileChooser.ExtensionFilter("CSV Files", "*.csv"));

        File file = chooser.showSaveDialog(owner);
        if (file == null) return;

        Path outputPath = file.toPath();
        statusLabel.setText("Exporting...");

        Task<CsvExporter.ExportResult> task = new Task<>() {
            @Override
            protected CsvExporter.ExportResult call() {
                var students = studentService.getAllStudents();
                if (importable) {
                    return csvExporter.exportForImport(students, outputPath);
                }
                return csvExporter.exportAll(students, outputPath);
            }
        };

        task.setOnSucceeded(e -> {
            CsvExporter.ExportResult result = task.getValue();
            String msg = result.getSummary();
            appendResult(msg);
            statusLabel.setText(msg);
            log.info("Export complete: {}", result);
        });

        task.setOnFailed(e -> {
            log.error("Export task failed.", task.getException());
            appendResult("EXPORT ERROR: " + task.getException().getMessage());
            statusLabel.setText("Export failed.");
        });

        Thread thread = new Thread(task);
        thread.setDaemon(true);
        thread.setName("csv-export");
        thread.start();
    }

    /** Appends a line to the result text area on the JavaFX thread. */
    private void appendResult(String line) {
        Platform.runLater(() ->
                resultArea.appendText(line + "\n"));
    }
}

