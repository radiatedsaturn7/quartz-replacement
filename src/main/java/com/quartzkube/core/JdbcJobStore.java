package com.quartzkube.core;

import javax.sql.DataSource;
import java.sql.*;
import java.util.ArrayList;
import java.util.List;

/**
 * JobStore backed by a JDBC DataSource. Stores job class names in a simple table
 * called 'scheduled_jobs'. This allows multiple scheduler instances to share a
 * persistent store for clustering and restart recovery.
 */
public class JdbcJobStore implements JobStore {
    private final DataSource dataSource;

    public JdbcJobStore(DataSource dataSource) throws SQLException {
        this.dataSource = dataSource;
        initSchema();
    }

    private void initSchema() throws SQLException {
        try (Connection c = dataSource.getConnection();
             Statement s = c.createStatement()) {
            s.executeUpdate("CREATE TABLE IF NOT EXISTS scheduled_jobs (job_class VARCHAR(255) PRIMARY KEY)");
        }
    }

    @Override
    public void saveJob(String jobClass) throws Exception {
        try (Connection c = dataSource.getConnection();
             PreparedStatement ps = c.prepareStatement("MERGE INTO scheduled_jobs (job_class) KEY(job_class) VALUES (?)")) {
            ps.setString(1, jobClass);
            ps.executeUpdate();
        }
    }

    @Override
    public List<String> loadJobs() throws Exception {
        List<String> list = new ArrayList<>();
        try (Connection c = dataSource.getConnection();
             PreparedStatement ps = c.prepareStatement("SELECT job_class FROM scheduled_jobs");
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                list.add(rs.getString(1));
            }
        }
        return list;
    }
}
