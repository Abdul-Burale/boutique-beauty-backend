package com.musebeauty.api;

import static org.assertj.core.api.Assertions.assertThat;
import org.junit.jupiter.api.Test;

class SessionTokenFilterTest {
  @Test void hashesOpaqueTokenDeterministicallyWithoutReturningIt() {
    String token="a-long-session-token-that-must-not-be-stored-in-the-database";
    String first=SessionTokenFilter.hash(token);
    assertThat(first).hasSize(64).matches("[0-9a-f]+").isEqualTo(SessionTokenFilter.hash(token)).isNotEqualTo(token);
  }
}
