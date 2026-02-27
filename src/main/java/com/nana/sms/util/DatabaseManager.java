package com.nana.sms.util;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

/**
 * DatabaseManager â€” Infrastructure / Utility Layer
 *
 * <p>WHY THIS CLASS EXISTS:
 * Every JDBC-based application needs one authoritative place that owns the
 * database connection lifecycle. Scattering DriverManager.getConnection() calls
 * across repository classes would make connection management unpredictable,
 * leak connections, and make testing painful.
 *
 * <p>RESPONSIBILITIES:
 * <ul>
 *   <li>Determine the correct OS-specific path for the SQLite file.</li>
 *   <li>Create the database file and parent directories if absent.</li>
 *   <li>Open and hold a single shared {@link Connection}.</li>
 *   <li>Execute all DDL (CREATE TABLE) statements on first launch.</li>
 *   <li>Manage schema versioning to support future migrations.</li>
 *   <li>Expose a {@code shutdown()} method for graceful connection close.</li>
 * </ul>
 *
 * <p>ARCHITECTURAL DECISION â€” Singleton vs. Dependency Injection:
 * We use a classic initialization-on-demand singleton here because this is a
 * desktop app with a single user and a single process. In a server-side context
 * we would use a connection pool (HikariCP) and inject it. For this offline-first
 * desktop use case, a singleton connection is correct, simple, and avoids the
 * overhead of a pool.
 *
 * <p>THREAD SAFETY:
 * SQLite in WAL mode supports concurrent reads but serialises writes. Since
 * JavaFX background tasks will use this connection, we enable WAL mode and
 * set a busy timeout so write contention does not throw immediately.
 */
public final class DatabaseManager {

    // SLF4J logger â€” backed by Logback at runtime (configured in Phase 8)
    private static final Logger log = LoggerFactory.getLogger(DatabaseManager.class);

    // -----------------------------------------------------------------------
    // CONSTANTS
    // -----------------------------------------------------------------------

    /**
     * Application subdirectory name inside the OS user-data folder.
     * On Windows this resolves to: C:\Users\<user>\AppData\Roaming\SMS_Plus\
     */
    private static final String APP_DATA_DIR = "SMS_Plus";

    /** SQLite database filename inside the app data directory. */
    private static final String DB_FILE_NAME = "sms_plus.db";

    /**
     * Current schema version. Increment this integer whenever DDL changes
     * are made so the migration block can run upgrade steps automatically.
     */
    private static final int CURRENT_SCHEMA_VERSION = 1;

    /**
     * SQLite PRAGMA: Write-Ahead Logging mode.
     * WAL allows readers to proceed concurrently with a single writer,
     * which prevents UI freezes when a background thread commits data
     * while the TableView is reading.
     */
    private static final String PRAGMA_WAL = "PRAGMA journal_mode=WAL;";

    /**
     * SQLite PRAGMA: Foreign key enforcement.
     * SQLite disables FK constraints by default for legacy compatibility.
     * We must explicitly enable them per connection.
     */
    private static final String PRAGMA_FK = "PRAGMA foreign_keys=ON;";

    /**
     * SQLite PRAGMA: Busy timeout in milliseconds.
     * If the database is locked by another statement, SQLite will retry
     * for up to this duration before throwing SQLITE_BUSY.
     */
    private static final String PRAGMA_BUSY = "PRAGMA busy_timeout=5000;";

    // -----------------------------------------------------------------------
    // SINGLETON INSTANCE
    // -----------------------------------------------------------------------

    /** The one and only DatabaseManager instance for this process. */
    private static DatabaseManager instance;

    /** The single shared JDBC connection to the SQLite file. */
    private Connection connection;

    /** Resolved absolute path to the SQLite database file. */
    private final Path dbPath;

    // -----------------------------------------------------------------------
    // CONSTRUCTOR â€” private to enforce singleton
    // -----------------------------------------------------------------------

    /**
     * Private constructor: resolves the database path, ensures directories
     * exist, opens the connection, configures PRAGMAs, and runs DDL.
     *
     * @throws DatabaseInitException if anything in the startup sequence fails
     */
    private DatabaseManager() {
        // Resolve the platform-appropriate user data directory.
        // APPDATA env var is always set on Windows (points to Roaming folder).
        // We fall back to the user home directory on other OS (dev/test machines).
        String appDataRoot = System.getenv("APPDATA");
        if (appDataRoot == null || appDataRoot.isBlank()) {
            appDataRoot = System.getProperty("user.home");
            log.warn("APPDATA environment variable not found; falling back to user.home: {}", appDataRoot);
        }

        dbPath = Paths.get(appDataRoot, APP_DATA_DIR, DB_FILE_NAME);
        log.info("Database path resolved to: {}", dbPath.toAbsolutePath());

        try {
            initializeDirectory();
            openConnection();
            configurePragmas();
            initializeSchema();
            runMigrations();
        } catch (SQLException | IOException ex) {
            // Wrap checked exceptions in an unchecked runtime exception so
            // callers (e.g., the JavaFX Application start() method) do not
            // need to declare throws clauses throughout the call stack.
            throw new DatabaseInitException(
                    "Failed to initialize the database at: " + dbPath.toAbsolutePath(), ex);
        }
    }

    // -----------------------------------------------------------------------
    // PUBLIC STATIC ACCESS
    // -----------------------------------------------------------------------

    /**
     * Returns the singleton {@code DatabaseManager}, creating it on first call.
     *
     * <p>WHY synchronized: The JavaFX Application thread and the main thread
     * both run early in startup. Without synchronization, two threads could
     * both pass the {@code null} check and create two instances.
     *
     * @return the application-wide DatabaseManager
     * @throws DatabaseInitException if initialization failed
     */
    public static synchronized DatabaseManager getInstance() {
        if (instance == null) {
            instance = new DatabaseManager();
        }
        return instance;
    }

    /**
     * Returns the shared JDBC {@link Connection}.
     *
     * <p>Repositories call this method to obtain the connection for every
     * SQL operation. The connection is kept open for the lifetime of the
     * application â€” this is the correct pattern for SQLite desktop apps.
     *
     * @return the open, configured JDBC connection
     * @throws IllegalStateException if the connection is closed or null
     */
    public Connection getConnection() {
        try {
            if (connection == null || connection.isClosed()) {
                log.warn("Connection was closed or null â€” attempting to reopen.");
                openConnection();
                configurePragmas();
            }
        } catch (SQLException ex) {
            throw new DatabaseInitException("Failed to reopen database connection.", ex);
        }
        return connection;
    }

    /**
     * Gracefully closes the JDBC connection.
     *
     * <p>This must be called from the JavaFX {@code Application.stop()} method
     * to ensure SQLite flushes WAL frames and releases the file lock before
     * the process exits. Skipping this can leave a dangling WAL file.
     */
    public void shutdown() {
        if (connection != null) {
            try {
                if (!connection.isClosed()) {
                    // Checkpoint WAL to merge it back into the main DB file
                    try (Statement st = connection.createStatement()) {
                        st.execute("PRAGMA wal_checkpoint(TRUNCATE);");
                    }
                    connection.close();
                    log.info("Database connection closed successfully.");
                }
            } catch (SQLException ex) {
                // Log but do not re-throw â€” we are shutting down anyway
                log.error("Error closing database connection during shutdown.", ex);
            }
        }
    }

    // -----------------------------------------------------------------------
    // PRIVATE INITIALIZATION METHODS
    // -----------------------------------------------------------------------

    /**
     * Creates the application data directory and all parent directories
     * if they do not already exist.
     *
     * <p>WHY: On a fresh Windows installation the SMS_Plus folder will not
     * exist. SQLite cannot create a file inside a non-existent directory,
     * so we must create it first.
     *
     * @throws IOException if directory creation fails
     */
    private void initializeDirectory() throws IOException {
        Path dir = dbPath.getParent();
        if (!Files.exists(dir)) {
            Files.createDirectories(dir);
            log.info("Created application data directory: {}", dir.toAbsolutePath());
        }
    }

    /**
     * Opens the JDBC connection to the SQLite file.
     *
     * <p>SQLite JDBC creates the database file automatically if it does not
     * exist, which is exactly the offline-first behaviour we want.
     *
     * @throws SQLException if the JDBC driver cannot open the file
     */
    private void openConnection() throws SQLException {
        // The jdbc:sqlite: prefix is recognized by the xerial SQLite driver.
        // We pass the absolute path to avoid any working-directory ambiguity.
        String url = "jdbc:sqlite:" + dbPath.toAbsolutePath();
        connection = DriverManager.getConnection(url);

        // Log the SQLite library version for diagnostics
        DatabaseMetaData meta = connection.getMetaData();
        log.info("Connected to SQLite {} via driver {}",
                meta.getDatabaseProductVersion(),
                meta.getDriverVersion());
    }

    /**
     * Applies SQLite PRAGMA settings to the open connection.
     *
     * <p>PRAGMAs must be set on every new connection because they are not
     * persisted in the database file (except journal_mode which is stored
     * in the WAL header).
     *
     * @throws SQLException if any PRAGMA statement fails
     */
    private void configurePragmas() throws SQLException {
        try (Statement st = connection.createStatement()) {
            st.execute(PRAGMA_WAL);
            st.execute(PRAGMA_FK);
            st.execute(PRAGMA_BUSY);
            log.debug("SQLite PRAGMAs configured: WAL mode, FK enforcement, busy timeout.");
        }
    }

    /**
     * Creates all application tables if they do not already exist.
     *
     * <p>WHY "IF NOT EXISTS": This makes the DDL idempotent â€” safe to run
     * on every startup without destroying existing data.
     *
     * <p>TABLE DESIGN RATIONALE:
     * <ul>
     *   <li>{@code students} â€” core entity; all fields are NOT NULL where
     *       meaningful to enforce data integrity at the DB layer as a
     *       last-resort safety net (primary enforcement is in the service).</li>
     *   <li>{@code schema_version} â€” single-row table that tracks the DDL
     *       version so we can run targeted migration scripts.</li>
     * </ul>
     *
     * @throws SQLException if any DDL statement fails
     */
    private void initializeSchema() throws SQLException {
        log.info("Running schema initialization (CREATE TABLE IF NOT EXISTS)...");

        // Use a single Statement for DDL â€” no user input involved, so
        // prepared statements are not needed here (no injection risk).
        try (Statement st = connection.createStatement()) {

            // ----------------------------------------------------------
            // TABLE: students
            // ----------------------------------------------------------
            st.execute("""
                CREATE TABLE IF NOT EXISTS students (
                    id          INTEGER PRIMARY KEY AUTOINCREMENT,
                    student_id  TEXT    NOT NULL UNIQUE,
                    first_name  TEXT    NOT NULL,
                    last_name   TEXT    NOT NULL,
                    email       TEXT    NOT NULL UNIQUE,
                    phone       TEXT,
                    course      TEXT    NOT NULL,
                    year_level  INTEGER NOT NULL CHECK(year_level BETWEEN 1 AND 6),
                    gpa         REAL    NOT NULL DEFAULT 0.0
                                        CHECK(gpa BETWEEN 0.0 AND 4.0),
                    status      TEXT    NOT NULL DEFAULT 'ACTIVE'
                                        CHECK(status IN ('ACTIVE','INACTIVE','GRADUATED','SUSPENDED')),
                    enrolled_at TEXT    NOT NULL,
                    updated_at  TEXT    NOT NULL
                );
                """);

            // ----------------------------------------------------------
            // TABLE: schema_version
            // Stores a single row with the current schema version integer.
            // ----------------------------------------------------------
            st.execute("""
                CREATE TABLE IF NOT EXISTS schema_version (
                    version     INTEGER NOT NULL
                );
                """);

            log.info("Schema initialization complete.");
        }
    }

    /**
     * Checks the stored schema version and runs any pending migration steps.
     *
     * <p>MIGRATION STRATEGY:
     * On the very first run, {@code schema_version} is empty â€” we insert
     * {@code CURRENT_SCHEMA_VERSION}. On subsequent runs, if the stored
     * version is less than {@code CURRENT_SCHEMA_VERSION}, each migration
     * step from {@code storedVersion+1} to {@code CURRENT_SCHEMA_VERSION}
     * is executed in order. This is a simplified forward-only migration
     * pattern (similar to Flyway's approach but without the dependency).
     *
     * @throws SQLException if reading or writing schema version fails
     */
    private void runMigrations() throws SQLException {
        int storedVersion = getStoredSchemaVersion();

        if (storedVersion == -1) {
            // First ever launch â€” no version row exists yet
            insertSchemaVersion(CURRENT_SCHEMA_VERSION);
            log.info("First launch detected. Schema version set to {}.", CURRENT_SCHEMA_VERSION);
            return;
        }

        if (storedVersion == CURRENT_SCHEMA_VERSION) {
            log.debug("Schema is up to date at version {}.", CURRENT_SCHEMA_VERSION);
            return;
        }

        // Apply migrations sequentially from storedVersion+1 to CURRENT
        log.info("Migrating schema from version {} to {}.", storedVersion, CURRENT_SCHEMA_VERSION);
        for (int v = storedVersion + 1; v <= CURRENT_SCHEMA_VERSION; v++) {
            applyMigration(v);
        }
        updateSchemaVersion(CURRENT_SCHEMA_VERSION);
        log.info("Schema migration complete. Now at version {}.", CURRENT_SCHEMA_VERSION);
    }

    /**
     * Reads the schema version stored in the {@code schema_version} table.
     *
     * @return the stored version integer, or -1 if the table is empty
     * @throws SQLException if the query fails
     */
    private int getStoredSchemaVersion() throws SQLException {
        String sql = "SELECT version FROM schema_version LIMIT 1;";
        try (PreparedStatement ps = connection.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            if (rs.next()) {
                return rs.getInt("version");
            }
            return -1; // Table exists but is empty
        }
    }

    /**
     * Inserts the initial schema version row.
     *
     * @param version the version to record
     * @throws SQLException if the insert fails
     */
    private void insertSchemaVersion(int version) throws SQLException {
        String sql = "INSERT INTO schema_version (version) VALUES (?);";
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setInt(1, version);
            ps.executeUpdate();
        }
    }

    /**
     * Updates the schema version to a new value.
     *
     * @param version the new version to store
     * @throws SQLException if the update fails
     */
    private void updateSchemaVersion(int version) throws SQLException {
        String sql = "UPDATE schema_version SET version = ?;";
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setInt(1, version);
            ps.executeUpdate();
        }
    }

    /**
     * Placeholder for individual migration steps.
     *
     * <p>When you increment {@code CURRENT_SCHEMA_VERSION} in the future,
     * add a {@code case} here with the ALTER TABLE / CREATE INDEX statements
     * needed for that version. Each case must be additive only (no DROP
     * unless you are certain the data is migrated elsewhere first).
     *
     * @param targetVersion the version being applied
     * @throws SQLException if a migration DDL statement fails
     */
    private void applyMigration(int targetVersion) throws SQLException {
        log.info("Applying migration to schema version {}...", targetVersion);
        try (Statement st = connection.createStatement()) {
            switch (targetVersion) {
                // Example future migration:
                // case 2 -> st.execute("ALTER TABLE students ADD COLUMN address TEXT;");
                // case 3 -> st.execute("CREATE INDEX IF NOT EXISTS idx_students_course ON students(course);");
                default -> log.warn("No migration defined for version {}. Skipping.", targetVersion);
            }
        }
    }

    // -----------------------------------------------------------------------
    // INNER EXCEPTION CLASS
    // -----------------------------------------------------------------------

    /**
     * Unchecked exception thrown when database initialization or connectivity
     * fails at a point where recovery is not possible (i.e., the app cannot
     * function without a database connection).
     *
     * <p>WHY unchecked: Database initialization failure is a fatal startup
     * error. Forcing every caller to catch a checked exception would pollute
     * the entire call stack with try/catch blocks for a condition that cannot
     * be meaningfully recovered from in the UI layer.
     */
    public static final class DatabaseInitException extends RuntimeException {

        public DatabaseInitException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}

