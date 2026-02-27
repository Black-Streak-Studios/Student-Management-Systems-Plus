package com.nana.sms.ui;

import com.nana.sms.domain.Student;
import com.nana.sms.domain.StudentStatus;
import com.nana.sms.service.StudentService;
import com.nana.sms.service.ValidationException;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.Window;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;

/**
 * StudentFormController â€” Add / Edit Student Form Dialog
 *
 * <p>WHY THIS CLASS EXISTS:
 * Both "Add Student" and "Edit Student" use the same form fields â€” only
 * the title, the pre-populated values, and the service method called differ.
 * A single form controller that accepts a nullable {@code Student} parameter
 * handles both cases cleanly: if {@code student == null}, we are adding;
 * if {@code student != null}, we are editing.
 *
 * <p>VALIDATION DISPLAY:
 * When the service throws {@link ValidationException}, each field error
 * is displayed below the corresponding input field in red. Fields without
 * errors are displayed normally.
 *
 * <p>MODAL DIALOG:
 * This form is shown as an application-modal dialog (blocks the parent
 * window). Returns {@code true} if the user saved successfully,
 * {@code false} if they cancelled.
 */
public class StudentFormController {

    private static final Logger log =
            LoggerFactory.getLogger(StudentFormController.class);

    private final StudentService studentService;
    /** null = add mode; non-null = edit mode */
    private final Student        existingStudent;

    /** Map of field name â†’ error label for displaying validation errors. */
    private final Map<String, Label> errorLabels = new HashMap<>();

    /** Whether the user saved the form (vs cancelling). */
    private boolean saved = false;

    // -----------------------------------------------------------------------
    // FORM FIELDS
    // -----------------------------------------------------------------------

    private TextField studentIdField;
    private TextField firstNameField;
    private TextField lastNameField;
    private TextField emailField;
    private TextField phoneField;
    private TextField courseField;
    private Spinner<Integer>  yearLevelSpinner;
    private Spinner<Double>   gpaSpinner;
    private ComboBox<StudentStatus> statusCombo;

    // -----------------------------------------------------------------------
    // CONSTRUCTOR
    // -----------------------------------------------------------------------

    /**
     * Creates a form controller.
     *
     * @param studentService  the service for add/update operations
     * @param existingStudent null for Add mode; the student to edit for Edit mode
     */
    public StudentFormController(StudentService studentService,
                                  Student existingStudent) {
        this.studentService  = studentService;
        this.existingStudent = existingStudent;
    }

    // -----------------------------------------------------------------------
    // SHOW DIALOG
    // -----------------------------------------------------------------------

    /**
     * Shows the form as an application-modal dialog.
     *
     * @param owner the parent window (for centring the dialog)
     * @return true if the student was saved; false if cancelled
     */
    public boolean showDialog(Window owner) {
        Stage dialog = new Stage();
        dialog.initModality(Modality.APPLICATION_MODAL);
        dialog.initOwner(owner);
        dialog.setTitle(existingStudent == null
                ? "Add New Student" : "Edit Student");
        dialog.setResizable(false);

        VBox content = buildFormContent(dialog);
        Scene scene = new Scene(content, 480, 580);
        dialog.setScene(scene);
        dialog.showAndWait();

        return saved;
    }

    // -----------------------------------------------------------------------
    // FORM CONTENT
    // -----------------------------------------------------------------------

    /**
     * Builds the complete form layout.
     *
     * @param dialog the dialog stage (needed for the cancel/close button)
     * @return the root {@link VBox} of the form
     */
    private VBox buildFormContent(Stage dialog) {
        VBox root = new VBox(0);
        root.setStyle("-fx-background-color: white;");

        // --- Header ---
        VBox header = new VBox(4);
        header.setPadding(new Insets(20, 24, 16, 24));
        header.setStyle("-fx-background-color: #1e293b;");

        Label titleLabel = new Label(existingStudent == null
                ? "Add New Student" : "Edit Student");
        titleLabel.setStyle(
                "-fx-font-size: 18px; -fx-font-weight: bold; "
                + "-fx-text-fill: white;");

        Label subtitleLabel = new Label(existingStudent == null
                ? "Enter student details below"
                : "Editing: " + existingStudent.getFullName());
        subtitleLabel.setStyle("-fx-text-fill: #94a3b8; -fx-font-size: 12px;");

        header.getChildren().addAll(titleLabel, subtitleLabel);

        // --- Form fields ---
        GridPane grid = new GridPane();
        grid.setVgap(12);
        grid.setHgap(16);
        grid.setPadding(new Insets(20, 24, 8, 24));

        int row = 0;
        studentIdField    = addFormRow(grid, row++, "Student ID *",
                "e.g. STU-2024-001", "studentId");
        firstNameField    = addFormRow(grid, row++, "First Name *",
                "Given name", "firstName");
        lastNameField     = addFormRow(grid, row++, "Last Name *",
                "Family name", "lastName");
        emailField        = addFormRow(grid, row++, "Email *",
                "contact@email.com", "email");
        phoneField        = addFormRow(grid, row++, "Phone",
                "+1-555-000-0000 (optional)", "phone");
        courseField       = addFormRow(grid, row++, "Course *",
                "e.g. Computer Science", "course");

        // Year level spinner (1â€“6)
        Label yearLabel = formLabel("Year Level *");
        yearLevelSpinner = new Spinner<>(1, 6, 1);
        yearLevelSpinner.setEditable(true);
        yearLevelSpinner.setStyle(buildFieldStyle());
        yearLevelSpinner.setPrefWidth(Double.MAX_VALUE);
        Label yearError = errorLabel("yearLevel");
        grid.add(yearLabel, 0, row);
        grid.add(yearLevelSpinner, 1, row);
        grid.add(yearError, 1, row + 1 > row ? row : row + 1);
        row++;

        // GPA spinner (0.0â€“4.0, step 0.01)
        Label gpaLabel = formLabel("GPA *");
        SpinnerValueFactory.DoubleSpinnerValueFactory gpaFactory =
                new SpinnerValueFactory.DoubleSpinnerValueFactory(
                        0.0, 4.0, 0.0, 0.01);
        gpaSpinner = new Spinner<>(gpaFactory);
        gpaSpinner.setEditable(true);
        gpaSpinner.setStyle(buildFieldStyle());
        gpaSpinner.setPrefWidth(Double.MAX_VALUE);
        Label gpaError = errorLabel("gpa");
        grid.add(gpaLabel, 0, row);
        grid.add(gpaSpinner, 1, row);
        grid.add(gpaError, 1, row + 1 > row ? row : row + 1);
        row++;

        // Status combo
        Label statusLabel = formLabel("Status *");
        statusCombo = new ComboBox<>();
        statusCombo.getItems().addAll(StudentStatus.values());
        statusCombo.setValue(StudentStatus.ACTIVE);
        statusCombo.setMaxWidth(Double.MAX_VALUE);
        statusCombo.setStyle(buildFieldStyle());
        Label statusError = errorLabel("status");
        grid.add(statusLabel, 0, row);
        grid.add(statusCombo, 1, row);
        grid.add(statusError, 1, row + 1 > row ? row : row + 1);

        // Set column constraints
        ColumnConstraints labelCol = new ColumnConstraints(110);
        ColumnConstraints fieldCol = new ColumnConstraints();
        fieldCol.setHgrow(Priority.ALWAYS);
        grid.getColumnConstraints().addAll(labelCol, fieldCol);

        // Populate fields if editing
        if (existingStudent != null) {
            populateFields(existingStudent);
        }

        // --- Buttons ---
        HBox buttons = new HBox(10);
        buttons.setPadding(new Insets(16, 24, 20, 24));
        buttons.setAlignment(Pos.CENTER_RIGHT);

        Button btnCancel = new Button("Cancel");
        btnCancel.setStyle(
                "-fx-background-color: #f1f5f9; -fx-text-fill: #475569;"
                + "-fx-background-radius: 6; -fx-padding: 8 20;"
                + "-fx-cursor: hand;");
        btnCancel.setOnAction(e -> dialog.close());

        Button btnSave = new Button(existingStudent == null
                ? "Add Student" : "Save Changes");
        btnSave.setStyle(
                "-fx-background-color: #3b82f6; -fx-text-fill: white;"
                + "-fx-background-radius: 6; -fx-padding: 8 20;"
                + "-fx-cursor: hand; -fx-font-weight: bold;");
        btnSave.setDefaultButton(true);
        btnSave.setOnAction(e -> handleSave(dialog));

        buttons.getChildren().addAll(btnCancel, btnSave);

        root.getChildren().addAll(header, grid, buttons);
        return root;
    }

    /**
     * Adds a labelled text field row to the form grid.
     *
     * @param grid      the form grid
     * @param row       the grid row index
     * @param labelText the label text
     * @param prompt    the placeholder text
     * @param fieldName the field name for error label registration
     * @return the created {@link TextField}
     */
    private TextField addFormRow(GridPane grid, int row,
                                  String labelText, String prompt,
                                  String fieldName) {
        Label label = formLabel(labelText);
        TextField field = new TextField();
        field.setPromptText(prompt);
        field.setStyle(buildFieldStyle());
        Label error = errorLabel(fieldName);

        grid.add(label, 0, row);
        grid.add(field,  1, row);
        // Error label occupies same row below field (handled by adding
        // a sub-row â€” simpler to just add it in the next available row)
        // We add error labels into the same cell via a VBox
        VBox fieldWithError = new VBox(2, field, error);
        grid.getChildren().remove(field);
        grid.add(fieldWithError, 1, row);

        return field;
    }

    /** Creates a styled form label. */
    private Label formLabel(String text) {
        Label lbl = new Label(text);
        lbl.setStyle(
                "-fx-font-size: 12px; -fx-text-fill: #374151;"
                + "-fx-font-weight: bold;");
        return lbl;
    }

    /**
     * Creates a hidden error label for a specific field.
     * Registered in {@link #errorLabels} for later population.
     *
     * @param fieldName the field name key
     * @return the styled, initially invisible error label
     */
    private Label errorLabel(String fieldName) {
        Label lbl = new Label();
        lbl.setStyle("-fx-text-fill: #ef4444; -fx-font-size: 11px;");
        lbl.setVisible(false);
        lbl.setManaged(false);
        errorLabels.put(fieldName, lbl);
        return lbl;
    }

    // -----------------------------------------------------------------------
    // FIELD POPULATION (edit mode)
    // -----------------------------------------------------------------------

    /**
     * Pre-populates form fields with the existing student's data (edit mode).
     *
     * @param student the student whose data to display
     */
    private void populateFields(Student student) {
        studentIdField.setText(student.getStudentId());
        firstNameField.setText(student.getFirstName());
        lastNameField.setText(student.getLastName());
        emailField.setText(student.getEmail());
        phoneField.setText(student.getPhone());
        courseField.setText(student.getCourse());
        yearLevelSpinner.getValueFactory().setValue(student.getYearLevel());
        gpaSpinner.getValueFactory().setValue(student.getGpa());
        statusCombo.setValue(student.getStatus());
    }

    // -----------------------------------------------------------------------
    // SAVE HANDLER
    // -----------------------------------------------------------------------

    /**
     * Handles the Save / Add Student button click.
     *
     * <p>Reads all form fields, constructs a {@link Student}, delegates to
     * the service layer. On {@link ValidationException}, populates error
     * labels for each failing field. On success, closes the dialog.
     *
     * @param dialog the dialog stage to close on success
     */
    private void handleSave(Stage dialog) {
        // Clear previous errors
        clearErrors();

        // Build student from form fields
        Student student = buildStudentFromForm();

        try {
            if (existingStudent == null) {
                studentService.addStudent(student);
                log.info("Student added: {}", student.getStudentId());
            } else {
                student.setId(existingStudent.getId());
                student.setEnrolledAt(existingStudent.getEnrolledAt());
                studentService.updateStudent(student);
                log.info("Student updated: id={}.", student.getId());
            }

            saved = true;
            dialog.close();

        } catch (ValidationException ex) {
            log.warn("Form validation failed: {}", ex.getMessage());
            displayErrors(ex);
        } catch (Exception ex) {
            log.error("Unexpected error saving student.", ex);
            Alert alert = new Alert(Alert.AlertType.ERROR);
            alert.setTitle("Save Error");
            alert.setContentText("An unexpected error occurred: " + ex.getMessage());
            alert.showAndWait();
        }
    }

    /**
     * Builds a {@link Student} from the current form field values.
     *
     * <p>Does NOT validate â€” validation is the service layer's responsibility.
     *
     * @return a Student populated with form values
     */
    private Student buildStudentFromForm() {
        Student student = new Student();
        student.setStudentId(studentIdField.getText());
        student.setFirstName(firstNameField.getText());
        student.setLastName(lastNameField.getText());
        student.setEmail(emailField.getText());
        student.setPhone(phoneField.getText());
        student.setCourse(courseField.getText());
        student.setYearLevel(yearLevelSpinner.getValue());

        // GPA spinner may have uncommitted text â€” commit it first
        gpaSpinner.getEditor().commitValue();
        student.setGpa(gpaSpinner.getValue());
        student.setStatus(statusCombo.getValue());
        return student;
    }

    // -----------------------------------------------------------------------
    // ERROR DISPLAY
    // -----------------------------------------------------------------------

    /**
     * Displays field-level validation errors from a {@link ValidationException}.
     * Shows the error label for each failing field and styles the field red.
     *
     * @param ex the validation exception with field errors
     */
    private void displayErrors(ValidationException ex) {
        ex.getFieldErrors().forEach((fieldName, message) -> {
            Label errLabel = errorLabels.get(fieldName);
            if (errLabel != null) {
                errLabel.setText(message);
                errLabel.setVisible(true);
                errLabel.setManaged(true);
            }
        });
    }

    /**
     * Clears all error labels and resets field styles to normal.
     */
    private void clearErrors() {
        errorLabels.values().forEach(lbl -> {
            lbl.setVisible(false);
            lbl.setManaged(false);
            lbl.setText("");
        });
    }

    /** Returns a consistent CSS style string for form input fields. */
    private String buildFieldStyle() {
        return "-fx-background-radius: 6; -fx-border-radius: 6;"
                + "-fx-border-color: #e2e8f0; -fx-border-width: 1;"
                + "-fx-padding: 6 10; -fx-font-size: 13px;";
    }
}

