package com.nana.sms.util;

import javafx.concurrent.Task;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.Callable;
import java.util.function.Consumer;

/**
 * TaskBuilder â€” Fluent Builder for JavaFX Background Tasks
 *
 * <p>WHY THIS CLASS EXISTS:
 * Creating a {@link Task} requires extending an abstract class for each
 * operation, even simple one-liners. {@code TaskBuilder} wraps any
 * {@link Callable} in a {@code Task} and provides a fluent API for
 * attaching callbacks and submitting to the {@link TaskRunner}.
 *
 * <p>WITHOUT TaskBuilder (Phase 12 style):
 * <pre>
 *     Task&lt;List&lt;Student&gt;&gt; task = new Task&lt;&gt;() {
 *         {@literal @}Override
 *         protected List&lt;Student&gt; call() {
 *             return studentService.getAllStudents();
 *         }
 *     };
 *     task.setOnSucceeded(e -&gt; {
 *         studentData.setAll(task.getValue());
 *         updateStatusBar();
 *     });
 *     task.setOnFailed(e -&gt; {
 *         log.error("Failed.", task.getException());
 *         statusBar.setText("Error: " + task.getException().getMessage());
 *     });
 *     Thread t = new Thread(task);
 *     t.setDaemon(true);
 *     t.start();
 * </pre>
 *
 * <p>WITH TaskBuilder (Phase 13 style):
 * <pre>
 *     TaskBuilder.read(studentService::getAllStudents)
 *                .onSuccess(students -&gt; studentData.setAll(students))
 *                .onFailure(ex -&gt; statusBar.setText("Error: " + ex.getMessage()))
 *                .submit();
 * </pre>
 *
 * <p>THREAD SAFETY:
 * {@code TaskBuilder} instances are single-use and not thread-safe.
 * Build and submit on the JavaFX Application Thread; the work runs
 * on the pool thread.
 *
 * @param <T> the result type of the background operation
 */
public final class TaskBuilder<T> {

    private static final Logger log = LoggerFactory.getLogger(TaskBuilder.class);

    // -----------------------------------------------------------------------
    // FIELDS
    // -----------------------------------------------------------------------

    /** The background work to execute. */
    private final Callable<T> work;

    /** Whether this is a write operation (routes to dbWritePool). */
    private boolean isWrite = false;

    /** Whether to show a progress dialog while running. */
    private boolean showProgress = false;

    /** Title for the progress dialog (if shown). */
    private String progressTitle = "Working...";

    /** Message for the progress dialog (if shown). */
    private String progressMessage = "Please wait.";

    /** Whether the user can cancel the operation. */
    private boolean cancellable = false;

    /** The MDC operation context label for logging. */
    private String operationContext = null;

    /** Success callback â€” runs on JavaFX Application Thread. */
    private Consumer<T> onSuccess;

    /** Failure callback â€” runs on JavaFX Application Thread. */
    private Consumer<Throwable> onFailure;

    /** Progress callback â€” runs on JavaFX Application Thread. */
    private Consumer<TaskRunner.TaskProgress> onProgress;

    /** The owner window for the progress dialog. */
    private javafx.stage.Window ownerWindow;

    // -----------------------------------------------------------------------
    // STATIC FACTORY METHODS
    // -----------------------------------------------------------------------

    /**
     * Creates a {@code TaskBuilder} for a read operation.
     *
     * <p>The task will be submitted to the {@code uiPool} (concurrent reads).
     *
     * @param <T>  the result type
     * @param work the background operation to perform
     * @return a new {@code TaskBuilder} for chaining
     */
    public static <T> TaskBuilder<T> read(Callable<T> work) {
        return new TaskBuilder<>(work, false);
    }

    /**
     * Creates a {@code TaskBuilder} for a write operation.
     *
     * <p>The task will be submitted to the {@code dbWritePool}
     * (single-thread serialised writes).
     *
     * @param <T>  the result type (often {@code Void})
     * @param work the background operation to perform
     * @return a new {@code TaskBuilder} for chaining
     */
    public static <T> TaskBuilder<T> write(Callable<T> work) {
        return new TaskBuilder<>(work, true);
    }

    // -----------------------------------------------------------------------
    // CONSTRUCTOR
    // -----------------------------------------------------------------------

    private TaskBuilder(Callable<T> work, boolean isWrite) {
        if (work == null) {
            throw new IllegalArgumentException("Work callable must not be null.");
        }
        this.work    = work;
        this.isWrite = isWrite;
    }

    // -----------------------------------------------------------------------
    // FLUENT CONFIGURATION METHODS
    // -----------------------------------------------------------------------

    /**
     * Sets the success callback. Runs on the JavaFX Application Thread.
     *
     * @param onSuccess consumer receiving the task result
     * @return this builder for chaining
     */
    public TaskBuilder<T> onSuccess(Consumer<T> onSuccess) {
        this.onSuccess = onSuccess;
        return this;
    }

    /**
     * Sets the failure callback. Runs on the JavaFX Application Thread.
     *
     * <p>If not set, failures are only logged (not silently swallowed â€”
     * the {@link TaskRunner} always logs failures at ERROR level).
     *
     * @param onFailure consumer receiving the exception
     * @return this builder for chaining
     */
    public TaskBuilder<T> onFailure(Consumer<Throwable> onFailure) {
        this.onFailure = onFailure;
        return this;
    }

    /**
     * Sets the progress callback. Runs on the JavaFX Application Thread.
     *
     * <p>Only receives updates when the task calls
     * {@code updateProgress()} or {@code updateMessage()}.
     *
     * @param onProgress consumer receiving {@link TaskRunner.TaskProgress} objects
     * @return this builder for chaining
     */
    public TaskBuilder<T> onProgress(
            Consumer<TaskRunner.TaskProgress> onProgress) {
        this.onProgress = onProgress;
        return this;
    }

    /**
     * Configures the task to show a modal {@link ProgressDialog} while running.
     *
     * @param title    the dialog title
     * @param message  the dialog body message
     * @param owner    the parent window to centre the dialog over
     * @return this builder for chaining
     */
    public TaskBuilder<T> withProgressDialog(String title,
                                              String message,
                                              javafx.stage.Window owner) {
        this.showProgress    = true;
        this.progressTitle   = title;
        this.progressMessage = message;
        this.ownerWindow     = owner;
        return this;
    }

    /**
     * Makes the progress dialog cancellable (shows a Cancel button).
     * Only meaningful when {@link #withProgressDialog} is also called.
     *
     * @return this builder for chaining
     */
    public TaskBuilder<T> cancellable() {
        this.cancellable = true;
        return this;
    }

    /**
     * Sets the MDC operation context label for log correlation.
     *
     * <p>All log messages from the background thread will include this
     * label while the task runs. The label is cleared automatically when
     * the task completes.
     *
     * @param context the operation name (e.g., "STUDENT_SAVE")
     * @return this builder for chaining
     */
    public TaskBuilder<T> withContext(String context) {
        this.operationContext = context;
        return this;
    }

    // -----------------------------------------------------------------------
    // SUBMIT
    // -----------------------------------------------------------------------

    /**
     * Builds the {@link Task}, wires callbacks, and submits it to
     * the appropriate {@link TaskRunner} pool.
     *
     * <p>This is the terminal method in the fluent chain. After calling
     * {@code submit()}, the builder should be discarded â€” it is single-use.
     *
     * <p>If {@link #withProgressDialog} was configured, the progress dialog
     * is shown immediately. It closes automatically when the task succeeds,
     * fails, or is cancelled.
     *
     * @return the created {@link Task} (for cancellation support if needed)
     */
    public Task<T> submit() {
        String context = operationContext;
        Callable<T> wrappedWork = () -> {
            // Set MDC context on the worker thread
            if (context != null) {
                AppLogger.setOperationContext(context);
            }
            try {
                return work.call();
            } finally {
                // Always clear MDC â€” even on exception
                if (context != null) {
                    AppLogger.clearOperationContext();
                }
            }
        };

        // Build the JavaFX Task wrapping our callable
        Task<T> task = new Task<>() {
            @Override
            protected T call() throws Exception {
                return wrappedWork.call();
            }
        };

        // Wire progress dialog if configured
        ProgressDialog dialog = null;
        if (showProgress) {
            dialog = new ProgressDialog(
                    progressTitle, progressMessage,
                    ownerWindow, cancellable);
            dialog.bind(task);
            dialog.show();
        }

        // Wire success / failure / progress callbacks
        final ProgressDialog finalDialog = dialog;
        Consumer<T> successWrapper = result -> {
            if (finalDialog != null) finalDialog.close();
            if (onSuccess != null) onSuccess.accept(result);
        };
        Consumer<Throwable> failureWrapper = ex -> {
            if (finalDialog != null) finalDialog.close();
            if (onFailure != null) onFailure.accept(ex);
        };

        // Submit to the appropriate pool
        if (onProgress != null) {
            TaskRunner.getInstance().runWithProgress(
                    task, successWrapper, failureWrapper, onProgress);
        } else if (isWrite) {
            TaskRunner.getInstance().runWrite(
                    task, successWrapper, failureWrapper);
        } else {
            TaskRunner.getInstance().runRead(
                    task, successWrapper, failureWrapper);
        }

        log.debug("Task submitted via TaskBuilder: write={}, context={}.",
                isWrite, context);

        return task;
    }
}

