package com.quartzkube.core;

import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;

/**
 * Simple HTTP server exposing job metrics in Prometheus text format.
 */
public final class MetricsServer {
    private static HttpServer server;
    private static int port;

    private MetricsServer() {}

    /**
     * Initializes the server using the METRICS_PORT environment variable.
     * If the variable is not set, the server is not started.
     */
    public static void init() {
        String portStr = System.getenv("METRICS_PORT");
        if (portStr != null && !portStr.isEmpty()) {
            try {
                start(Integer.parseInt(portStr));
            } catch (NumberFormatException ignored) {
                // invalid port - ignore
            }
        }
    }

    /** Starts the server on the given port if not already running. */
    public static void start(int port) {
        if (server != null) {
            return;
        }
        try {
            server = HttpServer.create(new InetSocketAddress(port), 0);
            MetricsServer.port = server.getAddress().getPort();
            server.createContext("/metrics", exchange -> {
                byte[] body = buildMetrics().getBytes();
                exchange.sendResponseHeaders(200, body.length);
                try (OutputStream os = exchange.getResponseBody()) {
                    os.write(body);
                }
            });
            server.start();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    /** Stops the server if running. */
    public static void stop() {
        if (server != null) {
            server.stop(0);
            server = null;
        }
    }

    /** Returns the port the server is bound to, or -1 if not running. */
    public static int getPort() {
        return server == null ? -1 : port;
    }

    private static String buildMetrics() {
        Metrics m = Metrics.getInstance();
        StringBuilder sb = new StringBuilder();
        sb.append("# HELP quartzkube_job_success_total Number of successful job executions\n");
        sb.append("# TYPE quartzkube_job_success_total counter\n");
        sb.append("quartzkube_job_success_total ").append(m.getSuccessCount()).append('\n');
        sb.append("# HELP quartzkube_job_failure_total Number of failed job executions\n");
        sb.append("# TYPE quartzkube_job_failure_total counter\n");
        sb.append("quartzkube_job_failure_total ").append(m.getFailureCount()).append('\n');
        sb.append("# HELP quartzkube_job_duration_millis_total Total time spent running jobs in milliseconds\n");
        sb.append("# TYPE quartzkube_job_duration_millis_total counter\n");
        sb.append("quartzkube_job_duration_millis_total ").append(m.getTotalDurationMillis()).append('\n');
        sb.append("# HELP quartzkube_job_duration_millis_avg Average job execution time in milliseconds\n");
        sb.append("# TYPE quartzkube_job_duration_millis_avg gauge\n");
        sb.append("quartzkube_job_duration_millis_avg ").append(m.getAverageDurationMillis()).append('\n');
        return sb.toString();
    }
}
