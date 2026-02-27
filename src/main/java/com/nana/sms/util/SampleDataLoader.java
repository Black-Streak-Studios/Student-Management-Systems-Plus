package com.nana.sms.util;

import com.nana.sms.domain.Student;
import com.nana.sms.domain.StudentStatus;
import com.nana.sms.service.StudentService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * SampleDataLoader - Demo / Development Data Seeder
 */
public final class SampleDataLoader {

    private static final Logger log = LoggerFactory.getLogger(SampleDataLoader.class);

    private static final String[] FIRST_NAMES = {
        "Emma", "Liam", "Olivia", "Noah", "Ava", "James", "Isabella",
        "Oliver", "Sophia", "William", "Mia", "Benjamin", "Charlotte",
        "Elijah", "Amelia", "Lucas", "Harper", "Mason", "Evelyn",
        "Logan", "Abigail", "Alexander", "Emily", "Ethan", "Elizabeth",
        "Daniel", "Mila", "Jacob", "Ella", "Michael"
    };

    private static final String[] LAST_NAMES = {
        "Smith", "Johnson", "Williams", "Brown", "Jones", "Garcia",
        "Miller", "Davis", "Rodriguez", "Martinez", "Hernandez", "Lopez",
        "Gonzalez", "Wilson", "Anderson", "Thomas", "Taylor", "Moore",
        "Jackson", "Martin", "Lee", "Perez", "Thompson", "White",
        "Harris", "Sanchez", "Clark", "Ramirez", "Lewis", "Robinson"
    };

    private static final String[] COURSES = {
        "Bachelor of Computer Science",
        "Bachelor of Business Administration",
        "Bachelor of Engineering (Civil)",
        "Bachelor of Engineering (Software)",
        "Bachelor of Science (Mathematics)",
        "Bachelor of Arts (Psychology)",
        "Diploma in Information Technology",
        "Bachelor of Education"
    };

    private static final StudentStatus[] STATUSES = {
        StudentStatus.ACTIVE, StudentStatus.ACTIVE, StudentStatus.ACTIVE,
        StudentStatus.GRADUATED, StudentStatus.INACTIVE, StudentStatus.SUSPENDED
    };

    private SampleDataLoader() {
        throw new UnsupportedOperationException("SampleDataLoader is a static utility class.");
    }

    public static void loadIfEmpty(StudentService studentService, int count) {
        if (studentService.getTotalStudentCount() > 0) {
            log.debug("Database not empty - skipping sample data load.");
            return;
        }
        log.info("Database is empty - loading {} sample students.", count);
        List<Student> samples = generateSampleStudents(count);
        StudentService.ImportResult result = studentService.importStudents(samples);
        log.info("Sample data load complete: {}", result.getSummary());
        AppLogger.logEvent("SAMPLE_DATA_LOADED",
                "requested=" + count + ", imported=" + result.getSuccessCount());
    }

    private static List<Student> generateSampleStudents(int count) {
        List<Student> students = new ArrayList<>(count);
        Random rng = new Random(42L);
        LocalDateTime baseTime = LocalDateTime.now().minusYears(2);

        for (int i = 0; i < count; i++) {
            String firstName  = FIRST_NAMES[rng.nextInt(FIRST_NAMES.length)];
            String lastName   = LAST_NAMES[rng.nextInt(LAST_NAMES.length)];
            String course     = COURSES[rng.nextInt(COURSES.length)];
            StudentStatus status = STATUSES[rng.nextInt(STATUSES.length)];
            int yearLevel     = rng.nextInt(4) + 1;
            double gpa        = clamp(2.8 + (rng.nextGaussian() * 0.6), 0.0, 4.0);
            LocalDateTime enrolledAt = baseTime.plusDays(rng.nextInt(730));
            String studentId  = String.format("STU-%04d-%03d", 2022 + (i / 100), (i % 100) + 1);
            String email      = (firstName.toLowerCase() + "." + lastName.toLowerCase()
                    + "." + (i + 1) + "@university.edu").replace(" ", "");
            String phone      = rng.nextInt(5) > 0 ? generatePhone(rng) : "";

            students.add(new Student(
                    0, studentId, firstName, lastName, email, phone, course,
                    yearLevel, Math.round(gpa * 100.0) / 100.0, status,
                    enrolledAt, enrolledAt));
        }
        return students;
    }

    private static String generatePhone(Random rng) {
        return String.format("+1-555-%03d-%04d", rng.nextInt(900) + 100, rng.nextInt(9000) + 1000);
    }

    private static double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }
}

