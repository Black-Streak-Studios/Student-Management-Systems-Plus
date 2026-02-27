package com.nana.sms.service;

import com.nana.sms.domain.Student;
import com.nana.sms.domain.StudentStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * ReportGenerator â€” Service Layer Report Assembly
 *
 * <p>WHY THIS CLASS EXISTS:
 * Report generation involves querying multiple service methods, aggregating
 * results, sorting, filtering, annotating rows, and formatting statistics.
 * Putting this logic in a controller would violate the "no business logic
 * in the UI" rule. Putting it in the service layer directly would bloat
 * {@link StudentServiceImpl}. A dedicated generator class gives this
 * concern its own home while keeping it firmly in the service layer.
 *
 * <p>RESPONSIBILITIES:
 * <ul>
 *   <li>Accept a {@link ReportType} and optional parameters.</li>
 *   <li>Query {@link StudentService} for the data needed.</li>
 *   <li>Aggregate, sort, and annotate the data.</li>
 *   <li>Assemble and return a fully computed {@link ReportData} object.</li>
 * </ul>
 *
 * <p>THIS CLASS DOES NOT:
 * <ul>
 *   <li>Render HTML, generate PDF bytes, or write CSV files.</li>
 *   <li>Touch any JavaFX classes.</li>
 *   <li>Execute SQL directly â€” all data comes through the service.</li>
 * </ul>
 *
 * <p>ANNOTATION THRESHOLDS:
 * <ul>
 *   <li>Honour Roll: GPA &ge; 3.5</li>
 *   <li>At Risk: GPA &lt; 2.0</li>
 * </ul>
 * These thresholds are constants so they can be found and changed in one
 * place without hunting through switch cases.
 */
public class ReportGenerator {

    private static final Logger log = LoggerFactory.getLogger(ReportGenerator.class);

    // -----------------------------------------------------------------------
    // ANNOTATION THRESHOLDS
    // -----------------------------------------------------------------------

    /** Students with GPA at or above this threshold receive "Honour Roll". */
    private static final double HONOUR_ROLL_GPA = 3.5;

    /** Students with GPA below this threshold receive "At Risk" warning. */
    private static final double AT_RISK_GPA = 2.0;

    /** Honour Roll annotation label. */
    private static final String ANNOTATION_HONOUR = "\uD83C\uDFC5 Honour Roll";

    /** At-Risk annotation label. */
    private static final String ANNOTATION_AT_RISK = "\u26A0 At Risk";

    // -----------------------------------------------------------------------
    // DEPENDENCY
    // -----------------------------------------------------------------------

    /**
     * The service used to fetch student data.
     * Injected via constructor to allow mocking in tests.
     */
    private final StudentService studentService;

    // -----------------------------------------------------------------------
    // CONSTRUCTOR
    // -----------------------------------------------------------------------

    /**
     * Creates a {@code ReportGenerator} backed by the given service.
     *
     * @param studentService the service to query for student data;
     *                       must not be null
     * @throws IllegalArgumentException if studentService is null
     */
    public ReportGenerator(StudentService studentService) {
        if (studentService == null) {
            throw new IllegalArgumentException("StudentService must not be null.");
        }
        this.studentService = studentService;
        log.debug("ReportGenerator instantiated.");
    }

    // -----------------------------------------------------------------------
    // PUBLIC API
    // -----------------------------------------------------------------------

    /**
     * Generates a {@link ReportData} object for the given report type.
     *
     * <p>This is the primary entry point for report generation. The UI
     * calls this method (typically from a background {@link javafx.concurrent.Task})
     * and receives a fully assembled {@link ReportData} ready for rendering.
     *
     * @param type the type of report to generate; must not be null
     * @return a fully computed, immutable {@link ReportData} object
     * @throws IllegalArgumentException if type is null
     */
    public ReportData generate(ReportType type) {
        if (type == null) {
            throw new IllegalArgumentException("ReportType must not be null.");
        }
        log.info("Generating report: {}", type);

        ReportData report = switch (type) {
            case FULL_ROSTER        -> generateFullRoster();
            case SUMMARY_STATISTICS -> generateSummaryStatistics();
            case ACTIVE_STUDENTS    -> generateStatusReport(
                                            StudentStatus.ACTIVE,
                                            type);
            case GRADUATED_STUDENTS -> generateStatusReport(
                                            StudentStatus.GRADUATED,
                                            type);
            case GPA_REPORT         -> generateGpaReport();
            case COURSE_BREAKDOWN   -> generateCourseBreakdown();
        };

        log.info("Report generation complete: {}", report);
        return report;
    }

    /**
     * Generates a course-specific report using the COURSE_BREAKDOWN type,
     * filtered to a single course.
     *
     * <p>This overload supports the UI's "Filter by Course" selector on
     * the reports screen.
     *
     * @param courseName the course to filter by; must not be null or blank
     * @return a {@link ReportData} scoped to the specified course
     * @throws IllegalArgumentException if courseName is blank
     */
    public ReportData generateForCourse(String courseName) {
        if (courseName == null || courseName.isBlank()) {
            throw new IllegalArgumentException("Course name must not be blank.");
        }
        log.info("Generating course-specific report for: {}", courseName);

        List<Student> courseStudents = studentService.getStudentsByCourse(courseName);
        double avgGpa = studentService.getAverageGpaByCourse(courseName);

        Map<String, String> stats = new LinkedHashMap<>();
        stats.put("Course",          courseName);
        stats.put("Total Students",  String.valueOf(courseStudents.size()));
        stats.put("Average GPA",     String.format("%.2f", avgGpa));
        stats.put("Active Students", String.valueOf(countByStatus(courseStudents, StudentStatus.ACTIVE)));
        stats.put("Graduated",       String.valueOf(countByStatus(courseStudents, StudentStatus.GRADUATED)));

        return new ReportData.Builder(ReportType.COURSE_BREAKDOWN,
                "Course Report: " + courseName)
                .subtitle("Filtered to course: " + courseName)
                .generatedAt(LocalDateTime.now())
                .students(sortByLastName(courseStudents))
                .summaryStats(stats)
                .studentAnnotations(buildAnnotations(courseStudents))
                .build();
    }

    // -----------------------------------------------------------------------
    // PRIVATE REPORT BUILDERS
    // -----------------------------------------------------------------------

    /**
     * Builds the FULL_ROSTER report â€” all students sorted alphabetically.
     *
     * <p>Includes summary stats: total count, active/inactive/graduated/
     * suspended breakdowns, and overall average GPA.
     *
     * @return assembled {@link ReportData}
     */
    private ReportData generateFullRoster() {
        List<Student> all = studentService.getAllStudents();
        double avgGpa     = studentService.getOverallAverageGpa();

        Map<String, String> stats = buildStatusBreakdownStats(all, avgGpa);

        return new ReportData.Builder(ReportType.FULL_ROSTER,
                ReportType.FULL_ROSTER.getDisplayName())
                .subtitle("All students â€” sorted by last name")
                .generatedAt(LocalDateTime.now())
                .students(sortByLastName(all))
                .summaryStats(stats)
                .studentAnnotations(buildAnnotations(all))
                .build();
    }

    /**
     * Builds the SUMMARY_STATISTICS report â€” aggregate numbers with no
     * individual student rows.
     *
     * <p>The student list is populated (for potential CSV export) but the
     * primary content is the summaryStats and courseStats maps.
     *
     * @return assembled {@link ReportData}
     */
    private ReportData generateSummaryStatistics() {
        List<Student> all = studentService.getAllStudents();
        double avgGpa     = studentService.getOverallAverageGpa();
        List<String> courses = studentService.getDistinctCourses();

        // --- Global stats ---
        Map<String, String> stats = buildStatusBreakdownStats(all, avgGpa);
        stats.put("Number of Courses", String.valueOf(courses.size()));

        // Honour roll and at-risk counts
        long honourCount = all.stream()
                .filter(s -> s.getGpa() >= HONOUR_ROLL_GPA)
                .count();
        long atRiskCount = all.stream()
                .filter(s -> s.getGpa() < AT_RISK_GPA
                          && s.getStatus() == StudentStatus.ACTIVE)
                .count();
        stats.put("Honour Roll Students", String.valueOf(honourCount));
        stats.put("At-Risk Students",     String.valueOf(atRiskCount));

        // --- Per-course stats rows ---
        List<ReportData.CourseStatRow> courseRows = buildCourseStatRows(all, courses);

        return new ReportData.Builder(ReportType.SUMMARY_STATISTICS,
                ReportType.SUMMARY_STATISTICS.getDisplayName())
                .subtitle("Aggregate statistics across all students and courses")
                .generatedAt(LocalDateTime.now())
                .students(all)
                .summaryStats(stats)
                .courseStats(courseRows)
                .build();
    }

    /**
     * Builds a status-filtered report (ACTIVE_STUDENTS or GRADUATED_STUDENTS).
     *
     * <p>Reuses this method for both status types to avoid duplication â€”
     * the only differences are the status filter, title, and subtitle.
     *
     * @param status the status to filter by
     * @param type   the report type enum constant for the title
     * @return assembled {@link ReportData}
     */
    private ReportData generateStatusReport(StudentStatus status, ReportType type) {
        List<Student> filtered = studentService.getStudentsByStatus(status);
        double avgGpa = filtered.isEmpty() ? 0.0
                : filtered.stream().mapToDouble(Student::getGpa).average().orElse(0.0);

        Map<String, String> stats = new LinkedHashMap<>();
        stats.put("Total Students",  String.valueOf(filtered.size()));
        stats.put("Status Filter",   status.getDisplayName());
        stats.put("Average GPA",     String.format("%.2f", avgGpa));
        stats.put("Highest GPA",     filtered.isEmpty() ? "N/A"
                : String.format("%.2f",
                    filtered.stream().mapToDouble(Student::getGpa).max().orElse(0.0)));
        stats.put("Lowest GPA",      filtered.isEmpty() ? "N/A"
                : String.format("%.2f",
                    filtered.stream().mapToDouble(Student::getGpa).min().orElse(0.0)));

        return new ReportData.Builder(type, type.getDisplayName())
                .subtitle("Filtered by status: " + status.getDisplayName())
                .generatedAt(LocalDateTime.now())
                .students(sortByLastName(filtered))
                .summaryStats(stats)
                .studentAnnotations(buildAnnotations(filtered))
                .build();
    }

    /**
     * Builds the GPA_REPORT â€” all students sorted by GPA descending,
     * with honour roll and at-risk annotations applied.
     *
     * <p>Only ACTIVE students are considered for at-risk flagging.
     * Graduated students with low historical GPA should not be flagged.
     *
     * @return assembled {@link ReportData}
     */
    private ReportData generateGpaReport() {
        List<Student> all = studentService.getAllStudents();

        // Sort by GPA descending, then last name ascending as tiebreaker
        List<Student> sorted = all.stream()
                .sorted(Comparator.comparingDouble(Student::getGpa).reversed()
                        .thenComparing(Student::getLastName))
                .collect(Collectors.toList());

        double avgGpa = studentService.getOverallAverageGpa();

        long honourCount = sorted.stream()
                .filter(s -> s.getGpa() >= HONOUR_ROLL_GPA).count();
        long atRiskCount = sorted.stream()
                .filter(s -> s.getGpa() < AT_RISK_GPA
                          && s.getStatus() == StudentStatus.ACTIVE).count();

        // Find top student (already first in sorted list)
        String topStudentName = sorted.isEmpty() ? "N/A"
                : sorted.get(0).getFullName()
                  + " (" + sorted.get(0).getGpaFormatted() + ")";

        Map<String, String> stats = new LinkedHashMap<>();
        stats.put("Total Students",   String.valueOf(sorted.size()));
        stats.put("Average GPA",      String.format("%.2f", avgGpa));
        stats.put("Honour Roll",      HONOUR_ROLL_GPA + "+ GPA: " + honourCount + " students");
        stats.put("At Risk",          "Below " + AT_RISK_GPA + " GPA (Active): "
                                      + atRiskCount + " students");
        stats.put("Top Student",      topStudentName);

        return new ReportData.Builder(ReportType.GPA_REPORT,
                ReportType.GPA_REPORT.getDisplayName())
                .subtitle("Students ranked by GPA â€” Honour Roll â‰¥ "
                          + HONOUR_ROLL_GPA + " | At Risk < " + AT_RISK_GPA)
                .generatedAt(LocalDateTime.now())
                .students(sorted)
                .summaryStats(stats)
                .studentAnnotations(buildAnnotations(all))
                .build();
    }

    /**
     * Builds the COURSE_BREAKDOWN report â€” one summary row per course
     * showing student count and average GPA.
     *
     * <p>The student list contains all students sorted by course then
     * last name, so the UI can render a grouped view if desired.
     *
     * @return assembled {@link ReportData}
     */
    private ReportData generateCourseBreakdown() {
        List<Student> all    = studentService.getAllStudents();
        List<String> courses = studentService.getDistinctCourses();
        double avgGpa        = studentService.getOverallAverageGpa();

        List<ReportData.CourseStatRow> courseRows = buildCourseStatRows(all, courses);

        // Sort students by course name then last name for grouped display
        List<Student> sorted = all.stream()
                .sorted(Comparator.comparing(Student::getCourse)
                        .thenComparing(Student::getLastName))
                .collect(Collectors.toList());

        Map<String, String> stats = new LinkedHashMap<>();
        stats.put("Total Courses",   String.valueOf(courses.size()));
        stats.put("Total Students",  String.valueOf(all.size()));
        stats.put("Overall Avg GPA", String.format("%.2f", avgGpa));

        return new ReportData.Builder(ReportType.COURSE_BREAKDOWN,
                ReportType.COURSE_BREAKDOWN.getDisplayName())
                .subtitle("Student counts and GPA breakdown by academic programme")
                .generatedAt(LocalDateTime.now())
                .students(sorted)
                .summaryStats(stats)
                .courseStats(courseRows)
                .build();
    }

    // -----------------------------------------------------------------------
    // PRIVATE HELPER METHODS
    // -----------------------------------------------------------------------

    /**
     * Builds a map of per-student GPA annotations.
     *
     * <p>Iterates the student list once and assigns:
     * <ul>
     *   <li>"{@value #ANNOTATION_HONOUR}" to any student with GPA &ge; {@value #HONOUR_ROLL_GPA}</li>
     *   <li>"{@value #ANNOTATION_AT_RISK}" to ACTIVE students with GPA &lt; {@value #AT_RISK_GPA}</li>
     * </ul>
     * A student cannot be both â€” Honour Roll takes precedence.
     *
     * @param students the list of students to annotate
     * @return map of DB id â†’ annotation string
     */
    private Map<Integer, String> buildAnnotations(List<Student> students) {
        Map<Integer, String> annotations = new LinkedHashMap<>();
        for (Student s : students) {
            if (s.getGpa() >= HONOUR_ROLL_GPA) {
                annotations.put(s.getId(), ANNOTATION_HONOUR);
            } else if (s.getGpa() < AT_RISK_GPA
                    && s.getStatus() == StudentStatus.ACTIVE) {
                annotations.put(s.getId(), ANNOTATION_AT_RISK);
            }
        }
        return annotations;
    }

    /**
     * Builds the standard status-breakdown summary statistics map used
     * by multiple report types.
     *
     * <p>Counts how many students have each {@link StudentStatus} by
     * streaming the already-loaded list â€” avoids additional DB queries.
     *
     * @param all    the full list of students
     * @param avgGpa the pre-calculated overall average GPA
     * @return ordered map of statistic label â†’ formatted value
     */
    private Map<String, String> buildStatusBreakdownStats(List<Student> all,
                                                           double avgGpa) {
        Map<String, String> stats = new LinkedHashMap<>();
        stats.put("Total Students",  String.valueOf(all.size()));
        stats.put("Active",          String.valueOf(
                countByStatus(all, StudentStatus.ACTIVE)));
        stats.put("Inactive",        String.valueOf(
                countByStatus(all, StudentStatus.INACTIVE)));
        stats.put("Graduated",       String.valueOf(
                countByStatus(all, StudentStatus.GRADUATED)));
        stats.put("Suspended",       String.valueOf(
                countByStatus(all, StudentStatus.SUSPENDED)));
        stats.put("Overall Avg GPA", String.format("%.2f", avgGpa));
        return stats;
    }

    /**
     * Builds the list of {@link ReportData.CourseStatRow} objects from
     * the provided student list, grouped by course name.
     *
     * <p>Uses the already-loaded student list rather than making per-course
     * DB queries to minimise database round-trips.
     *
     * @param all     the full list of students
     * @param courses the list of distinct course names (determines row order)
     * @return list of {@link ReportData.CourseStatRow} in course-name order
     */
    private List<ReportData.CourseStatRow> buildCourseStatRows(
            List<Student> all, List<String> courses) {

        return courses.stream().map(course -> {
            List<Student> inCourse = all.stream()
                    .filter(s -> course.equals(s.getCourse()))
                    .collect(Collectors.toList());

            double courseAvgGpa = inCourse.stream()
                    .mapToDouble(Student::getGpa)
                    .average()
                    .orElse(0.0);

            int activeCount = countByStatus(inCourse, StudentStatus.ACTIVE);
            int gradCount   = countByStatus(inCourse, StudentStatus.GRADUATED);

            return new ReportData.CourseStatRow(
                    course,
                    inCourse.size(),
                    courseAvgGpa,
                    activeCount,
                    gradCount
            );
        }).collect(Collectors.toList());
    }

    /**
     * Counts the number of students with a specific status in a list.
     *
     * <p>A simple stream filter and count â€” extracted to a method to
     * eliminate repeated inline stream expressions throughout this class.
     *
     * @param students the list to count from
     * @param status   the status to count
     * @return number of students with the given status
     */
    private int countByStatus(List<Student> students, StudentStatus status) {
        return (int) students.stream()
                .filter(s -> s.getStatus() == status)
                .count();
    }

    /**
     * Returns a new list of students sorted by last name then first name,
     * both ascending. Does not modify the original list.
     *
     * @param students the list to sort
     * @return a new sorted list
     */
    private List<Student> sortByLastName(List<Student> students) {
        return students.stream()
                .sorted(Comparator.comparing(Student::getLastName)
                        .thenComparing(Student::getFirstName))
                .collect(Collectors.toList());
    }
}

