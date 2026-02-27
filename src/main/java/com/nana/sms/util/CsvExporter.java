package com.nana.sms.util;

import com.nana.sms.domain.Student;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.List;

/**
 * CsvExporter â€” CSV Export Utility
 *
 * <p>WHY THIS CLASS EXISTS:
 * Writing CSV files correctly is surprisingly non-trivial. The naive approach
 * of joining field values with commas breaks immediately when a field value
 * contains a comma, a double quote, or a newline. This class implements
 * RFC 4180 CSV encoding correctly and consistently for the entire application.
 *
 * <p>RFC 4180 COMPLIANCE:
 * <ul>
 *   <li>Fields containing commas, double quotes, or newlines are enclosed
 *       in double-quote characters.</li>
 *   <li>Double-quote characters within a quoted field are escaped by
 *       doubling them (e.g., {@code He said "hello"} becomes
 *       {@code "He said ""hello"""}).</li>
 *   <li>Each record ends with CRLF ({@code \r\n}) per the RFC.
 *       Most tools (Excel, Google Sheets, LibreOffice) handle both
 *       CRLF and LF, but CRLF is the standard.</li>
 *   <li>The first row is a header row containing column names.</li>
 *   <li>All rows have the same number of fields.</li>
 * </ul>
 *
 * <p>ENCODING:
 * Files are written in UTF-8 with BOM (Byte Order Mark).
 * WHY BOM: Microsoft Excel on Windows does NOT recognise UTF-8 CSV files
 * without a BOM â€” it interprets them as the system codepage (often CP1252),
 * causing mojibake on any non-ASCII characters (accented names, etc.).
 * The BOM ({@code EF BB BF}) is a three-byte sequence at the start of the
 * file that signals UTF-8 encoding to Excel and other Windows tools.
 *
 * <p>DESIGN:
 * This is a stateless utility class with instance methods for flexibility
 * (allows subclassing and mocking in tests). A new instance should be
 * created for each export operation.
 *
 * <p>THREAD SAFETY:
 * Instances are not thread-safe â€” do not share a single instance across
 * threads. Since each export creates a new instance, this is not a concern
 * in practice.
 */
public class CsvExporter {

    private static final Logger log = LoggerFactory.getLogger(CsvExporter.class);

    // -----------------------------------------------------------------------
    // CONSTANTS
    // -----------------------------------------------------------------------

    /**
     * RFC 4180 record separator: carriage return + line feed.
     * Used instead of {@code System.lineSeparator()} to ensure consistent
     * output regardless of the OS this application runs on.
     */
    private static final String CRLF = "\r\n";

    /**
     * UTF-8 BOM bytes.
     * Written as the very first bytes of the file so Excel recognises
     * the encoding without a file format conversion dialog.
     */
    private static final byte[] UTF8_BOM = new byte[]{
            (byte) 0xEF, (byte) 0xBB, (byte) 0xBF
    };

    /**
     * Timestamp format for the export metadata comment row.
     * Written as the second line of the file (commented with #) so
     * import tools that skip comment lines are not affected.
     */
    private static final DateTimeFormatter META_FORMAT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    // -----------------------------------------------------------------------
    // PUBLIC EXPORT METHODS
    // -----------------------------------------------------------------------

    /**
     * Exports a list of students to a CSV file using the default column set.
     *
     * <p>This is the primary export method called by the UI's "Export"
     * button when the user has not customised column selection.
     *
     * <p>The default column set is defined by {@link CsvColumn#defaultColumns()}.
     *
     * @param students   the list of students to export; must not be null
     * @param outputPath the file path to write to; parent directories must exist
     * @return an {@link ExportResult} describing the outcome
     */
    public ExportResult exportAll(List<Student> students, Path outputPath) {
        return exportWithColumns(students, outputPath,
                Arrays.asList(CsvColumn.defaultColumns()));
    }

    /**
     * Exports a list of students to a CSV file using import-compatible columns.
     *
     * <p>The output of this method can be directly re-imported by
     * {@code CsvImporter} without data loss. Used by the "Export for Import"
     * option in the UI.
     *
     * @param students   the list of students to export
     * @param outputPath the file path to write to
     * @return an {@link ExportResult} describing the outcome
     */
    public ExportResult exportForImport(List<Student> students, Path outputPath) {
        return exportWithColumns(students, outputPath,
                Arrays.asList(CsvColumn.importCompatibleColumns()));
    }

    /**
     * Exports a list of students to a CSV file with a caller-specified
     * set of columns.
     *
     * <p>This is the most flexible export method â€” the UI's custom export
     * dialog calls this after the user has selected which columns to include
     * and in what order.
     *
     * <p>IMPLEMENTATION OVERVIEW:
     * <ol>
     *   <li>Validate inputs and ensure the output directory exists.</li>
     *   <li>Open a {@link BufferedWriter} with UTF-8 encoding.</li>
     *   <li>Write the UTF-8 BOM bytes directly to the underlying stream.</li>
     *   <li>Write the metadata comment line.</li>
     *   <li>Write the header row.</li>
     *   <li>Write one data row per student.</li>
     *   <li>Return an {@link ExportResult} with the row count and file path.</li>
     * </ol>
     *
     * @param students   the list of students to export; must not be null
     * @param outputPath the file path to write to
     * @param columns    the ordered list of columns to include
     * @return an {@link ExportResult} describing the outcome
     */
    public ExportResult exportWithColumns(List<Student> students,
                                           Path outputPath,
                                           List<CsvColumn> columns) {
        // --- Input validation ---
        if (students == null) {
            return ExportResult.failure("Student list must not be null.");
        }
        if (outputPath == null) {
            return ExportResult.failure("Output path must not be null.");
        }
        if (columns == null || columns.isEmpty()) {
            return ExportResult.failure("Column list must not be null or empty.");
        }

        log.info("Starting CSV export: {} students, {} columns, to '{}'.",
                students.size(), columns.size(), outputPath);

        AppLogger.setOperationContext("CSV_EXPORT");

        try {
            // Ensure the parent directory exists
            ensureParentDirectory(outputPath);

            // Write BOM + CSV content
            writeCsvFile(students, outputPath, columns);

            long fileSizeBytes = Files.size(outputPath);
            log.info("CSV export complete: {} rows written, file size {} bytes.",
                    students.size(), fileSizeBytes);

            AppLogger.logEvent("CSV_EXPORT_COMPLETE",
                    "rows=" + students.size()
                    + ", columns=" + columns.size()
                    + ", file=" + outputPath.getFileName()
                    + ", size=" + fileSizeBytes + "B");

            return ExportResult.success(outputPath, students.size(), fileSizeBytes);

        } catch (IOException ex) {
            log.error("CSV export failed for path '{}'.", outputPath, ex);
            AppLogger.logErrorEvent("CSV_EXPORT_FAILED",
                    "path=" + outputPath, ex);
            return ExportResult.failure(
                    "Export failed: " + ex.getMessage());
        } finally {
            AppLogger.clearOperationContext();
        }
    }

    /**
     * Exports a {@link com.nana.sms.service.ReportData} student list
     * with an automatically generated filename including the report type
     * and a timestamp.
     *
     * <p>Convenience method for the report screen's "Export to CSV" button.
     *
     * @param students      the report's student list
     * @param reportTitle   the report title (used in the filename)
     * @param exportDir     the directory to write the file into
     * @return an {@link ExportResult} describing the outcome
     */
    public ExportResult exportReport(List<Student> students,
                                      String reportTitle,
                                      Path exportDir) {
        // Generate a safe filename from the report title + timestamp
        String safeTitle = reportTitle
                .replaceAll("[^a-zA-Z0-9\\-_]", "_")
                .replaceAll("_+", "_");
        String timestamp = LocalDateTime.now()
                .format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
        String filename  = safeTitle + "_" + timestamp + ".csv";
        Path   outputPath = exportDir.resolve(filename);

        log.debug("exportReport(): generated filename '{}'.", filename);
        return exportAll(students, outputPath);
    }

    // -----------------------------------------------------------------------
    // PRIVATE IMPLEMENTATION
    // -----------------------------------------------------------------------

    /**
     * Creates the parent directories of the output path if they do not exist.
     *
     * @param outputPath the file path whose parent to create
     * @throws IOException if directory creation fails
     */
    private void ensureParentDirectory(Path outputPath) throws IOException {
        Path parent = outputPath.getParent();
        if (parent != null && !Files.exists(parent)) {
            Files.createDirectories(parent);
            log.debug("Created export directory: {}", parent);
        }
    }

    /**
     * Opens the output file and writes the BOM, metadata, header, and
     * all data rows.
     *
     * <p>WHY TWO WRITE CALLS:
     * {@link Files#newBufferedWriter} opens the file in text mode with
     * UTF-8 encoding. We cannot write the BOM through the {@code Writer}
     * because the BOM bytes must appear before any UTF-8 encoded characters.
     * Instead, we first write the BOM via {@link Files#write} with
     * {@code APPEND} disabled, then open the writer in append mode.
     * This guarantees BOM is always the first 3 bytes of the file.
     *
     * @param students   the student list to export
     * @param outputPath the file to write
     * @param columns    the columns to include
     * @throws IOException if any file write operation fails
     */
    private void writeCsvFile(List<Student> students,
                               Path outputPath,
                               List<CsvColumn> columns) throws IOException {

        // Step 1: Write BOM as raw bytes â€” must be the very first bytes
        Files.write(outputPath, UTF8_BOM,
                StandardOpenOption.CREATE,
                StandardOpenOption.TRUNCATE_EXISTING);

        // Step 2: Open writer in APPEND mode so BOM is not overwritten
        try (BufferedWriter writer = Files.newBufferedWriter(
                outputPath,
                StandardCharsets.UTF_8,
                StandardOpenOption.APPEND)) {

            // --- Metadata comment (skipped by most import tools) ---
            writeMetadataComment(writer, students.size(), columns.size());

            // --- Header row ---
            writeHeaderRow(writer, columns);

            // --- Data rows ---
            for (Student student : students) {
                writeDataRow(writer, student, columns);
            }
        }
    }

    /**
     * Writes a metadata comment line as the first text row of the file.
     *
     * <p>The comment starts with {@code #} and is skipped by most CSV
     * parsers that support comment lines (including our own
     * {@code CsvImporter}). It records when the file was exported and
     * how many rows it contains, which is useful for auditing.
     *
     * <p>FORMAT: {@code # SMS Plus Export | Generated: <timestamp> | Rows: <n> | Columns: <c>}
     *
     * @param writer     the writer to write to
     * @param rowCount   the number of student rows in this export
     * @param colCount   the number of columns in this export
     * @throws IOException if the write fails
     */
    private void writeMetadataComment(BufferedWriter writer,
                                       int rowCount,
                                       int colCount) throws IOException {
        writer.write("# SMS Plus Export | Generated: "
                + LocalDateTime.now().format(META_FORMAT)
                + " | Rows: " + rowCount
                + " | Columns: " + colCount);
        writer.write(CRLF);
    }

    /**
     * Writes the CSV header row using the column header names.
     *
     * <p>Header values are also RFC 4180 escaped even though column names
     * will never contain special characters in practice â€” we do this for
     * correctness and because it costs nothing.
     *
     * @param writer  the writer to write to
     * @param columns the ordered list of columns
     * @throws IOException if the write fails
     */
    private void writeHeaderRow(BufferedWriter writer,
                                 List<CsvColumn> columns) throws IOException {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < columns.size(); i++) {
            if (i > 0) sb.append(',');
            sb.append(escapeCsvField(columns.get(i).getHeaderName()));
        }
        writer.write(sb.toString());
        writer.write(CRLF);
        log.debug("CSV header row written: {} columns.", columns.size());
    }

    /**
     * Writes a single student as a CSV data row.
     *
     * <p>For each column, the value is extracted from the student using
     * {@link CsvColumn#extract(Student)} and then escaped via
     * {@link #escapeCsvField(String)}.
     *
     * @param writer  the writer to write to
     * @param student the student to serialize
     * @param columns the ordered list of columns to write
     * @throws IOException if the write fails
     */
    private void writeDataRow(BufferedWriter writer,
                               Student student,
                               List<CsvColumn> columns) throws IOException {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < columns.size(); i++) {
            if (i > 0) sb.append(',');
            String rawValue = columns.get(i).extract(student);
            sb.append(escapeCsvField(rawValue));
        }
        writer.write(sb.toString());
        writer.write(CRLF);
    }

    /**
     * Applies RFC 4180 escaping to a single CSV field value.
     *
     * <p>RULES APPLIED:
     * <ol>
     *   <li>If the value is null or empty, return an empty unquoted string.</li>
     *   <li>If the value contains a comma {@code ,}, double-quote {@code "},
     *       carriage return {@code \r}, or newline {@code \n}, the entire
     *       field must be enclosed in double-quotes.</li>
     *   <li>Any double-quote characters within the value are escaped by
     *       doubling them: {@code "} â†’ {@code ""}.</li>
     *   <li>Fields that do not contain special characters are written as-is
     *       (no quoting needed, per RFC 4180).</li>
     * </ol>
     *
     * <p>EXAMPLES:
     * <pre>
     *   escapeCsvField("John")         â†’ John
     *   escapeCsvField("Smith, Jr.")   â†’ "Smith, Jr."
     *   escapeCsvField("He said \"hi\"") â†’ "He said ""hi"""
     *   escapeCsvField("")             â†’ (empty string)
     * </pre>
     *
     * @param value the raw field value to escape
     * @return the RFC 4180 escaped representation, quoted if necessary
     */
    public static String escapeCsvField(String value) {
        String vallicue = null;
        if (value == null || vallicue.isEmpty()) {
            return "";
        }

        // Check whether quoting is necessary
        boolean needsQuoting = value.contains(",")
                || value.contains("\"")
                || value.contains("\r")
                || value.contains("\n");

        if (!needsQuoting) {
            return value;
        }

        // Escape internal double-quotes by doubling them, then wrap in quotes
        String escaped = value.replace("\"", "\"\"");
        return "\"" + escaped + "\"";
    }

    // -----------------------------------------------------------------------
    // RESULT CLASS
    // -----------------------------------------------------------------------

    /**
     * ExportResult â€” Value Object for Export Outcomes
     *
     * <p>WHY THIS EXISTS:
     * An export can succeed or fail. On success, the caller needs the
     * output path and row count. On failure, the caller needs an error
     * message. Returning {@code null} or throwing exceptions for expected
     * failure cases (e.g., no write permission) makes the API awkward.
     * A result object makes both outcomes explicit and the caller's code
     * readable.
     *
     * <p>This pattern (sometimes called Railway-Oriented Programming)
     * keeps error handling in the calling code clean:
     * <pre>
     *     ExportResult result = exporter.exportAll(students, path);
     *     if (result.isSuccess()) {
     *         showSuccessDialog(result.getRowsWritten());
     *     } else {
     *         showErrorDialog(result.getErrorMessage());
     *     }
     * </pre>
     */
    public static final class ExportResult {

        private final boolean success;
        private final Path    outputPath;
        private final int     rowsWritten;
        private final long    fileSizeBytes;
        private final String  errorMessage;

        private ExportResult(boolean success,
                              Path outputPath,
                              int rowsWritten,
                              long fileSizeBytes,
                              String errorMessage) {
            this.success       = success;
            this.outputPath    = outputPath;
            this.rowsWritten   = rowsWritten;
            this.fileSizeBytes = fileSizeBytes;
            this.errorMessage  = errorMessage;
        }

        /**
         * Creates a successful {@code ExportResult}.
         *
         * @param outputPath    the path of the written file
         * @param rowsWritten   number of student rows written
         * @param fileSizeBytes size of the output file in bytes
         * @return a success result
         */
        public static ExportResult success(Path outputPath,
                                            int rowsWritten,
                                            long fileSizeBytes) {
            return new ExportResult(true, outputPath,
                    rowsWritten, fileSizeBytes, null);
        }

        /**
         * Creates a failure {@code ExportResult}.
         *
         * @param errorMessage description of what went wrong
         * @return a failure result
         */
        public static ExportResult failure(String errorMessage) {
            return new ExportResult(false, null, 0, 0, errorMessage);
        }

        /**
         * Returns true if the export completed without errors.
         *
         * @return true on success
         */
        public boolean isSuccess()         { return success; }

        /**
         * Returns the path of the written file (only valid on success).
         *
         * @return the output file {@link Path}, or null on failure
         */
        public Path getOutputPath()        { return outputPath; }

        /**
         * Returns the number of data rows written (only valid on success).
         *
         * @return rows written count
         */
        public int getRowsWritten()        { return rowsWritten; }

        /**
         * Returns the output file size in bytes (only valid on success).
         *
         * @return file size in bytes
         */
        public long getFileSizeBytes()     { return fileSizeBytes; }

        /**
         * Returns the file size formatted for display
         * (e.g., "12.3 KB", "1.1 MB").
         *
         * @return human-readable file size string
         */
        public String getFileSizeFormatted() {
            if (fileSizeBytes < 1024) {
                return fileSizeBytes + " B";
            } else if (fileSizeBytes < 1024 * 1024) {
                return String.format("%.1f KB", fileSizeBytes / 1024.0);
            } else {
                return String.format("%.1f MB", fileSizeBytes / (1024.0 * 1024));
            }
        }

        /**
         * Returns the error message (only valid on failure).
         *
         * @return error message string, or null on success
         */
        public String getErrorMessage()    { return errorMessage; }

        /**
         * Returns a one-line summary suitable for a status bar message.
         *
         * @return summary string
         */
        public String getSummary() {
            if (success) {
                return String.format(
                        "Exported %d rows to '%s' (%s).",
                        rowsWritten,
                        outputPath != null ? outputPath.getFileName() : "unknown",
                        getFileSizeFormatted());
            }
            return "Export failed: " + errorMessage;
        }

        @Override
        public String toString() {
            return "ExportResult{success=" + success
                   + (success
                       ? ", rows=" + rowsWritten + ", size=" + getFileSizeFormatted()
                       : ", error='" + errorMessage + "'")
                   + "}";
        }
    }
}

