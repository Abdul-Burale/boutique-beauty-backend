package com.musebeauty.api;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;

@Configuration
class AdminBootstrap {
  @Bean CommandLineRunner bootstrapAdmin(JdbcTemplate db, PasswordEncoder encoder,
    @Value("${app.bootstrap.admin-email:}") String email,
    @Value("${app.bootstrap.admin-password:}") String password) {
    return args -> {
      if(email.isBlank() && password.isBlank()) return;
      if(email.isBlank() || password.length()<12) throw new IllegalStateException("Set both bootstrap admin values and use a password of at least 12 characters");
      Integer exists=db.queryForObject("select count(*) from customers where role='ADMIN'",Integer.class);
      if(exists==0) db.update("insert into customers(email,password_hash,first_name,last_name,role) values(lower(?),?,'Store','Administrator','ADMIN')",email,encoder.encode(password));
    };
  }
}
