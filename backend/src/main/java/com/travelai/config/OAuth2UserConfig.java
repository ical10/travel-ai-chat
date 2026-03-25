package com.travelai.config;

import com.travelai.model.User;
import com.travelai.repository.UserRepository;
import com.travelai.util.UserIdHasher;
import java.util.ArrayList;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.client.oidc.userinfo.OidcUserRequest;
import org.springframework.security.oauth2.client.oidc.userinfo.OidcUserService;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserService;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;

/** Configures OAuth2 user handling to create new users in the database on first Google login. */
@Configuration
public class OAuth2UserConfig {
  /**
   * Wraps the default OIDC user service to create a local {@link User} record on first login.
   *
   * <p>Google uses OpenID Connect (OIDC), not plain OAuth2, so this must implement {@link
   * OAuth2UserService} with {@link OidcUserRequest} and {@link OidcUser}.
   *
   * @param userRepo the user repository for lookup and persistence
   * @return an OidcUserService that creates missing users on first login
   */
  @Bean
  public OAuth2UserService<OidcUserRequest, OidcUser> oidcUserService(UserRepository userRepo) {
    OidcUserService delegate = new OidcUserService();
    return request -> {
      OidcUser oidcUser = delegate.loadUser(request);
      String sub = oidcUser.getSubject();
      String hashedSub = UserIdHasher.hash(sub);
      userRepo
          .findById(hashedSub)
          .orElseGet(
              () -> {
                User user = new User();
                user.setId(hashedSub);
                user.setPreferences("{}");
                user.setHistory(new ArrayList<>());
                return userRepo.save(user);
              });
      return oidcUser;
    };
  }
}
