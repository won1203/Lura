package com.lura.lambda;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.APIGatewayV2HTTPEvent;
import com.amazonaws.services.lambda.runtime.events.APIGatewayV2HTTPResponse;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lura.core.api.CategoryResponse;
import com.lura.core.api.SoundResponse;
import com.lura.core.catalog.LuraSoundCatalog;
import com.lura.core.catalog.SoundCatalogItem;
import com.lura.core.storage.S3Config;
import com.lura.core.storage.S3PresignedUrlService;
import com.lura.core.storage.S3RandomSoundSelector;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public class LuraApiLambdaHandler implements RequestHandler<APIGatewayV2HTTPEvent, APIGatewayV2HTTPResponse> {

    private static final ObjectMapper JSON = new ObjectMapper();

    private final PlaybackUrlProvider playbackUrlProvider;

    public LuraApiLambdaHandler() {
        this(defaultPlaybackUrlProvider());
    }

    LuraApiLambdaHandler(PlaybackUrlProvider playbackUrlProvider) {
        this.playbackUrlProvider = playbackUrlProvider;
    }

    @Override
    public APIGatewayV2HTTPResponse handleRequest(APIGatewayV2HTTPEvent event, Context context) {
        try {
            return route(event);
        } catch (ApiException exception) {
            return json(exception.statusCode(), Map.of("message", exception.getMessage()));
        } catch (Exception exception) {
            if (context != null && context.getLogger() != null) {
                context.getLogger().log("Unhandled Lura API error: " + exception);
            }
            return json(500, Map.of("message", "Internal server error"));
        }
    }

    private APIGatewayV2HTTPResponse route(APIGatewayV2HTTPEvent event) {
        String method = requestMethod(event);
        List<String> segments = pathSegments(event);

        if (!"GET".equals(method)) {
            throw new ApiException(405, "Method not allowed");
        }

        if (segments.equals(List.of("api", "v1", "categories"))) {
            return json(200, LuraSoundCatalog.categories()
                    .stream()
                    .map(CategoryResponse::from)
                    .toList());
        }

        if (segments.equals(List.of("api", "v1", "sounds"))) {
            return json(200, LuraSoundCatalog.sounds()
                    .stream()
                    .map(SoundResponse::from)
                    .toList());
        }

        if (segments.size() == 5
                && segments.subList(0, 4).equals(List.of("api", "v1", "sounds", "category"))) {
            String categoryId = segments.get(4);
            requireCategory(categoryId);
            return json(200, LuraSoundCatalog.soundsByCategory(categoryId)
                    .stream()
                    .map(SoundResponse::from)
                    .toList());
        }

        if (segments.size() == 7
                && segments.subList(0, 4).equals(List.of("api", "v1", "sounds", "category"))
                && segments.get(5).equals("play")
                && segments.get(6).equals("random")) {
            String categoryId = segments.get(4);
            requireCategory(categoryId);
            SoundCatalogItem sound = LuraSoundCatalog.soundsByCategory(categoryId)
                    .stream()
                    .findFirst()
                    .orElseThrow(() -> new ApiException(404, "Sound not found"));
            return json(200, playbackUrlProvider.getPlayUrl(sound, null));
        }

        if (segments.size() == 5
                && segments.subList(0, 3).equals(List.of("api", "v1", "sounds"))
                && segments.get(4).equals("play")) {
            SoundCatalogItem sound = LuraSoundCatalog.findSound(segments.get(3))
                    .orElseThrow(() -> new ApiException(404, "Sound not found"));
            return json(200, playbackUrlProvider.getPlayUrl(sound, queryParameter(event, "objectKey")));
        }

        throw new ApiException(404, "Not found");
    }

    private void requireCategory(String categoryId) {
        LuraSoundCatalog.findCategory(categoryId)
                .orElseThrow(() -> new ApiException(404, "Category not found"));
    }

    private static PlaybackUrlProvider defaultPlaybackUrlProvider() {
        S3Config config = LambdaEnvironment.s3Config(System.getenv());
        Region region = Region.of(config.region());
        DefaultCredentialsProvider credentialsProvider = DefaultCredentialsProvider.create();
        S3Client s3Client = S3Client.builder()
                .region(region)
                .credentialsProvider(credentialsProvider)
                .build();
        S3Presigner s3Presigner = S3Presigner.builder()
                .region(region)
                .credentialsProvider(credentialsProvider)
                .build();

        return new S3PlaybackUrlProvider(
                new S3RandomSoundSelector(s3Client, config),
                new S3PresignedUrlService(s3Presigner, config)
        );
    }

    private static String requestMethod(APIGatewayV2HTTPEvent event) {
        if (event.getRequestContext() != null
                && event.getRequestContext().getHttp() != null
                && event.getRequestContext().getHttp().getMethod() != null) {
            return event.getRequestContext().getHttp().getMethod();
        }
        if (event.getRouteKey() != null && event.getRouteKey().contains(" ")) {
            return event.getRouteKey().substring(0, event.getRouteKey().indexOf(' '));
        }
        return "";
    }

    private static List<String> pathSegments(APIGatewayV2HTTPEvent event) {
        String path = event.getRawPath();
        if (path == null || path.isBlank()) {
            path = "/";
        }

        String stage = event.getRequestContext() == null ? null : event.getRequestContext().getStage();
        if (stage != null && !stage.isBlank() && !stage.equals("$default")) {
            String stagePrefix = "/" + stage + "/";
            if (path.equals("/" + stage)) {
                path = "/";
            } else if (path.startsWith(stagePrefix)) {
                path = path.substring(stage.length() + 1);
            }
        }

        return Arrays.stream(path.split("/"))
                .filter(segment -> !segment.isBlank())
                .map(segment -> URLDecoder.decode(segment, StandardCharsets.UTF_8))
                .toList();
    }

    private static String queryParameter(APIGatewayV2HTTPEvent event, String name) {
        Map<String, String> queryStringParameters = event.getQueryStringParameters();
        if (queryStringParameters == null) {
            return null;
        }
        return queryStringParameters.get(name);
    }

    private static APIGatewayV2HTTPResponse json(int statusCode, Object body) {
        try {
            return APIGatewayV2HTTPResponse.builder()
                    .withStatusCode(statusCode)
                    .withHeaders(Map.of(
                            "Content-Type", "application/json",
                            "Cache-Control", "no-store"
                    ))
                    .withBody(JSON.writeValueAsString(Objects.requireNonNull(body)))
                    .build();
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException(exception);
        }
    }
}
