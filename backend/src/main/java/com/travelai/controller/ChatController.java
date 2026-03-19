package com.travelai.controller;

import com.travelai.service.TravelAgent;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** REST controller for the chat endpoint. Requires an authenticated OAuth2 session. */
@RestController
@RequestMapping("/api")
public class ChatController {
  private final TravelAgent travelAgent;

  public ChatController(TravelAgent travelAgent) {
    this.travelAgent = travelAgent;
  }

  /**
   * Sends a user message to the travel agent and returns the AI response.
   *
   * @param principal the authenticated Google OAuth2 user (injected by Spring Security)
   * @param message the user's chat message
   * @return the AI-generated response with hotel search results and Trivago links
   */
  @PostMapping("/chat")
  public String chat(@AuthenticationPrincipal OAuth2User principal, @RequestBody String message) {
    String userId = principal.getAttribute("sub");
    return travelAgent.chat(userId, message);
  }
}
