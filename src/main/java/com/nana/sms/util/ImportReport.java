package com.nana.sms.util;

import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * ImportReport â€” Immutable Import Outcome Value Object
 *
 * <p>WHY THIS CLASS EXISTS:
 * A CSV import is not a binary success/failure operation â€” it can partially
 * succeed with some rows imported and others rejected. The UI needs to show
 * the user a detailed breakdown: how many rows were processed, how many
 * succeeded, which rows failed, and exactly why each one failed.
 *
 * <p>This class collects all of that information during the import process
 * and presents it in a structured, immutable form. It also provides a
 * {@link #toReportText()} method that produces a human-readable text
 * document suitable for writing to a companion "_errors.txt" file alongside
 * the original CSV â€” so users have a permanent record of what failed.
 *
 * <p>IMMUTABILITY:
 * The object is built via a mutable {@link Builder} during import
 * processing, then finalised into an immutable {@code ImportReport}.
 * Once finalised, no state can change â€” the report can be safely passed
 * across threads (e.g., from a background Task to the JavaFX Application
 * Thread) without synchronisation.
 *
 * <p>ROW RESULT MODEL:
 * Each row in the import file is represented by a {@link RowResult}
 * which records the original row number (1-based, matching what the user
 * sees in Excel), the raw CSV line, the outcome (SUCCESS or one of several
 * failure types), and an error message.
 */
public final class ImportReport {

    // -----------------------------------------------------------------------
    // DISPLAY FORMATTER
    // -----------------------------------------------------------------------

    private static final DateTimeFormatter DISPLAY_FORMAT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    // -----------------------------------------------------------------------
    // FIELDS
    // -----------------------------------------------------------------------

    /** The source CSV file path (for display in the report header). */
    private final Path sourceFile;

    /** Timestamp when the import was completed. */
    private final LocalDateTime importedAt;

    /** Total number of data rows in the file (excluding header and comments). */
    private final int totalRows;

    /** Number of rows successfully imported. */
    private final int successCount;

    /** Number of rows that failed for any reason. */
    private final int failureCount;

    /** Number of rows skipped (comment lines or blank lines). */
    private final int skippedCount;

    /**
     * Detailed result for every processed row.
     * Includes both successes and failures for a complete audit trail.
     */
    private final List<RowResult> rowResults;

    // -----------------------------------------------------------------------
    // PRIVATE CONSTRUCTOR â€” use Builder
    // -----------------------------------------------------------------------

    private ImportReport(Builder builder) {
        this.sourceFile   = builder.sourceFile;
        this.importedAt   = builder.importedAt != null
                ? builder.importedAt : LocalDateTime.now();
        this.totalRows    = builder.totalRows;
        this.successCount = builder.successCount;
        this.failureCount = builder.failureCount;
        this.skippedCount = builder.skippedCount;
        this.rowResults   = Collections.unmodifiableList(
                new ArrayList<>(builder.rowResults));
    }

    // -----------------------------------------------------------------------
    // ACCESSORS
    // -----------------------------------------------------------------------

    /** @return the source CSV file path */
    public Path getSourceFile()     { return sourceFile; }

    /** @return the import completion timestamp */
    public LocalDateTime getImportedAt() { return importedAt; }

    /** @return total data rows processed */
    public int getTotalRows()       { return totalRows; }

    /** @return number of rows successfully imported */
    public int getSuccessCount()    { return successCount; }

    /** @return number of rows that failed */
    public int getFailureCount()    { return failureCount; }

    /** @return number of rows skipped (blank/comment) */
    public int getSkippedCount()    { return skippedCount; }

    /** @return the unmodifiable list of per-row results */
    public List<RowResult> getRowResults() { return rowResults; }

    /**
     * Returns only the failed row results â€” convenient for displaying
     * the error list in the UI without filtering in the controller.
     *
     * @return list of {@link RowResult} objects with non-SUCCESS outcomes
     */
    public List<RowResult> getFailedRows() {
        return rowResults.stream()
                .filter(r -> r.getOutcome() != RowResult.Outcome.SUCCESS)
                .toList();
    }

    /**
     * Returns true if every data row was imported successfully.
     * (Skipped rows do not count as failures.)
     *
     * @return true if failureCount == 0
     */
    public boolean isFullSuccess()  { return failureCount == 0; }

    /**
     * Returns true if every data row failed.
     *
     * @return true if successCount == 0 and totalRows > 0
     */
    public boolean isFullFailure()  {
        return successCount == 0 && totalRows > 0;
    }

    /**
     * Returns a one-line summary suitable for a status bar or alert title.
     *
     * @return summary string (e.g., "Imported 47 of 50 rows. 3 failed.")
     */
    public String getSummary() {
        if (totalRows == 0) {
            return "No data rows found in the file.";
        }
        if (isFullSuccess()) {
            return String.format(
                    "Successfully imported all %d rows.", totalRows);
        }
        return String.format(
                "Imported %d of %d rows. %d failed.",
                successCount, totalRows, failureCount);
    }

    // -----------------------------------------------------------------------
    // REPORT TEXT GENERATION
    // -----------------------------------------------------------------------

    /**
     * Generates a human-readable plain-text import report.
     *
     * <p>This text is written to a companion "_import_report.txt" file
     * alongside the original CSV so users have a permanent, portable
     * record of what happened during the import. It can also be displayed
     * directly in the UI's import result dialog.
     *
     * <p>STRUCTURE:
     * <pre>
     * ============================================================
     *  SMS Plus â€” Import Report
     * ============================================================
     *  Source File : students.csv
     *  Imported At : 2025-01-15 14:32:00
     *  Total Rows  : 50
     *  Succeeded   : 47
     *  Failed      : 3
     *  Skipped     : 0
     * ------------------------------------------------------------
     *  FAILED ROWS:
     * ------------------------------------------------------------
     *  Row 3  | VALIDATION_ERROR | email: invalid format
     *         | Raw: STU-2024-003,John,Smith,badmail,...
     *  Row 17 | DUPLICATE_KEY    | Student ID 'STU-2024-017' already exists
     *         | Raw: STU-2024-017,Jane,Doe,...
     * ============================================================
     *  Import complete.
     * ============================================================
     * </pre>
     *
     * @return the full report as a multi-line string
     */
    public String toReportText() {
        StringBuilder sb = new StringBuilder();
        String line60  = "=".repeat(60);
        String line60d = "-".repeat(60);

        sb.append(line60).append("\n");
        sb.append(" SMS Plus \u2014 Import Report\n");
        sb.append(line60).append("\n");
        sb.append(String.format(" %-14s: %s%n", "Source File",
                sourceFile != null ? sourceFile.getFileName() : "Unknown"));
        sb.append(String.format(" %-14s: %s%n", "Imported At",
                importedAt.format(DISPLAY_FORMAT)));
        sb.append(String.format(" %-14s: %d%n", "Total Rows",  totalRows));
        sb.append(String.format(" %-14s: %d%n", "Succeeded",   successCount));
        sb.append(String.format(" %-14s: %d%n", "Failed",      failureCount));
        sb.append(String.format(" %-14s: %d%n", "Skipped",     skippedCount));
        sb.append(line60d).append("\n");

        if (failureCount == 0) {
            sb.append(" All rows imported successfully.\n");
        } else {
            sb.append(" FAILED ROWS:\n");
            sb.append(line60d).append("\n");
            for (RowResult rr : getFailedRows()) {
                sb.append(String.format(" Row %-5d | %-20s | %s%n",
                        rr.getRowNumber(),
                        rr.getOutcome().name(),
                        rr.getErrorMessage()));
                if (rr.getRawLine() != null && !rr.getRawLine().isBlank()) {
                    // Truncate raw line to 80 chars for readability
                    String raw = rr.getRawLine();
                    if (raw.length() > 80) {
                        raw = raw.substring(0, 77) + "...";
                    }
                    sb.append(String.format("         | Raw: %s%n", raw));
                }
            }
        }

        sb.append(line60).append("\n");
        sb.append(" Import complete.\n");
        sb.append(line60).append("\n");

        return sb.toString();
    }

    @Override
    public String toString() {
        return "ImportReport{total=" + totalRows
               + ", success=" + successCount
               + ", failed=" + failureCount
               + ", skipped=" + skippedCount + "}";
    }

    // -----------------------------------------------------------------------
    // INNER CLASS: RowResult
    // -----------------------------------------------------------------------

    /**
     * RowResult â€” Per-Row Import Outcome
     *
     * <p>WHY A NESTED CLASS:
     * A row result is only meaningful in the context of an import report.
     * Nesting it here keeps the two concepts co-located without polluting
     * the package namespace.
     *
     * <p>Each {@code RowResult} records:
     * <ul>
     *   <li>The 1-based row number in the source file (matching Excel's row
     *       numbers so users can find the row in their spreadsheet).</li>
     *   <li>The raw CSV line (for display in the error report).</li>
     *   <li>The outcome enum (categorises the type of failure).</li>
     *   <li>An error message (human-readable explanation).</li>
     * </ul>
     */
    public static final class RowResult {

        /**
         * Outcome â€” Categorises the result of processing a single CSV row.
         *
         * <p>Using a typed enum rather than a free-form string allows the
         * UI to show outcome-specific icons (green tick, red cross, warning)
         * and allows the report to group failures by type.
         */
        public enum Outcome {
            /** Row was parsed and imported without errors. */
            SUCCESS,

            /**
             * Row was skipped because it was blank or a comment line
             * (starts with {@code #}).
             */
            SKIPPED,

            /**
             * Row had the wrong number of columns.
             * Usually caused by unquoted commas in a field.
             */
            WRONG_COLUMN_COUNT,

            /**
             * Row contained a field value that failed type conversion
             * (e.g., non-numeric year level, non-numeric GPA).
             */
            PARSE_ERROR,

            /**
             * Row passed parsing but failed business rule validation
             * (e.g., blank name, invalid email, GPA out of range).
             */
            VALIDATION_ERROR,

            /**
             * Row passed validation but the database rejected it due to
             * a unique constraint violation (duplicate studentId or email).
             */
            DUPLICATE_KEY,

            /**
             * An unexpected exception occurred while processing this row.
             * Should not happen in normal operation â€” indicates a bug.
             */
            UNEXPECTED_ERROR
        }

        private final int     rowNumber;
        private final String  rawLine;
        private final Outcome outcome;
        private final String  errorMessage;

        /**
         * Constructs a {@code RowResult}.
         *
         * @param rowNumber    1-based row number in the source file
         * @param rawLine      the original CSV line text
         * @param outcome      the processing outcome
         * @param errorMessage human-readable error detail (empty for SUCCESS)
         */
        public RowResult(int rowNumber,
                          String rawLine,
                          Outcome outcome,
                          String errorMessage) {
            this.rowNumber    = rowNumber;
            this.rawLine      = rawLine;
            this.outcome      = outcome;
            this.errorMessage = errorMessage == null ? "" : errorMessage;
        }

        /** @return the 1-based row number in the source file */
        public int getRowNumber()      { return rowNumber; }

        /** @return the raw CSV line as it appeared in the file */
        public String getRawLine()     { return rawLine; }

        /** @return the processing outcome enum */
        public Outcome getOutcome()    { return outcome; }

        /** @return human-readable error message (empty for SUCCESS) */
        public String getErrorMessage() { return errorMessage; }

        /** @return true if this row was imported successfully */
        public boolean isSuccess() {
            return outcome == Outcome.SUCCESS;
        }

        @Override
        public String toString() {
            return "RowResult{row=" + rowNumber
                   + ", outcome=" + outcome
                   + (errorMessage.isBlank() ? "" : ", error='" + errorMessage + "'")
                   + "}";
        }
    }

    // -----------------------------------------------------------------------
    // INNER CLASS: Builder
    // -----------------------------------------------------------------------

    /**
     * Builder â€” Mutable accumulator for building an {@link ImportReport}.
     *
     * <p>The {@link CsvImporter} creates one {@code Builder} per import
     * operation and calls {@code addRow()} for each processed row as
     * processing proceeds. After the last row is processed, it calls
     * {@link #build()} to produce the immutable {@link ImportReport}.
     *
     * <p>WHY A BUILDER HERE:
     * Unlike {@link com.nana.sms.service.ReportData} where all data is
     * available upfront, import results accumulate row-by-row. A mutable
     * builder that can accept rows incrementally is the natural fit.
     */
    public static final class Builder {

        private Path          sourceFile;
        private LocalDateTime importedAt;
        private int           totalRows    = 0;
        private int           successCount = 0;
        private int           failureCount = 0;
        private int           skippedCount = 0;
        private final List<RowResult> rowResults = new ArrayList<>();

        /**
         * Creates a {@code Builder} for the given source file.
         *
         * @param sourceFile the CSV file being imported
         */
        public Builder(Path sourceFile) {
            this.sourceFile = sourceFile;
            this.importedAt = LocalDateTime.now();
        }

        /**
         * Records a successfully imported row.
         *
         * @param rowNumber the 1-based row number
         * @param rawLine   the original CSV line
         * @return this builder for chaining
         */
        public Builder addSuccess(int rowNumber, String rawLine) {
            rowResults.add(new RowResult(
                    rowNumber, rawLine,
                    RowResult.Outcome.SUCCESS, ""));
            totalRows++;
            successCount++;
            return this;
        }

        /**
         * Records a skipped row (blank or comment line).
         *
         * @param rowNumber the 1-based row number
         * @param rawLine   the original line
         * @param reason    why the row was skipped
         * @return this builder for chaining
         */
        public Builder addSkipped(int rowNumber, String rawLine, String reason) {
            rowResults.add(new RowResult(
                    rowNumber, rawLine,
                    RowResult.Outcome.SKIPPED, reason));
            skippedCount++;
            return this;
        }

        /**
         * Records a failed row with a specific outcome type.
         *
         * @param rowNumber    the 1-based row number
         * @param rawLine      the original CSV line
         * @param outcome      the specific failure type
         * @param errorMessage human-readable explanation
         * @return this builder for chaining
         */
        public Builder addFailure(int rowNumber,
                                   String rawLine,
                                   RowResult.Outcome outcome,
                                   String errorMessage) {
            rowResults.add(new RowResult(
                    rowNumber, rawLine, outcome, errorMessage));
            totalRows++;
            failureCount++;
            return this;
        }

        /**
         * Builds and returns the immutable {@link ImportReport}.
         * Sets the import timestamp to now if not already set.
         *
         * @return the completed {@link ImportReport}
         */
        public ImportReport build() {
            if (importedAt == null) {
                importedAt = LocalDateTime.now();
            }
            return new ImportReport(this);
        }
    }
}

