package com.travelai.controller;

import static org.hamcrest.Matchers.startsWith;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.oidcLogin;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.travelai.dto.ChatResult;
import com.travelai.dto.Preferences;
import com.travelai.model.SearchHistory;
import com.travelai.service.TravelAgent;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(ChatController.class)
class ChatControllerTest {
  @Autowired private MockMvc mockMvc;

  @MockitoBean private TravelAgent travelAgent;

  @Nested
  class PostChat {
    @Test
    void returnsResultWhenAuthenticated() throws Exception {
      // 1. Define hardcoded values
      String genericChat = "Hello! This is Travel AI Chat";
      List<Map<String, Object>> hotels =
          List.of(Map.of("accommodation_name", "Hotel Paris", "price_per_night", "€120"));

      // 2. Tell the mock to return values above
      when(travelAgent.chat(anyString(), anyString()))
          .thenReturn(new ChatResult(genericChat, hotels));

      // 3. Send a request with a mocked OAuth2 user
      mockMvc
          .perform(
              post("/api/chat")
                  .content("Find hotels in Paris")
                  .contentType("text/plain")
                  .with(csrf())
                  .with(oidcLogin().idToken(token -> token.claim("sub", "google-123"))))
          // 4. Assert the response
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.message").value(genericChat))
          .andExpect(jsonPath("$.accommodations").isArray())
          .andExpect(jsonPath("$.accommodations.length()").value(1))
          .andExpect(jsonPath("$.accommodations[0].accommodation_name").value("Hotel Paris"))
          .andExpect(jsonPath("$.accommodations[0].price_per_night").value("€120"));
    }

    @Test
    void returns500WhenServiceThrows() throws Exception {
      // 1. Tell the mock to throw a server runtime exception
      when(travelAgent.chat(anyString(), anyString()))
          .thenThrow(new RuntimeException("Internal server error"));

      // 2. Send a request with a mocked OAuth2 user
      mockMvc
          .perform(
              post("/api/chat")
                  .content("Find hotels in Paris")
                  .contentType("text/plain")
                  .with(csrf())
                  .with(oidcLogin().idToken(token -> token.claim("sub", "google-123"))))
          // 3. Assert the response
          .andExpect(status().isInternalServerError())
          .andExpect(jsonPath("$.message").value(startsWith("Error:")));
    }

    @Test
    void forbiddenWhenUnauthenticated() throws Exception {
      // No mocking needed because the request never reaches TravelAgent at all,
      // simply check redirect status
      mockMvc
          .perform(post("/api/chat").content("Find hotels in Paris").contentType("text/plain"))
          .andExpect(status().isForbidden());
    }
  }

  @Nested
  class GetPreferences {
    @Test
    void returnsPreferencesWhenAuthenticated() throws Exception {
      // 1. Define hardcoded values and tell mock what to return
      Preferences prefs = new Preferences(150, "beach", "double", List.of("pool", "wifi"));
      when(travelAgent.getPreferences(anyString())).thenReturn(prefs);
      // 2. Send a request with a mocked OAuth2 user
      mockMvc
          .perform(
              get("/api/preferences")
                  .with(oidcLogin().idToken(token -> token.claim("sub", "google-123"))))
          // 3. Assert the response
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.budget").value(150))
          .andExpect(jsonPath("$.style").value("beach"));
    }

    @Test
    void redirectsWhenUnauthenticated() throws Exception {
      mockMvc.perform(get("/api/preferences")).andExpect(status().is3xxRedirection());
    }
  }

  @Nested
  class GetHistory {
    @Test
    void returnsHistoryWhenAuthenticated() throws Exception {
      // 1. Define hardcoded values
      SearchHistory h1 = new SearchHistory();
      h1.setId(1L);
      h1.setQuery("Hotels in Paris");
      h1.setResultSummary("Found 8 hotels starting from €95");
      h1.setTimestamp(LocalDateTime.of(2026, 3, 28, 10, 0));

      SearchHistory h2 = new SearchHistory();
      h2.setId(2L);
      h2.setQuery("Beach resorts in Bali");
      h2.setResultSummary("Found 12 hotels starting from €45");
      h2.setTimestamp(LocalDateTime.of(2026, 3, 27, 14, 30));

      // 2. Tell mock what to return
      when(travelAgent.getSearchHistory(anyString())).thenReturn(List.of(h1, h2));

      // 3. Send a request with a mocked OAuth2 user
      mockMvc
          .perform(
              get("/api/history")
                  .with(oidcLogin().idToken(token -> token.claim("sub", "google-123"))))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$").isArray())
          .andExpect(jsonPath("$.length()").value(2))
          .andExpect(jsonPath("$[0].id").value(1))
          .andExpect(jsonPath("$[0].query").value("Hotels in Paris"))
          .andExpect(jsonPath("$[0].resultSummary").value("Found 8 hotels starting from €95"))
          .andExpect(jsonPath("$[0].timestamp").value("2026-03-28T10:00:00"))
          .andExpect(jsonPath("$[1].id").value(2))
          .andExpect(jsonPath("$[1].query").value("Beach resorts in Bali"))
          .andExpect(jsonPath("$[1].resultSummary").value("Found 12 hotels starting from €45"))
          .andExpect(jsonPath("$[1].timestamp").value("2026-03-27T14:30:00"));
    }

    @Test
    void redirectsWhenUnauthenticated() throws Exception {
      mockMvc.perform(get("/api/history")).andExpect(status().is3xxRedirection());
    }
  }
}
