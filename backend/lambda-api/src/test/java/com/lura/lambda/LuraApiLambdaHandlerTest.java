package com.lura.lambda;

import com.amazonaws.services.lambda.runtime.events.APIGatewayV2HTTPEvent;
import com.amazonaws.services.lambda.runtime.events.APIGatewayV2HTTPResponse;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lura.core.api.SoundPlayResponse;
import com.lura.core.catalog.SoundCatalogItem;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LuraApiLambdaHandlerTest {

    private static final ObjectMapper JSON = new ObjectMapper();

    private final LuraApiLambdaHandler handler = new LuraApiLambdaHandler(
            (sound, objectKey) -> new SoundPlayResponse(
                    sound.id(),
                    sound.categoryId(),
                    objectKey == null ? sound.s3Prefix() + "sample.mp3" : objectKey,
                    "https://cdn.example.com/" + sound.id() + ".mp3"
            )
    );

    @Test
    void returnsCategories() throws Exception {
        APIGatewayV2HTTPResponse response = handler.handleRequest(get("/api/v1/categories"), null);

        assertEquals(200, response.getStatusCode());
        JsonNode body = JSON.readTree(response.getBody());
        assertTrue(body.isArray());
        assertTrue(body.size() >= 5);
    }

    @Test
    void returnsSoundsByCategory() throws Exception {
        APIGatewayV2HTTPResponse response = handler.handleRequest(get("/api/v1/sounds/category/firewood"), null);

        assertEquals(200, response.getStatusCode());
        JsonNode body = JSON.readTree(response.getBody());
        assertEquals("random-firewood", body.get(0).get("id").asText());
        assertEquals("firewood", body.get(0).get("categoryId").asText());
    }

    @Test
    void returnsPlayUrlForSound() throws Exception {
        APIGatewayV2HTTPEvent event = get("/api/v1/sounds/random-firewood/play");
        event.setQueryStringParameters(Map.of("objectKey", "sounds/firewood/firewood-01.mp3"));

        APIGatewayV2HTTPResponse response = handler.handleRequest(event, null);

        assertEquals(200, response.getStatusCode());
        JsonNode body = JSON.readTree(response.getBody());
        assertEquals("random-firewood", body.get("soundId").asText());
        assertEquals("firewood", body.get("categoryId").asText());
        assertEquals("sounds/firewood/firewood-01.mp3", body.get("objectKey").asText());
    }

    @Test
    void returnsNotFoundForUnknownRoute() throws Exception {
        APIGatewayV2HTTPResponse response = handler.handleRequest(get("/api/v1/unknown"), null);

        assertEquals(404, response.getStatusCode());
        assertEquals("Not found", JSON.readTree(response.getBody()).get("message").asText());
    }

    private static APIGatewayV2HTTPEvent get(String rawPath) {
        APIGatewayV2HTTPEvent event = new APIGatewayV2HTTPEvent();
        event.setRawPath(rawPath);

        APIGatewayV2HTTPEvent.RequestContext requestContext = new APIGatewayV2HTTPEvent.RequestContext();
        APIGatewayV2HTTPEvent.RequestContext.Http http = new APIGatewayV2HTTPEvent.RequestContext.Http();
        http.setMethod("GET");
        requestContext.setHttp(http);
        event.setRequestContext(requestContext);

        return event;
    }
}
