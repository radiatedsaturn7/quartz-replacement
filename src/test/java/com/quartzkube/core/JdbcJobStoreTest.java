package com.quartzkube.core;

import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.Test;

import javax.sql.DataSource;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class JdbcJobStoreTest {
    @Test
    public void testSaveAndLoad() throws Exception {
        JdbcDataSource ds = new JdbcDataSource();
        ds.setURL("jdbc:h2:mem:test;DB_CLOSE_DELAY=-1");
        DataSource dataSource = ds;
        JdbcJobStore store = new JdbcJobStore(dataSource);
        store.saveJob("com.example.Job");
        List<String> jobs = store.loadJobs();
        assertEquals(1, jobs.size());
        assertEquals("com.example.Job", jobs.get(0));
    }
}
