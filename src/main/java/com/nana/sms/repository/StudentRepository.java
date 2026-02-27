package com.nana.sms.repository;

import com.nana.sms.domain.Student;
import com.nana.sms.domain.StudentStatus;

import java.util.List;
import java.util.Optional;

/**
 * StudentRepository â€” Repository Layer Interface
 *
 * <p>WHY THIS INTERFACE EXISTS:
 * In clean layered architecture the service layer must never contain SQL,
 * and the repository layer must never contain business logic. This interface
 * is the formal contract that enforces that boundary. It declares WHAT data
 * operations are available without saying anything about HOW they are
 * implemented.
 *
 * <p>DEPENDENCY INVERSION (SOLID â€” "D"):
 * The service layer declares a dependency on this interface, not on
 * {@code SqliteStudentRepository} or any other concrete class. This means:
 * <ul>
 *   <li>The service can be unit-tested by injecting a mock of this interface.</li>
 *   <li>The SQLite implementation can be replaced (e.g., with a JSON file store
 *       or a remote REST client) without changing a single line of service code.</li>
 *   <li>An in-memory implementation can be used for integration tests that need
 *       fast, deterministic data access without touching the filesystem.</li>
 * </ul>
 *
 * <p>INTERFACE SEGREGATION (SOLID â€” "I"):
 * All methods in this interface are genuinely used by the service layer.
 * We have not added speculative methods "just in case". If a new use case
 * requires a new query, a new method is added here and implemented
 * in the concrete class at that time.
 *
 * <p>EXCEPTION STRATEGY:
 * All methods declare {@link RepositoryException} â€” an unchecked wrapper
 * around {@link java.sql.SQLException}. This keeps the service layer free
 * from JDBC-specific checked exceptions while still propagating failure
 * information up the call stack for proper error handling in the UI.
 *
 * <p>RETURN TYPE STRATEGY:
 * <ul>
 *   <li>Single-result lookups return {@link Optional} to force callers to
 *       handle the "not found" case explicitly rather than risk NPEs.</li>
 *   <li>Multi-result queries return {@link List} â€” never null, always
 *       an empty list when no rows match.</li>
 *   <li>Write operations (save/update/delete) return primitive or void
 *       to keep the interface simple.</li>
 * </ul>
 */
public interface StudentRepository {

    // -----------------------------------------------------------------------
    // CREATE
    // -----------------------------------------------------------------------

    /**
     * Persists a new {@link Student} record to the data store.
     *
     * <p>The implementation must:
     * <ol>
     *   <li>Use a prepared statement with all fields bound by position.</li>
     *   <li>Retrieve the auto-generated surrogate key from the DB and set it
     *       on the provided {@code student} object so the caller has the
     *       persisted ID after the call returns.</li>
     *   <li>Set {@code enrolledAt} and {@code updatedAt} to the current
     *       timestamp if they are not already set.</li>
     * </ol>
     *
     * <p>PRE-CONDITION: The {@code student} object has passed service-layer
     * validation. The repository assumes it receives valid data.
     *
     * @param student the student to persist; must not be null;
     *                {@code id} field will be populated after this call
     * @throws RepositoryException if a DB constraint is violated (e.g.,
     *         duplicate {@code studentId} or {@code email}) or if any other
     *         SQL error occurs
     */
    void save(Student student);

    // -----------------------------------------------------------------------
    // READ
    // -----------------------------------------------------------------------

    /**
     * Retrieves all students from the data store, ordered by last name
     * then first name ascending.
     *
     * <p>Returns an empty list (never null) when no students exist.
     * Callers should not assume any particular ordering beyond what is
     * documented here.
     *
     * @return an unmodifiable-safe {@link List} of all {@link Student} objects
     * @throws RepositoryException if the query fails
     */
    List<Student> findAll();

    /**
     * Retrieves a single student by their surrogate database primary key.
     *
     * <p>WHY {@link Optional}: The caller must explicitly handle the case
     * where no student with that ID exists, rather than receiving a null
     * and potentially causing a NullPointerException downstream.
     *
     * @param id the surrogate DB primary key to look up
     * @return an {@link Optional} containing the student if found,
     *         or {@link Optional#empty()} if no row matches
     * @throws RepositoryException if the query fails
     */
    Optional<Student> findById(int id);

    /**
     * Retrieves a single student by their human-visible business identifier.
     *
     * <p>This is the preferred lookup when processing CSV imports or
     * handling user searches by student ID, because users interact with
     * the business key, not the surrogate DB key.
     *
     * @param studentId the business key to search for (e.g., "STU-2024-001")
     * @return an {@link Optional} containing the student if found,
     *         or {@link Optional#empty()} if no row matches
     * @throws RepositoryException if the query fails
     */
    Optional<Student> findByStudentId(String studentId);

    /**
     * Retrieves a single student by their email address.
     *
     * <p>Used by the service layer's duplicate-email check before saving
     * or updating a student record.
     *
     * @param email the email address to search for (case-insensitive lookup
     *              is handled by the implementation)
     * @return an {@link Optional} containing the student if found,
     *         or {@link Optional#empty()} if no row matches
     * @throws RepositoryException if the query fails
     */
    Optional<Student> findByEmail(String email);

    /**
     * Searches students whose first name, last name, or student ID contains
     * the given keyword (case-insensitive partial match).
     *
     * <p>This powers the live search bar in the UI. The implementation must
     * use a parameterised LIKE query â€” never string concatenation â€” to
     * prevent SQL injection.
     *
     * @param keyword the search term; must not be null; empty string returns
     *                all students (equivalent to {@link #findAll()})
     * @return a {@link List} of matching students, empty list if none found
     * @throws RepositoryException if the query fails
     */
    List<Student> search(String keyword);

    /**
     * Retrieves all students enrolled in a specific course.
     *
     * <p>Used by the report generation layer to produce per-course statistics.
     *
     * @param course the exact course name to filter by
     * @return a {@link List} of students in the specified course,
     *         empty list if none found
     * @throws RepositoryException if the query fails
     */
    List<Student> findByCourse(String course);

    /**
     * Retrieves all students with a specific enrollment status.
     *
     * <p>Used by the dashboard to display counts per status and by
     * the report layer to filter active vs graduated students.
     *
     * @param status the {@link StudentStatus} to filter by; must not be null
     * @return a {@link List} of students with the given status,
     *         empty list if none found
     * @throws RepositoryException if the query fails
     */
    List<Student> findByStatus(StudentStatus status);

    /**
     * Returns the total number of students in the data store.
     *
     * <p>Used by the dashboard summary panel. More efficient than calling
     * {@code findAll().size()} because it executes a {@code COUNT(*)} query
     * rather than loading all rows into memory.
     *
     * @return total student count (0 if the table is empty)
     * @throws RepositoryException if the query fails
     */
    int countAll();

    /**
     * Returns the number of students with a specific enrollment status.
     *
     * <p>Used by the dashboard to populate the status summary cards
     * (e.g., "Active: 142", "Graduated: 89").
     *
     * @param status the status to count; must not be null
     * @return count of students with the given status
     * @throws RepositoryException if the query fails
     */
    int countByStatus(StudentStatus status);

    /**
     * Returns a distinct, sorted list of all course names that currently
     * have at least one student enrolled.
     *
     * <p>Used to populate the course filter ComboBox in the UI and the
     * course selector in the report generation screen.
     *
     * @return a {@link List} of unique course name strings, sorted ascending;
     *         empty list if no students exist
     * @throws RepositoryException if the query fails
     */
    List<String> findDistinctCourses();

    /**
     * Calculates the average GPA across all students.
     *
     * <p>Executed as a single {@code AVG()} aggregate query for efficiency.
     *
     * @return the average GPA as a double, or {@code 0.0} if no students exist
     * @throws RepositoryException if the query fails
     */
    double getAverageGpa();

    /**
     * Calculates the average GPA for students in a specific course.
     *
     * <p>Used by the per-course report to display course-level academic
     * performance.
     *
     * @param course the course name to aggregate
     * @return the average GPA for the course, or {@code 0.0} if no students
     *         are enrolled in that course
     * @throws RepositoryException if the query fails
     */
    double getAverageGpaByCourse(String course);

    // -----------------------------------------------------------------------
    // UPDATE
    // -----------------------------------------------------------------------

    /**
     * Updates an existing student record in the data store.
     *
     * <p>The implementation must:
     * <ol>
     *   <li>Match the record by the student's surrogate {@code id}.</li>
     *   <li>Update ALL mutable fields â€” the caller is responsible for
     *       setting only the fields they want to change before calling this.</li>
     *   <li>Automatically set {@code updatedAt} to the current timestamp.</li>
     * </ol>
     *
     * <p>PRE-CONDITION: The student's {@code id} must be &gt; 0 (i.e., the
     * record must have been previously persisted). The service layer is
     * responsible for verifying this before calling update.
     *
     * @param student the student with updated fields; {@code id} must be &gt; 0
     * @throws RepositoryException if no row with the given ID exists,
     *         a unique constraint is violated, or any SQL error occurs
     */
    void update(Student student);

    // -----------------------------------------------------------------------
    // DELETE
    // -----------------------------------------------------------------------

    /**
     * Deletes the student record with the given surrogate primary key.
     *
     * <p>This is a hard delete â€” the record is permanently removed from
     * the data store. If soft-delete behaviour is needed in the future,
     * the service layer should call {@link #update} with status set to
     * {@code INACTIVE} instead of calling this method.
     *
     * @param id the surrogate DB primary key of the student to delete;
     *           must be &gt; 0
     * @throws RepositoryException if no row with the given ID exists
     *         or any SQL error occurs
     */
    void delete(int id);

    /**
     * Deletes all student records from the data store.
     *
     * <p>Used exclusively by the CSV import process when the user chooses
     * the "Replace All" import mode, and by test teardown methods.
     *
     * <p>WARNING: This is a destructive operation with no undo. The service
     * layer must require explicit user confirmation before calling this.
     *
     * @throws RepositoryException if the delete fails
     */
    void deleteAll();

    // -----------------------------------------------------------------------
    // BATCH OPERATIONS
    // -----------------------------------------------------------------------

    /**
     * Persists a batch of student records in a single database transaction.
     *
     * <p>WHY a batch method: Inserting 500 CSV rows one-by-one with
     * individual commits is extremely slow in SQLite (each commit flushes
     * the WAL to disk). Wrapping the entire batch in a single transaction
     * reduces 500 disk syncs to 1, typically making batch inserts
     * 50â€“200Ã— faster.
     *
     * <p>ATOMICITY: The entire batch either succeeds completely or is
     * rolled back entirely. The caller receives a list of row indices
     * that failed (due to constraint violations) so they can be reported
     * to the user without failing the whole import.
     *
     * <p>The implementation must:
     * <ol>
     *   <li>Disable auto-commit before the batch.</li>
     *   <li>Use a single prepared statement executed in a loop.</li>
     *   <li>Catch per-row exceptions, record the failed index, and continue.</li>
     *   <li>Commit at the end if at least one row succeeded.</li>
     *   <li>Re-enable auto-commit in a {@code finally} block.</li>
     * </ol>
     *
     * @param students the list of students to insert; must not be null
     * @return a {@link List} of zero-based indices into {@code students}
     *         that failed to insert (empty list = all succeeded)
     * @throws RepositoryException if the transaction itself fails
     *         (as opposed to individual row failures)
     */
    List<Integer> saveAll(List<Student> students);

    // -----------------------------------------------------------------------
    // INNER EXCEPTION CLASS
    // -----------------------------------------------------------------------

    /**
     * RepositoryException â€” Repository Layer Unchecked Exception
     *
     * <p>WHY UNCHECKED:
     * {@link java.sql.SQLException} is a checked exception. If we let it
     * propagate directly, every method in the service layer and UI layer
     * would need {@code throws SQLException} in its signature, tightly
     * coupling them to the JDBC API. By wrapping it in an unchecked
     * exception we preserve layer independence: the service layer only
     * catches {@code RepositoryException}, not {@code SQLException}.
     *
     * <p>WHY NESTED IN THE INTERFACE:
     * Keeping the exception class nested inside {@code StudentRepository}
     * means it is co-located with the contract that throws it. Any code
     * that imports the interface can use the exception without a separate
     * import. It also communicates clearly that this exception is
     * exclusively a repository-layer concern.
     */
    class RepositoryException extends RuntimeException {

        /**
         * Constructs a {@code RepositoryException} with a descriptive message
         * and the underlying cause.
         *
         * @param message human-readable description of what went wrong
         * @param cause   the underlying {@link java.sql.SQLException} or
         *                other low-level exception
         */
        public RepositoryException(String message, Throwable cause) {
            super(message, cause);
        }

        /**
         * Constructs a {@code RepositoryException} with a message only.
         * Used when the failure is detected at the repository level itself
         * (e.g., "Update affected 0 rows â€” student not found") rather than
         * being caused by a JDBC exception.
         *
         * @param message human-readable description of what went wrong
         */
        public RepositoryException(String message) {
            super(message);
        }
    }
}

