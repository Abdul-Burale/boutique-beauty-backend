package com.musebeauty.api;

import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;

@Configuration
class AdminBootstrap {
  @Bean CommandLineRunner bootstrapAdmin(JdbcTemplate db,
    @Value("${app.bootstrap.admin-user-id:}") String userId,
    @Value("${app.bootstrap.admin-email:}") String email) {
    return args -> {
      if (userId.isBlank() && email.isBlank()) return;
      if (userId.isBlank() || email.isBlank()) {
        throw new IllegalStateException("Set both BOOTSTRAP_ADMIN_USER_ID and BOOTSTRAP_ADMIN_EMAIL");
      }
      UUID authUserId;
      try { authUserId = UUID.fromString(userId); }
      catch (IllegalArgumentException e) { throw new IllegalStateException("BOOTSTRAP_ADMIN_USER_ID must be the UUID of an existing Supabase Auth user", e); }
      promoteAdmin(db, authUserId, email.trim().toLowerCase(java.util.Locale.ROOT));
    };
  }

  void promoteAdmin(JdbcTemplate db, UUID authUserId, String email) {
    var existing = db.query("select id from customers where auth_user_id=?", (rs, row) -> rs.getLong(1), authUserId);
    if (existing.isEmpty()) {
      db.update("insert into customers(auth_user_id,email,first_name,last_name,role) values(?,?,'Store','Administrator','ADMIN') on conflict(auth_user_id) do update set role='ADMIN'",
        authUserId, email);
    } else {
      db.update("update customers set role='ADMIN' where auth_user_id=?", authUserId);
    }
  }
}
