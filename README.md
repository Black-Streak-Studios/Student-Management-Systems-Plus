# Student Management System Plus

A production-ready offline-first desktop application for student record management.

## Tech Stack
- Java 21
- Maven
- JavaFX 21
- SQLite (JDBC)
- JUnit 5
- SLF4J / Logback

## Architecture
Clean layered architecture:
- `com.nana.sms.domain`     â€” Domain model (Student, StudentStatus)
- `com.nana.sms.repository` â€” Data access layer (interface + SQLite impl)
- `com.nana.sms.service`    â€” Business logic, validation, report generation
- `com.nana.sms.ui`         â€” JavaFX controllers (8 screens)
- `com.nana.sms.util`       â€” Infrastructure (DB, CSV, logging, threading)

## Prerequisites
- Java 21 JDK
- Maven 3.8+
- Windows 10/11

## Build and Run

```bash
# Run in development
mvn javafx:run

# Build fat JAR
mvn clean package

# Run fat JAR
java -jar target/student-management-system-plus-1.0.0-SNAPSHOT.jar

# Run tests
mvn test
```

## Data Location
- Database: `%APPDATA%\SMS_Plus\sms_plus.db`
- Logs:     `%APPDATA%\SMS_Plus\logs\sms_plus.log`
- Config:   `%APPDATA%\SMS_Plus\sms_plus.properties`

## Features
- Add, edit, delete student records
- Live search and status filtering
- CSV import and export (RFC 4180)
- Report generation (6 report types)
- Background threading (no UI freezes)
- Sample data loader for demos

