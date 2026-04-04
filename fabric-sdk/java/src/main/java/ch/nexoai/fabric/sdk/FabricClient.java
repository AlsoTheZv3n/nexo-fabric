package ch.nexoai.fabric.sdk;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.*;

public class FabricClient {
    private final String baseUrl;
    private final HttpClient http;
    private final ObjectMapper mapper = new ObjectMapper();
    private final Map<String, String> headers = new LinkedHashMap<>();

    public FabricClient(String baseUrl, String apiKey) {
        this.baseUrl = baseUrl.replaceAll("/$", "");
        this.http = HttpClient.newHttpClient();
        headers.put("Content-Type", "application/json");
        if (apiKey != null) headers.put("Authorization", "Bearer " + apiKey);
    }

    public JsonNode listObjectTypes() throws Exception {
        return graphql("{ getAllObjectTypes { id apiName displayName } }").get("getAllObjectTypes");
    }

    public JsonNode searchObjects(String objectType, int limit) throws Exception {
        return graphql(
            "query($t:String!,$p:PaginationInput){searchObjects(objectType:$t,pagination:$p){items{id objectType properties}totalCount}}",
            Map.of("t", objectType, "p", Map.of("limit", limit))
        ).get("searchObjects");
    }

    public JsonNode createObject(String objectType, Map<String, Object> properties) throws Exception {
        return graphql(
            "mutation($t:String!,$p:JSON!){createObject(objectType:$t,properties:$p){id objectType properties}}",
            Map.of("t", objectType, "p", properties)
        ).get("createObject");
    }

    public JsonNode ask(String message) throws Exception {
        return graphql(
            "mutation($m:String!){agentChat(message:$m){message sessionId}}",
            Map.of("m", message)
        ).get("agentChat");
    }

    private JsonNode graphql(String query) throws Exception {
        return graphql(query, Map.of());
    }

    private JsonNode graphql(String query, Map<String, Object> variables) throws Exception {
        String body = mapper.writeValueAsString(Map.of("query", query, "variables", variables));
        HttpRequest.Builder builder = HttpRequest.newBuilder()
            .uri(URI.create(baseUrl + "/graphql"))
            .POST(HttpRequest.BodyPublishers.ofString(body));
        headers.forEach(builder::header);
        HttpResponse<String> response = http.send(builder.build(), HttpResponse.BodyHandlers.ofString());
        JsonNode json = mapper.readTree(response.body());
        if (json.has("errors")) throw new RuntimeException(json.get("errors").get(0).get("message").asText());
        return json.get("data");
    }
}
