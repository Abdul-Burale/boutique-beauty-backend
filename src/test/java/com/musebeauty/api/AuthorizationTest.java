package com.musebeauty.api;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(ApiController.class)
@Import({SecurityConfig.class,ApiErrors.class})
@TestPropertySource(properties={"app.supabase.issuer-uri=https://project-ref.supabase.co/auth/v1","app.supabase.jwks-uri=https://project-ref.supabase.co/auth/v1/.well-known/jwks.json","app.supabase.audience=authenticated"})
class AuthorizationTest {
  @Autowired MockMvc mvc;
  @MockBean JdbcTemplate db;
  @MockBean CustomerProfileService profiles;
  @MockBean JwtDecoder decoder;
  @MockBean SupabaseStorageService storage;

  @Test void customerDataRequiresSupabaseToken() throws Exception { mvc.perform(get("/api/account/me")).andExpect(status().isUnauthorized()); }
  @Test void adminEndpointsRequireSupabaseToken() throws Exception { mvc.perform(get("/api/admin/products")).andExpect(status().isUnauthorized()); }
  @Test @WithMockUser(roles="CUSTOMER") void customerCannotAccessAdminCatalog() throws Exception { mvc.perform(get("/api/admin/products")).andExpect(status().isForbidden()); }
  @Test @WithMockUser(roles="CUSTOMER") void customerCannotRequestProductImageUpload() throws Exception {
    mvc.perform(post("/api/admin/storage/product-images/upload-url").contentType("application/json").content("{\"contentType\":\"image/png\"}")).andExpect(status().isForbidden());
  }
  @Test @WithMockUser(roles="ADMIN") void adminCanRequestProductImageUpload() throws Exception {
    when(storage.createProductImageUpload("image/png")).thenReturn(java.util.Map.of("bucket","product-images","path","products/example.png","signedUrl","https://example/signed"));
    mvc.perform(post("/api/admin/storage/product-images/upload-url").contentType("application/json").content("{\"contentType\":\"image/png\"}"))
        .andExpect(status().isOk()).andExpect(jsonPath("$.path").value("products/example.png"));
  }
  @Test @WithMockUser(roles="ADMIN") void unsupportedImageTypesAreRejected() throws Exception {
    mvc.perform(post("/api/admin/storage/product-images/upload-url").contentType("application/json").content("{\"contentType\":\"image/svg+xml\"}"))
        .andExpect(status().isBadRequest());
  }
  @Test @WithMockUser(roles="CUSTOMER") void customerCannotEditProductVariant() throws Exception {
    mvc.perform(put("/api/admin/products/7/variants/15").contentType("application/json")
        .content("{\"sku\":\"SERUM-30\",\"optionName\":\"Size\",\"optionValue\":\"30ml\",\"priceMinor\":1299}"))
        .andExpect(status().isForbidden());
  }
  @Test @WithMockUser(roles="ADMIN") void adminCanEditVariantPricingAndSku() throws Exception {
    when(db.update(contains("update product_variants set sku="), any(Object[].class))).thenReturn(1);
    mvc.perform(put("/api/admin/products/7/variants/15").contentType("application/json")
        .content("{\"sku\":\"SERUM-30\",\"optionName\":\"Size\",\"optionValue\":\"30ml\",\"priceMinor\":1299}"))
        .andExpect(status().isOk()).andExpect(jsonPath("$.variantId").value(15))
        .andExpect(jsonPath("$.priceMinor").value(1299));
  }
  @Test @WithMockUser(roles="ADMIN") void invalidVariantUpdateIsRejected() throws Exception {
    mvc.perform(put("/api/admin/products/7/variants/15").contentType("application/json")
        .content("{\"sku\":\"\",\"optionName\":\"Size\",\"optionValue\":\"30ml\",\"priceMinor\":1299}"))
        .andExpect(status().isBadRequest());
  }
  @Test @WithMockUser(roles="ADMIN") void adminsCanReadAdminCatalog() throws Exception {
    when(db.queryForList(anyString(),any(Object[].class))).thenReturn(List.of());
    when(db.queryForObject(contains("select count(*) from products"),eq(Long.class))).thenReturn(0L);
    mvc.perform(get("/api/admin/products")).andExpect(status().isOk());
  }
}
