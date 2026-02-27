package com.nana.sms.util;

import javafx.application.Platform;
import javafx.scene.control.Alert;
import javafx.scene.control.Label;
import javafx.scene.control.TextArea;
import javafx.scene.layout.VBox;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.lang.Thread.UncaughtExceptionHandler;

/**
 * GlobalExceptionHandler - Application-Wide Uncaught Exception Handler
 */
public final class GlobalExceptionHandler implements UncaughtExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    public static void install() {
        GlobalExceptionHandler handler = new GlobalExceptionHandler();
        Thread.setDefaultUncaughtExceptionHandler(handler);
        Thread.currentThread().setUncaughtExceptionHandler(handler);
        log.info("GlobalExceptionHandler installed on all threads.");
    }

    @Override
    public void uncaughtException(Thread thread, Throwable throwable) {
        log.error("UNCAUGHT EXCEPTION on thread \'{}\': {}", thread.getName(), throwable.getMessage(), throwable);
        AppLogger.logErrorEvent("UNCAUGHT_EXCEPTION",
                "thread=" + thread.getName() + ", exception=" + throwable.getClass().getSimpleName(),
                throwable);
        if (Platform.isFxApplicationThread()) {
            showCrashDialog(thread, throwable);
        } else {
            Platform.runLater(() -> showCrashDialog(thread, throwable));
        }
    }

    private void showCrashDialog(Thread thread, Throwable throwable) {
        try {
            Alert alert = new Alert(Alert.AlertType.ERROR);
            alert.setTitle("Unexpected Error");
            alert.setHeaderText("An unexpected error has occurred.");
            String userMessage = buildUserMessage(throwable);
            Label messageLabel = new Label(userMessage);
            messageLabel.setWrapText(true);
            messageLabel.setMaxWidth(480);
            messageLabel.setStyle("-fx-font-size: 13px;");
            String stackTrace = getStackTrace(throwable);
            Label techLabel = new Label("Technical Details (for bug reports):");
            techLabel.setStyle("-fx-font-weight: bold; -fx-font-size: 12px; -fx-padding: 10 0 4 0;");
            TextArea traceArea = new TextArea("Thread: " + thread.getName() + "\n\n" + stackTrace);
            traceArea.setEditable(false);
            traceArea.setWrapText(false);
            traceArea.setStyle("-fx-font-family: monospace; -fx-font-size: 11px;");
            traceArea.setPrefHeight(200);
            Label logLabel = new Label("Full log file: " + AppLogger.getLogFilePath());
            logLabel.setStyle("-fx-text-fill: #64748b; -fx-font-size: 11px; -fx-padding: 4 0 0 0;");
            VBox content = new VBox(8, messageLabel, techLabel, traceArea, logLabel);
            content.setPrefWidth(500);
            alert.getDialogPane().setContent(content);
            alert.getDialogPane().setPrefWidth(540);
            alert.showAndWait();
        } catch (Exception dialogEx) {
            log.error("Failed to show crash dialog.", dialogEx);
        }
    }

    private String buildUserMessage(Throwable throwable) {
        if (throwable instanceof OutOfMemoryError) {
            return "The application ran out of memory. Please restart the application.";
        }
        if (throwable instanceof java.sql.SQLException) {
            return "A database operation failed:\n" + throwable.getMessage()
                    + "\n\nYour data has not been lost. Please try the operation again.";
        }
        String message = throwable.getMessage();
        if (message == null || message.isBlank()) {
            message = throwable.getClass().getSimpleName();
        }
        return "An unexpected error occurred:\n" + message
                + "\n\nThe application may still be usable. Please restart if problems persist.";
    }

    private String getStackTrace(Throwable throwable) {
        StringWriter sw = new StringWriter();
        throwable.printStackTrace(new PrintWriter(sw));
        return sw.toString();
    }
}

