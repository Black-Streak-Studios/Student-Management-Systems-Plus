package com.nana.sms.service;

/**
 * ReportType â€” Service Layer Enumeration
 *
 * <p>WHY THIS EXISTS:
 * Representing report types as raw strings (e.g., "FULL_ROSTER") is fragile
 * and makes it impossible to exhaustively handle all types in a switch
 * expression. This enum gives the UI a type-safe way to request a specific
 * report and allows {@link ReportGenerator} to use an exhaustive switch.
 *
 * <p>Each constant carries a human-readable {@code displayName} for UI labels
 * and a {@code description} for tooltip text or a report header subtitle.
 */
public enum ReportType {

    /**
     * Lists every student in the system with all their details.
     * Ordered alphabetically by last name.
     */
    FULL_ROSTER(
            "Full Student Roster",
            "Complete list of all students with full details."
    ),

    /**
     * Summary statistics across the entire student population:
     * total count, counts per status, average GPA, course breakdown.
     */
    SUMMARY_STATISTICS(
            "Summary Statistics",
            "Aggregate statistics across all students and courses."
    ),

    /**
     * Lists only currently active students.
     * Useful for attendance tracking and tutor load reports.
     */
    ACTIVE_STUDENTS(
            "Active Students",
            "All students with ACTIVE enrollment status."
    ),

    /**
     * Lists students who have graduated.
     * Useful for alumni records and completion rate reporting.
     */
    GRADUATED_STUDENTS(
            "Graduated Students",
            "All students who have completed their programme."
    ),

    /**
     * Academic performance report: all students sorted by GPA descending,
     * with honour roll and at-risk flags applied.
     */
    GPA_REPORT(
            "GPA / Academic Performance",
            "Students ranked by GPA with honour roll and at-risk indicators."
    ),

    /**
     * Breaks down student counts and average GPAs by course.
     * One section per course, sorted alphabetically.
     */
    COURSE_BREAKDOWN(
            "Course Breakdown",
            "Student counts and average GPA grouped by course."
    );

    // -----------------------------------------------------------------------
    // FIELDS
    // -----------------------------------------------------------------------

    /** Human-readable name for UI labels and report headers. */
    private final String displayName;

    /** Brief description for tooltips and report subtitles. */
    private final String description;

    // -----------------------------------------------------------------------
    // CONSTRUCTOR
    // -----------------------------------------------------------------------

    ReportType(String displayName, String description) {
        this.displayName = displayName;
        this.description = description;
    }

    // -----------------------------------------------------------------------
    // ACCESSORS
    // -----------------------------------------------------------------------

    /**
     * Returns the display name for this report type.
     *
     * @return display name (e.g., "Full Student Roster")
     */
    public String getDisplayName() {
        return displayName;
    }

    /**
     * Returns the description for this report type.
     *
     * @return description string
     */
    public String getDescription() {
        return description;
    }

    /**
     * Returns the display name â€” used when this enum is rendered
     * in a JavaFX ComboBox without a custom cell factory.
     *
     * @return the display name string
     */
    @Override
    public String toString() {
        return displayName;
    }
}

