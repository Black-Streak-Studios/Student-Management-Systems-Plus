package com.nana.sms.util;

import com.nana.sms.domain.Student;

import java.util.function.Function;

/**
 * CsvColumn â€” CSV Export Column Enumeration
 *
 * <p>WHY THIS EXISTS:
 * Hardcoding column names and their data extraction logic inside the
 * exporter class would make it impossible to give users control over
 * which columns to include in an export without a large if/else block.
 * This enum solves both problems:
 * <ul>
 *   <li>Each constant IS a column â€” it carries its header name and knows
 *       how to extract its value from a {@link Student} object.</li>
 *   <li>The UI can present a {@code CheckBox} list populated directly from
 *       {@code CsvColumn.values()} â€” type-safe, extensible, zero UI code
 *       changes needed when a new column is added.</li>
 *   <li>The exporter iterates the caller-supplied column list without
 *       any switch/if logic â€” it just calls {@code column.extract(student)}
 *       for each column in order.</li>
 * </ul>
 *
 * <p>FUNCTION FIELD:
 * Each constant holds a {@link Function}{@code <Student, String>} that
 * extracts the column value from a {@code Student}. This is the Strategy
 * pattern â€” the extraction behaviour is encapsulated in the enum constant
 * itself rather than in a separate method or switch block.
 *
 * <p>CSV SAFETY:
 * The extractor functions return raw values. The {@link CsvExporter} is
 * responsible for applying RFC 4180 quoting/escaping to these values
 * before writing them to the file. The enum does not need to know about
 * CSV formatting.
 */
public enum CsvColumn {

    // -----------------------------------------------------------------------
    // COLUMN DEFINITIONS
    // Each constant defines:
    //   1. headerName  â€” the column header written to the first CSV row
    //   2. extractor   â€” a lambda that pulls the field value from a Student
    //   3. description â€” a human-readable explanation for the UI tooltip
    // -----------------------------------------------------------------------

    /**
     * The surrogate database primary key.
     * Useful for re-importing data or joining with other exports.
     */
    ID(
        "ID",
        student -> String.valueOf(student.getId()),
        "Internal database ID"
    ),

    /**
     * The human-visible business identifier (e.g., "STU-2024-001").
     * This is the primary identifier used on transcripts and reports.
     */
    STUDENT_ID(
        "Student ID",
        Student::getStudentId,
        "Human-visible student identifier (e.g., STU-2024-001)"
    ),

    /** Student's given (first) name. */
    FIRST_NAME(
        "First Name",
        Student::getFirstName,
        "Student's given name"
    ),

    /** Student's family (last) name. */
    LAST_NAME(
        "Last Name",
        Student::getLastName,
        "Student's family name"
    ),

    /**
     * Full name as "FirstName LastName".
     * Provided as a convenience for exports destined for mail-merge or
     * reporting tools that expect a single name column.
     */
    FULL_NAME(
        "Full Name",
        Student::getFullName,
        "Combined first and last name"
    ),

    /** Contact email address. */
    EMAIL(
        "Email",
        Student::getEmail,
        "Contact email address"
    ),

    /** Contact phone number (may be empty). */
    PHONE(
        "Phone",
        Student::getPhone,
        "Contact phone number"
    ),

    /** Academic programme / course name. */
    COURSE(
        "Course",
        Student::getCourse,
        "Programme of study"
    ),

    /** Academic year level (1â€“6). */
    YEAR_LEVEL(
        "Year Level",
        student -> String.valueOf(student.getYearLevel()),
        "Current academic year (1-6)"
    ),

    /**
     * Grade Point Average formatted to 2 decimal places.
     * Uses {@link Student#getGpaFormatted()} rather than raw
     * {@code getGpa()} to avoid floating-point artefacts in the CSV
     * (e.g., "3.9000000000000004").
     */
    GPA(
        "GPA",
        Student::getGpaFormatted,
        "Grade Point Average (0.00 - 4.00)"
    ),

    /**
     * Enrollment status as the enum display name (e.g., "Active").
     * Uses {@code getStatus().getDisplayName()} rather than {@code name()}
     * so the exported value is human-readable.
     */
    STATUS(
        "Status",
        student -> student.getStatus().getDisplayName(),
        "Enrollment status (Active, Inactive, Graduated, Suspended)"
    ),

    /**
     * Enrollment date formatted as "yyyy-MM-dd HH:mm:ss".
     * ISO-adjacent format for maximum compatibility with spreadsheet
     * applications and database import tools.
     */
    ENROLLED_AT(
        "Enrolled At",
        Student::getEnrolledAtFormatted,
        "Date and time the student record was created"
    ),

    /**
     * Last update timestamp.
     * Useful for auditing and detecting stale exported data.
     */
    UPDATED_AT(
        "Updated At",
        Student::getUpdatedAtFormatted,
        "Date and time the student record was last modified"
    );

    // -----------------------------------------------------------------------
    // FIELDS
    // -----------------------------------------------------------------------

    /** The column header string written to the first row of the CSV file. */
    private final String headerName;

    /**
     * The extraction function: given a {@link Student}, returns the
     * string value for this column. Never returns null â€” uses empty
     * string for absent optional fields.
     */
    private final Function<Student, String> extractor;

    /** Human-readable description for UI tooltips and export dialogs. */
    private final String description;

    // -----------------------------------------------------------------------
    // CONSTRUCTOR
    // -----------------------------------------------------------------------

    CsvColumn(String headerName,
              Function<Student, String> extractor,
              String description) {
        this.headerName  = headerName;
        this.extractor   = extractor;
        this.description = description;
    }

    // -----------------------------------------------------------------------
    // ACCESSORS
    // -----------------------------------------------------------------------

    /**
     * Returns the column header name for the CSV first row.
     *
     * @return header string (e.g., "Student ID", "GPA")
     */
    public String getHeaderName() {
        return headerName;
    }

    /**
     * Extracts this column's value from the given student.
     *
     * <p>The returned value is a raw string â€” the caller is responsible
     * for applying CSV escaping before writing it to the file.
     *
     * @param student the student to extract the value from
     * @return the field value as a string; never null
     */
    public String extract(Student student) {
        if (student == null) return "";
        String value = extractor.apply(student);
        return value == null ? "" : value;
    }

    /**
     * Returns the human-readable description for this column.
     * Used in the export dialog to explain what each column contains.
     *
     * @return description string
     */
    public String getDescription() {
        return description;
    }

    /**
     * Returns the header name â€” used when this enum is rendered
     * in a JavaFX CheckBox list without a custom cell factory.
     *
     * @return the header name string
     */
    @Override
    public String toString() {
        return headerName;
    }

    // -----------------------------------------------------------------------
    // STATIC HELPERS
    // -----------------------------------------------------------------------

    /**
     * Returns the default set of columns for a standard export.
     *
     * <p>Excludes {@code ID} (internal DB key, not useful to end users)
     * and {@code FULL_NAME} (redundant when FIRST_NAME and LAST_NAME
     * are both included).
     *
     * <p>This is used when the user clicks "Export All" without
     * customising the column selection.
     *
     * @return array of default export columns in logical display order
     */
    public static CsvColumn[] defaultColumns() {
        return new CsvColumn[] {
            STUDENT_ID,
            FIRST_NAME,
            LAST_NAME,
            EMAIL,
            PHONE,
            COURSE,
            YEAR_LEVEL,
            GPA,
            STATUS,
            ENROLLED_AT,
            UPDATED_AT
        };
    }

    /**
     * Returns all columns that are required for a round-trip import.
     *
     * <p>These are the columns that the {@link CsvImporter} expects to
     * find in an import file. Exporting with this column set guarantees
     * the output can be re-imported without data loss.
     *
     * @return array of import-compatible columns
     */
    public static CsvColumn[] importCompatibleColumns() {
        return new CsvColumn[] {
            STUDENT_ID,
            FIRST_NAME,
            LAST_NAME,
            EMAIL,
            PHONE,
            COURSE,
            YEAR_LEVEL,
            GPA,
            STATUS
        };
    }
}

