package com.travelai.config;

import com.travelai.model.User;
import com.travelai.repository.UserRepository;
import java.util.ArrayList;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.client.userinfo.DefaultOAuth2UserService;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserRequest;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserService;
import org.springframework.security.oauth2.core.user.OAuth2User;

/** Configures OAuth2 user handling to create new users in the database on first Google login. */
@Configuration
public class OAuth2UserConfig {
  /**
   * Wraps the default OAuth2 user service to create a local {@link User} record on first login.
   *
   * <p>Delegates to {@link DefaultOAuth2UserService} for the actual Google API call, then checks if
   * a user with the Google {@code sub} ID exists in the database. If not, creates one with email,
   * name, and an empty search history.
   *
   * @param userRepo the user repository for lookup and persistence
   * @return an OAuth2UserService that creates missing users on first login
   */
  @Bean
  public OAuth2UserService<OAuth2UserRequest, OAuth2User> oAuth2UserService(
      UserRepository userRepo) {
    DefaultOAuth2UserService delegate = new DefaultOAuth2UserService();
    return request -> {
      OAuth2User oauth2User = delegate.loadUser(request);
      String sub = oauth2User.getAttribute("sub");
      userRepo
          .findById(sub)
          .orElseGet(
              () -> {
                User user = new User();
                user.setId(sub);
                user.setEmail(oauth2User.getAttribute("email"));
                user.setName(oauth2User.getAttribute("name"));
                user.setHistory(new ArrayList<>());
                return userRepo.save(user);
              });
      return oauth2User;
    };
  }
}
