package com.quartzkube.core;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * JobStore implementation backed by Kubernetes ScheduledJob CRDs.
 */
public class CrdJobStore implements JobStore {
    private final HttpClient client = HttpClient.newHttpClient();
    private final String apiUrl;
    private final String namespace;

    public CrdJobStore(String apiUrl, String namespace) {
        this.apiUrl = apiUrl;
        this.namespace = namespace;
    }

    private URI resourceUri() {
        return URI.create(apiUrl + "/apis/quartzkube.com/v1/namespaces/" + namespace + "/scheduledjobs");
    }

    @Override
    public void saveJob(String jobClass) throws Exception {
        String body = String.format("""
apiVersion: quartzkube.com/v1
kind: ScheduledJob
metadata:
  name: %s
spec:
  jobClass: %s
""", jobClass.toLowerCase(), jobClass);
        HttpRequest req = HttpRequest.newBuilder(resourceUri())
                .header("Content-Type", "application/yaml")
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();
        client.send(req, HttpResponse.BodyHandlers.discarding());
    }

    @Override
    public List<String> loadJobs() throws Exception {
        HttpRequest req = HttpRequest.newBuilder(resourceUri()).GET().build();
        HttpResponse<String> resp = client.send(req, HttpResponse.BodyHandlers.ofString());
        List<String> list = new ArrayList<>();
        Matcher m = Pattern.compile("\\\"jobClass\\\"\\s*:\\s*\\\"([^\\\"]+)\\\"").matcher(resp.body());
        while (m.find()) {
            list.add(m.group(1));
        }
        return list;
    }
}
