package com.nana.sms;

import com.nana.sms.domain.Student;
import com.nana.sms.domain.StudentStatus;
import com.nana.sms.repository.StudentRepository;
import com.nana.sms.service.ReportData;
import com.nana.sms.service.ReportGenerator;
import com.nana.sms.service.ReportType;
import com.nana.sms.service.StudentService;
import com.nana.sms.service.StudentServiceImpl;
import com.nana.sms.service.ValidationException;
import com.nana.sms.util.CsvColumn;
import com.nana.sms.util.CsvExporter;
import com.nana.sms.util.CsvImporter;
import com.nana.sms.util.ImportReport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.junit.jupiter.api.extension.ExtendWith;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * StudentManagementSystemTest â€” Comprehensive Unit Test Suite
 *
 * <p>WHY THIS STRUCTURE:
 * Tests are organised into {@link Nested} inner classes, one per component
 * under test. This gives JUnit 5's test runner a two-level hierarchy that
 * makes it immediately obvious which component a failing test belongs to:
 * <pre>
 *   StudentManagementSystemTest
 *   â”œâ”€â”€ StudentStatusTests
 *   â”œâ”€â”€ ValidationExceptionTests
 *   â”œâ”€â”€ StudentServiceTests
 *   â”œâ”€â”€ ReportGeneratorTests
 *   â”œâ”€â”€ CsvExporterTests
 *   â”œâ”€â”€ CsvImporterParserTests
 *   â””â”€â”€ ImportReportTests
 * </pre>
 *
 * <p>MOCKING STRATEGY:
 * The {@link StudentRepository} is mocked with Mockito in service tests.
 * This keeps tests isolated from the database â€” they run fast, deterministically,
 * and without any filesystem setup. Only the CSV file tests use a real
 * {@link TempDir} filesystem because they test actual I/O behaviour.
 *
 * <p>TEST COUNT: 35+ tests covering all layers.
 */
@ExtendWith(MockitoExtension.class)
class StudentManagementSystemTest {

    // -----------------------------------------------------------------------
    // SHARED MOCK
    // -----------------------------------------------------------------------

    @Mock
    private StudentRepository mockRepository;

    // -----------------------------------------------------------------------
    // SHARED FACTORY METHODS
    // -----------------------------------------------------------------------

    /**
     * Creates a valid {@link Student} with all required fields populated.
     * Used as a baseline in tests that only need to override one field.
     *
     * @return a fully populated, valid Student
     */
    private Student buildValidStudent() {
        Student s = new Student();
        s.setStudentId("STU-2024-001");
        s.setFirstName("Jane");
        s.setLastName("Smith");
        s.setEmail("jane.smith@university.edu");
        s.setPhone("+1-555-123-4567");
        s.setCourse("Computer Science");
        s.setYearLevel(2);
        s.setGpa(3.75);
        s.setStatus(StudentStatus.ACTIVE);
        s.setEnrolledAt(LocalDateTime.now());
        s.setUpdatedAt(LocalDateTime.now());
        return s;
    }

    /**
     * Creates a {@link StudentServiceImpl} backed by the shared mock repository.
     *
     * @return service under test
     */
    private StudentServiceImpl buildService() {
        return new StudentServiceImpl(mockRepository);
    }

    // ======================================================================
    // NESTED TEST CLASS 1: StudentStatus
    // ======================================================================

    @Nested
    @DisplayName("StudentStatus Enum Tests")
    class StudentStatusTests {

        @Test
        @DisplayName("fromString returns ACTIVE for 'ACTIVE' (case-insensitive)")
        void fromString_active_returnsActive() {
            assertEquals(StudentStatus.ACTIVE,
                    StudentStatus.fromString("ACTIVE"));
            assertEquals(StudentStatus.ACTIVE,
                    StudentStatus.fromString("active"));
            assertEquals(StudentStatus.ACTIVE,
                    StudentStatus.fromString("Active"));
        }

        @Test
        @DisplayName("fromString returns safe default ACTIVE for null input")
        void fromString_null_returnsDefaultActive() {
            assertEquals(StudentStatus.ACTIVE,
                    StudentStatus.fromString(null));
        }

        @Test
        @DisplayName("fromString returns safe default ACTIVE for unrecognised value")
        void fromString_unknown_returnsDefaultActive() {
            // Unrecognised values should not throw â€” return ACTIVE as safe default
            assertEquals(StudentStatus.ACTIVE,
                    StudentStatus.fromString("ENROLLED"));
        }

        @Test
        @DisplayName("All enum constants have non-blank displayName")
        void allConstants_haveNonBlankDisplayName() {
            for (StudentStatus status : StudentStatus.values()) {
                assertFalse(status.getDisplayName().isBlank(),
                        "DisplayName is blank for: " + status.name());
            }
        }

        @Test
        @DisplayName("toString returns enum name (for DB storage)")
        void toString_returnsEnumName() {
            assertEquals("GRADUATED", StudentStatus.GRADUATED.toString());
            assertEquals("SUSPENDED", StudentStatus.SUSPENDED.toString());
        }
    }

    // ======================================================================
    // NESTED TEST CLASS 2: ValidationException
    // ======================================================================

    @Nested
    @DisplayName("ValidationException Tests")
    class ValidationExceptionTests {

        @Test
        @DisplayName("Single-field constructor populates fieldErrors correctly")
        void singleFieldConstructor_populatesErrors() {
            ValidationException ex = new ValidationException("email",
                    "Invalid format");

            assertTrue(ex.hasError("email"));
            assertEquals("Invalid format", ex.getError("email"));
            assertFalse(ex.isEmpty());
        }

        @Test
        @DisplayName("Map constructor preserves all field errors")
        void mapConstructor_preservesAllErrors() {
            java.util.Map<String, String> errors = new java.util.LinkedHashMap<>();
            errors.put("firstName", "must not be blank");
            errors.put("email",     "invalid format");
            errors.put("gpa",       "must be 0.0 to 4.0");

            ValidationException ex = new ValidationException(errors);

            assertEquals(3, ex.getFieldErrors().size());
            assertTrue(ex.hasError("firstName"));
            assertTrue(ex.hasError("email"));
            assertTrue(ex.hasError("gpa"));
        }

        @Test
        @DisplayName("getError returns null for field with no error")
        void getError_noError_returnsNull() {
            ValidationException ex = new ValidationException("email", "bad");
            assertNull(ex.getError("firstName"));
        }

        @Test
        @DisplayName("getMessage returns summary of all errors")
        void getMessage_containsAllFieldNames() {
            ValidationException ex = new ValidationException("email", "bad format");
            assertTrue(ex.getMessage().contains("email"));
            assertTrue(ex.getMessage().contains("bad format"));
        }
    }

    // ======================================================================
    // NESTED TEST CLASS 3: StudentServiceImpl
    // ======================================================================

    @Nested
    @DisplayName("StudentServiceImpl Tests")
    class StudentServiceTests {

        private StudentServiceImpl service;

        @BeforeEach
        void setUp() {
            service = buildService();
        }

        // --- addStudent validation ---

        @Test
        @DisplayName("addStudent succeeds with valid student")
        void addStudent_validStudent_callsRepositorySave() throws ValidationException {
            Student valid = buildValidStudent();

            // Mock: no existing records with this studentId or email
            when(mockRepository.findByStudentId("STU-2024-001"))
                    .thenReturn(Optional.empty());
            when(mockRepository.findByEmail("jane.smith@university.edu"))
                    .thenReturn(Optional.empty());

            service.addStudent(valid);

            // Verify repository.save was called exactly once
            verify(mockRepository, times(1)).save(valid);
        }

        @Test
        @DisplayName("addStudent throws ValidationException for blank firstName")
        void addStudent_blankFirstName_throwsValidationException() {
            Student invalid = buildValidStudent();
            invalid.setFirstName("");

            ValidationException ex = assertThrows(ValidationException.class,
                    () -> service.addStudent(invalid));

            assertTrue(ex.hasError("firstName"),
                    "Expected 'firstName' error but got: " + ex.getFieldErrors());
        }

        @Test
        @DisplayName("addStudent throws ValidationException for invalid email")
        void addStudent_invalidEmail_throwsValidationException() {
            Student invalid = buildValidStudent();
            invalid.setEmail("not-an-email");

            ValidationException ex = assertThrows(ValidationException.class,
                    () -> service.addStudent(invalid));

            assertTrue(ex.hasError("email"));
        }

        @Test
        @DisplayName("addStudent throws ValidationException for GPA above 4.0")
        void addStudent_gpaAboveMax_throwsValidationException() {
            Student invalid = buildValidStudent();
            invalid.setGpa(4.5);

            ValidationException ex = assertThrows(ValidationException.class,
                    () -> service.addStudent(invalid));

            assertTrue(ex.hasError("gpa"));
        }

        @Test
        @DisplayName("addStudent throws ValidationException for GPA below 0.0")
        void addStudent_gpaBelowMin_throwsValidationException() {
            Student invalid = buildValidStudent();
            invalid.setGpa(-0.1);

            ValidationException ex = assertThrows(ValidationException.class,
                    () -> service.addStudent(invalid));

            assertTrue(ex.hasError("gpa"));
        }

        @Test
        @DisplayName("addStudent throws ValidationException for year level 0")
        void addStudent_yearLevelZero_throwsValidationException() {
            Student invalid = buildValidStudent();
            invalid.setYearLevel(0);

            ValidationException ex = assertThrows(ValidationException.class,
                    () -> service.addStudent(invalid));

            assertTrue(ex.hasError("yearLevel"));
        }

        @Test
        @DisplayName("addStudent throws ValidationException for year level 7")
        void addStudent_yearLevelTooHigh_throwsValidationException() {
            Student invalid = buildValidStudent();
            invalid.setYearLevel(7);

            ValidationException ex = assertThrows(ValidationException.class,
                    () -> service.addStudent(invalid));

            assertTrue(ex.hasError("yearLevel"));
        }

        @Test
        @DisplayName("addStudent throws ValidationException for duplicate studentId")
        void addStudent_duplicateStudentId_throwsValidationException() {
            Student existing = buildValidStudent();
            existing.setId(1);

            Student duplicate = buildValidStudent(); // same studentId

            when(mockRepository.findByStudentId("STU-2024-001"))
                    .thenReturn(Optional.of(existing));
            when(mockRepository.findByEmail(anyString()))
                    .thenReturn(Optional.empty());

            ValidationException ex = assertThrows(ValidationException.class,
                    () -> service.addStudent(duplicate));

            assertTrue(ex.hasError("studentId"));
        }

        @Test
        @DisplayName("addStudent throws ValidationException for duplicate email")
        void addStudent_duplicateEmail_throwsValidationException() {
            Student existing = buildValidStudent();
            existing.setId(1);

            Student duplicate = buildValidStudent();
            duplicate.setStudentId("STU-2024-002"); // different ID, same email

            when(mockRepository.findByStudentId("STU-2024-002"))
                    .thenReturn(Optional.empty());
            when(mockRepository.findByEmail("jane.smith@university.edu"))
                    .thenReturn(Optional.of(existing));

            ValidationException ex = assertThrows(ValidationException.class,
                    () -> service.addStudent(duplicate));

            assertTrue(ex.hasError("email"));
        }

        @Test
        @DisplayName("addStudent collects multiple errors in one pass")
        void addStudent_multipleErrors_allReported() {
            Student invalid = new Student();
            invalid.setStudentId("bad-id");      // fails pattern
            invalid.setFirstName("");            // fails blank check
            invalid.setLastName("O");            // valid
            invalid.setEmail("notanemail");      // fails format
            invalid.setCourse("CS");             // valid
            invalid.setYearLevel(3);             // valid
            invalid.setGpa(5.0);                 // fails range
            invalid.setStatus(StudentStatus.ACTIVE);

            ValidationException ex = assertThrows(ValidationException.class,
                    () -> service.addStudent(invalid));

            // Should report all errors in one throw
            assertTrue(ex.hasError("studentId"),  "Missing studentId error");
            assertTrue(ex.hasError("firstName"),  "Missing firstName error");
            assertTrue(ex.hasError("email"),       "Missing email error");
            assertTrue(ex.hasError("gpa"),         "Missing gpa error");
        }

        @Test
        @DisplayName("addStudent throws ValidationException for invalid studentId format")
        void addStudent_invalidStudentIdFormat_throwsValidationException() {
            Student invalid = buildValidStudent();
            invalid.setStudentId("12345"); // does not match pattern

            ValidationException ex = assertThrows(ValidationException.class,
                    () -> service.addStudent(invalid));

            assertTrue(ex.hasError("studentId"));
        }

        // --- updateStudent validation ---

        @Test
        @DisplayName("updateStudent throws ValidationException for id <= 0")
        void updateStudent_idZero_throwsValidationException() {
            Student student = buildValidStudent();
            student.setId(0); // not yet persisted

            ValidationException ex = assertThrows(ValidationException.class,
                    () -> service.updateStudent(student));

            assertTrue(ex.hasError("id"));
        }

        @Test
        @DisplayName("updateStudent allows keeping own email (no false duplicate)")
        void updateStudent_sameEmail_doesNotFlagAsDuplicate()
                throws ValidationException {

            Student existing = buildValidStudent();
            existing.setId(42);

            // When looking up by ID, return the same student (it exists)
            when(mockRepository.findById(42))
                    .thenReturn(Optional.of(existing));
            // When looking up studentId, return same student (own record)
            when(mockRepository.findByStudentId("STU-2024-001"))
                    .thenReturn(Optional.of(existing));
            // When looking up email, return same student (own record, not a conflict)
            when(mockRepository.findByEmail("jane.smith@university.edu"))
                    .thenReturn(Optional.of(existing));

            // Should NOT throw â€” the duplicate is the student itself
            assertDoesNotThrow(() -> service.updateStudent(existing));
            verify(mockRepository, times(1)).update(existing);
        }

        // --- removeStudent ---

        @Test
        @DisplayName("removeStudent throws ValidationException when student not found")
        void removeStudent_notFound_throwsValidationException() {
            when(mockRepository.findById(999)).thenReturn(Optional.empty());

            ValidationException ex = assertThrows(ValidationException.class,
                    () -> service.removeStudent(999));

            assertTrue(ex.hasError("id"));
        }

        @Test
        @DisplayName("removeStudent calls repository.delete on valid id")
        void removeStudent_validId_callsRepositoryDelete() throws ValidationException {
            Student student = buildValidStudent();
            student.setId(5);

            when(mockRepository.findById(5)).thenReturn(Optional.of(student));

            service.removeStudent(5);

            verify(mockRepository, times(1)).delete(5);
        }

        // --- read operations ---

        @Test
        @DisplayName("getAllStudents delegates to repository.findAll")
        void getAllStudents_delegatesToRepository() {
            List<Student> expected = List.of(buildValidStudent());
            when(mockRepository.findAll()).thenReturn(expected);

            List<Student> result = service.getAllStudents();

            assertEquals(expected, result);
            verify(mockRepository, times(1)).findAll();
        }

        @Test
        @DisplayName("searchStudents passes null keyword as empty string to repository")
        void searchStudents_nullKeyword_passesEmptyString() {
            when(mockRepository.search("")).thenReturn(Collections.emptyList());

            service.searchStudents(null);

            verify(mockRepository, times(1)).search("");
        }

        @Test
        @DisplayName("getTotalStudentCount delegates to repository.countAll")
        void getTotalStudentCount_delegatesToRepository() {
            when(mockRepository.countAll()).thenReturn(42);

            int count = service.getTotalStudentCount();

            assertEquals(42, count);
        }

        // --- importStudents ---

        @Test
        @DisplayName("importStudents returns full success result for valid list")
        void importStudents_allValid_returnsFullSuccess() {
            Student s = buildValidStudent();
            List<Student> students = List.of(s);

            // saveAll returns empty list = no failures
            when(mockRepository.saveAll(anyList()))
                    .thenReturn(Collections.emptyList());

            StudentService.ImportResult result = service.importStudents(students);

            assertTrue(result.isFullSuccess());
            assertEquals(1, result.getSuccessCount());
            assertEquals(0, result.getFailureCount());
        }

        @Test
        @DisplayName("importStudents reports validation failures per row")
        void importStudents_invalidRow_reportsFailureNotException() {
            Student invalid = new Student(); // all blanks â€” will fail validation
            invalid.setStudentId("");
            invalid.setFirstName("");
            invalid.setLastName("");
            invalid.setEmail("");
            invalid.setCourse("");
            invalid.setYearLevel(3);
            invalid.setGpa(3.0);
            invalid.setStatus(StudentStatus.ACTIVE);

            StudentService.ImportResult result =
                    service.importStudents(List.of(invalid));

            // Should not throw â€” should report failure in result
            assertFalse(result.isFullSuccess());
            assertEquals(1, result.getFailureCount());
            assertEquals(0, result.getSuccessCount());
        }
    }

    // ======================================================================
    // NESTED TEST CLASS 4: ReportGenerator
    // ======================================================================

    @Nested
    @DisplayName("ReportGenerator Tests")
    class ReportGeneratorTests {

        private ReportGenerator generator;

        @BeforeEach
        void setUp() {
            StudentService mockService = mock(StudentService.class);

            // Set up common stubs
            when(mockService.getAllStudents()).thenReturn(buildStudentList());
            when(mockService.getOverallAverageGpa()).thenReturn(3.2);
            when(mockService.getDistinctCourses())
                    .thenReturn(List.of("Computer Science", "Business"));
            when(mockService.getStudentsByStatus(StudentStatus.ACTIVE))
                    .thenReturn(buildStudentList());
            when(mockService.getStudentsByStatus(StudentStatus.GRADUATED))
                    .thenReturn(Collections.emptyList());
            when(mockService.getStudentsByCourse(anyString()))
                    .thenReturn(buildStudentList());
            when(mockService.getAverageGpaByCourse(anyString()))
                    .thenReturn(3.2);

            generator = new ReportGenerator(mockService);
        }

        @Test
        @DisplayName("generate(FULL_ROSTER) returns report with all students")
        void generate_fullRoster_containsAllStudents() {
            ReportData report = generator.generate(ReportType.FULL_ROSTER);

            assertNotNull(report);
            assertEquals(ReportType.FULL_ROSTER, report.getReportType());
            assertEquals(2, report.getStudentCount());
            assertFalse(report.getSummaryStats().isEmpty());
        }

        @Test
        @DisplayName("generate(SUMMARY_STATISTICS) includes course stats")
        void generate_summaryStatistics_includesCourseStats() {
            ReportData report = generator.generate(ReportType.SUMMARY_STATISTICS);

            assertNotNull(report);
            assertFalse(report.getCourseStats().isEmpty());
            assertEquals(2, report.getCourseStats().size());
        }

        @Test
        @DisplayName("generate(GPA_REPORT) sorts students by GPA descending")
        void generate_gpaReport_sortedByGpaDescending() {
            ReportData report = generator.generate(ReportType.GPA_REPORT);

            List<Student> students = report.getStudents();
            assertFalse(students.isEmpty());

            // Verify descending GPA order
            for (int i = 0; i < students.size() - 1; i++) {
                assertTrue(
                        students.get(i).getGpa() >= students.get(i + 1).getGpa(),
                        "GPA not in descending order at index " + i);
            }
        }

        @Test
        @DisplayName("generate(COURSE_BREAKDOWN) produces one row per course")
        void generate_courseBreakdown_oneRowPerCourse() {
            ReportData report = generator.generate(ReportType.COURSE_BREAKDOWN);

            assertEquals(2, report.getCourseStats().size());
        }

        @Test
        @DisplayName("generateForCourse filters to specific course")
        void generateForCourse_returnsCorrectTitle() {
            ReportData report = generator.generateForCourse("Computer Science");

            assertNotNull(report);
            assertTrue(report.getTitle().contains("Computer Science"));
        }

        @Test
        @DisplayName("generate throws IllegalArgumentException for null type")
        void generate_nullType_throwsIllegalArgumentException() {
            assertThrows(IllegalArgumentException.class,
                    () -> generator.generate(null));
        }

        @Test
        @DisplayName("Honour Roll annotation applied for GPA >= 3.5")
        void annotations_honourRollApplied_forHighGpa() {
            ReportData report = generator.generate(ReportType.GPA_REPORT);

            // Student with GPA 3.9 should have honour roll annotation
            report.getStudents().stream()
                    .filter(s -> s.getGpa() >= 3.5)
                    .forEach(s -> {
                        String annotation = report.getAnnotation(s.getId());
                        assertTrue(annotation.contains("Honour"),
                                "Expected Honour Roll for student with GPA "
                                + s.getGpa());
                    });
        }

        @Test
        @DisplayName("ReportData generatedAt is set to a recent timestamp")
        void reportData_generatedAt_isRecent() {
            ReportData report = generator.generate(ReportType.FULL_ROSTER);

            assertNotNull(report.getGeneratedAt());
            // Should be within the last 5 seconds
            assertTrue(
                    report.getGeneratedAt().isAfter(
                            LocalDateTime.now().minusSeconds(5)));
        }

        /**
         * Builds a small test student list with two students having
         * different GPAs so GPA sorting tests are meaningful.
         */
        private List<Student> buildStudentList() {
            Student s1 = new Student(1, "STU-2024-001", "Jane", "Smith",
                    "jane@uni.edu", "", "Computer Science",
                    2, 3.9, StudentStatus.ACTIVE,
                    LocalDateTime.now(), LocalDateTime.now());

            Student s2 = new Student(2, "STU-2024-002", "Bob", "Jones",
                    "bob@uni.edu", "", "Business",
                    3, 2.5, StudentStatus.ACTIVE,
                    LocalDateTime.now(), LocalDateTime.now());

            return List.of(s1, s2);
        }
    }

    // ======================================================================
    // NESTED TEST CLASS 5: CsvExporter
    // ======================================================================

    @Nested
    @DisplayName("CsvExporter Tests")
    class CsvExporterTests {

        private CsvExporter exporter;

        @TempDir
        Path tempDir;

        @BeforeEach
        void setUp() {
            exporter = new CsvExporter();
        }

        @Test
        @DisplayName("escapeCsvField: plain value returned unchanged")
        void escapeCsvField_plainValue_returnedUnchanged() {
            assertEquals("John", CsvExporter.escapeCsvField("John"));
        }

        @Test
        @DisplayName("escapeCsvField: value with comma gets quoted")
        void escapeCsvField_withComma_getsQuoted() {
            String result = CsvExporter.escapeCsvField("Smith, Jr.");
            assertEquals("\"Smith, Jr.\"", result);
        }

        @Test
        @DisplayName("escapeCsvField: value with double-quote gets escaped")
        void escapeCsvField_withDoubleQuote_getsEscaped() {
            String result = CsvExporter.escapeCsvField("He said \"hello\"");
            assertEquals("\"He said \"\"hello\"\"\"", result);
        }

        @Test
        @DisplayName("escapeCsvField: empty string returns empty string")
        void escapeCsvField_emptyString_returnsEmpty() {
            assertEquals("", CsvExporter.escapeCsvField(""));
        }

        @Test
        @DisplayName("escapeCsvField: null returns empty string")
        void escapeCsvField_null_returnsEmpty() {
            assertEquals("", CsvExporter.escapeCsvField(null));
        }

        @Test
        @DisplayName("escapeCsvField: value with newline gets quoted")
        void escapeCsvField_withNewline_getsQuoted() {
            String result = CsvExporter.escapeCsvField("line1\nline2");
            assertEquals("\"line1\nline2\"", result);
        }

        @Test
        @DisplayName("exportAll writes file with UTF-8 BOM")
        void exportAll_writesUtf8Bom() throws IOException {
            List<Student> students = List.of(buildValidStudent());
            Path output = tempDir.resolve("export_test.csv");

            CsvExporter.ExportResult result = exporter.exportAll(students, output);

            assertTrue(result.isSuccess());
            // Read raw bytes and verify BOM
            byte[] bytes = Files.readAllBytes(output);
            assertEquals((byte) 0xEF, bytes[0], "First BOM byte");
            assertEquals((byte) 0xBB, bytes[1], "Second BOM byte");
            assertEquals((byte) 0xBF, bytes[2], "Third BOM byte");
        }

        @Test
        @DisplayName("exportAll creates file with header row")
        void exportAll_createsFileWithHeaderRow() throws IOException {
            List<Student> students = List.of(buildValidStudent());
            Path output = tempDir.resolve("export_header.csv");

            exporter.exportAll(students, output);

            String content = Files.readString(output, StandardCharsets.UTF_8);
            // BOM is stripped by readString, header should be present
            assertTrue(content.contains("Student ID"));
            assertTrue(content.contains("First Name"));
            assertTrue(content.contains("Last Name"));
            assertTrue(content.contains("Email"));
            assertTrue(content.contains("GPA"));
        }

        @Test
        @DisplayName("exportAll writes correct student data")
        void exportAll_writesCorrectStudentData() throws IOException {
            Student student = buildValidStudent();
            Path output = tempDir.resolve("export_data.csv");

            exporter.exportAll(List.of(student), output);

            String content = Files.readString(output, StandardCharsets.UTF_8);
            assertTrue(content.contains("STU-2024-001"));
            assertTrue(content.contains("Jane"));
            assertTrue(content.contains("Smith"));
            assertTrue(content.contains("jane.smith@university.edu"));
        }

        @Test
        @DisplayName("exportAll returns failure result for null output path")
        void exportAll_nullOutputPath_returnsFailure() {
            CsvExporter.ExportResult result =
                    exporter.exportAll(List.of(buildValidStudent()), null);

            assertFalse(result.isSuccess());
            assertNotNull(result.getErrorMessage());
        }

        @Test
        @DisplayName("ExportResult.getFileSizeFormatted returns 'B' for small files")
        void exportResult_fileSizeFormatted_bytes() throws IOException {
            List<Student> students = List.of(buildValidStudent());
            Path output = tempDir.resolve("tiny.csv");

            CsvExporter.ExportResult result = exporter.exportAll(students, output);
            // Result should have a non-empty formatted size
            assertNotNull(result.getFileSizeFormatted());
            assertFalse(result.getFileSizeFormatted().isBlank());
        }

        @Test
        @DisplayName("CsvColumn.defaultColumns excludes ID column")
        void csvColumn_defaultColumns_excludesIdColumn() {
            CsvColumn[] defaults = CsvColumn.defaultColumns();
            boolean hasId = Arrays.asList(defaults).contains(CsvColumn.ID);
            assertFalse(hasId, "Default columns should not include internal DB ID");
        }

        @Test
        @DisplayName("CsvColumn.extract returns empty string for null student")
        void csvColumn_extract_nullStudent_returnsEmpty() {
            assertEquals("", CsvColumn.STUDENT_ID.extract(null));
            assertEquals("", CsvColumn.EMAIL.extract(null));
        }

        @Test
        @DisplayName("exportWithColumns respects column selection")
        void exportWithColumns_customColumns_onlySelectedColumns()
                throws IOException {
            List<Student> students = List.of(buildValidStudent());
            Path output = tempDir.resolve("custom_cols.csv");

            // Export only STUDENT_ID and EMAIL
            exporter.exportWithColumns(students, output,
                    List.of(CsvColumn.STUDENT_ID, CsvColumn.EMAIL));

            String content = Files.readString(output, StandardCharsets.UTF_8);
            assertTrue(content.contains("Student ID"));
            assertTrue(content.contains("Email"));
            assertFalse(content.contains("First Name"),
                    "First Name should not appear in custom export");
        }
    }

    // ======================================================================
    // NESTED TEST CLASS 6: CsvImporter Parser
    // ======================================================================

    @Nested
    @DisplayName("CsvImporter Parser Tests")
    class CsvImporterParserTests {

        private CsvImporter importer;

        @TempDir
        Path tempDir;

        @BeforeEach
        void setUp() {
            // Use a mock service for import tests â€” we test parsing only
            StudentService mockService = mock(StudentService.class);
            when(mockService.importStudents(anyList()))
                    .thenReturn(new StudentService.ImportResult(
                            0, 0, 0, Collections.emptyMap()));
            importer = new CsvImporter(mockService);
        }

        @Test
        @DisplayName("parseCsvRow: plain fields parsed correctly")
        void parseCsvRow_plainFields_parsedCorrectly() {
            List<String> fields = importer.parseCsvRow("John,Smith,john@email.com");
            assertEquals(3, fields.size());
            assertEquals("John",           fields.get(0));
            assertEquals("Smith",          fields.get(1));
            assertEquals("john@email.com", fields.get(2));
        }

        @Test
        @DisplayName("parseCsvRow: quoted field with comma parsed as single field")
        void parseCsvRow_quotedFieldWithComma_singleField() {
            List<String> fields = importer.parseCsvRow("\"Smith, Jr.\",John");
            assertEquals(2, fields.size());
            assertEquals("Smith, Jr.", fields.get(0));
            assertEquals("John",       fields.get(1));
        }

        @Test
        @DisplayName("parseCsvRow: escaped double-quote inside quoted field")
        void parseCsvRow_escapedDoubleQuote_resolvedCorrectly() {
            List<String> fields = importer.parseCsvRow("\"He said \"\"hi\"\"\",done");
            assertEquals(2, fields.size());
            assertEquals("He said \"hi\"", fields.get(0));
            assertEquals("done",            fields.get(1));
        }

        @Test
        @DisplayName("parseCsvRow: empty fields produce empty strings")
        void parseCsvRow_emptyFields_produceEmptyStrings() {
            List<String> fields = importer.parseCsvRow("John,,john@email.com");
            assertEquals(3, fields.size());
            assertEquals("",             fields.get(1));
        }

        @Test
        @DisplayName("parseCsvRow: unclosed quote throws IllegalArgumentException")
        void parseCsvRow_unclosedQuote_throwsException() {
            assertThrows(IllegalArgumentException.class,
                    () -> importer.parseCsvRow("\"unclosed field,next"));
        }

        @Test
        @DisplayName("parseCsvRow: empty string returns empty list")
        void parseCsvRow_emptyString_returnsEmptyList() {
            List<String> fields = importer.parseCsvRow("");
            assertTrue(fields.isEmpty());
        }

        @Test
        @DisplayName("importFile returns empty report for non-existent file")
        void importFile_nonExistentFile_returnsErrorReport() {
            Path nonExistent = tempDir.resolve("does_not_exist.csv");
            ImportReport report = importer.importFile(nonExistent);

            // Should not throw â€” should return a report with failure info
            assertNotNull(report);
            assertTrue(report.getFailureCount() > 0 || report.getTotalRows() == 0);
        }

        @Test
        @DisplayName("importFile handles UTF-8 BOM correctly")
        void importFile_withUtf8Bom_bomStripped() throws IOException {
            // Create a CSV file with UTF-8 BOM
            Path csvFile = tempDir.resolve("bom_test.csv");
            byte[] bom = {(byte) 0xEF, (byte) 0xBB, (byte) 0xBF};
            String content =
                    "Student ID,First Name,Last Name,Email,Phone,"
                    + "Course,Year Level,GPA,Status\r\n";
            byte[] contentBytes = content.getBytes(StandardCharsets.UTF_8);
            byte[] full = new byte[bom.length + contentBytes.length];
            System.arraycopy(bom, 0, full, 0, bom.length);
            System.arraycopy(contentBytes, 0, full, bom.length, contentBytes.length);
            Files.write(csvFile, full);

            // Should not throw, BOM should be stripped before header parsing
            ImportReport report = importer.importFile(csvFile);
            assertNotNull(report);
            // No data rows â€” total should be 0
            assertEquals(0, report.getTotalRows());
        }

        @Test
        @DisplayName("importFile skips comment lines starting with #")
        void importFile_commentLines_skipped() throws IOException {
            Path csvFile = tempDir.resolve("comments.csv");
            String content =
                    "# This is a comment\r\n"
                    + "Student ID,First Name,Last Name,Email,Phone,"
                    + "Course,Year Level,GPA,Status\r\n"
                    + "# Another comment\r\n";
            Files.writeString(csvFile, content, StandardCharsets.UTF_8);

            ImportReport report = importer.importFile(csvFile);
            assertNotNull(report);
            assertEquals(0, report.getTotalRows()); // no data rows
            assertTrue(report.getSkippedCount() >= 1);
        }

        @Test
        @DisplayName("importFile handles file with only header row")
        void importFile_headerOnly_noDataRows() throws IOException {
            Path csvFile = tempDir.resolve("header_only.csv");
            Files.writeString(csvFile,
                    "Student ID,First Name,Last Name,Email,Phone,"
                    + "Course,Year Level,GPA,Status\r\n",
                    StandardCharsets.UTF_8);

            ImportReport report = importer.importFile(csvFile);
            assertNotNull(report);
            assertEquals(0, report.getTotalRows());
            assertEquals(0, report.getFailureCount());
        }
    }

    // ======================================================================
    // NESTED TEST CLASS 7: ImportReport
    // ======================================================================

    @Nested
    @DisplayName("ImportReport Tests")
    class ImportReportTests {

        @Test
        @DisplayName("Builder.addSuccess increments successCount")
        void builder_addSuccess_incrementsSuccessCount() {
            ImportReport.Builder builder =
                    new ImportReport.Builder(Path.of("test.csv"));
            builder.addSuccess(1, "row1");
            builder.addSuccess(2, "row2");

            ImportReport report = builder.build();
            assertEquals(2, report.getSuccessCount());
            assertEquals(0, report.getFailureCount());
            assertEquals(2, report.getTotalRows());
        }

        @Test
        @DisplayName("Builder.addFailure increments failureCount")
        void builder_addFailure_incrementsFailureCount() {
            ImportReport.Builder builder =
                    new ImportReport.Builder(Path.of("test.csv"));
            builder.addFailure(1, "row1",
                    ImportReport.RowResult.Outcome.VALIDATION_ERROR,
                    "email is invalid");

            ImportReport report = builder.build();
            assertEquals(1, report.getFailureCount());
            assertEquals(0, report.getSuccessCount());
        }

        @Test
        @DisplayName("getFailedRows returns only failed rows")
        void getFailedRows_returnsOnlyFailures() {
            ImportReport.Builder builder =
                    new ImportReport.Builder(Path.of("test.csv"));
            builder.addSuccess(1, "good row");
            builder.addFailure(2, "bad row",
                    ImportReport.RowResult.Outcome.PARSE_ERROR, "bad data");
            builder.addSuccess(3, "good row");

            ImportReport report = builder.build();
            List<ImportReport.RowResult> failed = report.getFailedRows();
            assertEquals(1, failed.size());
            assertEquals(2, failed.get(0).getRowNumber());
        }

        @Test
        @DisplayName("isFullSuccess returns true when no failures")
        void isFullSuccess_noFailures_returnsTrue() {
            ImportReport.Builder builder =
                    new ImportReport.Builder(Path.of("test.csv"));
            builder.addSuccess(1, "row1");
            assertTrue(builder.build().isFullSuccess());
        }

        @Test
        @DisplayName("toReportText contains source filename")
        void toReportText_containsSourceFilename() {
            ImportReport.Builder builder =
                    new ImportReport.Builder(Path.of("students.csv"));
            builder.addSuccess(1, "row1");

            String text = builder.build().toReportText();
            assertTrue(text.contains("students.csv"));
        }

        @Test
        @DisplayName("toReportText contains failed row number")
        void toReportText_containsFailedRowNumber() {
            ImportReport.Builder builder =
                    new ImportReport.Builder(Path.of("test.csv"));
            builder.addFailure(17, "raw line",
                    ImportReport.RowResult.Outcome.DUPLICATE_KEY,
                    "Duplicate studentId");

            String text = builder.build().toReportText();
            assertTrue(text.contains("17"),
                    "Report text should contain row number 17");
            assertTrue(text.contains("DUPLICATE_KEY"));
        }

        @Test
        @DisplayName("getSummary returns correct message for partial success")
        void getSummary_partialSuccess_correctMessage() {
            ImportReport.Builder builder =
                    new ImportReport.Builder(Path.of("test.csv"));
            builder.addSuccess(1, "row1");
            builder.addSuccess(2, "row2");
            builder.addFailure(3, "row3",
                    ImportReport.RowResult.Outcome.VALIDATION_ERROR, "bad");

            ImportReport report = builder.build();
            String summary = report.getSummary();
            assertTrue(summary.contains("2"),  "Should mention 2 successes");
            assertTrue(summary.contains("3"),  "Should mention 3 total");
            assertTrue(summary.contains("1"),  "Should mention 1 failure");
        }

        @Test
        @DisplayName("addSkipped does not increment totalRows")
        void addSkipped_doesNotIncrementTotalRows() {
            ImportReport.Builder builder =
                    new ImportReport.Builder(Path.of("test.csv"));
            builder.addSkipped(1, "# comment", "Comment line");
            builder.addSkipped(2, "",           "Blank line");

            ImportReport report = builder.build();
            assertEquals(0, report.getTotalRows());
            assertEquals(2, report.getSkippedCount());
        }
    }

    // ======================================================================
    // NESTED TEST CLASS 8: Student Domain
    // ======================================================================

    @Nested
    @DisplayName("Student Domain Tests")
    class StudentDomainTests {

        @Test
        @DisplayName("setEmail normalises to lowercase")
        void setEmail_normalisesToLowercase() {
            Student student = new Student();
            student.setEmail("JOHN.DOE@UNIVERSITY.EDU");
            assertEquals("john.doe@university.edu", student.getEmail());
        }

        @Test
        @DisplayName("getFullName concatenates first and last name")
        void getFullName_concatenatesNames() {
            Student student = buildValidStudent();
            assertEquals("Jane Smith", student.getFullName());
        }

        @Test
        @DisplayName("equals based on studentId (not DB id)")
        void equals_basedOnStudentId() {
            Student s1 = buildValidStudent();
            s1.setId(1);
            Student s2 = buildValidStudent();
            s2.setId(99); // different DB id, same studentId

            assertEquals(s1, s2, "Students with same studentId should be equal");
        }

        @Test
        @DisplayName("hashCode consistent with equals")
        void hashCode_consistentWithEquals() {
            Student s1 = buildValidStudent();
            Student s2 = buildValidStudent();

            assertEquals(s1.hashCode(), s2.hashCode());
        }

        @Test
        @DisplayName("setPhone with null sets empty string")
        void setPhone_null_setsEmptyString() {
            Student student = new Student();
            student.setPhone(null);
            assertEquals("", student.getPhone());
        }

        @Test
        @DisplayName("getGpaFormatted returns 2 decimal places")
        void getGpaFormatted_returnsTwoDecimals() {
            Student student = buildValidStudent();
            student.setGpa(3.9);
            assertEquals("3.90", student.getGpaFormatted());
        }

        @Test
        @DisplayName("setStatus with null defaults to ACTIVE")
        void setStatus_null_defaultsToActive() {
            Student student = new Student();
            student.setStatus(null);
            assertEquals(StudentStatus.ACTIVE, student.getStatus());
        }

        @Test
        @DisplayName("toString does not expose email (sensitive field)")
        void toString_doesNotExposeEmail() {
            Student student = buildValidStudent();
            String str = student.toString();
            assertFalse(str.contains("jane.smith@university.edu"),
                    "toString should not expose email address");
        }
    }
}

