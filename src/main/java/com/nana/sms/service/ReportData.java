package com.nana.sms.service;

import com.nana.sms.domain.Student;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * ReportData â€” Immutable Report Value Object
 *
 * <p>WHY THIS CLASS EXISTS:
 * A report is more than a list of students â€” it has a title, a generation
 * timestamp, summary statistics, per-course breakdowns, and a set of rows
 * with optional annotations (e.g., honour roll flags). Rather than passing
 * all these pieces as separate method parameters to the UI, we package
 * everything into one coherent, immutable value object.
 *
 * <p>IMMUTABILITY:
 * All collections are wrapped in {@link Collections#unmodifiableList} /
 * {@link Collections#unmodifiableMap} at construction. The UI can safely
 * share this object across threads (e.g., pass from a background Task to
 * the JavaFX Application Thread) without synchronisation concerns.
 *
 * <p>BUILDER PATTERN:
 * Because this object has many optional fields (not every report type needs
 * all fields), we use an inner {@link Builder} to construct it. This avoids
 * a telescoping constructor with 10+ parameters and makes construction
 * code at the call site readable and self-documenting.
 *
 * <p>The UI layer renders this object â€” it does not compute anything from
 * raw {@link Student} objects directly. This separation means the report
 * rendering code is simple, and all computation lives in
 * {@link ReportGenerator}.
 */
public final class ReportData {

    // -----------------------------------------------------------------------
    // DISPLAY FORMATTER
    // -----------------------------------------------------------------------

    /** Formatter for the generated-at timestamp shown in the report header. */
    public static final DateTimeFormatter DISPLAY_FORMAT =
            DateTimeFormatter.ofPattern("MMMM d, yyyy 'at' HH:mm:ss");

    // -----------------------------------------------------------------------
    // FIELDS â€” all final for immutability
    // -----------------------------------------------------------------------

    /** The type of report this data represents. */
    private final ReportType reportType;

    /** Main title of the report (e.g., "Full Student Roster"). */
    private final String title;

    /** Optional subtitle or filter description (e.g., "Course: Computer Science"). */
    private final String subtitle;

    /** Timestamp of when this report was generated. */
    private final LocalDateTime generatedAt;

    /**
     * The primary list of students this report covers.
     * May be all students, filtered by status, sorted by GPA, etc.
     * depending on the report type.
     */
    private final List<Student> students;

    /**
     * Summary statistics for the report header/footer.
     * Keys are statistic labels (e.g., "Total Students", "Average GPA"),
     * values are pre-formatted strings (e.g., "142", "3.45").
     * Ordered by insertion (LinkedHashMap) for deterministic display.
     */
    private final Map<String, String> summaryStats;

    /**
     * Per-course statistics rows.
     * Each entry is a {@link CourseStatRow} containing course name,
     * student count, and average GPA for that course.
     * Used by the COURSE_BREAKDOWN report type.
     */
    private final List<CourseStatRow> courseStats;

    /**
     * Per-student annotation flags.
     * Maps student's surrogate ID to a short annotation string
     * (e.g., 1 â†’ "ðŸ… Honour Roll", 2 â†’ "âš  At Risk").
     * Used by the GPA_REPORT type to annotate individual rows.
     */
    private final Map<Integer, String> studentAnnotations;

    // -----------------------------------------------------------------------
    // PRIVATE CONSTRUCTOR â€” use Builder
    // -----------------------------------------------------------------------

    private ReportData(Builder builder) {
        this.reportType          = builder.reportType;
        this.title               = builder.title;
        this.subtitle            = builder.subtitle;
        this.generatedAt         = builder.generatedAt;
        this.students            = Collections.unmodifiableList(
                                        List.copyOf(builder.students));
        this.summaryStats        = Collections.unmodifiableMap(
                                        Map.copyOf(builder.summaryStats));
        this.courseStats         = Collections.unmodifiableList(
                                        List.copyOf(builder.courseStats));
        this.studentAnnotations  = Collections.unmodifiableMap(
                                        Map.copyOf(builder.studentAnnotations));
    }

    // -----------------------------------------------------------------------
    // ACCESSORS
    // -----------------------------------------------------------------------

    /** @return the {@link ReportType} enum constant for this report */
    public ReportType getReportType()          { return reportType; }

    /** @return the main report title */
    public String getTitle()                   { return title; }

    /** @return the subtitle / filter description (may be empty) */
    public String getSubtitle()                { return subtitle; }

    /** @return the timestamp when this report was generated */
    public LocalDateTime getGeneratedAt()      { return generatedAt; }

    /**
     * Returns the generation timestamp formatted for display in the
     * report header (e.g., "January 15, 2025 at 14:32:00").
     *
     * @return formatted timestamp string
     */
    public String getGeneratedAtFormatted() {
        return generatedAt == null ? "" : generatedAt.format(DISPLAY_FORMAT);
    }

    /** @return the unmodifiable list of students in this report */
    public List<Student> getStudents()         { return students; }

    /** @return total number of student rows in this report */
    public int getStudentCount()               { return students.size(); }

    /**
     * Returns the unmodifiable summary statistics map.
     * Keys are labels, values are formatted display strings.
     *
     * @return summary stats map (insertion-ordered)
     */
    public Map<String, String> getSummaryStats() { return summaryStats; }

    /** @return the unmodifiable list of per-course statistics rows */
    public List<CourseStatRow> getCourseStats()  { return courseStats; }

    /**
     * Returns the annotation string for a specific student, or an empty
     * string if the student has no annotation.
     *
     * @param studentDbId the surrogate DB id of the student
     * @return annotation string (e.g., "ðŸ… Honour Roll") or ""
     */
    public String getAnnotation(int studentDbId) {
        return studentAnnotations.getOrDefault(studentDbId, "");
    }

    /** @return the full unmodifiable annotation map */
    public Map<Integer, String> getStudentAnnotations() { return studentAnnotations; }

    /**
     * Returns a one-line description of this report for logging and
     * status bar display.
     *
     * @return summary string
     */
    @Override
    public String toString() {
        return "ReportData{type=" + reportType
               + ", students=" + students.size()
               + ", generatedAt=" + generatedAt + "}";
    }

    // -----------------------------------------------------------------------
    // INNER CLASS: CourseStatRow
    // -----------------------------------------------------------------------

    /**
     * CourseStatRow â€” Value Object for Per-Course Statistics
     *
     * <p>WHY A NESTED CLASS: This data structure is only meaningful in the
     * context of a {@code ReportData} object. Nesting it here keeps related
     * code together and prevents namespace pollution with a class that has
     * no use outside of reports.
     *
     * <p>Immutable â€” all fields set at construction.
     */
    public static final class CourseStatRow {

        private final String courseName;
        private final int    studentCount;
        private final double averageGpa;
        private final int    activeCount;
        private final int    graduatedCount;

        /**
         * Constructs a {@code CourseStatRow}.
         *
         * @param courseName     name of the academic programme
         * @param studentCount   total students enrolled in this course
         * @param averageGpa     average GPA across all students in the course
         * @param activeCount    number of ACTIVE students in the course
         * @param graduatedCount number of GRADUATED students in the course
         */
        public CourseStatRow(String courseName,
                             int studentCount,
                             double averageGpa,
                             int activeCount,
                             int graduatedCount) {
            this.courseName     = courseName;
            this.studentCount   = studentCount;
            this.averageGpa     = averageGpa;
            this.activeCount    = activeCount;
            this.graduatedCount = graduatedCount;
        }

        /** @return the course name */
        public String getCourseName()     { return courseName; }

        /** @return total student count for this course */
        public int getStudentCount()      { return studentCount; }

        /** @return average GPA for this course */
        public double getAverageGpa()     { return averageGpa; }

        /** @return formatted average GPA (2 decimal places) */
        public String getAverageGpaFormatted() {
            return String.format("%.2f", averageGpa);
        }

        /** @return count of ACTIVE students in this course */
        public int getActiveCount()       { return activeCount; }

        /** @return count of GRADUATED students in this course */
        public int getGraduatedCount()    { return graduatedCount; }

        @Override
        public String toString() {
            return "CourseStatRow{course='" + courseName
                   + "', count=" + studentCount
                   + ", avgGpa=" + getAverageGpaFormatted() + "}";
        }
    }

    // -----------------------------------------------------------------------
    // INNER CLASS: Builder
    // -----------------------------------------------------------------------

    /**
     * Builder â€” Fluent Constructor for {@link ReportData}
     *
     * <p>WHY A BUILDER:
     * {@code ReportData} has 8 fields, several of which are optional
     * depending on the report type (e.g., courseStats is only meaningful
     * for COURSE_BREAKDOWN). A single constructor with 8 parameters is
     * hard to read and error-prone. The builder pattern gives us:
     * <ul>
     *   <li>Named parameters (self-documenting call sites).</li>
     *   <li>Optional fields with sensible defaults.</li>
     *   <li>Immutability of the built object.</li>
     * </ul>
     */
    public static final class Builder {

        // Required fields
        private final ReportType reportType;
        private final String title;

        // Optional fields with defaults
        private String subtitle                        = "";
        private LocalDateTime generatedAt             = LocalDateTime.now();
        private List<Student> students                = new java.util.ArrayList<>();
        private Map<String, String> summaryStats      = new java.util.LinkedHashMap<>();
        private List<CourseStatRow> courseStats       = new java.util.ArrayList<>();
        private Map<Integer, String> studentAnnotations = new java.util.LinkedHashMap<>();

        /**
         * Starts building a {@code ReportData} with required fields.
         *
         * @param reportType the type of report being built
         * @param title      the main report title
         */
        public Builder(ReportType reportType, String title) {
            this.reportType = reportType;
            this.title      = title;
        }

        /**
         * Sets the subtitle / filter description.
         *
         * @param subtitle description string (e.g., "Course: Computer Science")
         * @return this builder for chaining
         */
        public Builder subtitle(String subtitle) {
            this.subtitle = subtitle == null ? "" : subtitle;
            return this;
        }

        /**
         * Sets the report generation timestamp.
         * Defaults to {@link LocalDateTime#now()} if not called.
         *
         * @param generatedAt the timestamp to record
         * @return this builder for chaining
         */
        public Builder generatedAt(LocalDateTime generatedAt) {
            this.generatedAt = generatedAt == null ? LocalDateTime.now() : generatedAt;
            return this;
        }

        /**
         * Sets the primary student list for this report.
         *
         * @param students the list of students; must not be null
         * @return this builder for chaining
         */
        public Builder students(List<Student> students) {
            this.students = students == null ? new java.util.ArrayList<>() : students;
            return this;
        }

        /**
         * Sets the summary statistics map.
         *
         * @param summaryStats map of label â†’ formatted value
         * @return this builder for chaining
         */
        public Builder summaryStats(Map<String, String> summaryStats) {
            this.summaryStats = summaryStats == null
                    ? new java.util.LinkedHashMap<>() : summaryStats;
            return this;
        }

        /**
         * Adds a single summary statistic entry.
         *
         * @param label the statistic label (e.g., "Total Students")
         * @param value the pre-formatted value string (e.g., "142")
         * @return this builder for chaining
         */
        public Builder addStat(String label, String value) {
            this.summaryStats.put(label, value);
            return this;
        }

        /**
         * Sets the per-course statistics rows.
         *
         * @param courseStats list of {@link CourseStatRow} objects
         * @return this builder for chaining
         */
        public Builder courseStats(List<CourseStatRow> courseStats) {
            this.courseStats = courseStats == null
                    ? new java.util.ArrayList<>() : courseStats;
            return this;
        }

        /**
         * Sets the per-student annotation map.
         *
         * @param annotations map of student DB id â†’ annotation string
         * @return this builder for chaining
         */
        public Builder studentAnnotations(Map<Integer, String> annotations) {
            this.studentAnnotations = annotations == null
                    ? new java.util.LinkedHashMap<>() : annotations;
            return this;
        }

        /**
         * Builds and returns the immutable {@link ReportData} object.
         *
         * @return the constructed {@code ReportData}
         */
        public ReportData build() {
            return new ReportData(this);
        }
    }
}

