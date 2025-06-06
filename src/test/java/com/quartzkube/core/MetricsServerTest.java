package com.quartzkube.core;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

public class MetricsServerTest {
    @Test
    public void testMetricsEndpoint() throws Exception {
        Metrics.reset();
        Metrics.getInstance().recordSuccess();
        Metrics.getInstance().recordDuration(100);
        MetricsServer.start(0);
        int port = MetricsServer.getPort();
        java.net.URL url = new java.net.URL("http://localhost:" + port + "/metrics");
        String body = new String(url.openStream().readAllBytes());
        MetricsServer.stop();
        assertTrue(body.contains("quartzkube_job_success_total 1"));
        assertTrue(body.contains("quartzkube_job_duration_millis_total 100"));
    }
}
