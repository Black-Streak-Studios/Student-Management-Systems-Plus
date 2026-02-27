package com.nana.sms.util;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * AppLogger â€” Logging Utility
 *
 * <p>WHY THIS CLASS EXISTS:
 * Every class in the application already uses SLF4J directly via:
 * <pre>
 *     private static final Logger log = LoggerFactory.getLogger(MyClass.class);
 * </pre>
 * That pattern is correct and should be used for per-class logging.
 *
 * <p>{@code AppLogger} provides ADDITIONAL utilities that go beyond
 * per-class logging:
 * <ul>
 *   <li>Application startup/shutdown banner logging â€” a clear visual
 *       marker in the log file showing where each session begins and ends.</li>
 *   <li>MDC (Mapped Diagnostic Context) management â€” attaches contextual
 *       key-value pairs (e.g., current operation name) to all log lines
 *       produced within a scope, without modifying every log call.</li>
 *   <li>Structured event logging â€” logs important business events
 *       (student added, import completed) as structured entries with
 *       consistent format for later log parsing or auditing.</li>
 *   <li>Log file path resolution â€” provides the UI's "Open Log File"
 *       button with the correct path to the current log file.</li>
 * </ul>
 *
 * <p>DESIGN DECISION â€” Static Utility Class:
 * {@code AppLogger} is a stateless utility class with only static methods.
 * There is no state to inject or mock, and it is called from application
 * lifecycle points (startup, shutdown) and event handlers. A static class
 * is appropriate here. It is {@code final} with a private constructor to
 * prevent instantiation and subclassing.
 *
 * <p>MDC (Mapped Diagnostic Context):
 * MDC is a thread-local map of key-value pairs that Logback automatically
 * includes in every log line while the map contains values. For example,
 * wrapping a CSV import operation in an MDC context with
 * {@code operation=CSV_IMPORT} makes every log line during that import
 * carry that label â€” making it trivial to grep the log file for all
 * messages related to that specific operation.
 */
public final class AppLogger {

    // -----------------------------------------------------------------------
    // PRIVATE CONSTANTS
    // -----------------------------------------------------------------------

    /** Application-level logger for startup/shutdown banners and events. */
    private static final Logger APP_LOG =
            LoggerFactory.getLogger("com.nana.sms.APP");

    /** Formatter for event log timestamps. */
    private static final DateTimeFormatter EVENT_FORMAT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    /** MDC key for the current operation name. */
    public static final String MDC_OPERATION = "operation";

    /** MDC key for the current user session (for future multi-user support). */
    public static final String MDC_SESSION = "session";

    /** Application version string â€” shown in startup banner. */
    private static final String APP_VERSION = "1.0.0-SNAPSHOT";

    /** Application name â€” shown in startup banner. */
    private static final String APP_NAME = "Student Management System Plus";

    // -----------------------------------------------------------------------
    // PRIVATE CONSTRUCTOR
    // -----------------------------------------------------------------------

    /**
     * Private constructor â€” this class must not be instantiated.
     * All methods are static utilities.
     */
    private AppLogger() {
        throw new UnsupportedOperationException(
                "AppLogger is a static utility class.");
    }

    // -----------------------------------------------------------------------
    // STARTUP / SHUTDOWN BANNERS
    // -----------------------------------------------------------------------

    /**
     * Logs a startup banner to mark the beginning of an application session.
     *
     * <p>WHY A BANNER: When reviewing a log file that spans multiple
     * application launches, it can be hard to tell where one session ends
     * and another begins. A prominent banner makes session boundaries
     * immediately visible.
     *
     * <p>Also logs the Java version and OS info â€” invaluable for diagnosing
     * "works on my machine" issues reported by users on different Windows
     * versions or Java builds.
     */
    public static void logStartup() {
        String separator = "=".repeat(60);
        APP_LOG.info(separator);
        APP_LOG.info("  {} v{}", APP_NAME, APP_VERSION);
        APP_LOG.info("  Starting up â€” {}",
                LocalDateTime.now().format(EVENT_FORMAT));
        APP_LOG.info("  Java:    {} ({})",
                System.getProperty("java.version"),
                System.getProperty("java.vendor"));
        APP_LOG.info("  OS:      {} {} ({})",
                System.getProperty("os.name"),
                System.getProperty("os.version"),
                System.getProperty("os.arch"));
        APP_LOG.info("  User:    {}",
                System.getProperty("user.name"));
        APP_LOG.info("  AppData: {}",
                System.getenv("APPDATA") != null
                        ? System.getenv("APPDATA") : System.getProperty("user.home"));
        APP_LOG.info(separator);
    }

    /**
     * Logs a shutdown banner to mark the end of an application session.
     *
     * <p>Includes the total session duration in a human-readable format,
     * calculated from the provided start time.
     *
     * @param startTime the {@link LocalDateTime} when the application started,
     *                  used to compute session duration
     */
    public static void logShutdown(LocalDateTime startTime) {
        String separator = "-".repeat(60);
        long seconds = 0;
        if (startTime != null) {
            seconds = java.time.Duration.between(startTime, LocalDateTime.now())
                    .getSeconds();
        }
        long hours   = seconds / 3600;
        long minutes = (seconds % 3600) / 60;
        long secs    = seconds % 60;

        APP_LOG.info(separator);
        APP_LOG.info("  {} shutting down â€” {}",
                APP_NAME, LocalDateTime.now().format(EVENT_FORMAT));
        APP_LOG.info("  Session duration: {}h {}m {}s", hours, minutes, secs);
        APP_LOG.info(separator);
    }

    // -----------------------------------------------------------------------
    // STRUCTURED EVENT LOGGING
    // -----------------------------------------------------------------------

    /**
     * Logs a significant business event with a consistent structured format.
     *
     * <p>Business events are things like "student added", "report generated",
     * "CSV import completed". They are distinct from debug/trace logs because:
     * <ul>
     *   <li>They represent meaningful state changes in the application.</li>
     *   <li>They should always appear in logs regardless of debug level.</li>
     *   <li>They use a consistent format that can be parsed or monitored.</li>
     * </ul>
     *
     * <p>Format: {@code [EVENT] <eventName> | <details>}
     *
     * @param eventName a short event label (e.g., "STUDENT_ADDED")
     * @param details   additional context (e.g., "studentId=STU-2024-001")
     */
    public static void logEvent(String eventName, String details) {
        APP_LOG.info("[EVENT] {} | {}", eventName, details);
    }

    /**
     * Logs a warning-level business event.
     *
     * <p>Used for events that are not errors but warrant attention,
     * such as an import with partial failures or a report generated
     * with no data.
     *
     * @param eventName a short event label
     * @param details   additional context
     */
    public static void logWarningEvent(String eventName, String details) {
        APP_LOG.warn("[WARN_EVENT] {} | {}", eventName, details);
    }

    /**
     * Logs an error-level business event with an associated exception.
     *
     * <p>Used when a significant operation fails and the failure should
     * be prominently visible in the log file.
     *
     * @param eventName a short event label
     * @param details   additional context describing what was attempted
     * @param throwable the exception that caused the failure
     */
    public static void logErrorEvent(String eventName,
                                      String details,
                                      Throwable throwable) {
        APP_LOG.error("[ERROR_EVENT] {} | {}", eventName, details, throwable);
    }

    // -----------------------------------------------------------------------
    // MDC CONTEXT MANAGEMENT
    // -----------------------------------------------------------------------

    /**
     * Sets the current operation name in the MDC.
     *
     * <p>After calling this, every log line produced on the current thread
     * will include the operation name. Call {@link #clearOperationContext()}
     * when the operation completes.
     *
     * <p>EXAMPLE USAGE:
     * <pre>
     *     AppLogger.setOperationContext("CSV_IMPORT");
     *     try {
     *         // ... import logic ...
     *         // All log lines here will include: operation=CSV_IMPORT
     *     } finally {
     *         AppLogger.clearOperationContext();
     *     }
     * </pre>
     *
     * <p>NOTE: MDC is thread-local. JavaFX background Tasks run on worker
     * threads â€” call this at the start of the Task's {@code call()} method
     * to tag all log output from that task with the operation name.
     *
     * @param operationName the operation label (e.g., "CSV_IMPORT",
     *                      "REPORT_GENERATION", "STUDENT_DELETE")
     */
    public static void setOperationContext(String operationName) {
        MDC.put(MDC_OPERATION, operationName);
    }

    /**
     * Removes the operation name from the MDC.
     *
     * <p>Must be called in a {@code finally} block after
     * {@link #setOperationContext(String)} to prevent the MDC entry
     * from leaking onto future log lines from thread pool reuse.
     */
    public static void clearOperationContext() {
        MDC.remove(MDC_OPERATION);
    }

    /**
     * Sets the session identifier in the MDC.
     *
     * <p>Called once at application startup. Provides a stable identifier
     * that appears on every log line for the lifetime of the session,
     * making it easy to correlate all log output from one run.
     *
     * @param sessionId a unique session identifier (e.g., a UUID or
     *                  a timestamp-based string)
     */
    public static void setSessionContext(String sessionId) {
        MDC.put(MDC_SESSION, sessionId);
    }

    /**
     * Clears all MDC values set by this utility.
     * Called at application shutdown.
     */
    public static void clearAllContext() {
        MDC.remove(MDC_OPERATION);
        MDC.remove(MDC_SESSION);
    }

    // -----------------------------------------------------------------------
    // LOG FILE PATH RESOLUTION
    // -----------------------------------------------------------------------

    /**
     * Returns the resolved {@link Path} of the current active log file.
     *
     * <p>Used by the Settings screen's "Open Log File" button to give
     * users direct access to the log file via the OS file manager.
     *
     * <p>Mirrors the path logic in {@code logback.xml} â€” both resolve
     * the log directory using the {@code APPDATA} environment variable
     * with a fallback to {@code user.home}.
     *
     * @return the absolute {@link Path} to the active log file
     */
    public static Path getLogFilePath() {
        String appDataRoot = System.getenv("APPDATA");
        if (appDataRoot == null || appDataRoot.isBlank()) {
            appDataRoot = System.getProperty("user.home");
        }
        return Paths.get(appDataRoot, "SMS_Plus", "logs", "sms_plus.log");
    }

    /**
     * Returns the resolved {@link Path} of the log directory.
     *
     * <p>Used by the Settings screen to display the log folder path and
     * provide an "Open Log Folder" button.
     *
     * @return the absolute {@link Path} to the log directory
     */
    public static Path getLogDirectoryPath() {
        return getLogFilePath().getParent();
    }

    // -----------------------------------------------------------------------
    // CONVENIENCE FACTORY
    // -----------------------------------------------------------------------

    /**
     * Returns a named SLF4J {@link Logger} for the given class.
     *
     * <p>This is a thin wrapper around {@code LoggerFactory.getLogger()}
     * provided here so callers can use a single import ({@code AppLogger})
     * to access both the utility methods and obtain loggers, if they prefer
     * that style.
     *
     * <p>RECOMMENDED USAGE:
     * Most classes should declare their own logger as a field:
     * <pre>
     *     private static final Logger log =
     *             LoggerFactory.getLogger(MyClass.class);
     * </pre>
     * Use this factory method only when you need a logger in a context
     * where the class reference is inconvenient (e.g., lambda or
     * anonymous class scope).
     *
     * @param clazz the class to create a logger for
     * @return the SLF4J {@link Logger} for the given class
     */
    public static Logger getLogger(Class<?> clazz) {
        return LoggerFactory.getLogger(clazz);
    }

    /**
     * Generates a simple session ID based on the current timestamp.
     *
     * <p>Not cryptographically unique, but unique enough for the purposes
     * of correlating log lines within a single desktop session.
     * Called from the JavaFX {@code Application.start()} method.
     *
     * @return a timestamp-based session ID string
     *         (e.g., "SESSION-20250115-143200")
     */
    public static String generateSessionId() {
        return "SESSION-" + LocalDateTime.now()
                .format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss"));
    }
}

