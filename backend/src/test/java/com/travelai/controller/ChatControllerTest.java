package com.travelai.controller;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.oauth2Login;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.travelai.dto.ChatResult;
import com.travelai.service.TravelAgent;
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
                  .with(oauth2Login().attributes(attrs -> attrs.put("sub", "google-123"))))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.message").value(genericChat))
          .andExpect(jsonPath("$.accommodations").isArray())
          .andExpect(jsonPath("$.accommodations.length()").value(1))
          .andExpect(jsonPath("$.accommodations[0].accommodation_name").value("Hotel Paris"))
          .andExpect(jsonPath("$.accommodations[0].price_per_night").value("€120"));
    }
  }
}
