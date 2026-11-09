package com.musebeauty.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.headerDoesNotExist;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

class SupabaseStorageServiceTest {
  private static final String PROJECT_URL = "https://project-ref.supabase.co";
  private static final String SIGNED_RESPONSE = "{\"url\":\"/object/upload/sign/product-images/products/test.webp?token=upload-token\"}";

  @Test
  void sendsModernSecretKeyOnlyAsApikeyAndReturnsSignedUploadDetails() {
    RestClient.Builder builder = RestClient.builder();
    MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
    server.expect(requestTo(org.hamcrest.Matchers.startsWith(PROJECT_URL + "/storage/v1/object/upload/sign/product-images/products/")))
        .andExpect(method(HttpMethod.POST)).andExpect(header("apikey", "sb_secret_test"))
        .andExpect(headerDoesNotExist(HttpHeaders.AUTHORIZATION))
        .andRespond(withSuccess(SIGNED_RESPONSE, MediaType.APPLICATION_JSON));
    SupabaseStorageService storage = new SupabaseStorageService(builder, PROJECT_URL, "sb_secret_test", "", "product-images");

    var result = storage.createProductImageUpload("image/webp");

    assertThat(result).containsEntry("bucket", "product-images").containsEntry("contentType", "image/webp")
        .containsEntry("token", "upload-token").containsEntry("expiresInSeconds", 7200);
    assertThat(result.get("path").toString()).matches("products/[0-9a-f-]{36}\\.webp");
    assertThat(result.get("publicUrl").toString()).contains("/object/public/product-images/");
    server.verify();
  }

  @Test
  void sendsLegacyServiceRoleAsBearerForCompatibility() {
    RestClient.Builder builder = RestClient.builder();
    MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
    server.expect(requestTo(org.hamcrest.Matchers.startsWith(PROJECT_URL + "/storage/v1/object/upload/sign/")))
        .andExpect(method(HttpMethod.POST)).andExpect(header("apikey", "legacy-service-role"))
        .andExpect(header(HttpHeaders.AUTHORIZATION, "Bearer legacy-service-role"))
        .andRespond(withSuccess(SIGNED_RESPONSE, MediaType.APPLICATION_JSON));
    SupabaseStorageService storage = new SupabaseStorageService(builder, PROJECT_URL, "", "legacy-service-role", "product-images");

    storage.createProductImageUpload("image/webp");

    server.verify();
  }
}
