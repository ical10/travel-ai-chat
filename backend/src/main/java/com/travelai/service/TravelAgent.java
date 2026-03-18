package com.travelai.service;

import com.travelai.model.SearchHistory;
import com.travelai.model.User;
import com.travelai.repository.UserRepository;
import java.time.LocalDateTime;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.stereotype.Service;

@Service
public class TravelAgent {
  private final ChatClient chatClient;
  private final UserRepository userRepo;

  public TravelAgent(
      ChatClient.Builder builder, ToolCallbackProvider tools, UserRepository userRepo) {
    this.chatClient = builder.defaultToolCallbacks(tools).build(); // registers all MCP tools
    this.userRepo = userRepo;
  }

  public String chat(String userId, String message) {
    User user = userRepo.findById(userId).orElseThrow();
    String prefs = user.getPreferences() != null ? user.getPreferences() : "{}";

    // Generate prompt object
    String response =
        chatClient
            .prompt()
            .system(
                "You are a travel assistant. User preferences: "
                    + prefs
                    + "Use Trivago tools for all hotel searches. Always include the"
                    + " accommodation_url (trivago link) for each hotel in your response. Always"
                    + " suggest 'Best Deals' from user's history if relevant.")
            .user(message)
            .call()
            .content();

    // Persist search history
    SearchHistory history = new SearchHistory();
    history.setUser(user);
    history.setQuery(message);
    history.setResultSummary(response.substring(0, Math.min(response.length(), 500)));
    history.setTimestamp(LocalDateTime.now());
    user.getHistory().add(history);
    userRepo.save(user);

    return response;
  }
}
