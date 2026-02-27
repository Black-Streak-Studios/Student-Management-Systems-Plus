package com.nana.sms.util;

import javafx.application.Platform;
import javafx.concurrent.Task;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressBar;
import javafx.scene.layout.VBox;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.StageStyle;
import javafx.stage.Window;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * ProgressDialog â€” Modal Progress Indicator Dialog
 *
 * <p>WHY THIS CLASS EXISTS:
 * Long-running operations (CSV import of 10,000 rows, report generation
 * over large datasets) need to communicate progress to the user. Without
 * feedback, the user assumes the application has frozen and may close it.
 *
 * <p>This dialog provides:
 * <ul>
 *   <li>A title and message describing the current operation.</li>
 *   <li>A {@link ProgressBar} that shows either determinate progress
 *       (0â€“100%) or an indeterminate animation.</li>
 *   <li>A live status message that updates as the task reports progress.</li>
 *   <li>An optional Cancel button that calls {@link Task#cancel()} to
 *       request cooperative task cancellation.</li>
 * </ul>
 *
 * <p>BINDING:
 * The dialog binds directly to a JavaFX {@link Task}'s observable
 * properties ({@code progressProperty}, {@code messageProperty}).
 * JavaFX delivers property changes on the Application Thread, so the
 * UI updates are automatically thread-safe.
 *
 * <p>COOPERATIVE CANCELLATION:
 * Clicking Cancel calls {@link Task#cancel()} which sets the task's
 * state to CANCELLED and interrupts its thread if it is blocked. The task's
 * {@code call()} method must periodically check {@code isCancelled()} and
 * return early or throw {@code InterruptedException} for cancellation to
 * actually stop the work. For tasks that do not check, cancellation only
 * prevents the {@code onSucceeded} handler from running.
 *
 * <p>LIFECYCLE:
 * <ol>
 *   <li>Create with title, message, owner window, and cancellable flag.</li>
 *   <li>Call {@link #bind(Task)} to attach the task's observable properties.</li>
 *   <li>Call {@link #show()} to display the dialog.</li>
 *   <li>Call {@link #close()} (or let {@link TaskBuilder} close it) when done.</li>
 * </ol>
 */
public final class ProgressDialog {

    private static final Logger log =
            LoggerFactory.getLogger(ProgressDialog.class);

    // -----------------------------------------------------------------------
    // UI COMPONENTS
    // -----------------------------------------------------------------------

    /** The dialog window. */
    private final Stage stage;

    /** Displays operation progress 0â€“100% or animates if indeterminate. */
    private final ProgressBar progressBar;

    /** Live status message from the task's {@code updateMessage()} calls. */
    private final Label statusLabel;

    /** Optional cancel button. */
    private final Button cancelButton;

    // -----------------------------------------------------------------------
    // STATE
    // -----------------------------------------------------------------------

    /** Whether this dialog has been closed (prevents double-close). */
    private boolean closed = false;

    // -----------------------------------------------------------------------
    // CONSTRUCTOR
    // -----------------------------------------------------------------------

    /**
     * Creates a {@code ProgressDialog}.
     *
     * @param title       the dialog window title (e.g., "Importing Students...")
     * @param message     the body message (e.g., "Please wait while the file is processed.")
     * @param owner       the parent window to centre the dialog over
     * @param cancellable if true, shows a Cancel button
     */
    public ProgressDialog(String title,
                           String message,
                           Window owner,
                           boolean cancellable) {
        stage = new Stage(StageStyle.UTILITY);
        stage.setTitle(title);
        stage.initModality(Modality.APPLICATION_MODAL);
        stage.initOwner(owner);
        stage.setResizable(false);

        // Prevent the user from closing via the X button while running
        // (they must use the Cancel button if available)
        stage.setOnCloseRequest(event -> event.consume());

        // --- UI Layout ---
        VBox root = new VBox(14);
        root.setPadding(new Insets(24));
        root.setAlignment(Pos.CENTER);
        root.setPrefWidth(380);
        root.setStyle("-fx-background-color: white;");

        // Title label
        Label titleLabel = new Label(title);
        titleLabel.setStyle(
                "-fx-font-size: 15px; -fx-font-weight: bold; "
                + "-fx-text-fill: #1e293b;");

        // Body message
        Label messageLabel = new Label(message);
        messageLabel.setWrapText(true);
        messageLabel.setStyle(
                "-fx-text-fill: #64748b; -fx-font-size: 12px;");
        messageLabel.setMaxWidth(330);

        // Progress bar â€” starts indeterminate
        progressBar = new ProgressBar(ProgressBar.INDETERMINATE_PROGRESS);
        progressBar.setPrefWidth(330);
        progressBar.setPrefHeight(12);
        progressBar.setStyle(
                "-fx-accent: #3b82f6;");

        // Live status message
        statusLabel = new Label("Initialising...");
        statusLabel.setStyle(
                "-fx-text-fill: #94a3b8; -fx-font-size: 11px;");

        root.getChildren().addAll(titleLabel, messageLabel,
                progressBar, statusLabel);

        // Cancel button (optional)
        cancelButton = new Button("Cancel");
        cancelButton.setStyle(
                "-fx-background-color: #f1f5f9; -fx-text-fill: #ef4444;"
                + "-fx-background-radius: 6; -fx-padding: 6 20;"
                + "-fx-cursor: hand; -fx-font-weight: bold;");
        cancelButton.setVisible(cancellable);
        cancelButton.setManaged(cancellable);
        root.getChildren().add(cancelButton);

        stage.setScene(new Scene(root));
    }

    // -----------------------------------------------------------------------
    // PUBLIC API
    // -----------------------------------------------------------------------

    /**
     * Binds the progress dialog's bar and status label to the given task's
     * observable properties.
     *
     * <p>Must be called before {@link #show()}. Binding is one-way â€”
     * the task updates the dialog, not the reverse.
     *
     * <p>WHY BIND instead of manual listeners:
     * JavaFX's {@link javafx.beans.binding.Bindings} API delivers property
     * changes on the Application Thread automatically. We do not need
     * {@code Platform.runLater()} for the progress bar updates.
     *
     * <p>The Cancel button's action is also wired here so the task reference
     * is captured in the lambda.
     *
     * @param task the task to bind to; must not be null
     */
    public void bind(Task<?> task) {
        // Bind progress bar to task progress (0.0â€“1.0, or -1 for indeterminate)
        progressBar.progressProperty().bind(task.progressProperty());

        // Bind status label to task message
        task.messageProperty().addListener((obs, oldMsg, newMsg) -> {
            if (newMsg != null && !newMsg.isBlank()) {
                statusLabel.setText(newMsg);
            }
        });

        // Wire cancel button to task cancellation
        cancelButton.setOnAction(e -> {
            log.info("User requested task cancellation via ProgressDialog.");
            task.cancel();
            cancelButton.setDisable(true);
            cancelButton.setText("Cancelling...");
            statusLabel.setText("Cancelling â€” please wait...");
        });

        log.debug("ProgressDialog bound to task: {}",
                task.getClass().getSimpleName());
    }

    /**
     * Shows the progress dialog.
     *
     * <p>Unlike {@code showAndWait()}, this method returns immediately â€”
     * the dialog is non-blocking from the caller's perspective. The task
     * runs on a background thread; the dialog updates via property bindings.
     *
     * <p>Must be called on the JavaFX Application Thread.
     */
    public void show() {
        if (!Platform.isFxApplicationThread()) {
            Platform.runLater(this::show);
            return;
        }
        stage.show();
        log.debug("ProgressDialog shown: '{}'.", stage.getTitle());
    }

    /**
     * Closes the progress dialog.
     *
     * <p>Safe to call from any thread â€” if called from a background thread,
     * the close is marshalled to the JavaFX Application Thread via
     * {@link Platform#runLater}.
     *
     * <p>Idempotent â€” calling close() multiple times is safe.
     */
    public void close() {
        if (closed) return;
        closed = true;

        if (Platform.isFxApplicationThread()) {
            stage.close();
            log.debug("ProgressDialog closed: '{}'.", stage.getTitle());
        } else {
            Platform.runLater(() -> {
                stage.close();
                log.debug("ProgressDialog closed (from background thread): '{}'.",
                        stage.getTitle());
            });
        }
    }

    /**
     * Updates the status label text directly (without requiring a task binding).
     *
     * <p>Useful when the overall operation is composed of multiple steps and
     * we want to update the message between task submissions.
     *
     * <p>Safe to call from any thread.
     *
     * @param message the new status message
     */
    public void updateMessage(String message) {
        if (Platform.isFxApplicationThread()) {
            statusLabel.setText(message);
        } else {
            Platform.runLater(() -> statusLabel.setText(message));
        }
    }

    /**
     * Returns the dialog's {@link Stage} for advanced configuration
     * (e.g., setting position before {@link #show()}).
     *
     * @return the underlying stage
     */
    public Stage getStage() {
        return stage;
    }
}

