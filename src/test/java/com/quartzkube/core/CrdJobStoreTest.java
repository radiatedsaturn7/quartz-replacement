package com.quartzkube.core;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;

import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class CrdJobStoreTest {
    @Test
    public void testSaveAndLoad() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
        List<String> bodies = new ArrayList<>();
        server.createContext("/apis/quartzkube.com/v1/namespaces/ns/scheduledjobs", ex -> {
            if (ex.getRequestMethod().equals("POST")) {
                bodies.add(new String(ex.getRequestBody().readAllBytes()));
                ex.sendResponseHeaders(201, -1);
                ex.close();
            } else {
                String resp = "{\"items\":[{\"spec\":{\"jobClass\":\"com.example.Job\"}}]}";
                ex.sendResponseHeaders(200, resp.length());
                ex.getResponseBody().write(resp.getBytes());
                ex.close();
            }
        });
        server.start();
        String url = "http://localhost:" + server.getAddress().getPort();
        try {
            CrdJobStore store = new CrdJobStore(url, "ns");
            store.saveJob("com.example.Job");
            List<String> jobs = store.loadJobs();
            assertEquals(1, jobs.size());
            assertEquals("com.example.Job", jobs.get(0));
        } finally {
            server.stop(0);
        }
        assertFalse(bodies.isEmpty());
        assertTrue(bodies.get(0).contains("jobClass: com.example.Job"));
    }
}
