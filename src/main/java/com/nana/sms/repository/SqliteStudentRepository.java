package com.nana.sms.repository;

import com.nana.sms.domain.Student;
import com.nana.sms.domain.StudentStatus;
import com.nana.sms.util.DatabaseManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

/**
 * SqliteStudentRepository â€” Repository Layer Concrete Implementation
 *
 * <p>WHY THIS CLASS EXISTS:
 * This class is the single authoritative source of all SQL in the application.
 * It implements {@link StudentRepository} by translating each interface method
 * into one or more JDBC operations against the SQLite database managed by
 * {@link DatabaseManager}.
 *
 * <p>ARCHITECTURAL RULES ENFORCED HERE:
 * <ul>
 *   <li>ALL SQL lives in this class â€” nowhere else in the application.</li>
 *   <li>ALL SQL uses prepared statements â€” no string concatenation ever.</li>
 *   <li>ALL {@link SQLException} instances are caught here and wrapped in
 *       {@link StudentRepository.RepositoryException} before propagating.</li>
 *   <li>NO business logic exists here â€” only data access.</li>
 *   <li>NO validation exists here â€” only persistence.</li>
 * </ul>
 *
 * <p>ROW MAPPER PATTERN:
 * The private {@link #mapRow(ResultSet)} method centralises the translation
 * from a JDBC {@link ResultSet} row to a {@link Student} object. This means
 * if the schema changes (e.g., a new column is added), only {@code mapRow}
 * needs updating rather than every query method.
 *
 * <p>CONNECTION MANAGEMENT:
 * This class obtains its {@link Connection} from {@link DatabaseManager} on
 * every method call rather than storing it as a field. This is intentional â€”
 * {@code DatabaseManager.getConnection()} handles reconnection if the
 * connection was somehow closed, making this class resilient to transient
 * connection failures.
 */
public class SqliteStudentRepository implements StudentRepository {

    private static final Logger log = LoggerFactory.getLogger(SqliteStudentRepository.class);

    // -----------------------------------------------------------------------
    // SQL CONSTANTS
    // -----------------------------------------------------------------------
    // All SQL is defined as named constants at the top of the class.
    // WHY: This makes SQL auditable in one place, keeps method bodies clean,
    // and makes it obvious at a glance that no SQL is constructed dynamically.
    // -----------------------------------------------------------------------

    private static final String SQL_INSERT = """
            INSERT INTO students
                (student_id, first_name, last_name, email, phone,
                 course, year_level, gpa, status, enrolled_at, updated_at)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            """;

    private static final String SQL_FIND_ALL = """
            SELECT id, student_id, first_name, last_name, email, phone,
                   course, year_level, gpa, status, enrolled_at, updated_at
            FROM students
            ORDER BY last_name ASC, first_name ASC
            """;

    private static final String SQL_FIND_BY_ID = """
            SELECT id, student_id, first_name, last_name, email, phone,
                   course, year_level, gpa, status, enrolled_at, updated_at
            FROM students
            WHERE id = ?
            """;

    private static final String SQL_FIND_BY_STUDENT_ID = """
            SELECT id, student_id, first_name, last_name, email, phone,
                   course, year_level, gpa, status, enrolled_at, updated_at
            FROM students
            WHERE student_id = ?
            """;

    private static final String SQL_FIND_BY_EMAIL = """
            SELECT id, student_id, first_name, last_name, email, phone,
                   course, year_level, gpa, status, enrolled_at, updated_at
            FROM students
            WHERE LOWER(email) = LOWER(?)
            """;

    private static final String SQL_SEARCH = """
            SELECT id, student_id, first_name, last_name, email, phone,
                   course, year_level, gpa, status, enrolled_at, updated_at
            FROM students
            WHERE LOWER(first_name)  LIKE LOWER(?)
               OR LOWER(last_name)   LIKE LOWER(?)
               OR LOWER(student_id)  LIKE LOWER(?)
               OR LOWER(email)       LIKE LOWER(?)
               OR LOWER(course)      LIKE LOWER(?)
            ORDER BY last_name ASC, first_name ASC
            """;

    private static final String SQL_FIND_BY_COURSE = """
            SELECT id, student_id, first_name, last_name, email, phone,
                   course, year_level, gpa, status, enrolled_at, updated_at
            FROM students
            WHERE course = ?
            ORDER BY last_name ASC, first_name ASC
            """;

    private static final String SQL_FIND_BY_STATUS = """
            SELECT id, student_id, first_name, last_name, email, phone,
                   course, year_level, gpa, status, enrolled_at, updated_at
            FROM students
            WHERE status = ?
            ORDER BY last_name ASC, first_name ASC
            """;

    private static final String SQL_COUNT_ALL =
            "SELECT COUNT(*) FROM students";

    private static final String SQL_COUNT_BY_STATUS =
            "SELECT COUNT(*) FROM students WHERE status = ?";

    private static final String SQL_DISTINCT_COURSES =
            "SELECT DISTINCT course FROM students ORDER BY course ASC";

    private static final String SQL_AVG_GPA =
            "SELECT AVG(gpa) FROM students";

    private static final String SQL_AVG_GPA_BY_COURSE =
            "SELECT AVG(gpa) FROM students WHERE course = ?";

    private static final String SQL_UPDATE = """
            UPDATE students
            SET student_id = ?,
                first_name = ?,
                last_name  = ?,
                email      = ?,
                phone      = ?,
                course     = ?,
                year_level = ?,
                gpa        = ?,
                status     = ?,
                updated_at = ?
            WHERE id = ?
            """;

    private static final String SQL_DELETE =
            "DELETE FROM students WHERE id = ?";

    private static final String SQL_DELETE_ALL =
            "DELETE FROM students";

    // -----------------------------------------------------------------------
    // CONSTRUCTOR
    // -----------------------------------------------------------------------

    /**
     * Creates a new {@code SqliteStudentRepository}.
     *
     * <p>No connection is opened here â€” the connection is obtained from
     * {@link DatabaseManager} on each method call. This keeps the repository
     * stateless and simplifies testing (the DatabaseManager singleton
     * can be initialised with an in-memory SQLite URL for tests).
     */
    public SqliteStudentRepository() {
        log.debug("SqliteStudentRepository instantiated.");
    }

    // -----------------------------------------------------------------------
    // PRIVATE HELPERS
    // -----------------------------------------------------------------------

    /**
     * Returns the shared JDBC {@link Connection} from {@link DatabaseManager}.
     *
     * <p>Centralising this call means if we ever need to add connection
     * instrumentation (e.g., timing queries), there is exactly one place to do it.
     *
     * @return the active database connection
     */
    private Connection conn() {
        return DatabaseManager.getInstance().getConnection();
    }

    /**
     * Maps a single {@link ResultSet} row to a {@link Student} domain object.
     *
     * <p>WHY a dedicated mapper method:
     * Every SELECT query in this class produces the same 12-column result set.
     * Centralising the mapping here means a column rename or type change
     * requires updating exactly one method instead of 8+ query methods.
     *
     * <p>TIMESTAMP PARSING:
     * SQLite stores timestamps as TEXT in "yyyy-MM-dd HH:mm:ss" format.
     * We parse them back to {@link LocalDateTime} using
     * {@link Student#TIMESTAMP_FORMAT}. If a stored value is null or
     * unparseable (e.g., legacy data), we fall back to
     * {@link LocalDateTime#now()} to avoid crashing the entire list load.
     *
     * @param rs the {@link ResultSet} positioned at the row to map;
     *           the cursor must already be advanced (rs.next() called by caller)
     * @return a fully populated {@link Student} object
     * @throws SQLException if any column access fails
     */
    private Student mapRow(ResultSet rs) throws SQLException {
        // Parse timestamps safely â€” fall back to now() on parse failure
        LocalDateTime enrolledAt = parseTimestamp(rs.getString("enrolled_at"));
        LocalDateTime updatedAt  = parseTimestamp(rs.getString("updated_at"));

        return new Student(
                rs.getInt("id"),
                rs.getString("student_id"),
                rs.getString("first_name"),
                rs.getString("last_name"),
                rs.getString("email"),
                rs.getString("phone"),
                rs.getString("course"),
                rs.getInt("year_level"),
                rs.getDouble("gpa"),
                StudentStatus.fromString(rs.getString("status")),
                enrolledAt,
                updatedAt
        );
    }

    /**
     * Safely parses a timestamp string from the database.
     *
     * <p>Returns {@link LocalDateTime#now()} if the string is null, blank,
     * or cannot be parsed. This prevents a single corrupt timestamp value
     * from crashing the entire student list load.
     *
     * @param value the timestamp string stored in SQLite
     * @return the parsed {@link LocalDateTime}, or now() on failure
     */
    private LocalDateTime parseTimestamp(String value) {
        if (value == null || value.isBlank()) {
            return LocalDateTime.now();
        }
        try {
            return LocalDateTime.parse(value, Student.TIMESTAMP_FORMAT);
        } catch (Exception ex) {
            log.warn("Failed to parse timestamp '{}'; defaulting to now().", value);
            return LocalDateTime.now();
        }
    }

    /**
     * Formats a {@link LocalDateTime} to the string format expected by SQLite.
     *
     * @param dt the datetime to format; if null, uses now()
     * @return formatted string (e.g., "2024-09-01 09:00:00")
     */
    private String formatTimestamp(LocalDateTime dt) {
        return (dt == null ? LocalDateTime.now() : dt).format(Student.TIMESTAMP_FORMAT);
    }

    /**
     * Wraps the LIKE wildcard characters around a search keyword.
     *
     * <p>Produces "%keyword%" so the LIKE operator performs a
     * substring (contains) match. The wildcard wrapping is done here
     * rather than in the SQL constant to keep the SQL clean and readable.
     *
     * @param keyword the raw search term from the user
     * @return the keyword wrapped in % characters
     */
    private String likeParam(String keyword) {
        return "%" + keyword + "%";
    }

    // -----------------------------------------------------------------------
    // CREATE
    // -----------------------------------------------------------------------

    /**
     * {@inheritDoc}
     *
     * <p>IMPLEMENTATION NOTES:
     * We use {@code Statement.RETURN_GENERATED_KEYS} to retrieve the
     * SQLite AUTOINCREMENT value assigned to the new row, then set it
     * back on the {@code student} object so the caller has the DB ID.
     */
    @Override
    public void save(Student student) {
        log.debug("Saving student: {}", student);

        // Set timestamps â€” enrolledAt only on insert, updatedAt always
        LocalDateTime now = LocalDateTime.now();
        if (student.getEnrolledAt() == null) {
            student.setEnrolledAt(now);
        }
        student.setUpdatedAt(now);

        try (PreparedStatement ps = conn().prepareStatement(
                SQL_INSERT, Statement.RETURN_GENERATED_KEYS)) {

            bindStudentForInsert(ps, student);
            ps.executeUpdate();

            // Retrieve and set the auto-generated surrogate key
            try (ResultSet keys = ps.getGeneratedKeys()) {
                if (keys.next()) {
                    student.setId(keys.getInt(1));
                    log.info("Student saved with DB id={}.", student.getId());
                }
            }

        } catch (SQLException ex) {
            throw new RepositoryException(
                    "Failed to save student: " + student.getStudentId(), ex);
        }
    }

    /**
     * Binds all student fields to the INSERT prepared statement.
     *
     * <p>WHY a separate bind method: Keeps {@code save()} readable and
     * allows the same binding logic to be reused in {@code saveAll()}.
     *
     * @param ps      the prepared statement for the INSERT SQL
     * @param student the student whose fields are to be bound
     * @throws SQLException if any parameter binding fails
     */
    private void bindStudentForInsert(PreparedStatement ps, Student student)
            throws SQLException {
        ps.setString(1,  student.getStudentId());
        ps.setString(2,  student.getFirstName());
        ps.setString(3,  student.getLastName());
        ps.setString(4,  student.getEmail());
        ps.setString(5,  student.getPhone());
        ps.setString(6,  student.getCourse());
        ps.setInt(7,     student.getYearLevel());
        ps.setDouble(8,  student.getGpa());
        ps.setString(9,  student.getStatus().name());
        ps.setString(10, formatTimestamp(student.getEnrolledAt()));
        ps.setString(11, formatTimestamp(student.getUpdatedAt()));
    }

    // -----------------------------------------------------------------------
    // READ
    // -----------------------------------------------------------------------

    /**
     * {@inheritDoc}
     *
     * <p>Iterates the entire {@code students} table ordered by name.
     * Returns an empty list if no rows exist â€” never null.
     */
    @Override
    public List<Student> findAll() {
        log.debug("Finding all students.");
        List<Student> students = new ArrayList<>();

        try (PreparedStatement ps = conn().prepareStatement(SQL_FIND_ALL);
             ResultSet rs = ps.executeQuery()) {

            while (rs.next()) {
                students.add(mapRow(rs));
            }

        } catch (SQLException ex) {
            throw new RepositoryException("Failed to retrieve all students.", ex);
        }

        log.debug("findAll() returned {} students.", students.size());
        return students;
    }

    /**
     * {@inheritDoc}
     *
     * <p>Uses the surrogate integer PK for the lookup â€” the fastest possible
     * query in SQLite (primary key lookup uses the B-tree index directly).
     */
    @Override
    public Optional<Student> findById(int id) {
        log.debug("Finding student by id={}.", id);

        try (PreparedStatement ps = conn().prepareStatement(SQL_FIND_BY_ID)) {
            ps.setInt(1, id);

            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(mapRow(rs));
                }
            }

        } catch (SQLException ex) {
            throw new RepositoryException("Failed to find student by id: " + id, ex);
        }

        return Optional.empty();
    }

    /**
     * {@inheritDoc}
     *
     * <p>Performs an exact match on the {@code student_id} column which
     * has a UNIQUE constraint, so at most one row can match.
     */
    @Override
    public Optional<Student> findByStudentId(String studentId) {
        log.debug("Finding student by studentId='{}'.", studentId);

        try (PreparedStatement ps = conn().prepareStatement(SQL_FIND_BY_STUDENT_ID)) {
            ps.setString(1, studentId);

            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(mapRow(rs));
                }
            }

        } catch (SQLException ex) {
            throw new RepositoryException(
                    "Failed to find student by studentId: " + studentId, ex);
        }

        return Optional.empty();
    }

    /**
     * {@inheritDoc}
     *
     * <p>Uses {@code LOWER(email) = LOWER(?)} for a case-insensitive
     * exact match. The UNIQUE constraint on the email column ensures
     * at most one row matches.
     */
    @Override
    public Optional<Student> findByEmail(String email) {
        log.debug("Finding student by email='{}'.", email);

        try (PreparedStatement ps = conn().prepareStatement(SQL_FIND_BY_EMAIL)) {
            ps.setString(1, email);

            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(mapRow(rs));
                }
            }

        } catch (SQLException ex) {
            throw new RepositoryException(
                    "Failed to find student by email: " + email, ex);
        }

        return Optional.empty();
    }

    /**
     * {@inheritDoc}
     *
     * <p>Applies a case-insensitive LIKE match across five columns:
     * first name, last name, student ID, email, and course.
     * An empty keyword returns all students by passing "%%" as the
     * LIKE parameter (matches everything).
     *
     * <p>WHY 5 bound parameters for one keyword:
     * SQL does not support binding one parameter to multiple OR branches.
     * Each {@code LIKE ?} clause needs its own parameter binding, so we
     * call {@code setString} five times with the same wrapped value.
     */
    @Override
    public List<Student> search(String keyword) {
        String safeKeyword = (keyword == null) ? "" : keyword.trim();
        String param = likeParam(safeKeyword);
        log.debug("Searching students with keyword='{}'.", safeKeyword);

        List<Student> results = new ArrayList<>();

        try (PreparedStatement ps = conn().prepareStatement(SQL_SEARCH)) {
            // Bind the same wrapped keyword to all 5 LIKE parameters
            for (int i = 1; i <= 5; i++) {
                ps.setString(i, param);
            }

            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    results.add(mapRow(rs));
                }
            }

        } catch (SQLException ex) {
            throw new RepositoryException(
                    "Failed to search students with keyword: " + safeKeyword, ex);
        }

        log.debug("search('{}') returned {} results.", safeKeyword, results.size());
        return results;
    }

    /**
     * {@inheritDoc}
     *
     * <p>Exact match on the {@code course} column.
     */
    @Override
    public List<Student> findByCourse(String course) {
        log.debug("Finding students by course='{}'.", course);
        List<Student> results = new ArrayList<>();

        try (PreparedStatement ps = conn().prepareStatement(SQL_FIND_BY_COURSE)) {
            ps.setString(1, course);

            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    results.add(mapRow(rs));
                }
            }

        } catch (SQLException ex) {
            throw new RepositoryException(
                    "Failed to find students by course: " + course, ex);
        }

        return results;
    }

    /**
     * {@inheritDoc}
     *
     * <p>Binds the enum's {@code name()} string (e.g., "ACTIVE") to match
     * the TEXT value stored in the {@code status} column.
     */
    @Override
    public List<Student> findByStatus(StudentStatus status) {
        log.debug("Finding students by status='{}'.", status);
        List<Student> results = new ArrayList<>();

        try (PreparedStatement ps = conn().prepareStatement(SQL_FIND_BY_STATUS)) {
            ps.setString(1, status.name());

            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    results.add(mapRow(rs));
                }
            }

        } catch (SQLException ex) {
            throw new RepositoryException(
                    "Failed to find students by status: " + status, ex);
        }

        return results;
    }

    /**
     * {@inheritDoc}
     *
     * <p>Executes a single {@code COUNT(*)} aggregate â€” far more efficient
     * than loading all rows into memory and calling {@code .size()}.
     */
    @Override
    public int countAll() {
        log.debug("Counting all students.");

        try (PreparedStatement ps = conn().prepareStatement(SQL_COUNT_ALL);
             ResultSet rs = ps.executeQuery()) {

            if (rs.next()) {
                return rs.getInt(1);
            }

        } catch (SQLException ex) {
            throw new RepositoryException("Failed to count all students.", ex);
        }

        return 0;
    }

    /**
     * {@inheritDoc}
     *
     * <p>Uses a parameterised {@code COUNT(*) WHERE status = ?} for efficiency.
     */
    @Override
    public int countByStatus(StudentStatus status) {
        log.debug("Counting students by status='{}'.", status);

        try (PreparedStatement ps = conn().prepareStatement(SQL_COUNT_BY_STATUS)) {
            ps.setString(1, status.name());

            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return rs.getInt(1);
                }
            }

        } catch (SQLException ex) {
            throw new RepositoryException(
                    "Failed to count students by status: " + status, ex);
        }

        return 0;
    }

    /**
     * {@inheritDoc}
     *
     * <p>Uses {@code SELECT DISTINCT} to deduplicate course names.
     * The {@code ORDER BY} ensures the ComboBox in the UI is sorted
     * alphabetically without any additional sorting in the service layer.
     */
    @Override
    public List<String> findDistinctCourses() {
        log.debug("Finding distinct courses.");
        List<String> courses = new ArrayList<>();

        try (PreparedStatement ps = conn().prepareStatement(SQL_DISTINCT_COURSES);
             ResultSet rs = ps.executeQuery()) {

            while (rs.next()) {
                courses.add(rs.getString(1));
            }

        } catch (SQLException ex) {
            throw new RepositoryException("Failed to retrieve distinct courses.", ex);
        }

        return courses;
    }

    /**
     * {@inheritDoc}
     *
     * <p>SQLite's {@code AVG()} returns NULL when the table is empty.
     * We guard against this with a null check on the result, returning
     * 0.0 rather than throwing a NullPointerException.
     */
    @Override
    public double getAverageGpa() {
        log.debug("Calculating average GPA.");

        try (PreparedStatement ps = conn().prepareStatement(SQL_AVG_GPA);
             ResultSet rs = ps.executeQuery()) {

            if (rs.next()) {
                double avg = rs.getDouble(1);
                // rs.wasNull() returns true if the AVG result was SQL NULL
                // (i.e., the table was empty)
                return rs.wasNull() ? 0.0 : avg;
            }

        } catch (SQLException ex) {
            throw new RepositoryException("Failed to calculate average GPA.", ex);
        }

        return 0.0;
    }

    /**
     * {@inheritDoc}
     *
     * <p>Same NULL guard as {@link #getAverageGpa()} applied here.
     */
    @Override
    public double getAverageGpaByCourse(String course) {
        log.debug("Calculating average GPA for course='{}'.", course);

        try (PreparedStatement ps = conn().prepareStatement(SQL_AVG_GPA_BY_COURSE)) {
            ps.setString(1, course);

            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    double avg = rs.getDouble(1);
                    return rs.wasNull() ? 0.0 : avg;
                }
            }

        } catch (SQLException ex) {
            throw new RepositoryException(
                    "Failed to calculate average GPA for course: " + course, ex);
        }

        return 0.0;
    }

    // -----------------------------------------------------------------------
    // UPDATE
    // -----------------------------------------------------------------------

    /**
     * {@inheritDoc}
     *
     * <p>Updates all mutable fields by surrogate PK. Always sets
     * {@code updated_at} to the current timestamp.
     *
     * <p>AFFECTED ROWS CHECK:
     * If {@code executeUpdate()} returns 0, no row matched the given ID.
     * This is treated as an error because the caller (service layer) should
     * have verified existence before calling update. Returning silently
     * would hide a logical bug.
     */
    @Override
    public void update(Student student) {
        log.debug("Updating student id={}.", student.getId());

        student.setUpdatedAt(LocalDateTime.now());

        try (PreparedStatement ps = conn().prepareStatement(SQL_UPDATE)) {

            ps.setString(1,  student.getStudentId());
            ps.setString(2,  student.getFirstName());
            ps.setString(3,  student.getLastName());
            ps.setString(4,  student.getEmail());
            ps.setString(5,  student.getPhone());
            ps.setString(6,  student.getCourse());
            ps.setInt(7,     student.getYearLevel());
            ps.setDouble(8,  student.getGpa());
            ps.setString(9,  student.getStatus().name());
            ps.setString(10, formatTimestamp(student.getUpdatedAt()));
            // WHERE clause â€” must be last parameter
            ps.setInt(11,    student.getId());

            int affected = ps.executeUpdate();
            if (affected == 0) {
                throw new RepositoryException(
                        "Update affected 0 rows â€” no student found with id: "
                        + student.getId());
            }

            log.info("Student id={} updated successfully.", student.getId());

        } catch (SQLException ex) {
            throw new RepositoryException(
                    "Failed to update student id: " + student.getId(), ex);
        }
    }

    // -----------------------------------------------------------------------
    // DELETE
    // -----------------------------------------------------------------------

    /**
     * {@inheritDoc}
     *
     * <p>Same affected-rows check as {@link #update(Student)} â€” a 0-row
     * delete means the ID was not found and is treated as an error.
     */
    @Override
    public void delete(int id) {
        log.debug("Deleting student id={}.", id);

        try (PreparedStatement ps = conn().prepareStatement(SQL_DELETE)) {
            ps.setInt(1, id);

            int affected = ps.executeUpdate();
            if (affected == 0) {
                throw new RepositoryException(
                        "Delete affected 0 rows â€” no student found with id: " + id);
            }

            log.info("Student id={} deleted.", id);

        } catch (SQLException ex) {
            throw new RepositoryException(
                    "Failed to delete student with id: " + id, ex);
        }
    }

    /**
     * {@inheritDoc}
     *
     * <p>Executes a single {@code DELETE FROM students} with no WHERE clause.
     * Intentionally does NOT check affected rows because zero rows deleted
     * from an already-empty table is a valid (non-error) state.
     */
    @Override
    public void deleteAll() {
        log.warn("deleteAll() called â€” all student records will be deleted.");

        try (PreparedStatement ps = conn().prepareStatement(SQL_DELETE_ALL)) {
            int affected = ps.executeUpdate();
            log.info("deleteAll() deleted {} rows.", affected);

        } catch (SQLException ex) {
            throw new RepositoryException("Failed to delete all students.", ex);
        }
    }

    // -----------------------------------------------------------------------
    // BATCH OPERATIONS
    // -----------------------------------------------------------------------

    /**
     * {@inheritDoc}
     *
     * <p>TRANSACTION STRATEGY:
     * <ol>
     *   <li>Disable auto-commit so all inserts share one transaction.</li>
     *   <li>Iterate the list; for each student attempt an insert.</li>
     *   <li>On per-row {@link SQLException} (e.g., duplicate key), record the
     *       zero-based index in {@code failedIndices} and continue â€” do NOT
     *       roll back the whole batch for one bad row.</li>
     *   <li>After the loop, commit the successful rows.</li>
     *   <li>Re-enable auto-commit in a {@code finally} block regardless of
     *       outcome to restore normal single-statement commit behaviour.</li>
     * </ol>
     *
     * <p>WHY NOT JDBC batch (addBatch/executeBatch):
     * SQLite's JDBC driver does not report per-row failures through
     * {@code executeBatch()} â€” it throws on the first failure and aborts
     * the entire batch. Our approach of catching per-row SQLExceptions
     * gives us the per-row error reporting required by the CSV import feature.
     */
    @Override
    public List<Integer> saveAll(List<Student> students) {
        if (students == null || students.isEmpty()) {
            log.debug("saveAll() called with empty list â€” nothing to do.");
            return Collections.emptyList();
        }

        log.info("saveAll() starting batch insert of {} students.", students.size());
        List<Integer> failedIndices = new ArrayList<>();
        Connection connection = conn();

        try {
            // Disable auto-commit to batch all inserts into one transaction
            connection.setAutoCommit(false);

            LocalDateTime now = LocalDateTime.now();

            try (PreparedStatement ps = connection.prepareStatement(
                    SQL_INSERT, Statement.RETURN_GENERATED_KEYS)) {

                for (int i = 0; i < students.size(); i++) {
                    Student student = students.get(i);

                    try {
                        // Apply timestamps before binding
                        if (student.getEnrolledAt() == null) {
                            student.setEnrolledAt(now);
                        }
                        student.setUpdatedAt(now);

                        bindStudentForInsert(ps, student);
                        ps.executeUpdate();

                        // Retrieve and set generated key
                        try (ResultSet keys = ps.getGeneratedKeys()) {
                            if (keys.next()) {
                                student.setId(keys.getInt(1));
                            }
                        }

                        log.debug("Batch row {}/{} inserted: {}.",
                                i + 1, students.size(), student.getStudentId());

                    } catch (SQLException rowEx) {
                        // Record failed index and continue with the next row
                        log.warn("Batch row {} failed (studentId='{}'): {}",
                                i, student.getStudentId(), rowEx.getMessage());
                        failedIndices.add(i);
                    }
                }
            }

            // Commit all successful rows as one atomic transaction
            connection.commit();
            int successCount = students.size() - failedIndices.size();
            log.info("saveAll() committed {} rows. {} rows failed.",
                    successCount, failedIndices.size());

        } catch (SQLException transactionEx) {
            // A transaction-level failure (not a row-level failure) â€”
            // attempt rollback and report as a RepositoryException
            log.error("saveAll() transaction failed â€” attempting rollback.", transactionEx);
            try {
                connection.rollback();
                log.warn("saveAll() rolled back successfully.");
            } catch (SQLException rollbackEx) {
                log.error("Rollback also failed.", rollbackEx);
            }
            throw new RepositoryException("Batch insert transaction failed.", transactionEx);

        } finally {
            // ALWAYS re-enable auto-commit â€” whether we committed, rolled back,
            // or threw an exception. Failing to do this would leave every
            // subsequent single-row operation waiting for a commit that
            // never comes.
            try {
                connection.setAutoCommit(true);
                log.debug("Auto-commit re-enabled after saveAll().");
            } catch (SQLException acEx) {
                log.error("Failed to re-enable auto-commit after saveAll().", acEx);
            }
        }

        return failedIndices;
    }
}

