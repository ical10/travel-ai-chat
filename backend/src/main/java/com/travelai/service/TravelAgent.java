package com.travelai.service;

import com.travelai.model.SearchHistory;
import com.travelai.model.User;
import com.travelai.repository.UserRepository;
import java.time.LocalDateTime;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

@Service
public class TravelAgent {
  private static final Logger log = LoggerFactory.getLogger(TravelAgent.class);
  private final ChatClient chatClient;
  private final ChatClient extractionClient;
  private final UserRepository userRepo;
  private final ObjectMapper objectMapper;

  private static final String EXTRACTION_PROMPT =
      "Extract travel preferences from this user message. Return ONLY a JSON object with these"
          + " fields: budget (integer, max price per night in EUR), style (one of: beach, city,"
          + " mountain, luxury, boutique), roomType (one of: single, double, suite, family),"
          + " amenities (array of strings from: pool, wifi, parking, gym, breakfast, spa,"
          + " airConditioning, petFriendly, kitchen, freeCancellation). Use null for fields not"
          + " mentioned. Return ONLY valid JSON, no markdown or explanation.";

  public TravelAgent(
      ChatClient.Builder builder, ToolCallbackProvider tools, UserRepository userRepo) {
    this.extractionClient = builder.build(); // no tools — lightweight extraction only
    this.chatClient = builder.defaultToolCallbacks(tools).build(); // with MCP tools
    this.userRepo = userRepo;
    this.objectMapper = new ObjectMapper();
  }

  @Transactional
  public String chat(String userId, String message) {
    User user =
        userRepo
            .findById(userId)
            .orElseThrow(
                () ->
                    new IllegalStateException(
                        "User not found: " + userId + ". Try logging in again."));
    String prefs = user.getPreferences() != null ? user.getPreferences() : "{}";

    // 1. Main chat response with MCP tools
    String response =
        chatClient
            .prompt()
            .system(
                "You are a travel assistant. You give concise answers in a light and cheerful tone"
                    + " to set a happy mood. User preferences: "
                    + prefs
                    + " Use Trivago tools for all hotel searches. Always include the"
                    + " accommodation_url (trivago link) for each hotel in your response. Always"
                    + " suggest 'Best Deals' from user's history if relevant.")
            .user(message)
            .call()
            .content();

    // 2. Extract preferences from user message (lightweight, no tools)
    extractPreferences(user, message);

    // 3. Persist search history
    SearchHistory history = new SearchHistory();
    history.setUser(user);
    history.setQuery(message);
    history.setResultSummary(response.substring(0, Math.min(response.length(), 500)));
    history.setTimestamp(LocalDateTime.now());
    user.getHistory().add(history);
    userRepo.save(user);

    return response;
  }

  private void extractPreferences(User user, String message) {
    try {
      String extractedJson =
          extractionClient.prompt().system(EXTRACTION_PROMPT).user(message).call().content();

      // Strip markdown code fences if present
      extractedJson = extractedJson.strip();
      if (extractedJson.startsWith("```")) {
        extractedJson =
            extractedJson.replaceAll("^```(?:json)?\\s*", "").replaceAll("```$", "").strip();
      }

      Map<String, Object> extracted =
          objectMapper.readValue(extractedJson, new TypeReference<>() {});

      // Merge with existing preferences (only overwrite non-null fields)
      String existingJson = user.getPreferences() != null ? user.getPreferences() : "{}";
      Map<String, Object> existing = objectMapper.readValue(existingJson, new TypeReference<>() {});

      for (Map.Entry<String, Object> entry : extracted.entrySet()) {
        if (entry.getValue() != null) {
          existing.put(entry.getKey(), entry.getValue());
        }
      }

      user.setPreferences(objectMapper.writeValueAsString(existing));
    } catch (Exception e) {
      log.warn("Failed to extract preferences from message: {}", e.getMessage());
    }
  }

  public String getPreferences(String userId) {
    return userRepo
        .findById(userId)
        .map(u -> u.getPreferences() != null ? u.getPreferences() : "{}")
        .orElse("{}");
  }
}
