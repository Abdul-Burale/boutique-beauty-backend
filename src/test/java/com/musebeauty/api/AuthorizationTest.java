package com.musebeauty.api;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(ApiController.class)
@Import({SecurityConfig.class,ApiErrors.class})
class AuthorizationTest {
  @Autowired MockMvc mvc;
  @MockBean JdbcTemplate db;
  @MockBean org.springframework.security.crypto.password.PasswordEncoder encoder;

  @Test void customerDataRequiresAuthentication() throws Exception { mvc.perform(get("/api/account/me")).andExpect(status().isUnauthorized()); }
  @Test void adminEndpointsRequireAdminRole() throws Exception { mvc.perform(get("/api/admin/products")).andExpect(status().isUnauthorized()); }
  @Test @WithMockUser(roles="CUSTOMER") void customerCannotAccessAdminCatalog() throws Exception { mvc.perform(get("/api/admin/products")).andExpect(status().isForbidden()); }
  @Test @WithMockUser(roles="ADMIN") void adminsCanReadAdminCatalog() throws Exception {
    when(db.queryForList(anyString(),any(Object[].class))).thenReturn(List.of());
    mvc.perform(get("/api/admin/products")).andExpect(status().isOk());
  }
}
