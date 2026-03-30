package com.travelai.util;

import static org.assertj.core.api.Assertions.*;

import org.junit.jupiter.api.Test;

class UserIdHasherTest {

  @Test
  void sameInputProducesSameHash() {
    String sub = "google-sub-123";
    String hash1 = UserIdHasher.hash(sub);
    String hash2 = UserIdHasher.hash(sub);
    assertThat(hash1).as("Same input should produce same hash").isEqualTo(hash2);
  }

  @Test
  void differentInputProducesDifferentHash() {
    String sub1 = "google-sub-123";
    String sub2 = "google-sub-456";
    String hash1 = UserIdHasher.hash(sub1);
    String hash2 = UserIdHasher.hash(sub2);
    assertThat(hash1).as("Different input should produce different hash").isNotEqualTo(hash2);
  }

  @Test
  void outputIs64CharHex() {
    String hash = UserIdHasher.hash("random-string");
    assertThat(hash).as("output must be 64-character hex").hasSize(64).matches("[0-9a-f]+");
  }

  @Test
  void throwsWhenNullInput() {
    assertThatThrownBy(() -> UserIdHasher.hash(null)).isInstanceOf(NullPointerException.class);
  }
}
