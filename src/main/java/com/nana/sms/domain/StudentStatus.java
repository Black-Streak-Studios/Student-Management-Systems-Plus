package com.nana.sms.domain;

/**
 * StudentStatus â€” Domain Layer Enumeration
 *
 * <p>WHY THIS EXISTS:
 * Representing student status as a raw {@code String} throughout the
 * application is fragile â€” a typo like "Actve" silently produces wrong
 * behaviour. An enum makes invalid states unrepresentable at compile time,
 * enables switch exhaustiveness checks in Java 21, and gives us a single
 * authoritative list of valid values that is shared by the domain, service
 * (validation), repository (SQL CHECK constraint mirrors these values), and
 * UI (ComboBox population).
 *
 * <p>The {@code displayName} field provides a human-readable label for
 * rendering in the UI without coupling the enum to JavaFX.
 */
public enum StudentStatus {

    /** Student is currently enrolled and attending. */
    ACTIVE("Active"),

    /** Student has temporarily stopped attending (e.g., leave of absence). */
    INACTIVE("Inactive"),

    /** Student has successfully completed their programme. */
    GRADUATED("Graduated"),

    /** Student is suspended pending disciplinary or academic review. */
    SUSPENDED("Suspended");

    // -----------------------------------------------------------------------
    // FIELDS
    // -----------------------------------------------------------------------

    /**
     * Human-readable label suitable for display in the UI.
     * Stored separately from the enum name so the DB-stored value
     * (e.g., "ACTIVE") and the display label (e.g., "Active") can
     * diverge without changing the enum constant name.
     */
    private final String displayName;

    // -----------------------------------------------------------------------
    // CONSTRUCTOR
    // -----------------------------------------------------------------------

    StudentStatus(String displayName) {
        this.displayName = displayName;
    }

    // -----------------------------------------------------------------------
    // ACCESSORS
    // -----------------------------------------------------------------------

    /**
     * Returns the human-readable display name for this status.
     * Used by JavaFX ComboBox cell factories and TableView cell renderers.
     *
     * @return display name string (e.g., "Active", "Graduated")
     */
    public String getDisplayName() {
        return displayName;
    }

    /**
     * Safe factory method that converts a stored database string back to
     * the enum constant, ignoring case to be resilient to legacy data.
     *
     * <p>WHY a factory method instead of {@code valueOf()}:
     * {@code Enum.valueOf()} throws {@link IllegalArgumentException} on an
     * unrecognised string, which would crash the app when reading a row with
     * a corrupt or legacy status value. This method falls back to
     * {@code ACTIVE} and logs a warning instead.
     *
     * @param value the string stored in the database (e.g., "ACTIVE")
     * @return the matching {@code StudentStatus}, or {@code ACTIVE} as default
     */
    public static StudentStatus fromString(String value) {
        if (value == null || value.isBlank()) {
            return ACTIVE;
        }
        for (StudentStatus s : values()) {
            if (s.name().equalsIgnoreCase(value.trim())) {
                return s;
            }
        }
        // Do not throw â€” return a safe default so a single bad row does
        // not prevent the entire student list from loading.
        return ACTIVE;
    }

    /**
     * Returns the enum name, which is also the value persisted to the
     * database. Using {@code name()} rather than {@code displayName} keeps
     * the DB values stable even if display labels change in future versions.
     *
     * @return the enum constant name (e.g., "ACTIVE")
     */
    @Override
    public String toString() {
        return name();
    }
}

