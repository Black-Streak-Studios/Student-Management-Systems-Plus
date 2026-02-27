package com.nana.sms.service;

import com.nana.sms.domain.Student;
import com.nana.sms.domain.StudentStatus;

import java.util.List;
import java.util.Optional;

/**
 * StudentService â€” Service Layer Interface
 *
 * <p>WHY THIS INTERFACE EXISTS:
 * Just as {@code StudentRepository} decouples the service from the database,
 * {@code StudentService} decouples the UI from business logic. Controllers
 * depend on this interface â€” never on {@code StudentServiceImpl} directly.
 * This allows:
 * <ul>
 *   <li>UI controllers to be unit-tested with a mock service.</li>
 *   <li>The implementation to be swapped (e.g., a remote API service)
 *       without touching any controller code.</li>
 *   <li>A clean definition of every operation the application supports,
 *       readable without any implementation noise.</li>
 * </ul>
 *
 * <p>EXCEPTION CONTRACT:
 * Methods that accept user-provided data declare {@link ValidationException}.
 * Read-only query methods do not â€” they can only fail at the infrastructure
 * level (RepositoryException, which is unchecked).
 *
 * <p>NAMING CONVENTION:
 * Service methods use business language ("enroll", "dismiss") not
 * persistence language ("save", "delete") where that adds clarity.
 * For CRUD-like operations we keep "add", "update", "remove" for simplicity.
 */
public interface StudentService {

    // -----------------------------------------------------------------------
    // WRITE OPERATIONS (declare ValidationException)
    // -----------------------------------------------------------------------

    /**
     * Validates and persists a new student record.
     *
     * <p>The implementation must:
     * <ol>
     *   <li>Validate all fields (blank checks, format checks, range checks).</li>
     *   <li>Check that {@code studentId} and {@code email} are not already
     *       taken by another record.</li>
     *   <li>Delegate to the repository to persist the record.</li>
     * </ol>
     *
     * @param student the student to add; must not be null
     * @throws ValidationException if any field fails validation or
     *         uniqueness checks
     */
    void addStudent(Student student) throws ValidationException;

    /**
     * Validates and updates an existing student record.
     *
     * <p>The implementation must verify that the student's ID exists before
     * attempting an update, and must perform the same field-level validation
     * as {@link #addStudent(Student)}. Uniqueness checks must exclude the
     * student's own record (i.e., a student can keep their existing email).
     *
     * @param student the student with updated fields; {@code id} must be &gt; 0
     * @throws ValidationException if any field fails validation, uniqueness
     *         check fails for another record, or the student ID does not exist
     */
    void updateStudent(Student student) throws ValidationException;

    /**
     * Removes a student record by surrogate primary key.
     *
     * <p>No validation required beyond confirming the student exists.
     * If the student does not exist, the implementation throws
     * {@link ValidationException} with an "id" field error.
     *
     * @param id the surrogate DB primary key of the student to remove
     * @throws ValidationException if no student with the given ID exists
     */
    void removeStudent(int id) throws ValidationException;

    /**
     * Imports a list of students as a batch, returning a summary of results.
     *
     * <p>Each student in the list is individually validated. Students that
     * pass validation are forwarded to the repository's batch insert.
     * The returned {@link ImportResult} contains counts of successes and
     * failures, plus row-level error messages for UI display.
     *
     * @param students the list of students to import; must not be null
     * @return an {@link ImportResult} summarising the import outcome
     */
    ImportResult importStudents(List<Student> students);

    // -----------------------------------------------------------------------
    // READ OPERATIONS
    // -----------------------------------------------------------------------

    /**
     * Returns all students ordered by last name then first name.
     *
     * @return list of all students; empty list if none exist
     */
    List<Student> getAllStudents();

    /**
     * Returns a student by surrogate primary key.
     *
     * @param id the surrogate DB key
     * @return an {@link Optional} containing the student, or empty if not found
     */
    Optional<Student> getStudentById(int id);

    /**
     * Returns a student by their human-visible business identifier.
     *
     * @param studentId the business key (e.g., "STU-2024-001")
     * @return an {@link Optional} containing the student, or empty if not found
     */
    Optional<Student> getStudentByStudentId(String studentId);

    /**
     * Searches students by keyword across name, student ID, email, and course.
     *
     * @param keyword the search term; empty string returns all students
     * @return list of matching students; empty list if none match
     */
    List<Student> searchStudents(String keyword);

    /**
     * Returns all students enrolled in a specific course.
     *
     * @param course the course name to filter by
     * @return list of matching students; empty list if none match
     */
    List<Student> getStudentsByCourse(String course);

    /**
     * Returns all students with a specific enrollment status.
     *
     * @param status the status to filter by
     * @return list of matching students; empty list if none match
     */
    List<Student> getStudentsByStatus(StudentStatus status);

    /**
     * Returns the total number of students in the system.
     *
     * @return total count
     */
    int getTotalStudentCount();

    /**
     * Returns the count of students with a specific status.
     *
     * @param status the status to count
     * @return count of students with the given status
     */
    int getStudentCountByStatus(StudentStatus status);

    /**
     * Returns a distinct sorted list of all course names with enrolled students.
     *
     * @return list of course name strings; empty list if no students exist
     */
    List<String> getDistinctCourses();

    /**
     * Returns the average GPA across all students.
     *
     * @return average GPA, or 0.0 if no students exist
     */
    double getOverallAverageGpa();

    /**
     * Returns the average GPA for a specific course.
     *
     * @param course the course name to aggregate
     * @return average GPA for the course, or 0.0 if no students in that course
     */
    double getAverageGpaByCourse(String course);

    // -----------------------------------------------------------------------
    // INNER RESULT CLASS
    // -----------------------------------------------------------------------

    /**
     * ImportResult â€” Value Object for Batch Import Outcomes
     *
     * <p>WHY THIS EXISTS:
     * A batch import can partially succeed â€” some rows valid, some invalid.
     * Rather than throwing an exception (which implies total failure) or
     * returning a plain integer (which loses detail), we return this rich
     * result object that the UI can use to show a detailed import report.
     *
     * <p>This is an immutable value object â€” all fields are set at
     * construction and the object is read-only thereafter.
     */
    final class ImportResult {

        private final int totalRows;
        private final int successCount;
        private final int failureCount;

        /**
         * Row-level error messages keyed by zero-based row index.
         * A row index maps to a human-readable description of why it failed
         * (e.g., "Row 3: email 'bad@' is not a valid email address").
         */
        private final java.util.Map<Integer, String> rowErrors;

        /**
         * Constructs an {@code ImportResult}.
         *
         * @param totalRows    total number of rows in the import file
         * @param successCount number of rows successfully imported
         * @param failureCount number of rows that failed
         * @param rowErrors    map of row index to error description
         */
        public ImportResult(int totalRows,
                            int successCount,
                            int failureCount,
                            java.util.Map<Integer, String> rowErrors) {
            this.totalRows    = totalRows;
            this.successCount = successCount;
            this.failureCount = failureCount;
            this.rowErrors    = java.util.Collections.unmodifiableMap(
                    new java.util.LinkedHashMap<>(rowErrors));
        }

        /** @return total number of rows processed */
        public int getTotalRows()    { return totalRows; }

        /** @return number of rows successfully imported */
        public int getSuccessCount() { return successCount; }

        /** @return number of rows that failed */
        public int getFailureCount() { return failureCount; }

        /** @return unmodifiable map of row index to error message */
        public java.util.Map<Integer, String> getRowErrors() { return rowErrors; }

        /** @return true if every row was imported successfully */
        public boolean isFullSuccess() { return failureCount == 0; }

        /** @return true if every row failed */
        public boolean isFullFailure() { return successCount == 0 && totalRows > 0; }

        /**
         * Returns a one-line summary suitable for a status bar or alert dialog.
         *
         * @return summary string (e.g., "Imported 47 of 50 rows. 3 failed.")
         */
        public String getSummary() {
            if (isFullSuccess()) {
                return String.format("Successfully imported all %d rows.", totalRows);
            }
            return String.format("Imported %d of %d rows. %d failed.",
                    successCount, totalRows, failureCount);
        }

        @Override
        public String toString() {
            return "ImportResult{total=" + totalRows +
                   ", success=" + successCount +
                   ", failed=" + failureCount + "}";
        }
    }
}

