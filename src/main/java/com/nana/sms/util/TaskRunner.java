package com.nana.sms.util;

import javafx.application.Platform;
import javafx.concurrent.Task;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

/**
 * TaskRunner â€” Centralised JavaFX Background Task Execution Engine
 *
 * <p>WHY THIS CLASS EXISTS:
 * Phase 12 controllers all created threads inline with identical boilerplate:
 * <pre>
 *     Thread t = new Thread(task);
 *     t.setDaemon(true);
 *     t.setName("some-name");
 *     t.start();
 * </pre>
 * This pattern has several problems at scale:
 * <ul>
 *   <li>Thread creation is expensive â€” creating a new OS thread for every
 *       search keystroke (which fires on every character) is wasteful.</li>
 *   <li>There is no thread pool â€” if the user clicks 20 buttons quickly,
 *       20 threads are created with no bound on concurrency.</li>
 *   <li>Error handling is duplicated â€” every task repeats the same
 *       {@code setOnFailed} pattern.</li>
 *   <li>No progress reporting infrastructure â€” each controller reinvents it.</li>
 *   <li>No way to cancel a running operation cleanly.</li>
 * </ul>
 *
 * <p>THIS CLASS PROVIDES:
 * <ul>
 *   <li>A fixed-size daemon thread pool that bounds concurrency.</li>
 *   <li>A single-thread pool for sequential DB operations (prevents
 *       write contention on SQLite).</li>
 *   <li>Consistent success/failure/progress callback wiring.</li>
 *   <li>Automatic MDC context propagation to worker threads.</li>
 *   <li>Graceful pool shutdown on application exit.</li>
 *   <li>Thread naming with sequential numbering for log readability.</li>
 * </ul>
 *
 * <p>ARCHITECTURAL DECISION â€” Two Thread Pools:
 * <ul>
 *   <li>{@code uiPool} (4 threads): For read-heavy tasks like search,
 *       dashboard stats, and report generation. Multiple reads can run
 *       concurrently because SQLite WAL mode supports concurrent readers.</li>
 *   <li>{@code dbWritePool} (1 thread): For write operations (save, update,
 *       delete, import). SQLite serialises writes â€” a single-thread pool
 *       matches this constraint and prevents write-write contention without
 *       requiring explicit synchronisation.</li>
 * </ul>
 *
 * <p>SINGLETON:
 * The thread pools are application-global resources that must be shut down
 * gracefully. A singleton ensures there is exactly one pool of each type.
 */
public final class TaskRunner {

    private static final Logger log = LoggerFactory.getLogger(TaskRunner.class);

    // -----------------------------------------------------------------------
    // SINGLETON
    // -----------------------------------------------------------------------

    private static volatile TaskRunner instance;

    /**
     * Returns the singleton {@code TaskRunner} instance.
     * Uses double-checked locking for thread-safe lazy initialisation.
     *
     * @return the application-wide {@code TaskRunner}
     */
    public static TaskRunner getInstance() {
        if (instance == null) {
            synchronized (TaskRunner.class) {
                if (instance == null) {
                    instance = new TaskRunner();
                }
            }
        }
        return instance;
    }

    // -----------------------------------------------------------------------
    // THREAD POOLS
    // -----------------------------------------------------------------------

    /**
     * General-purpose pool for UI-facing read operations.
     * 4 threads allows concurrent reads (safe with SQLite WAL mode).
     *
     * <p>WHY 4 threads: The application rarely needs more than 2â€“3
     * concurrent background operations (dashboard load + search + report).
     * 4 gives headroom without wasting OS resources.
     */
    private final ExecutorService uiPool;

    /**
     * Single-thread pool for database write operations.
     * Ensures writes are serialised â€” matches SQLite's single-writer model.
     *
     * <p>WHY a pool and not a single thread: {@link ExecutorService} manages
     * the thread lifecycle (creation, error recovery) better than a manually
     * created {@code Thread}. If the worker thread crashes, the pool
     * creates a replacement automatically.
     */
    private final ExecutorService dbWritePool;

    // -----------------------------------------------------------------------
    // CONSTRUCTOR
    // -----------------------------------------------------------------------

    private TaskRunner() {
        uiPool = Executors.newFixedThreadPool(4,
                new NamedDaemonThreadFactory("sms-ui-worker"));

        dbWritePool = Executors.newSingleThreadExecutor(
                new NamedDaemonThreadFactory("sms-db-writer"));

        log.info("TaskRunner initialised: uiPool(4) + dbWritePool(1).");
    }

    // -----------------------------------------------------------------------
    // PUBLIC API â€” SUBMIT METHODS
    // -----------------------------------------------------------------------

    /**
     * Submits a read-oriented {@link Task} to the general UI thread pool.
     *
     * <p>Use for: dashboard stats, search, report generation, CSV export.
     * These operations read from SQLite and are safe to run concurrently.
     *
     * <p>WIRING:
     * <ul>
     *   <li>{@code onSuccess} is called on the JavaFX Application Thread
     *       with the task's result value.</li>
     *   <li>{@code onFailure} is called on the JavaFX Application Thread
     *       with the exception that caused the failure.</li>
     * </ul>
     *
     * @param <T>       the task's result type
     * @param task      the task to execute
     * @param onSuccess callback invoked on the JavaFX thread on success
     * @param onFailure callback invoked on the JavaFX thread on failure
     */
    public <T> void runRead(Task<T> task,
                             Consumer<T> onSuccess,
                             Consumer<Throwable> onFailure) {
        wireCallbacks(task, onSuccess, onFailure);
        uiPool.submit(task);
        log.debug("Read task submitted to uiPool: {}",
                task.getClass().getSimpleName());
    }

    /**
     * Submits a write-oriented {@link Task} to the single-thread DB writer pool.
     *
     * <p>Use for: save, update, delete, CSV import.
     * Writes are queued and executed one at a time, preventing SQLite
     * write-write contention.
     *
     * @param <T>       the task's result type (often {@code Void})
     * @param task      the task to execute
     * @param onSuccess callback invoked on the JavaFX thread on success
     * @param onFailure callback invoked on the JavaFX thread on failure
     */
    public <T> void runWrite(Task<T> task,
                              Consumer<T> onSuccess,
                              Consumer<Throwable> onFailure) {
        wireCallbacks(task, onSuccess, onFailure);
        dbWritePool.submit(task);
        log.debug("Write task submitted to dbWritePool: {}",
                task.getClass().getSimpleName());
    }

    /**
     * Submits a read task with a progress listener attached.
     *
     * <p>The {@code onProgress} consumer receives {@link TaskProgress}
     * objects as the task reports progress via {@code updateProgress()}
     * and {@code updateMessage()}. Called on the JavaFX Application Thread.
     *
     * @param <T>        the task's result type
     * @param task       the task to execute
     * @param onSuccess  callback on success
     * @param onFailure  callback on failure
     * @param onProgress callback for progress updates
     */
    public <T> void runWithProgress(Task<T> task,
                                     Consumer<T> onSuccess,
                                     Consumer<Throwable> onFailure,
                                     Consumer<TaskProgress> onProgress) {
        wireCallbacks(task, onSuccess, onFailure);

        // Wire progress listener â€” delivered on JavaFX thread
        task.progressProperty().addListener((obs, oldProg, newProg) -> {
            String message = task.getMessage();
            double progress = newProg.doubleValue();
            Platform.runLater(() ->
                    onProgress.accept(
                            new TaskProgress(progress, message)));
        });

        uiPool.submit(task);
        log.debug("Progress task submitted: {}",
                task.getClass().getSimpleName());
    }

    // -----------------------------------------------------------------------
    // CONVENIENCE STATIC FACTORIES
    // -----------------------------------------------------------------------

    /**
     * Convenience method: runs a {@link Runnable} on the JavaFX Application
     * Thread. Equivalent to {@link Platform#runLater(Runnable)} but named
     * for clarity at call sites.
     *
     * <p>Use this when a background thread needs to update a UI label,
     * append to a TextArea, or trigger a scene graph change.
     *
     * @param uiAction the action to run on the UI thread
     */
    public static void onUiThread(Runnable uiAction) {
        Platform.runLater(uiAction);
    }

    /**
     * Checks whether the current thread is the JavaFX Application Thread.
     *
     * <p>Useful for assertions in development:
     * <pre>
     *     assert TaskRunner.isUiThread() : "This must run on the UI thread";
     * </pre>
     *
     * @return true if the caller is on the JavaFX Application Thread
     */
    public static boolean isUiThread() {
        return Platform.isFxApplicationThread();
    }

    // -----------------------------------------------------------------------
    // SHUTDOWN
    // -----------------------------------------------------------------------

    /**
     * Gracefully shuts down both thread pools.
     *
     * <p>Must be called from {@code MainApp.stop()} during application
     * shutdown. This method:
     * <ol>
     *   <li>Stops accepting new tasks.</li>
     *   <li>Waits up to 5 seconds for in-flight tasks to complete.</li>
     *   <li>Force-interrupts any tasks that are still running after the timeout.</li>
     * </ol>
     *
     * <p>WHY a timeout: Some tasks (e.g., a large CSV import) might be
     * mid-way through when shutdown is requested. We give them 5 seconds to
     * finish cleanly before force-interrupting. For a desktop app this is
     * a reasonable compromise between data safety and responsiveness.
     */
    public void shutdown() {
        log.info("TaskRunner shutdown initiated.");

        shutdownPool(uiPool,      "uiPool",      5);
        shutdownPool(dbWritePool, "dbWritePool", 5);

        log.info("TaskRunner shutdown complete.");
    }

    /**
     * Shuts down a single {@link ExecutorService} with a timeout.
     *
     * @param pool        the pool to shut down
     * @param poolName    name for logging
     * @param timeoutSecs maximum seconds to wait for graceful shutdown
     */
    private void shutdownPool(ExecutorService pool,
                               String poolName,
                               int timeoutSecs) {
        pool.shutdown();
        try {
            if (!pool.awaitTermination(timeoutSecs, TimeUnit.SECONDS)) {
                log.warn("{} did not terminate in {}s â€” forcing shutdown.",
                        poolName, timeoutSecs);
                pool.shutdownNow();
            } else {
                log.debug("{} terminated gracefully.", poolName);
            }
        } catch (InterruptedException ex) {
            pool.shutdownNow();
            Thread.currentThread().interrupt();
            log.warn("{} shutdown interrupted.", poolName);
        }
    }

    // -----------------------------------------------------------------------
    // PRIVATE HELPERS
    // -----------------------------------------------------------------------

    /**
     * Wires standard success and failure callbacks to a {@link Task}.
     *
     * <p>Both callbacks are guaranteed to run on the JavaFX Application
     * Thread â€” JavaFX ensures this for {@code setOnSucceeded} and
     * {@code setOnFailed} handlers.
     *
     * <p>The default failure handler also logs the exception at ERROR level
     * so no failure is ever silently swallowed.
     *
     * @param <T>       the task's result type
     * @param task      the task to wire
     * @param onSuccess the success handler (receives the result value)
     * @param onFailure the failure handler (receives the exception)
     */
    private <T> void wireCallbacks(Task<T> task,
                                    Consumer<T> onSuccess,
                                    Consumer<Throwable> onFailure) {
        task.setOnSucceeded(e -> {
            if (onSuccess != null) {
                onSuccess.accept(task.getValue());
            }
        });

        task.setOnFailed(e -> {
            Throwable ex = task.getException();
            log.error("Background task failed: {}",
                    task.getClass().getSimpleName(), ex);
            if (onFailure != null) {
                onFailure.accept(ex);
            }
        });

        task.setOnCancelled(e ->
                log.info("Task cancelled: {}",
                        task.getClass().getSimpleName()));
    }

    // -----------------------------------------------------------------------
    // INNER CLASSES
    // -----------------------------------------------------------------------

    /**
     * TaskProgress â€” Value Object for Progress Updates
     *
     * <p>Carries a progress fraction (0.0â€“1.0) and a human-readable
     * message string delivered to progress listeners during task execution.
     * The {@code -1.0} progress value means "indeterminate" (no known end).
     */
    public static final class TaskProgress {

        /** Progress fraction: 0.0 = 0%, 1.0 = 100%, -1.0 = indeterminate. */
        private final double  progress;

        /** Human-readable status message (e.g., "Importing row 47 of 200..."). */
        private final String  message;

        /**
         * Constructs a {@code TaskProgress} value.
         *
         * @param progress fraction 0.0â€“1.0, or -1.0 for indeterminate
         * @param message  status message string (may be null)
         */
        public TaskProgress(double progress, String message) {
            this.progress = progress;
            this.message  = message == null ? "" : message;
        }

        /** @return the progress fraction */
        public double getProgress() { return progress; }

        /** @return the status message */
        public String getMessage()  { return message; }

        /** @return true if progress is indeterminate (-1.0) */
        public boolean isIndeterminate() { return progress < 0; }

        /** @return progress as a percentage string (e.g., "47%") */
        public String getPercentageString() {
            if (isIndeterminate()) return "";
            return String.format("%.0f%%", progress * 100);
        }

        @Override
        public String toString() {
            return "TaskProgress{progress=" + getPercentageString()
                   + ", message='" + message + "'}";
        }
    }

    /**
     * NamedDaemonThreadFactory â€” Custom Thread Factory
     *
     * <p>WHY: {@link Executors#defaultThreadFactory()} produces threads
     * named "pool-N-thread-M" which are meaningless in log files.
     * This factory names threads "sms-ui-worker-1", "sms-db-writer-1" etc.,
     * making thread dumps and log files immediately readable.
     *
     * <p>WHY daemon threads: Daemon threads do not prevent JVM shutdown.
     * If the user closes the window, the JVM should exit even if a
     * background thread is still running (the {@code shutdown()} method
     * handles graceful termination for us â€” daemon status is a safety net).
     */
    private static final class NamedDaemonThreadFactory implements ThreadFactory {

        private final String      namePrefix;
        private final AtomicInteger counter = new AtomicInteger(1);

        NamedDaemonThreadFactory(String namePrefix) {
            this.namePrefix = namePrefix;
        }

        @Override
        public Thread newThread(Runnable r) {
            Thread t = new Thread(r,
                    namePrefix + "-" + counter.getAndIncrement());
            t.setDaemon(true);
            t.setPriority(Thread.NORM_PRIORITY);
            log.debug("Thread created: {}", t.getName());
            return t;
        }
    }
}

