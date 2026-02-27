package com.nana.sms.util;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

/**
 * BuildInfo - Build Metadata Reader
 */
public final class BuildInfo {

    private static final Logger log = LoggerFactory.getLogger(BuildInfo.class);
    private static final String BUILD_INFO_RESOURCE = "/build-info.properties";

    private static BuildInfo instance;

    public static synchronized BuildInfo getInstance() {
        if (instance == null) {
            instance = new BuildInfo();
        }
        return instance;
    }

    private final Properties buildProps = new Properties();

    private BuildInfo() {
        try (InputStream in = BuildInfo.class.getResourceAsStream(BUILD_INFO_RESOURCE)) {
            if (in != null) {
                buildProps.load(in);
                log.debug("BuildInfo loaded: version={}, built={}.", getVersion(), getBuildTimestamp());
            } else {
                log.warn("build-info.properties not found. Run mvn process-resources to generate.");
            }
        } catch (IOException ex) {
            log.warn("Failed to load build-info.properties.", ex);
        }
    }

    public String getVersion()        { return buildProps.getProperty("app.version",         "1.0.0-SNAPSHOT"); }
    public String getAppName()        { return buildProps.getProperty("app.name",             "Student Management System Plus"); }
    public String getBuildTimestamp() { return buildProps.getProperty("app.build.timestamp",  "Unknown"); }
    public String getJavaVersion()    { return buildProps.getProperty("app.java.version",     "Unknown"); }
    public String getGitCommit()      { return buildProps.getProperty("app.build.git.commit", "Unknown"); }
    public String getGitBranch()      { return buildProps.getProperty("app.build.git.branch", "Unknown"); }

    public String getBuildSummary() {
        return String.format("v%s | Built: %s | Git: %s", getVersion(), getBuildTimestamp(), getGitCommit());
    }

    @Override
    public String toString() { return "BuildInfo{" + getBuildSummary() + "}"; }
}

