package com.nana.sms.ui;

import com.nana.sms.util.AppLogger;
import com.nana.sms.util.DatabaseManager;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.awt.Desktop;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * SettingsController â€” Application Settings Screen
 *
 * <p>Provides access to:
 * <ul>
 *   <li>Database file path and location.</li>
 *   <li>Log file path and "Open Log File" button.</li>
 *   <li>Application version and build info.</li>
 *   <li>Database statistics (row counts).</li>
 * </ul>
 */
public class SettingsController {

    private static final Logger log = LoggerFactory.getLogger(SettingsController.class);

    /**
     * Builds the settings screen view.
     *
     * @return the root {@link VBox}
     */
    public VBox buildView() {
        VBox root = new VBox(20);
        root.setPadding(new Insets(10));

        // --- Title ---
        Label title = new Label("Settings");
        title.setStyle(
                "-fx-font-size: 24px; -fx-font-weight: bold; "
                + "-fx-text-fill: #1e293b;");

        // --- Cards ---
        root.getChildren().addAll(
                title,
                buildAboutCard(),
                buildDatabaseCard(),
                buildLogsCard());

        return root;
    }

    // -----------------------------------------------------------------------
    // CARDS
    // -----------------------------------------------------------------------

    /**
     * Builds the "About" information card.
     *
     * @return the card {@link VBox}
     */
    private VBox buildAboutCard() {
        VBox card = buildCard("â„¹ï¸ About");

        addInfoRow(card, "Application",  "Student Management System Plus");
        addInfoRow(card, "Version",      "1.0.0-SNAPSHOT");
        addInfoRow(card, "Java Version", System.getProperty("java.version"));
        addInfoRow(card, "JavaFX",       System.getProperty(
                "javafx.version", "Unknown"));
        addInfoRow(card, "OS",
                System.getProperty("os.name") + " "
                + System.getProperty("os.version"));
        addInfoRow(card, "Architecture",
                System.getProperty("os.arch"));

        return card;
    }

    /**
     * Builds the database information card with path and open-folder button.
     *
     * @return the card {@link VBox}
     */
    private VBox buildDatabaseCard() {
        VBox card = buildCard("\uD83D\uDDC4\uFE0F Database");

        // Resolve DB path using the same logic as DatabaseManager
        String appData = System.getenv("APPDATA");
        if (appData == null) appData = System.getProperty("user.home");
        Path dbPath = Paths.get(appData, "SMS_Plus", "sms_plus.db");

        addInfoRow(card, "Database File", dbPath.toAbsolutePath().toString());
        addInfoRow(card, "File Exists",
                Files.exists(dbPath) ? "Yes" : "No");

        if (Files.exists(dbPath)) {
            try {
                long size = Files.size(dbPath);
                addInfoRow(card, "File Size",
                        String.format("%.1f KB", size / 1024.0));
            } catch (IOException ex) {
                addInfoRow(card, "File Size", "Unknown");
            }
        }

        Button btnOpenFolder = buildActionButton(
                "\uD83D\uDCC2 Open Database Folder", "#0ea5e9");
        btnOpenFolder.setOnAction(e ->
                openInExplorer(dbPath.getParent()));
        card.getChildren().add(btnOpenFolder);

        return card;
    }

    /**
     * Builds the log file information card with open-log button.
     *
     * @return the card {@link VBox}
     */
    private VBox buildLogsCard() {
        VBox card = buildCard("\uD83D\uDCDD Log Files");

        Path logPath = AppLogger.getLogFilePath();
        Path logDir  = AppLogger.getLogDirectoryPath();

        addInfoRow(card, "Log File",    logPath.toAbsolutePath().toString());
        addInfoRow(card, "Log Dir",     logDir.toAbsolutePath().toString());
        addInfoRow(card, "Log Exists",
                Files.exists(logPath) ? "Yes" : "No");

        HBox btnRow = new HBox(10);
        Button btnOpenLog = buildActionButton(
                "\uD83D\uDCC4 Open Log File", "#8b5cf6");
        btnOpenLog.setOnAction(e -> openFile(logPath));

        Button btnOpenLogFolder = buildActionButton(
                "\uD83D\uDCC2 Open Log Folder", "#64748b");
        btnOpenLogFolder.setOnAction(e -> openInExplorer(logDir));

        btnRow.getChildren().addAll(btnOpenLog, btnOpenLogFolder);
        card.getChildren().add(btnRow);

        return card;
    }

    // -----------------------------------------------------------------------
    // HELPERS
    // -----------------------------------------------------------------------

    /**
     * Builds a card container with a title.
     *
     * @param cardTitle the card header title
     * @return the card {@link VBox}
     */
    private VBox buildCard(String cardTitle) {
        VBox card = new VBox(10);
        card.setPadding(new Insets(16));
        card.setStyle(
                "-fx-background-color: white; -fx-background-radius: 8;"
                + "-fx-border-color: #e2e8f0; -fx-border-radius: 8;"
                + "-fx-effect: dropshadow(gaussian, rgba(0,0,0,0.05),"
                + " 4, 0, 0, 2);");

        Label header = new Label(cardTitle);
        header.setStyle(
                "-fx-font-size: 14px; -fx-font-weight: bold; "
                + "-fx-text-fill: #374151;");

        Separator sep = new Separator();
        card.getChildren().addAll(header, sep);
        return card;
    }

    /**
     * Adds a labelled info row to a card.
     *
     * @param card  the card to add to
     * @param label the row label
     * @param value the row value
     */
    private void addInfoRow(VBox card, String label, String value) {
        HBox row = new HBox(16);
        Label lbl = new Label(label + ":");
        lbl.setMinWidth(130);
        lbl.setStyle("-fx-text-fill: #64748b; -fx-font-size: 12px;");

        Label val = new Label(value);
        val.setStyle(
                "-fx-text-fill: #1e293b; -fx-font-size: 12px;"
                + "-fx-font-weight: bold;");
        val.setWrapText(true);

        row.getChildren().addAll(lbl, val);
        card.getChildren().add(row);
    }

    /**
     * Builds a styled action button.
     *
     * @param text  button label
     * @param color button background colour
     * @return the styled {@link Button}
     */
    private Button buildActionButton(String text, String color) {
        Button btn = new Button(text);
        btn.setStyle(
                "-fx-background-color: " + color + ";"
                + "-fx-text-fill: white; -fx-background-radius: 6;"
                + "-fx-padding: 7 14; -fx-cursor: hand;"
                + "-fx-font-size: 12px;");
        return btn;
    }

    /**
     * Opens a file using the OS default application (e.g., Notepad for .txt).
     *
     * @param path the file to open
     */
    private void openFile(Path path) {
        if (!Files.exists(path)) {
            showInfo("File Not Found",
                    "File does not exist yet: " + path);
            return;
        }
        try {
            Desktop.getDesktop().open(path.toFile());
        } catch (IOException ex) {
            log.error("Failed to open file: {}", path, ex);
            showInfo("Cannot Open File", ex.getMessage());
        }
    }

    /**
     * Opens a directory in Windows Explorer.
     *
     * @param dir the directory to open
     */
    private void openInExplorer(Path dir) {
        try {
            if (!Files.exists(dir)) {
                Files.createDirectories(dir);
            }
            Desktop.getDesktop().open(dir.toFile());
        } catch (IOException ex) {
            log.error("Failed to open explorer for: {}", dir, ex);
            showInfo("Cannot Open Folder", ex.getMessage());
        }
    }

    /** Shows an information alert. */
    private void showInfo(String title, String msg) {
        Platform.runLater(() -> {
            Alert alert = new Alert(Alert.AlertType.INFORMATION);
            alert.setTitle(title);
            alert.setHeaderText(null);
            alert.setContentText(msg);
            alert.showAndWait();
        });
    }
}

