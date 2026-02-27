package com.nana.sms.domain;

import javafx.beans.property.DoubleProperty;
import javafx.beans.property.IntegerProperty;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleDoubleProperty;
import javafx.beans.property.SimpleIntegerProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Objects;

/**
 * Student â€” Core Domain Entity
 *
 * <p>WHY THIS CLASS EXISTS:
 * {@code Student} is the central business object of the entire application.
 * It encapsulates all data that describes a student record and provides the
 * contract that every layer (repository, service, UI) programs against.
 *
 * <p>ARCHITECTURAL DECISION â€” JavaFX Properties in the Domain:
 * Normally, domain objects should be plain Java objects (POJOs) with no UI
 * framework dependencies. However, for a desktop JavaFX application this
 * rule is pragmatically relaxed because:
 * <ol>
 *   <li>JavaFX {@link StringProperty}, {@link IntegerProperty}, etc. are
 *       just observable wrappers around plain values â€” they carry no UI state
 *       themselves (no widgets, no rendering logic).</li>
 *   <li>Using them allows {@code TableView} columns to bind directly to
 *       properties via {@code PropertyValueFactory} with zero boilerplate
 *       in controller code.</li>
 *   <li>The service and repository layers access only plain getter/setter
 *       methods, so they remain entirely unaware of the JavaFX property
 *       system.</li>
 * </ol>
 * If this application were ever ported to a web or CLI frontend, the only
 * change needed would be replacing the property fields with plain fields â€”
 * the service and repository layers would not change at all.
 *
 * <p>FIELD OVERVIEW:
 * <pre>
 *   id          â€” Auto-incremented surrogate PK from SQLite (0 = unsaved)
 *   studentId   â€” Human-visible business key  (e.g., "STU-2024-001")
 *   firstName   â€” Given name
 *   lastName    â€” Family name
 *   email       â€” Contact email, must be unique
 *   phone       â€” Optional contact phone number
 *   course      â€” Programme of study (e.g., "Computer Science")
 *   yearLevel   â€” Academic year 1â€“6
 *   gpa         â€” Grade point average 0.0â€“4.0
 *   status      â€” Enrollment status (ACTIVE, INACTIVE, GRADUATED, SUSPENDED)
 *   enrolledAt  â€” Timestamp of record creation
 *   updatedAt   â€” Timestamp of last modification
 * </pre>
 */
public class Student {

    // -----------------------------------------------------------------------
    // CONSTANTS
    // -----------------------------------------------------------------------

    /**
     * ISO-8601 datetime format used for serialising timestamps to/from SQLite.
     * SQLite has no native DATETIME type â€” we store as TEXT in this format.
     * Using a constant here guarantees the same format is used everywhere.
     */
    public static final DateTimeFormatter TIMESTAMP_FORMAT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    // -----------------------------------------------------------------------
    // JAVAFX PROPERTIES
    // -----------------------------------------------------------------------

    /**
     * Surrogate primary key assigned by SQLite AUTOINCREMENT.
     * Value of 0 indicates this student has not yet been persisted.
     * We use IntegerProperty so the UI can bind to it if needed.
     */
    private final IntegerProperty id = new SimpleIntegerProperty(0);

    /**
     * Human-visible business identifier (e.g., "STU-2024-001").
     * This is what appears on transcripts and reports â€” it is distinct
     * from the DB surrogate key to decouple display IDs from DB internals.
     */
    private final StringProperty studentId = new SimpleStringProperty("");

    /** Student's given (first) name. */
    private final StringProperty firstName = new SimpleStringProperty("");

    /** Student's family (last) name. */
    private final StringProperty lastName = new SimpleStringProperty("");

    /**
     * Contact email address. Must be unique across all students.
     * Validated by the service layer; uniqueness enforced by DB UNIQUE constraint.
     */
    private final StringProperty email = new SimpleStringProperty("");

    /**
     * Optional contact phone number. No format enforcement at the domain level â€”
     * the service layer applies format validation.
     */
    private final StringProperty phone = new SimpleStringProperty("");

    /**
     * Name of the academic programme / course the student is enrolled in
     * (e.g., "Bachelor of Computer Science", "Diploma in Business").
     */
    private final StringProperty course = new SimpleStringProperty("");

    /**
     * Current academic year level (1 through 6).
     * The CHECK constraint in the DB mirrors this range.
     */
    private final IntegerProperty yearLevel = new SimpleIntegerProperty(1);

    /**
     * Grade Point Average on a 0.0â€“4.0 scale.
     * The DB CHECK constraint mirrors this range.
     */
    private final DoubleProperty gpa = new SimpleDoubleProperty(0.0);

    /**
     * Current enrollment status. Stored as the enum's {@code name()}
     * string in the database (e.g., "ACTIVE").
     */
    private final ObjectProperty<StudentStatus> status =
            new SimpleObjectProperty<>(StudentStatus.ACTIVE);

    /**
     * Timestamp of when this student record was first created.
     * Set once on insert; never updated.
     */
    private final ObjectProperty<LocalDateTime> enrolledAt =
            new SimpleObjectProperty<>(LocalDateTime.now());

    /**
     * Timestamp of the most recent modification to this record.
     * Updated on every save/update operation.
     */
    private final ObjectProperty<LocalDateTime> updatedAt =
            new SimpleObjectProperty<>(LocalDateTime.now());

    // -----------------------------------------------------------------------
    // CONSTRUCTORS
    // -----------------------------------------------------------------------

    /**
     * Default no-argument constructor required by JavaFX and for creating
     * new student instances before populating fields.
     */
    public Student() {
        // All properties initialised with defaults above
    }

    /**
     * Full constructor for creating a complete {@code Student} object, typically
     * used when mapping a database row back to a domain object in the repository.
     *
     * @param id          surrogate DB primary key (0 if not yet persisted)
     * @param studentId   human-visible business identifier
     * @param firstName   given name
     * @param lastName    family name
     * @param email       email address
     * @param phone       phone number (may be null or empty)
     * @param course      programme name
     * @param yearLevel   academic year (1â€“6)
     * @param gpa         grade point average (0.0â€“4.0)
     * @param status      enrollment status
     * @param enrolledAt  record creation timestamp
     * @param updatedAt   last modification timestamp
     */
    public Student(int id,
                   String studentId,
                   String firstName,
                   String lastName,
                   String email,
                   String phone,
                   String course,
                   int yearLevel,
                   double gpa,
                   StudentStatus status,
                   LocalDateTime enrolledAt,
                   LocalDateTime updatedAt) {

        setId(id);
        setStudentId(studentId);
        setFirstName(firstName);
        setLastName(lastName);
        setEmail(email);
        setPhone(phone);
        setCourse(course);
        setYearLevel(yearLevel);
        setGpa(gpa);
        setStatus(status);
        setEnrolledAt(enrolledAt);
        setUpdatedAt(updatedAt);
    }

    // -----------------------------------------------------------------------
    // JAVAFX PROPERTY ACCESSORS
    // These are required by JavaFX's PropertyValueFactory for TableView binding.
    // Convention: the method name must be exactly <fieldName>Property().
    // -----------------------------------------------------------------------

    /** @return the JavaFX {@link IntegerProperty} for the surrogate PK */
    public IntegerProperty idProperty()         { return id; }

    /** @return the JavaFX {@link StringProperty} for the business student ID */
    public StringProperty studentIdProperty()   { return studentId; }

    /** @return the JavaFX {@link StringProperty} for first name */
    public StringProperty firstNameProperty()   { return firstName; }

    /** @return the JavaFX {@link StringProperty} for last name */
    public StringProperty lastNameProperty()    { return lastName; }

    /** @return the JavaFX {@link StringProperty} for email */
    public StringProperty emailProperty()       { return email; }

    /** @return the JavaFX {@link StringProperty} for phone */
    public StringProperty phoneProperty()       { return phone; }

    /** @return the JavaFX {@link StringProperty} for course */
    public StringProperty courseProperty()      { return course; }

    /** @return the JavaFX {@link IntegerProperty} for year level */
    public IntegerProperty yearLevelProperty()  { return yearLevel; }

    /** @return the JavaFX {@link DoubleProperty} for GPA */
    public DoubleProperty gpaProperty()         { return gpa; }

    /** @return the JavaFX {@link ObjectProperty} for enrollment status */
    public ObjectProperty<StudentStatus> statusProperty() { return status; }

    /** @return the JavaFX {@link ObjectProperty} for enrollment timestamp */
    public ObjectProperty<LocalDateTime> enrolledAtProperty() { return enrolledAt; }

    /** @return the JavaFX {@link ObjectProperty} for last-updated timestamp */
    public ObjectProperty<LocalDateTime> updatedAtProperty()  { return updatedAt; }

    // -----------------------------------------------------------------------
    // PLAIN JAVA GETTERS
    // Used by the service layer, repository layer, CSV utilities, and tests.
    // These are deliberately separate from the property accessors so that
    // non-JavaFX code does not need to call .get() on every property access.
    // -----------------------------------------------------------------------

    /** @return surrogate database primary key (0 = not yet persisted) */
    public int getId()              { return id.get(); }

    /** @return the human-visible student business identifier */
    public String getStudentId()    { return studentId.get(); }

    /** @return the student's given (first) name */
    public String getFirstName()    { return firstName.get(); }

    /** @return the student's family (last) name */
    public String getLastName()     { return lastName.get(); }

    /** @return the student's full name as "FirstName LastName" */
    public String getFullName()     { return firstName.get() + " " + lastName.get(); }

    /** @return the student's email address */
    public String getEmail()        { return email.get(); }

    /** @return the student's phone number (may be empty string) */
    public String getPhone()        { return phone.get(); }

    /** @return the programme / course name */
    public String getCourse()       { return course.get(); }

    /** @return the academic year level (1â€“6) */
    public int getYearLevel()       { return yearLevel.get(); }

    /** @return the grade point average (0.0â€“4.0) */
    public double getGpa()          { return gpa.get(); }

    /** @return the enrollment status enum constant */
    public StudentStatus getStatus(){ return status.get(); }

    /** @return the record creation timestamp */
    public LocalDateTime getEnrolledAt() { return enrolledAt.get(); }

    /** @return the last modification timestamp */
    public LocalDateTime getUpdatedAt()  { return updatedAt.get(); }

    // -----------------------------------------------------------------------
    // PLAIN JAVA SETTERS
    // The service layer calls these after validation passes.
    // -----------------------------------------------------------------------

    /** @param id surrogate DB primary key */
    public void setId(int id)               { this.id.set(id); }

    /** @param studentId human-visible business identifier */
    public void setStudentId(String studentId) {
        this.studentId.set(studentId == null ? "" : studentId.trim());
    }

    /** @param firstName given name */
    public void setFirstName(String firstName) {
        this.firstName.set(firstName == null ? "" : firstName.trim());
    }

    /** @param lastName family name */
    public void setLastName(String lastName) {
        this.lastName.set(lastName == null ? "" : lastName.trim());
    }

    /** @param email contact email address */
    public void setEmail(String email) {
        this.email.set(email == null ? "" : email.trim().toLowerCase());
    }

    /** @param phone contact phone number (null treated as empty string) */
    public void setPhone(String phone) {
        this.phone.set(phone == null ? "" : phone.trim());
    }

    /** @param course programme / course name */
    public void setCourse(String course) {
        this.course.set(course == null ? "" : course.trim());
    }

    /** @param yearLevel academic year (1â€“6) */
    public void setYearLevel(int yearLevel) { this.yearLevel.set(yearLevel); }

    /** @param gpa grade point average (0.0â€“4.0) */
    public void setGpa(double gpa)          { this.gpa.set(gpa); }

    /** @param status enrollment status enum constant */
    public void setStatus(StudentStatus status) {
        this.status.set(status == null ? StudentStatus.ACTIVE : status);
    }

    /** @param enrolledAt record creation timestamp */
    public void setEnrolledAt(LocalDateTime enrolledAt) {
        this.enrolledAt.set(enrolledAt == null ? LocalDateTime.now() : enrolledAt);
    }

    /** @param updatedAt last modification timestamp */
    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt.set(updatedAt == null ? LocalDateTime.now() : updatedAt);
    }

    // -----------------------------------------------------------------------
    // CONVENIENCE METHODS
    // -----------------------------------------------------------------------

    /**
     * Returns the {@code enrolledAt} timestamp formatted as a display string.
     * Useful in TableView cell factories and report generators without
     * requiring them to import or use {@code DateTimeFormatter} directly.
     *
     * @return formatted enrollment date string (e.g., "2024-09-01 09:00:00")
     */
    public String getEnrolledAtFormatted() {
        LocalDateTime dt = enrolledAt.get();
        return dt == null ? "" : dt.format(TIMESTAMP_FORMAT);
    }

    /**
     * Returns the {@code updatedAt} timestamp formatted as a display string.
     *
     * @return formatted last-updated string (e.g., "2025-01-15 14:32:00")
     */
    public String getUpdatedAtFormatted() {
        LocalDateTime dt = updatedAt.get();
        return dt == null ? "" : dt.format(TIMESTAMP_FORMAT);
    }

    /**
     * Returns a GPA formatted to two decimal places for display.
     * Avoids raw double rendering artefacts like "3.9000000000000004".
     *
     * @return GPA string (e.g., "3.75")
     */
    public String getGpaFormatted() {
        return String.format("%.2f", gpa.get());
    }

    // -----------------------------------------------------------------------
    // OBJECT IDENTITY
    // -----------------------------------------------------------------------

    /**
     * Two {@code Student} objects are considered equal if they share the
     * same {@code studentId} business key. We deliberately exclude the
     * surrogate {@code id} from equality because:
     * <ul>
     *   <li>An unsaved student (id=0) and a saved student with the same
     *       studentId represent the same real-world entity.</li>
     *   <li>Equality based on business keys is the correct DDD pattern.</li>
     * </ul>
     *
     * @param o the other object
     * @return true if both students share the same {@code studentId}
     */
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Student other)) return false;
        return Objects.equals(getStudentId(), other.getStudentId());
    }

    /**
     * Hash code is consistent with {@code equals} â€” based on {@code studentId}.
     *
     * @return hash of the studentId string
     */
    @Override
    public int hashCode() {
        return Objects.hash(getStudentId());
    }

    /**
     * Human-readable representation for logging and debugging.
     * Does NOT expose sensitive fields like email or phone to avoid
     * accidental leakage in log files.
     *
     * @return a concise description of the student
     */
    @Override
    public String toString() {
        return "Student{" +
               "id=" + getId() +
               ", studentId='" + getStudentId() + '\'' +
               ", name='" + getFullName() + '\'' +
               ", course='" + getCourse() + '\'' +
               ", year=" + getYearLevel() +
               ", gpa=" + getGpaFormatted() +
               ", status=" + getStatus() +
               '}';
    }
}

