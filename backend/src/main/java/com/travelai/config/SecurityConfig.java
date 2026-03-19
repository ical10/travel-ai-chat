package com.travelai.config;

import java.util.List;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.oauth2.client.oidc.userinfo.OidcUserRequest;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserService;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

/** Configures Spring Security for OAuth2 login using Google and REST API protection. */
@Configuration
public class SecurityConfig {
  private final OAuth2UserService<OidcUserRequest, OidcUser> oidcUserService;

  public SecurityConfig(OAuth2UserService<OidcUserRequest, OidcUser> oidcUserService) {
    this.oidcUserService = oidcUserService;
  }

  /**
   * Defines the security filter chain.
   *
   * @param http the HttpSecurity builder
   * @return the configured SecurityFilterChain
   * @throws Exception if configuration fails
   */
  @Bean
  public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
    http.cors(cors -> cors.configurationSource(corsConfigurationSource()))
        .csrf(csrf -> csrf.ignoringRequestMatchers("/api/**"))
        .authorizeHttpRequests(
            auth -> auth.requestMatchers("/api/**").authenticated().anyRequest().permitAll())
        .oauth2Login(oauth2 -> oauth2
            .userInfoEndpoint(userInfo -> userInfo.oidcUserService(oidcUserService))
            .defaultSuccessUrl("http://localhost:5173", true));
    return http.build();
  }

  /**
   * Defines the CORS configuration.
   *
   * @implNote This method is infallible — it only builds config objects and registers them in a
   *     map. Invalid origins would only surface when Spring processes an actual request.
   * @return the URL-based CORS configuration source.
   */
  @Bean
  public CorsConfigurationSource corsConfigurationSource() {
    CorsConfiguration config = new CorsConfiguration();
    config.setAllowedOrigins(List.of("http://localhost:5173")); // Vite dev server
    config.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE"));
    config.setAllowedHeaders(List.of("*"));
    config.setAllowCredentials(true); // needed for session cookies
    return new UrlBasedCorsConfigurationSource() {
      {
        registerCorsConfiguration("/**", config);
      }
    };
  }
}
