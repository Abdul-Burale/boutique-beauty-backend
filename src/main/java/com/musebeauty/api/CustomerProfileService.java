package com.musebeauty.api;

import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.security.oauth2.jwt.Jwt;

@Service
class CustomerProfileService {
  private final JdbcTemplate db;
  CustomerProfileService(JdbcTemplate db) { this.db = db; }

  @Transactional
  AppPrincipal loadOrCreate(Jwt jwt) {
    UUID authUserId;
    try { authUserId = UUID.fromString(jwt.getSubject()); }
    catch (RuntimeException e) { throw new BadCredentialsException("Supabase token subject must be a UUID"); }

    String email = jwt.getClaimAsString("email");
    if (email == null || email.isBlank() || email.length() > 254 || email.indexOf('@') < 1 || email.indexOf('@') == email.length() - 1) {
      throw new BadCredentialsException("Supabase token must include a valid email address");
    }
    email = email.trim().toLowerCase(Locale.ROOT);

    var existing = db.query("select id,role,email,active from customers where auth_user_id=?",
      (rs, row) -> new AppPrincipal(rs.getLong("id"), authUserId, rs.getString("role"), rs.getString("email"), rs.getBoolean("active")), authUserId);
    if (!existing.isEmpty()) {
      AppPrincipal profile = existing.get(0);
      if (!profile.active()) throw new BadCredentialsException("This customer profile is disabled");
      if (!profile.email().equals(email)) {
        try { db.update("update customers set email=? where id=?", email, profile.id()); }
        catch (DuplicateKeyException e) { throw new BadCredentialsException("This email is already linked to another customer profile"); }
        return new AppPrincipal(profile.id(), authUserId, profile.role(), email, true);
      }
      return profile;
    }

    Integer emailAlreadyUsed = db.queryForObject("select count(*) from customers where email=?", Integer.class, email);
    if (emailAlreadyUsed != null && emailAlreadyUsed > 0) {
      throw new BadCredentialsException("An existing customer profile must be linked to this Supabase user by an administrator");
    }

    Map<String, Object> metadata = jwt.getClaim("user_metadata");
    String fallback = email.substring(0, email.indexOf('@'));
    String firstName = metadataName(metadata, "first_name", fallback);
    String lastName = metadataName(metadata, "last_name", "");
    try {
      var inserted = db.query("insert into customers(auth_user_id,email,first_name,last_name,role) values(?,?,?,?,'CUSTOMER') on conflict(auth_user_id) do nothing returning id",
        (rs, row) -> rs.getLong(1), authUserId, email, firstName, lastName);
      if (inserted.isEmpty()) {
        var raced = db.query("select id,role,email,active from customers where auth_user_id=?",
          (rs, row) -> new AppPrincipal(rs.getLong("id"), authUserId, rs.getString("role"), rs.getString("email"), rs.getBoolean("active")), authUserId);
        if (raced.isEmpty()) throw new BadCredentialsException("Unable to create a customer profile for this Supabase user");
        if (!raced.get(0).active()) throw new BadCredentialsException("This customer profile is disabled");
        return raced.get(0);
      }
      Long id = inserted.get(0);
      ensureBasket(id);
      return new AppPrincipal(id, authUserId, "CUSTOMER", email, true);
    } catch (DuplicateKeyException e) {
      throw new BadCredentialsException("This email is already associated with an existing profile");
    }
  }

  private String metadataName(Map<String, Object> metadata, String key, String fallback) {
    Object value = metadata == null ? null : metadata.get(key);
    if (value instanceof String text && !text.isBlank() && text.trim().length() <= 80) return text.trim();
    return fallback.length() > 80 ? fallback.substring(0, 80) : fallback;
  }

  private void ensureBasket(long customerId) {
    db.update("insert into baskets(customer_id) values(?) on conflict(customer_id) do nothing", customerId);
  }
}
