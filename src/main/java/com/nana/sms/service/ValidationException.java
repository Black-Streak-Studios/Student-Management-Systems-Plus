package com.nana.sms.service;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * ValidationException â€” Service Layer Checked Exception
 *
 * <p>WHY THIS CLASS EXISTS:
 * When a student form is submitted with invalid data (e.g., blank name,
 * malformed email, GPA out of range), we need to communicate ALL validation
 * errors back to the UI in one pass â€” not just the first error found.
 * A single {@code IllegalArgumentException} with one message is not enough.
 *
 * <p>This exception carries a {@code Map<String, String>} of field-to-error
 * pairs so the UI can highlight each invalid field individually
 * (e.g., mark the email TextField red with "Invalid email format").
 *
 * <p>WHY CHECKED:
 * {@code ValidationException} represents a recoverable business rule
 * violation â€” the user can fix their input and try again. Checked exceptions
 * are appropriate here because every caller of the service layer MUST handle
 * this case explicitly (show the errors to the user). An unchecked exception
 * would let callers accidentally ignore validation failures.
 *
 * <p>FIELD KEY CONVENTION:
 * Field keys match the {@link com.nana.sms.domain.Student} getter names
 * without "get" (e.g., "firstName", "email", "gpa") so the UI can map
 * errors back to specific form controls by name.
 */
public class ValidationException extends Exception {

    /**
     * Map of field name â†’ error message for all fields that failed validation.
     * Uses {@link LinkedHashMap} to preserve insertion order so errors are
     * presented to the user in the same order they were detected.
     */
    private final Map<String, String> fieldErrors;

    // -----------------------------------------------------------------------
    // CONSTRUCTORS
    // -----------------------------------------------------------------------

    /**
     * Constructs a {@code ValidationException} with a complete map of
     * field-level errors.
     *
     * @param fieldErrors map of field name to error message; must not be null
     */
    public ValidationException(Map<String, String> fieldErrors) {
        super(buildMessage(fieldErrors));
        this.fieldErrors = Collections.unmodifiableMap(
                new LinkedHashMap<>(fieldErrors));
    }

    /**
     * Convenience constructor for a single-field validation failure.
     * Useful when only one field needs to be reported (e.g., duplicate
     * student ID detected after a DB uniqueness check).
     *
     * @param fieldName    the name of the invalid field
     * @param errorMessage the human-readable error description
     */
    public ValidationException(String fieldName, String errorMessage) {
        super(fieldName + ": " + errorMessage);
        Map<String, String> map = new LinkedHashMap<>();
        map.put(fieldName, errorMessage);
        this.fieldErrors = Collections.unmodifiableMap(map);
    }

    // -----------------------------------------------------------------------
    // ACCESSORS
    // -----------------------------------------------------------------------

    /**
     * Returns the map of field names to error messages.
     *
     * <p>The UI layer iterates this map to highlight invalid form fields
     * and display per-field error labels.
     *
     * @return an unmodifiable map of field name â†’ error message
     */
    public Map<String, String> getFieldErrors() {
        return fieldErrors;
    }

    /**
     * Returns true if a specific field has a validation error.
     *
     * <p>Convenience method for UI controllers that want to check
     * a specific field without iterating the whole map.
     *
     * @param fieldName the field to check
     * @return true if the field has an associated error
     */
    public boolean hasError(String fieldName) {
        return fieldErrors.containsKey(fieldName);
    }

    /**
     * Returns the error message for a specific field, or null if
     * that field has no error.
     *
     * @param fieldName the field to retrieve the error for
     * @return the error message string, or null if no error for that field
     */
    public String getError(String fieldName) {
        return fieldErrors.get(fieldName);
    }

    /**
     * Returns true if there are no field errors.
     * An empty {@code ValidationException} should never be thrown, but
     * this method is provided as a defensive guard.
     *
     * @return true if fieldErrors is empty
     */
    public boolean isEmpty() {
        return fieldErrors.isEmpty();
    }

    // -----------------------------------------------------------------------
    // PRIVATE HELPERS
    // -----------------------------------------------------------------------

    /**
     * Builds a single summary message string from all field errors.
     * Used as the {@link Exception#getMessage()} value for logging.
     *
     * @param errors the field error map
     * @return a human-readable summary (e.g., "firstName: must not be blank; email: invalid format")
     */
    private static String buildMessage(Map<String, String> errors) {
        if (errors == null || errors.isEmpty()) {
            return "Validation failed with no specific field errors.";
        }
        StringBuilder sb = new StringBuilder("Validation failed: ");
        errors.forEach((field, msg) ->
                sb.append(field).append(": ").append(msg).append("; "));
        // Remove trailing "; "
        if (sb.length() > 2) {
            sb.setLength(sb.length() - 2);
        }
        return sb.toString();
    }
}

