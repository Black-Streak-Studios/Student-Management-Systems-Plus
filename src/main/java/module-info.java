module com.nana.sms {
    requires javafx.controls;
    requires javafx.fxml;
    requires javafx.graphics;
    requires javafx.base;
    requires org.xerial.sqlitejdbc;
    requires java.sql;
    requires org.slf4j;
    requires ch.qos.logback.classic;
    requires ch.qos.logback.core;
    requires java.desktop;
    requires java.prefs;

    opens com.nana.sms.domain to javafx.base, javafx.fxml;
    opens com.nana.sms.ui to javafx.fxml, javafx.graphics;

    exports com.nana.sms.domain;
    exports com.nana.sms.repository;
    exports com.nana.sms.service;
    exports com.nana.sms.ui;
    exports com.nana.sms.util;
}

