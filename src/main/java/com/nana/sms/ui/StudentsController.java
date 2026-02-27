package com.nana.sms.ui;

import com.nana.sms.domain.Student;
import com.nana.sms.domain.StudentStatus;
import com.nana.sms.service.StudentService;
import com.nana.sms.service.ValidationException;
import javafx.application.Platform;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.concurrent.Task;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Optional;

/**
 * StudentsController â€” Student Management Screen Controller
 *
 * <p>WHY THIS CLASS EXISTS:
 * This is the primary CRUD screen for student records. It contains:
 * <ul>
 *   <li>A search bar with live filtering.</li>
 *   <li>A {@link TableView} displaying all students.</li>
 *   <li>Toolbar buttons for Add, Edit, Delete.</li>
 *   <li>Status filter ComboBox.</li>
 * </ul>
 *
 * <p>ALL DATA LOADS on background threads using JavaFX {@link Task}.
 * The TableView is never populated directly from the JavaFX Application
 * Thread after a database operation â€” always via {@code Platform.runLater}.
 *
 * <p>NO BUSINESS LOGIC HERE:
 * Validation, persistence, and querying are all delegated to
 * {@link StudentService}. This controller handles only UI state.
 */
public class StudentsController {

    private static final Logger log = LoggerFactory.getLogger(StudentsController.class);

    private final StudentService studentService;

    /** The backing list for the TableView â€” changes propagate automatically. */
    private final ObservableList<Student> studentData =
            FXCollections.observableArrayList();

    /** Reference to the TableView for selection access. */
    private TableView<Student> tableView;

    /** Status bar label at the bottom of the screen. */
    private Label statusBar;

    public StudentsController(StudentService studentService) {
        this.studentService = studentService;
    }

    /**
     * Builds the complete students management view.
     *
     * @return the root {@link VBox} for the students screen
     */
    public VBox buildView() {
        VBox root = new VBox(12);
        root.setPadding(new Insets(10));

        // --- Title ---
        Label title = new Label("Student Records");
        title.setStyle(
                "-fx-font-size: 24px; -fx-font-weight: bold; "
                + "-fx-text-fill: #1e293b;");

        // --- Toolbar ---
        HBox toolbar = buildToolbar();

        // --- TableView ---
        tableView = buildTableView();
        VBox.setVgrow(tableView, Priority.ALWAYS);

        // --- Status bar ---
        statusBar = new Label("Loading...");
        statusBar.setStyle("-fx-text-fill: #64748b; -fx-font-size: 12px;");

        root.getChildren().addAll(title, toolbar, tableView, statusBar);

        // Load data
        loadStudents();

        return root;
    }

    // -----------------------------------------------------------------------
    // TOOLBAR
    // -----------------------------------------------------------------------

    /**
     * Builds the toolbar with search bar and action buttons.
     *
     * @return the toolbar {@link HBox}
     */
    private HBox buildToolbar() {
        HBox toolbar = new HBox(10);
        toolbar.setAlignment(Pos.CENTER_LEFT);
        toolbar.setPadding(new Insets(0, 0, 4, 0));

        // Search field
        TextField searchField = new TextField();
        searchField.setPromptText("\uD83D\uDD0D  Search by name, ID, email, course...");
        searchField.setPrefWidth(320);
        searchField.setStyle(buildFieldStyle());

        // Search on every keystroke (live search)
        searchField.textProperty().addListener((obs, oldVal, newVal) ->
                performSearch(newVal));

        // Status filter
        ComboBox<String> statusFilter = new ComboBox<>();
        statusFilter.getItems().add("All Statuses");
        for (StudentStatus s : StudentStatus.values()) {
            statusFilter.getItems().add(s.getDisplayName());
        }
        statusFilter.setValue("All Statuses");
        statusFilter.setStyle(buildFieldStyle());
        statusFilter.setOnAction(e -> applyStatusFilter(statusFilter.getValue()));

        // Spacer
        HBox spacer = new HBox();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        // Action buttons
        Button btnAdd    = buildButton("+ Add Student",  "#3b82f6");
        Button btnEdit   = buildButton("\u270F Edit",    "#0ea5e9");
        Button btnDelete = buildButton("\uD83D\uDDD1 Delete", "#ef4444");
        Button btnRefresh = buildButton("\u21BB Refresh", "#64748b");

        btnAdd.setOnAction(e    -> handleAdd());
        btnEdit.setOnAction(e   -> handleEdit());
        btnDelete.setOnAction(e -> handleDelete());
        btnRefresh.setOnAction(e -> loadStudents());

        toolbar.getChildren().addAll(
                searchField, statusFilter, spacer,
                btnRefresh, btnAdd, btnEdit, btnDelete);

        return toolbar;
    }

    // -----------------------------------------------------------------------
    // TABLE VIEW
    // -----------------------------------------------------------------------

    /**
     * Builds and configures the {@link TableView} for student records.
     *
     * <p>Columns are bound to JavaFX Properties on the {@link Student} class.
     * Changes to the model automatically refresh the cell values without
     * any manual refresh calls.
     *
     * @return the configured {@link TableView}
     */
    @SuppressWarnings("unchecked")
    private TableView<Student> buildTableView() {
        TableView<Student> table = new TableView<>();
        table.setItems(studentData);
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);
        table.setStyle("-fx-background-radius: 8; -fx-border-radius: 8;");
        table.setPlaceholder(new Label("No students found."));

        // --- Columns ---
        TableColumn<Student, String> colStudentId =
                col("Student ID", 120,
                    s -> s.studentIdProperty());
        TableColumn<Student, String> colFirstName =
                col("First Name", 120,
                    s -> s.firstNameProperty());
        TableColumn<Student, String> colLastName =
                col("Last Name", 120,
                    s -> s.lastNameProperty());
        TableColumn<Student, String> colEmail =
                col("Email", 200,
                    s -> s.emailProperty());
        TableColumn<Student, String> colCourse =
                col("Course", 180,
                    s -> s.courseProperty());
        TableColumn<Student, String> colYear =
                col("Year", 60,
                    s -> s.yearLevelProperty().asString());
        TableColumn<Student, String> colGpa =
                col("GPA", 70,
                    s -> s.gpaProperty().asString("%.2f"));

        // Status column with colour-coded cell
        TableColumn<Student, String> colStatus = new TableColumn<>("Status");
        colStatus.setPrefWidth(90);
        colStatus.setCellValueFactory(data ->
                new SimpleStringProperty(
                        data.getValue().getStatus().getDisplayName()));
        colStatus.setCellFactory(col -> new TableCell<>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                    setStyle("");
                    return;
                }
                setText(item);
                String color = switch (item) {
                    case "Active"    -> "#22c55e";
                    case "Graduated" -> "#8b5cf6";
                    case "Inactive"  -> "#94a3b8";
                    case "Suspended" -> "#ef4444";
                    default          -> "#64748b";
                };
                setStyle("-fx-text-fill: " + color
                        + "; -fx-font-weight: bold;");
            }
        });

        table.getColumns().addAll(
                colStudentId, colFirstName, colLastName,
                colEmail, colCourse, colYear, colGpa, colStatus);

        // Double-click to edit
        table.setRowFactory(tv -> {
            TableRow<Student> row = new TableRow<>();
            row.setOnMouseClicked(event -> {
                if (event.getClickCount() == 2 && !row.isEmpty()) {
                    handleEdit();
                }
            });
            return row;
        });

        return table;
    }

    /**
     * Creates a bound {@link TableColumn} with a property value factory.
     *
     * @param <T>      the property type
     * @param header   column header text
     * @param width    preferred column width
     * @param extractor function from Student to an observable value
     * @return the configured column
     */
    private <T> TableColumn<Student, T> col(
            String header, int width,
            java.util.function.Function<Student,
                    javafx.beans.value.ObservableValue<T>> extractor) {

        TableColumn<Student, T> column = new TableColumn<>(header);
        column.setPrefWidth(width);
        column.setCellValueFactory(data -> extractor.apply(data.getValue()));
        return column;
    }

    // -----------------------------------------------------------------------
    // DATA LOADING (background threads)
    // -----------------------------------------------------------------------

    /**
     * Loads all students from the service on a background thread.
     *
     * <p>Shows a loading message in the status bar while running.
     * Updates the TableView on the JavaFX Application Thread on completion.
     */
    private void loadStudents() {
        statusBar.setText("Loading students...");

        Task<List<Student>> task = new Task<>() {
            @Override
            protected List<Student> call() {
                return studentService.getAllStudents();
            }
        };

        task.setOnSucceeded(e -> {
            studentData.setAll(task.getValue());
            updateStatusBar();
        });

        task.setOnFailed(e -> {
            log.error("Failed to load students.", task.getException());
            statusBar.setText("Error loading students: "
                    + task.getException().getMessage());
        });

        runInBackground(task, "student-loader");
    }

    /**
     * Performs a live search using the service on a background thread.
     *
     * @param keyword the search term
     */
    private void performSearch(String keyword) {
        Task<List<Student>> task = new Task<>() {
            @Override
            protected List<Student> call() {
                return studentService.searchStudents(keyword);
            }
        };

        task.setOnSucceeded(e -> {
            studentData.setAll(task.getValue());
            updateStatusBar();
        });

        task.setOnFailed(e ->
                statusBar.setText("Search failed: "
                        + task.getException().getMessage()));

        runInBackground(task, "student-search");
    }

    /**
     * Filters students by status using a background thread.
     *
     * @param statusDisplayName the selected status display name, or "All Statuses"
     */
    private void applyStatusFilter(String statusDisplayName) {
        if ("All Statuses".equals(statusDisplayName)) {
            loadStudents();
            return;
        }

        // Find matching status enum
        StudentStatus status = null;
        for (StudentStatus s : StudentStatus.values()) {
            if (s.getDisplayName().equals(statusDisplayName)) {
                status = s;
                break;
            }
        }

        final StudentStatus finalStatus = status;
        if (finalStatus == null) {
            loadStudents();
            return;
        }

        Task<List<Student>> task = new Task<>() {
            @Override
            protected List<Student> call() {
                return studentService.getStudentsByStatus(finalStatus);
            }
        };

        task.setOnSucceeded(e -> {
            studentData.setAll(task.getValue());
            updateStatusBar();
        });

        task.setOnFailed(e -> statusBar.setText("Filter failed."));
        runInBackground(task, "student-filter");
    }

    // -----------------------------------------------------------------------
    // CRUD HANDLERS
    // -----------------------------------------------------------------------

    /** Opens the Add Student form dialog. */
    private void handleAdd() {
        StudentFormController form = new StudentFormController(
                studentService, null);
        boolean saved = form.showDialog(
                tableView.getScene().getWindow());
        if (saved) {
            loadStudents();
        }
    }

    /**
     * Opens the Edit Student form dialog for the selected row.
     * Shows an alert if no row is selected.
     */
    private void handleEdit() {
        Student selected = tableView.getSelectionModel().getSelectedItem();
        if (selected == null) {
            showInfo("No Selection", "Please select a student to edit.");
            return;
        }

        StudentFormController form = new StudentFormController(
                studentService, selected);
        boolean saved = form.showDialog(
                tableView.getScene().getWindow());
        if (saved) {
            loadStudents();
        }
    }

    /**
     * Deletes the selected student after user confirmation.
     * Shows an alert if no row is selected.
     */
    private void handleDelete() {
        Student selected = tableView.getSelectionModel().getSelectedItem();
        if (selected == null) {
            showInfo("No Selection", "Please select a student to delete.");
            return;
        }

        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION);
        confirm.setTitle("Confirm Delete");
        confirm.setHeaderText("Delete " + selected.getFullName() + "?");
        confirm.setContentText(
                "Student ID: " + selected.getStudentId()
                + "\nThis action cannot be undone.");

        Optional<ButtonType> result = confirm.showAndWait();
        if (result.isPresent() && result.get() == ButtonType.OK) {
            Task<Void> task = new Task<>() {
                @Override
                protected Void call() throws Exception {
                    studentService.removeStudent(selected.getId());
                    return null;
                }
            };

            task.setOnSucceeded(e -> {
                loadStudents();
                statusBar.setText("Student deleted: "
                        + selected.getFullName());
            });

            task.setOnFailed(e -> {
                String msg = task.getException().getMessage();
                showError("Delete Failed", msg);
                log.error("Delete failed for id={}.", selected.getId(),
                        task.getException());
            });

            runInBackground(task, "student-delete");
        }
    }

    // -----------------------------------------------------------------------
    // HELPERS
    // -----------------------------------------------------------------------

    /** Updates the status bar with the current row count. */
    private void updateStatusBar() {
        Platform.runLater(() ->
                statusBar.setText("Showing " + studentData.size()
                        + " student(s)."));
    }

    /**
     * Runs a task on a named daemon background thread.
     *
     * @param task       the task to run
     * @param threadName name for the thread (visible in logs and thread dumps)
     */
    private void runInBackground(Task<?> task, String threadName) {
        Thread thread = new Thread(task);
        thread.setDaemon(true);
        thread.setName(threadName);
        thread.start();
    }

    /** Shows an information alert dialog. */
    private void showInfo(String title, String message) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }

    /** Shows an error alert dialog. */
    private void showError(String title, String message) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }

    /** Returns a consistent CSS style string for input fields. */
    private String buildFieldStyle() {
        return "-fx-background-radius: 6; -fx-border-radius: 6;"
                + "-fx-border-color: #e2e8f0; -fx-border-width: 1;"
                + "-fx-padding: 6 10;";
    }

    /** Builds a styled action button with a given background colour. */
    private Button buildButton(String text, String color) {
        Button btn = new Button(text);
        btn.setStyle(
                "-fx-background-color: " + color + ";"
                + "-fx-text-fill: white;"
                + "-fx-background-radius: 6;"
                + "-fx-padding: 7 14;"
                + "-fx-cursor: hand;"
                + "-fx-font-size: 12px;");
        return btn;
    }
}

