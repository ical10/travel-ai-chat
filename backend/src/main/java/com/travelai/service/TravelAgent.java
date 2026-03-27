package com.travelai.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.travelai.dto.ChatResult;
import com.travelai.model.SearchHistory;
import com.travelai.model.User;
import com.travelai.repository.UserRepository;
import io.modelcontextprotocol.client.McpSyncClient;
import io.modelcontextprotocol.spec.McpSchema;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TravelAgent {
  private static final Logger log = LoggerFactory.getLogger(TravelAgent.class);
  private final ChatClient chatClient;
  private final ChatClient extractionClient;
  private final List<McpSyncClient> mcpClients;
  private final UserRepository userRepo;
  private final ObjectMapper objectMapper;

  private static final String EXTRACTION_PROMPT =
      "Extract travel preferences from this user message. Return ONLY a JSON object with these"
          + " fields: budget (integer, max price per night in EUR), style (one of: beach, city,"
          + " mountain, luxury, boutique), roomType (one of: single, double, suite, family),"
          + " amenities (array of strings from: pool, wifi, parking, gym, breakfast, spa,"
          + " airConditioning, petFriendly, kitchen, freeCancellation). Use null for fields not"
          + " mentioned. Return ONLY valid JSON, no markdown or explanation.";

  private static final String SEARCH_PARAMS_PROMPT =
      "Extract hotel search parameters from this user message. Return ONLY a JSON object with:"
          + " query (city or country name as string), arrival (YYYY-MM-DD format, use tomorrow if"
          + " not specified), departure (YYYY-MM-DD format, use 2 days after arrival if not"
          + " specified), adults (integer, default 2), rooms (integer, default 1),"
          + " landmark (specific place/address if mentioned, or null),"
          + " latitude (number if landmark known, or null),"
          + " longitude (number if landmark known, or null)."
          + " If the message is not about hotel search, return {\"query\": null}."
          + " Return ONLY valid JSON, no markdown or explanation.";

  public TravelAgent(
      ChatClient.Builder builder, List<McpSyncClient> mcpClients, UserRepository userRepo) {
    this.extractionClient = builder.build();
    this.chatClient =
        builder.build(); // no integrated mcp tools because Grok messes up the MCP response
    this.mcpClients = mcpClients;
    this.userRepo = userRepo;
    this.objectMapper = new ObjectMapper();
  }

  @Transactional
  public ChatResult chat(String userId, String message) {
    User user =
        userRepo
            .findById(userId)
            .orElseThrow(
                () ->
                    new IllegalStateException(
                        "User not found: " + userId + ". Try logging in again."));
    String prefs = user.getPreferences() != null ? user.getPreferences() : "{}";

    // Build recent search history summary (last 5)
    List<SearchHistory> pastSearches = user.getHistory();
    StringBuilder historySummary = new StringBuilder();
    if (pastSearches != null && !pastSearches.isEmpty()) {
      historySummary.append(" Recent search history: ");
      pastSearches.stream()
          .sorted((a, b) -> b.getTimestamp().compareTo(a.getTimestamp()))
          .limit(5)
          .forEach(
              h ->
                  historySummary
                      .append("[\"")
                      .append(h.getQuery())
                      .append("\" → ")
                      .append(h.getResultSummary())
                      .append("] "));
    }

    // 1. Direct MCP call — raw hotel data
    List<Map<String, Object>> accommodations = searchAccommodationsDirect(message);

    // 2. Grok call — analyze hotel results with user preferences
    String hotelSummary = buildHotelSummary(accommodations);
    String response =
        chatClient
            .prompt()
            .system(
                "You are a travel assistant. Concise, cheerful tone."
                    + " User preferences: "
                    + prefs
                    + "."
                    + historySummary)
            .user(
                accommodations.isEmpty()
                    ? message
                    : message
                        + "\n\nHere are the hotel results found:\n"
                        + hotelSummary
                        + "\n\n"
                        + "Based on these results and the user's preferences, provide:\n"
                        + "1. A brief ranking of the top 3 best deals on clear, separated bullet"
                        + " points\n"
                        + "2. Why each is a good match for the user's preferences\n"
                        + "3. One travel tip for the destination\n"
                        + "Keep it concise (3-5 sentences). Do NOT include URLs or images.")
            .call()
            .content();

    // 3. Extract preferences from user message
    extractPreferences(user, message);

    // 4. Persist search history
    SearchHistory history = new SearchHistory();
    history.setUser(user);
    history.setQuery(message);
    history.setResultSummary(response.substring(0, Math.min(response.length(), 500)));
    history.setTimestamp(LocalDateTime.now());
    user.getHistory().add(history);
    userRepo.save(user);

    return new ChatResult(response, accommodations);
  }

  @SuppressWarnings("unchecked")
  private List<Map<String, Object>> searchAccommodationsDirect(String message) {
    try {
      if (mcpClients.isEmpty()) {
        log.warn("No MCP clients available");
        return List.of();
      }
      McpSyncClient mcpClient = mcpClients.get(0);

      String today = LocalDateTime.now().toLocalDate().toString();

      // Step 1: Extract search params from user message via LLM
      String paramsJson =
          extractionClient
              .prompt()
              .system(SEARCH_PARAMS_PROMPT + " Today's date is " + today + ".")
              .user(message)
              .call()
              .content()
              .strip();
      log.info("Search params LLM response (length={}): [{}]", paramsJson.length(), paramsJson);
      if (paramsJson.startsWith("```")) {
        paramsJson = paramsJson.replaceAll("^```(?:json)?\\s*", "").replaceAll("```$", "").strip();
      }
      // Extract JSON if LLM wrapped it in text
      if (!paramsJson.startsWith("{")) {
        int jsonStart = paramsJson.indexOf('{');
        int jsonEnd = paramsJson.lastIndexOf('}');
        if (jsonStart >= 0 && jsonEnd > jsonStart) {
          paramsJson = paramsJson.substring(jsonStart, jsonEnd + 1);
        } else {
          log.warn("No JSON found in search params response");
          return List.of();
        }
      }

      Map<String, Object> params = objectMapper.readValue(paramsJson, new TypeReference<>() {});
      String query = (String) params.get("query");
      if (query == null || query.isBlank()) return List.of();

      // Step 2: Parse params from JSON
      String arrival = (String) params.getOrDefault("arrival", defaultArrival());
      String departure = (String) params.getOrDefault("departure", defaultDeparture());
      // Ensure dates are in the future
      if (arrival.compareTo(today) < 0) {
        log.warn("LLM returned past arrival date {}, using default", arrival);
        arrival = defaultArrival();
        departure = defaultDeparture();
      }
      Number adults = (Number) params.getOrDefault("adults", 2);
      Number rooms = (Number) params.getOrDefault("rooms", 1);
      Number latitude = (Number) params.get("latitude");
      Number longitude = (Number) params.get("longitude");

      Map<String, Object> searchData;

      if (latitude != null && longitude != null) {
        // Step 2a: Landmark search — use radius search tool
        Map<String, Object> radiusArgs = new HashMap<>();
        radiusArgs.put("latitude", latitude);
        radiusArgs.put("longitude", longitude);
        radiusArgs.put("radius", 5000);
        radiusArgs.put("arrival", arrival);
        radiusArgs.put("departure", departure);
        radiusArgs.put("adults", adults);
        radiusArgs.put("rooms", rooms);

        McpSchema.CallToolResult radiusResult =
            mcpClient.callTool(
                new McpSchema.CallToolRequest("trivago-accommodation-radius-search", radiusArgs));
        searchData = extractResultData(radiusResult);
      } else {
        // Step 2b: City search — get location ID first
        McpSchema.CallToolResult suggestResult =
            mcpClient.callTool(
                new McpSchema.CallToolRequest(
                    "trivago-search-suggestions", Map.of("query", query)));
        Map<String, Object> suggestData = extractResultData(suggestResult);
        log.info("Suggest data keys: {}", suggestData.keySet());

        List<Map<String, Object>> suggestions =
            (List<Map<String, Object>>) suggestData.get("suggestions");
        log.info("Suggestions: {}", suggestions != null ? suggestions.size() + " items" : "null");
        if (suggestions == null || suggestions.isEmpty()) return List.of();

        Map<String, Object> bestSuggestion = suggestions.get(0);
        log.info("Best suggestion keys: {}, values: {}", bestSuggestion.keySet(), bestSuggestion);
        // Handle both lowercase (ns/id) and uppercase (NS/ID) keys
        Number ns = (Number) bestSuggestion.getOrDefault("ns", bestSuggestion.get("NS"));
        Number id = (Number) bestSuggestion.getOrDefault("id", bestSuggestion.get("ID"));
        log.info(
            "Using ns={}, id={}, arrival={}, departure={}, adults={}, rooms={}",
            ns,
            id,
            arrival,
            departure,
            adults,
            rooms);

        // Step 3: Search accommodations at location
        Map<String, Object> searchArgs = new HashMap<>();
        searchArgs.put("ns", ns);
        searchArgs.put("id", id);
        searchArgs.put("arrival", arrival);
        searchArgs.put("departure", departure);
        searchArgs.put("adults", adults);
        searchArgs.put("rooms", rooms);

        McpSchema.CallToolResult searchResult =
            mcpClient.callTool(
                new McpSchema.CallToolRequest("trivago-accommodation-search", searchArgs));
        searchData = extractResultData(searchResult);
      }

      // Extract accommodations from result
      log.info(
          "Search data keys: {}, full: {}",
          searchData.keySet(),
          searchData.toString().substring(0, Math.min(500, searchData.toString().length())));
      List<Map<String, Object>> accommodations =
          (List<Map<String, Object>>) searchData.get("accommodations");
      log.info(
          "Accommodations: {}", accommodations != null ? accommodations.size() + " items" : "null");
      return accommodations != null ? accommodations : List.of();

    } catch (Exception e) {
      log.warn("Direct MCP search failed: {} — {}", e.getClass().getSimpleName(), e.getMessage());
      return List.of();
    }
  }

  private Map<String, Object> extractResultData(McpSchema.CallToolResult result) {
    // Prefer structuredContent (already parsed as Map)
    if (result.structuredContent() != null && result.structuredContent() instanceof Map) {
      @SuppressWarnings("unchecked")
      Map<String, Object> data = (Map<String, Object>) result.structuredContent();
      return data;
    }
    // Fallback to text content (JSON string)
    StringBuilder sb = new StringBuilder();
    for (McpSchema.Content content : result.content()) {
      if (content instanceof McpSchema.TextContent text) {
        sb.append(text.text());
      }
    }
    try {
      return objectMapper.readValue(sb.toString(), new TypeReference<>() {});
    } catch (Exception e) {
      log.warn("Failed to parse MCP text content as JSON: {}", e.getMessage());
      return Map.of();
    }
  }

  private String buildHotelSummary(List<Map<String, Object>> accommodations) {
    StringBuilder sb = new StringBuilder();
    int limit = Math.min(accommodations.size(), 10);
    for (int i = 0; i < limit; i++) {
      Map<String, Object> a = accommodations.get(i);
      sb.append(i + 1)
          .append(". ")
          .append(a.getOrDefault("accommodation_name", "Unknown"))
          .append(" — ")
          .append(a.getOrDefault("price_per_night", "?"))
          .append(", ")
          .append(a.getOrDefault("review_rating", "?"))
          .append("/10")
          .append(" (")
          .append(a.getOrDefault("review_count", "?"))
          .append(" reviews)")
          .append(", ")
          .append(a.getOrDefault("hotel_rating", "?"))
          .append(" stars")
          .append(", ")
          .append(a.getOrDefault("distance", ""))
          .append(", amenities: ")
          .append(a.getOrDefault("top_amenities", ""))
          .append("\n");
    }
    return sb.toString();
  }

  private String defaultArrival() {
    return LocalDateTime.now().plusDays(1).toLocalDate().toString();
  }

  private String defaultDeparture() {
    return LocalDateTime.now().plusDays(3).toLocalDate().toString();
  }

  private void extractPreferences(User user, String message) {
    try {
      String extractedJson =
          extractionClient.prompt().system(EXTRACTION_PROMPT).user(message).call().content();

      extractedJson = extractedJson.strip();
      if (extractedJson.startsWith("```")) {
        extractedJson =
            extractedJson.replaceAll("^```(?:json)?\\s*", "").replaceAll("```$", "").strip();
      }

      Map<String, Object> extracted =
          objectMapper.readValue(extractedJson, new TypeReference<>() {});

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

  @Transactional(readOnly = true)
  public List<SearchHistory> getSearchHistory(String userId) {
    return userRepo
        .findById(userId)
        .map(u -> u.getHistory() != null ? u.getHistory() : List.<SearchHistory>of())
        .orElse(List.<SearchHistory>of());
  }
}
