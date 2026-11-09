package com.musebeauty.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.when;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.security.oauth2.jwt.Jwt;

class CustomerProfileServiceTest {
  @SuppressWarnings({"rawtypes","unchecked"})
  @Test void createsCustomerProfileFromSupabaseSubjectAndMetadata() {
    JdbcTemplate db=org.mockito.Mockito.mock(JdbcTemplate.class);
    CustomerProfileService profiles=new CustomerProfileService(db);
    UUID authUserId=UUID.fromString("adfc8988-c183-47fe-9f37-44a3ad8a62bd");
    String lookup="select id,role,email,active from customers where auth_user_id=?";
    doAnswer(invocation -> List.of()).when(db).query(eq(lookup),any(RowMapper.class),any(Object[].class));
    when(db.queryForObject(eq("select count(*) from customers where email=?"),eq(Integer.class),eq("ada@example.com"))).thenReturn(0);
    doAnswer(invocation -> List.of(41L)).when(db).query(contains("insert into customers(auth_user_id,email,first_name"),any(RowMapper.class),any(Object[].class));
    when(db.update(contains("insert into baskets(customer_id)"),eq(41L))).thenReturn(1);
    Jwt jwt=Jwt.withTokenValue("test-token").header("alg","ES256").subject(authUserId.toString())
      .claim("email","Ada@Example.com").claim("role","authenticated").claim("is_anonymous",false)
      .claim("user_metadata",Map.of("first_name","Ada","last_name","Lovelace")).build();

    AppPrincipal principal=profiles.loadOrCreate(jwt);

    assertThat(principal.id()).isEqualTo(41L);
    assertThat(principal.authUserId()).isEqualTo(authUserId);
    assertThat(principal.role()).isEqualTo("CUSTOMER");
    assertThat(principal.email()).isEqualTo("ada@example.com");
  }
}
