package com.travelai.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import com.travelai.dto.Preferences;
import com.travelai.model.SearchHistory;
import com.travelai.model.User;
import com.travelai.repository.UserRepository;
import io.modelcontextprotocol.client.McpSyncClient;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.chat.client.ChatClient;

@ExtendWith(MockitoExtension.class)
class TravelAgentTest {
  @Mock ChatClient.Builder builder;
  @Mock ChatClient chatClient;
  @Mock McpSyncClient mcpClient;
  @Mock UserRepository userRepo;

  TravelAgent travelAgent;

  List<Map<String, Object>> accommodations =
      List.of(
          Map.of(
              "accommodation_id", "A",
              "accommodation_name", "Hotel Paris",
              "price_per_night", "€120",
              "review_rating", "8.5",
              "review_count", 342,
              "hotel_rating", 4,
              "distance", "0.5 km from center",
              "top_amenities", "pool, wifi, breakfast"),
          Map.of(
              "accommodation_id", "B",
              "accommodation_name", "Beach Resort Bali",
              "price_per_night", "€85",
              "review_rating", "9.1",
              "review_count", 128,
              "hotel_rating", 5,
              "distance", "beachfront",
              "top_amenities", "pool, spa, gym"),
          Map.of(
              "accommodation_id", "C",
              "accommodation_name", "Tokyo Tower Inn",
              "price_per_night", "€150",
              "review_rating", "7.8",
              "review_count", 56,
              "hotel_rating", 3,
              "distance", "1.2 km from station",
              "top_amenities", "wifi, parking"));

  @BeforeEach
  void setUp() {
    when(builder.build()).thenReturn(chatClient);
    travelAgent = new TravelAgent(builder, List.of(mcpClient), userRepo);
  }

  @Nested
  class ReorderByRanking {

    @Test
    void reorderByRankedIds() {

      List<Map<String, Object>> result =
          travelAgent.reorderByRanking(accommodations, List.of("C", "A", "B"));

      assertThat(result.get(0).get("accommodation_id")).isEqualTo("C");
      assertThat(result.get(1).get("accommodation_id")).isEqualTo("A");
      assertThat(result.get(2).get("accommodation_id")).isEqualTo("B");
    }

    @Test
    void sameOrderIfRankingIsEmpty() {
      List<Map<String, Object>> result = travelAgent.reorderByRanking(accommodations, List.of());

      assertThat(result.get(0).get("accommodation_id")).isEqualTo("A");
      assertThat(result.get(1).get("accommodation_id")).isEqualTo("B");
      assertThat(result.get(2).get("accommodation_id")).isEqualTo("C");
    }

    @Test
    void missingRankedIdsIgnored() {
      List<Map<String, Object>> result =
          travelAgent.reorderByRanking(accommodations, List.of("unknown"));

      assertThat(result.get(0).get("accommodation_id")).isEqualTo("A");
      assertThat(result.get(1).get("accommodation_id")).isEqualTo("B");
      assertThat(result.get(2).get("accommodation_id")).isEqualTo("C");
    }

    @Test
    void emptyAccommodationsReturnsEmpty() {
      List<Map<String, Object>> result = travelAgent.reorderByRanking(List.of(), List.of());

      assertThat(result.isEmpty()).isTrue();
    }
  }

  @Nested
  class BuildHotelSummary {
    @Test
    void formatsHotelData() {
      String result = travelAgent.buildHotelSummary(accommodations);
      assertThat(result).contains("Hotel Paris", "€120", "8.5/10", "4 stars");
    }

    @Test
    void emptyListReturnsEmptyString() {
      String empty = travelAgent.buildHotelSummary(List.of());
      assertThat(empty).isEmpty();
    }

    @Test
    void handleMissingFields() {
      String sparse = travelAgent.buildHotelSummary(List.of(Map.of()));
      assertThat(sparse).contains("Unknown", "?");
    }

    @Test
    void capsAtTenHotels() {
      List<Map<String, Object>> hotels = new ArrayList<>();
      int cap = 10;
      int overload = 15;
      for (int i = 0; i < overload; i++) {
        hotels.add(Map.of("accommodation_name", "Hotel " + i));
      }
      String result = travelAgent.buildHotelSummary(hotels);
      assertThat(result.lines().count()).isEqualTo(cap);
    }
  }

  @Nested
  class GetPreferences {
    @Test
    void parseStoredJson() {
      User user = new User();
      user.setPreferences("{\"budget\":150,\"style\":\"beach\"}");
      when(userRepo.findById("user-1")).thenReturn(Optional.of(user));

      Preferences result = travelAgent.getPreferences("user-1");

      assertThat(result.budget()).isEqualTo(150);
      assertThat(result.style()).isEqualTo("beach");
    }

    @Test
    void returnsEmptyWhenNoPreferences() {
      User user = new User();
      when(userRepo.findById("user-1")).thenReturn(Optional.of(user));

      Preferences result = travelAgent.getPreferences("user-1");
      assertThat(result.budget()).isNull();
      assertThat(result.style()).isNull();
      assertThat(result.roomType()).isNull();
      assertThat(result.amenities()).isNull();
    }

    @Test
    void returnsEmptyWhenUserNotFound() {
      when(userRepo.findById("unknown")).thenReturn(Optional.empty());

      Preferences result = travelAgent.getPreferences("unknown");
      assertThat(result.budget()).isNull();
      assertThat(result.style()).isNull();
      assertThat(result.roomType()).isNull();
      assertThat(result.amenities()).isNull();
    }
  }

  @Nested
  class GetSearchHistory {
    @Test
    void returnsUserHistory() {
      User user = new User();
      SearchHistory h = new SearchHistory();
      h.setQuery("Hotels in Paris");
      user.setHistory(List.of(h));
      when(userRepo.findById("user-1")).thenReturn(Optional.of(user));

      List<SearchHistory> result = travelAgent.getSearchHistory("user-1");

      assertThat(result).hasSize(1);
      assertThat(result.get(0).getQuery()).isEqualTo("Hotels in Paris");
    }

    @Test
    void returnsEmptyWhenNoHistory() {
      User user = new User();
      when(userRepo.findById("user-1")).thenReturn(Optional.of(user));

      List<SearchHistory> result = travelAgent.getSearchHistory("user-1");

      assertThat(result).isEmpty();
    }

    @Test
    void returnsEmptyWhenUserNotFound() {
      when(userRepo.findById("unknown")).thenReturn(Optional.empty());

      List<SearchHistory> result = travelAgent.getSearchHistory("unknown");

      assertThat(result).isEmpty();
    }
  }

  @Nested
  class Chat {
    @Test
    void throwsWhenUserNotFound() {
      when(userRepo.findById("unknown")).thenReturn(Optional.empty());

      assertThatThrownBy(() -> travelAgent.chat("unknown", "hello"))
          .isInstanceOf(IllegalStateException.class);
    }
  }
}
