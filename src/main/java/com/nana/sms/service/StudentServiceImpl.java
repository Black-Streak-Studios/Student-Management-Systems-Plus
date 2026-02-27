package com.nana.sms.service;

import com.nana.sms.domain.Student;
import com.nana.sms.domain.StudentStatus;
import com.nana.sms.repository.StudentRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * StudentServiceImpl — Service Layer Concrete Implementation
 *
 * <p>WHY THIS CLASS EXISTS:
 * This class is the single home for ALL business logic in the application.
 * Controllers call this class; this class calls the repository. Nothing
 * in the UI layer ever touches a repository directly, and nothing in the
 * repository layer ever performs validation.
 *
 * <p>RESPONSIBILITIES:
 * <ul>
 *   <li>Field-level validation of all student data.</li>
 *   <li>Cross-field validation (e.g., GPA range relative to year level).</li>
 *   <li>Uniqueness checks (studentId and email must be unique).</li>
 *   <li>Orchestrating repository calls in the correct order.</li>
 *   <li>Translating repository exceptions into meaningful service-level
 *       errors where appropriate.</li>
 *   <li>Processing batch import results into {@link StudentService.ImportResult}.</li>
 * </ul>
 *
 * <p>DEPENDENCY INJECTION:
 * The repository is injected via the constructor rather than instantiated
 * here. This is the Dependency Inversion Principle applied — the service
 * depends on the {@link StudentRepository} abstraction, not on
 * {@code SqliteStudentRepository}. This makes unit testing with a mock
 * repository trivial.
 *
 * <p>VALIDATION STRATEGY:
 * All field validations are collected into a single {@link LinkedHashMap}
 * before throwing. This means the UI receives ALL errors in one pass
 * rather than one error per submit attempt.
 */
public class StudentServiceImpl implements StudentService {

    private static final Logger log = LoggerFactory.getLogger(StudentServiceImpl.class);

    // -----------------------------------------------------------------------
    // VALIDATION CONSTANTS
    // -----------------------------------------------------------------------

    /**
     * RFC 5322-inspired email regex. Not perfectly RFC-compliant (full
     * compliance requires a 6KB regex) but covers all realistic valid
     * addresses while rejecting clearly malformed ones.
     */
    private static final Pattern EMAIL_PATTERN = Pattern.compile(
            "^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$"
    );

    /**
     * Student ID: must not be blank and must not exceed 50 characters.
     * No format is enforced — institutions worldwide use different schemes
     * (e.g. "20241001", "S/2024/0042", "GH-UG-0023", "2024-CS-042").
     * Uniqueness is still enforced at the database level.
     */
    private static final int STUDENT_ID_MAX_LENGTH = 50;

    /**
     * Phone: optional field, but if provided must be 7–15 digits,
     * optionally prefixed with + and containing spaces, dashes, or parens.
     */
    private static final Pattern PHONE_PATTERN = Pattern.compile(
            "^[+]?[(]?[0-9]{1,4}[)]?[-\\s./0-9]{6,14}$"
    );

    /** Minimum allowed GPA value. */
    private static final double GPA_MIN = 0.0;

    /** Maximum allowed GPA value. */
    private static final double GPA_MAX = 4.0;

    /** Minimum academic year level. */
    private static final int YEAR_MIN = 1;

    /** Maximum academic year level. */
    private static final int YEAR_MAX = 6;

    /** Maximum length for name fields. */
    private static final int NAME_MAX_LENGTH = 100;

    /** Maximum length for course field. */
    private static final int COURSE_MAX_LENGTH = 150;

    // -----------------------------------------------------------------------
    // DEPENDENCIES
    // -----------------------------------------------------------------------

    /**
     * The repository this service delegates all persistence operations to.
     * Injected via constructor — never instantiated here.
     */
    private final StudentRepository repository;

    // -----------------------------------------------------------------------
    // CONSTRUCTOR
    // -----------------------------------------------------------------------

    /**
     * Creates a {@code StudentServiceImpl} with the given repository.
     *
     * <p>Constructor injection is used rather than setter injection because
     * it makes the dependency mandatory and the object immediately usable
     * after construction — there is no partially-initialised state.
     *
     * @param repository the repository to delegate persistence to;
     *                   must not be null
     * @throws IllegalArgumentException if repository is null
     */
    public StudentServiceImpl(StudentRepository repository) {
        if (repository == null) {
            throw new IllegalArgumentException("StudentRepository must not be null.");
        }
        this.repository = repository;
        log.debug("StudentServiceImpl instantiated with repository: {}",
                repository.getClass().getSimpleName());
    }

    // -----------------------------------------------------------------------
    // WRITE OPERATIONS
    // -----------------------------------------------------------------------

    /**
     * {@inheritDoc}
     *
     * <p>VALIDATION ORDER:
     * <ol>
     *   <li>Field-level validation (blank, format, range).</li>
     *   <li>Uniqueness check for studentId against the DB.</li>
     *   <li>Uniqueness check for email against the DB.</li>
     *   <li>Persist via repository.</li>
     * </ol>
     * All field errors are collected before throwing so the UI gets them all.
     * Uniqueness errors are added after field errors so field format errors
     * are presented first (a malformed email cannot be a duplicate anyway).
     */
    @Override
    public void addStudent(Student student) throws ValidationException {
        log.debug("addStudent() called for: {}", student);

        // Step 1: Collect all field-level errors
        Map<String, String> errors = validateStudentFields(student, false);

        // Step 2: Uniqueness checks (only if field formats are valid,
        // to avoid querying the DB with garbage values)
        if (!errors.containsKey("studentId")) {
            repository.findByStudentId(student.getStudentId()).ifPresent(existing ->
                    errors.put("studentId",
                            "Student ID '" + student.getStudentId() + "' is already in use."));
        }

        if (!errors.containsKey("email")) {
            repository.findByEmail(student.getEmail()).ifPresent(existing ->
                    errors.put("email",
                            "Email '" + student.getEmail() + "' is already registered."));
        }

        if (!errors.isEmpty()) {
            log.warn("addStudent() validation failed: {}", errors);
            throw new ValidationException(errors);
        }

        repository.save(student);
        log.info("Student added successfully: {}", student.getStudentId());
    }

    /**
     * {@inheritDoc}
     *
     * <p>UNIQUENESS CHECK FOR UPDATES:
     * When checking email uniqueness, we must exclude the student's own
     * record. A student should be able to submit the update form without
     * changing their email and not receive a "duplicate email" error.
     * We do this by checking if the found record's ID differs from the
     * updating student's ID.
     */
    @Override
    public void updateStudent(Student student) throws ValidationException {
        log.debug("updateStudent() called for id={}.", student.getId());

        // Guard: id must be set
        if (student.getId() <= 0) {
            throw new ValidationException("id",
                    "Cannot update a student that has not been persisted (id <= 0).");
        }

        // Verify the student exists in the DB
        repository.findById(student.getId()).orElseThrow(() ->
                new IllegalStateException(
                        "Update called for non-existent student id: " + student.getId()));

        // Collect field-level errors
        Map<String, String> errors = validateStudentFields(student, true);

        // Uniqueness check — exclude own record
        if (!errors.containsKey("studentId")) {
            repository.findByStudentId(student.getStudentId())
                    .filter(existing -> existing.getId() != student.getId())
                    .ifPresent(existing ->
                            errors.put("studentId",
                                    "Student ID '" + student.getStudentId()
                                    + "' is already in use by another student."));
        }

        if (!errors.containsKey("email")) {
            repository.findByEmail(student.getEmail())
                    .filter(existing -> existing.getId() != student.getId())
                    .ifPresent(existing ->
                            errors.put("email",
                                    "Email '" + student.getEmail()
                                    + "' is already registered to another student."));
        }

        if (!errors.isEmpty()) {
            log.warn("updateStudent() validation failed for id={}: {}", student.getId(), errors);
            throw new ValidationException(errors);
        }

        repository.update(student);
        log.info("Student id={} updated successfully.", student.getId());
    }

    /**
     * {@inheritDoc}
     *
     * <p>Verifies existence before delegating to the repository.
     * This gives a cleaner error message than letting the repository
     * throw "0 rows affected".
     */
    @Override
    public void removeStudent(int id) throws ValidationException {
        log.debug("removeStudent() called for id={}.", id);

        if (id <= 0) {
            throw new ValidationException("id", "Invalid student ID: " + id);
        }

        repository.findById(id).orElseThrow(() ->
                new ValidationException("id",
                        "No student found with ID: " + id));

        repository.delete(id);
        log.info("Student id={} removed.", id);
    }

    /**
     * {@inheritDoc}
     *
     * <p>IMPORT STRATEGY:
     * <ol>
     *   <li>Validate each student individually using the same rules as
     *       {@link #addStudent(Student)} but collect errors per-row
     *       rather than throwing immediately.</li>
     *   <li>Students that pass validation are forwarded to
     *       {@code repository.saveAll()} as a batch.</li>
     *   <li>Repository-level failures (e.g., duplicate key that slipped
     *       through validation because two import rows share an email)
     *       are captured from the returned failed-index list.</li>
     *   <li>All results are assembled into an {@link ImportResult}.</li>
     * </ol>
     */
    @Override
    public ImportResult importStudents(List<Student> students) {
        log.info("importStudents() processing {} rows.", students.size());

        Map<Integer, String> rowErrors = new LinkedHashMap<>();
        List<Student> validStudents   = new ArrayList<>();
        // Maps position in validStudents back to original row index
        List<Integer> originalIndices = new ArrayList<>();

        // --- Phase 1: Per-row validation ---
        for (int i = 0; i < students.size(); i++) {
            Student student = students.get(i);
            try {
                Map<String, String> fieldErrors = validateStudentFields(student, false);
                if (!fieldErrors.isEmpty()) {
                    rowErrors.put(i, "Validation errors: " + fieldErrors);
                    log.debug("Import row {} failed validation: {}", i, fieldErrors);
                } else {
                    validStudents.add(student);
                    originalIndices.add(i);
                }
            } catch (Exception ex) {
                rowErrors.put(i, "Unexpected error: " + ex.getMessage());
                log.warn("Import row {} threw unexpected exception.", i, ex);
            }
        }

        // --- Phase 2: Batch persist valid rows ---
        List<Integer> repoFailedIndices = new ArrayList<>();
        if (!validStudents.isEmpty()) {
            repoFailedIndices = repository.saveAll(validStudents);
        }

        // --- Phase 3: Map repository failures back to original row indices ---
        for (int repoFailedIndex : repoFailedIndices) {
            int originalIndex = originalIndices.get(repoFailedIndex);
            rowErrors.put(originalIndex,
                    "Database constraint violation (possible duplicate studentId or email).");
        }

        int failureCount = rowErrors.size();
        int successCount = students.size() - failureCount;

        ImportResult result = new ImportResult(
                students.size(), successCount, failureCount, rowErrors);

        log.info("importStudents() complete: {}", result);
        return result;
    }

    // -----------------------------------------------------------------------
    // READ OPERATIONS
    // -----------------------------------------------------------------------

    /** {@inheritDoc} */
    @Override
    public List<Student> getAllStudents() {
        return repository.findAll();
    }

    /** {@inheritDoc} */
    @Override
    public Optional<Student> getStudentById(int id) {
        return repository.findById(id);
    }

    /** {@inheritDoc} */
    @Override
    public Optional<Student> getStudentByStudentId(String studentId) {
        return repository.findByStudentId(studentId);
    }

    /** {@inheritDoc} */
    @Override
    public List<Student> searchStudents(String keyword) {
        return repository.search(keyword == null ? "" : keyword);
    }

    /** {@inheritDoc} */
    @Override
    public List<Student> getStudentsByCourse(String course) {
        return repository.findByCourse(course);
    }

    /** {@inheritDoc} */
    @Override
    public List<Student> getStudentsByStatus(StudentStatus status) {
        return repository.findByStatus(status);
    }

    /** {@inheritDoc} */
    @Override
    public int getTotalStudentCount() {
        return repository.countAll();
    }

    /** {@inheritDoc} */
    @Override
    public int getStudentCountByStatus(StudentStatus status) {
        return repository.countByStatus(status);
    }

    /** {@inheritDoc} */
    @Override
    public List<String> getDistinctCourses() {
        return repository.findDistinctCourses();
    }

    /** {@inheritDoc} */
    @Override
    public double getOverallAverageGpa() {
        return repository.getAverageGpa();
    }

    /** {@inheritDoc} */
    @Override
    public double getAverageGpaByCourse(String course) {
        return repository.getAverageGpaByCourse(course);
    }

    // -----------------------------------------------------------------------
    // PRIVATE VALIDATION METHODS
    // -----------------------------------------------------------------------

    /**
     * Validates all fields of a {@link Student} object and returns a map
     * of field name → error message for every field that fails.
     *
     * <p>Returns an empty map if all fields are valid.
     *
     * <p>WHY COLLECT ALL ERRORS: If we threw on the first error, a user
     * submitting a form with 3 invalid fields would need 3 submit-fix-submit
     * cycles to discover all errors. Collecting all errors in one pass and
     * returning them together gives a far better user experience.
     *
     * @param student  the student to validate
     * @param isUpdate true if this is an update (relaxes some checks that
     *                 are only relevant on insert)
     * @return a map of field errors; empty map means all fields are valid
     */
    private Map<String, String> validateStudentFields(Student student, boolean isUpdate) {
        Map<String, String> errors = new LinkedHashMap<>();

        // --- studentId ---
        validateStudentId(student.getStudentId(), errors);

        // --- firstName ---
        validateName(student.getFirstName(), "firstName", "First name", errors);

        // --- lastName ---
        validateName(student.getLastName(), "lastName", "Last name", errors);

        // --- email ---
        validateEmail(student.getEmail(), errors);

        // --- phone (optional) ---
        validatePhone(student.getPhone(), errors);

        // --- course ---
        validateCourse(student.getCourse(), errors);

        // --- yearLevel ---
        validateYearLevel(student.getYearLevel(), errors);

        // --- gpa ---
        validateGpa(student.getGpa(), errors);

        // --- status ---
        if (student.getStatus() == null) {
            errors.put("status", "Status must not be null.");
        }

        return errors;
    }

    /**
     * Validates the student ID field.
     *
     * <p>Rules:
     * <ul>
     *   <li>Must not be blank.</li>
     *   <li>Must match the pattern: 2–5 uppercase letters, hyphen,
     *       4 digits, hyphen, 3+ digits (e.g., "STU-2024-001").</li>
     * </ul>
     *
     * @param studentId the value to validate
     * @param errors    the error map to add to on failure
     */
    /**
     * Validates the student ID field.
     *
     * <p>Rules:
     * <ul>
     *   <li>Must not be blank.</li>
     *   <li>Must not exceed {@link #STUDENT_ID_MAX_LENGTH} characters.</li>
     * </ul>
     * No format is enforced — any non-blank value up to 50 characters
     * is accepted to accommodate all international ID schemes.
     *
     * @param studentId the value to validate
     * @param errors    the error map to add to on failure
     */
    private void validateStudentId(String studentId, Map<String, String> errors) {
        if (studentId == null || studentId.isBlank()) {
            errors.put("studentId", "Student ID must not be blank.");
            return;
        }
        if (studentId.length() > STUDENT_ID_MAX_LENGTH) {
            errors.put("studentId",
                    "Student ID must not exceed " + STUDENT_ID_MAX_LENGTH
                    + " characters. Got: " + studentId.length() + ".");
        }
    }

    /**
     * Validates a name field (first name or last name).
     *
     * <p>Rules:
     * <ul>
     *   <li>Must not be blank.</li>
     *   <li>Must not exceed {@link #NAME_MAX_LENGTH} characters.</li>
     *   <li>Must contain only letters, spaces, hyphens, and apostrophes
     *       (to support names like "O'Brien" and "Mary-Jane").</li>
     * </ul>
     *
     * @param value      the name value to validate
     * @param fieldKey   the map key to use on error (e.g., "firstName")
     * @param fieldLabel the human-readable label for error messages
     * @param errors     the error map to add to on failure
     */
    private void validateName(String value, String fieldKey,
                               String fieldLabel, Map<String, String> errors) {
        if (value == null || value.isBlank()) {
            errors.put(fieldKey, fieldLabel + " must not be blank.");
            return;
        }
        if (value.length() > NAME_MAX_LENGTH) {
            errors.put(fieldKey, fieldLabel + " must not exceed "
                    + NAME_MAX_LENGTH + " characters.");
            return;
        }
        if (!value.matches("[\\p{L}\\s''-]+")) {
            errors.put(fieldKey, fieldLabel
                    + " must contain only letters, spaces, hyphens, and apostrophes.");
        }
    }

    /**
     * Validates the email field.
     *
     * <p>Rules:
     * <ul>
     *   <li>Must not be blank.</li>
     *   <li>Must match {@link #EMAIL_PATTERN}.</li>
     * </ul>
     *
     * @param email  the email value to validate
     * @param errors the error map to add to on failure
     */
    private void validateEmail(String email, Map<String, String> errors) {
        if (email == null || email.isBlank()) {
            errors.put("email", "Email must not be blank.");
            return;
        }
        if (!EMAIL_PATTERN.matcher(email).matches()) {
            errors.put("email", "Email '" + email + "' is not a valid email address.");
        }
    }

    /**
     * Validates the phone field (optional).
     *
     * <p>Phone is not required — an empty or null value is valid.
     * If provided, it must match {@link #PHONE_PATTERN}.
     *
     * @param phone  the phone value to validate (may be null or blank)
     * @param errors the error map to add to on failure
     */
    private void validatePhone(String phone, Map<String, String> errors) {
        if (phone == null || phone.isBlank()) {
            return; // Phone is optional
        }
        if (!PHONE_PATTERN.matcher(phone).matches()) {
            errors.put("phone",
                    "Phone number '" + phone + "' is not in a recognised format.");
        }
    }

    /**
     * Validates the course field.
     *
     * <p>Rules:
     * <ul>
     *   <li>Must not be blank.</li>
     *   <li>Must not exceed {@link #COURSE_MAX_LENGTH} characters.</li>
     * </ul>
     *
     * @param course the course value to validate
     * @param errors the error map to add to on failure
     */
    private void validateCourse(String course, Map<String, String> errors) {
        if (course == null || course.isBlank()) {
            errors.put("course", "Course must not be blank.");
            return;
        }
        if (course.length() > COURSE_MAX_LENGTH) {
            errors.put("course", "Course name must not exceed "
                    + COURSE_MAX_LENGTH + " characters.");
        }
    }

    /**
     * Validates the year level field.
     *
     * <p>Must be between {@link #YEAR_MIN} and {@link #YEAR_MAX} inclusive.
     *
     * @param yearLevel the year level value to validate
     * @param errors    the error map to add to on failure
     */
    private void validateYearLevel(int yearLevel, Map<String, String> errors) {
        if (yearLevel < YEAR_MIN || yearLevel > YEAR_MAX) {
            errors.put("yearLevel",
                    "Year level must be between " + YEAR_MIN
                    + " and " + YEAR_MAX + ". Got: " + yearLevel + ".");
        }
    }

    /**
     * Validates the GPA field.
     *
     * <p>Must be between {@link #GPA_MIN} and {@link #GPA_MAX} inclusive.
     * Uses a small epsilon for floating-point boundary comparison to avoid
     * rejecting values like 4.000000001 due to double precision artefacts.
     *
     * @param gpa    the GPA value to validate
     * @param errors the error map to add to on failure
     */
    private void validateGpa(double gpa, Map<String, String> errors) {
        double epsilon = 0.0001;
        if (gpa < (GPA_MIN - epsilon) || gpa > (GPA_MAX + epsilon)) {
            errors.put("gpa",
                    "GPA must be between " + GPA_MIN + " and " + GPA_MAX
                    + ". Got: " + gpa + ".");
        }
    }
}