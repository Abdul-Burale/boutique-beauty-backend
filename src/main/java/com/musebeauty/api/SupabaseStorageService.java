package com.musebeauty.api;

import java.util.Map;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;
import org.springframework.web.util.UriUtils;

@Service
class SupabaseStorageService {
  private final RestClient client;
  private final String projectUrl;
  private final String storageApiKey;
  private final boolean legacyServiceRoleKey;
  private final String bucket;

  SupabaseStorageService(RestClient.Builder builder,
      @Value("${app.supabase.url}") String projectUrl,
      @Value("${app.supabase.storage.secret-key}") String secretKey,
      @Value("${app.supabase.storage.service-role-key}") String serviceRoleKey,
      @Value("${app.supabase.storage.bucket}") String bucket) {
    this.client = builder.build();
    this.projectUrl = projectUrl == null ? "" : projectUrl.replaceAll("/+$", "");
    String secret = secretKey == null ? "" : secretKey.trim();
    String legacy = serviceRoleKey == null ? "" : serviceRoleKey.trim();
    this.storageApiKey = secret.isBlank() ? legacy : secret;
    this.legacyServiceRoleKey = secret.isBlank() && !legacy.isBlank();
    this.bucket = bucket;
  }

  Map<String, Object> createProductImageUpload(String contentType) {
    String extension = switch (contentType) {
      case "image/jpeg" -> "jpg";
      case "image/png" -> "png";
      case "image/webp" -> "webp";
      case "image/avif" -> "avif";
      default -> throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unsupported image type");
    };
    if (projectUrl.isBlank() || storageApiKey.isBlank()) {
      throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Product image uploads are not configured");
    }
    String path = "products/" + UUID.randomUUID() + "." + extension;
    String encodedBucket = UriUtils.encodePathSegment(bucket, java.nio.charset.StandardCharsets.UTF_8);
    String encodedPath = UriUtils.encodePath(path, java.nio.charset.StandardCharsets.UTF_8);
    Map<?, ?> response;
    try {
      response = client.post()
          .uri(projectUrl + "/storage/v1/object/upload/sign/" + encodedBucket + "/" + encodedPath)
          .headers(headers -> {
            headers.set("apikey", storageApiKey);
            if (legacyServiceRoleKey) headers.set(HttpHeaders.AUTHORIZATION, "Bearer " + storageApiKey);
          })
          .header("x-upsert", "false")
          .contentType(MediaType.APPLICATION_JSON)
          .body(Map.of())
          .retrieve().body(Map.class);
    } catch (org.springframework.web.client.RestClientException ex) {
      throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Unable to create a product image upload", ex);
    }
    if (response == null || !(response.get("url") instanceof String signedPath)) {
      throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Storage returned an invalid upload response");
    }
    String signedUrl = signedPath.startsWith("http") ? signedPath : projectUrl + "/storage/v1" + (signedPath.startsWith("/") ? signedPath : "/" + signedPath);
    String query = java.net.URI.create(signedUrl).getRawQuery();
    String token = java.util.Arrays.stream(query == null ? new String[0] : query.split("&"))
        .filter(part -> part.startsWith("token=")).map(part -> part.substring("token=".length())).findFirst()
        .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Storage returned an invalid upload token"));
    String publicUrl = projectUrl + "/storage/v1/object/public/" + encodedBucket + "/" + encodedPath;
    return Map.of("bucket", bucket, "path", path, "contentType", contentType,
        "signedUrl", signedUrl, "token", token, "publicUrl", publicUrl, "expiresInSeconds", 7200);
  }
}
