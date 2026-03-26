package com.travelai.model;

import jakarta.persistence.*;
import java.util.List;
import lombok.Data;

@Entity
@Table(name = "app_user")
@Data
public class User {
  @Id private String id; // SHA-256 hash of Google sub
  private String preferences; // JSON string: {"budget": 150, "style": "beach"}

  @OneToMany(mappedBy = "user", cascade = CascadeType.ALL, fetch = FetchType.EAGER)
  private List<SearchHistory> history;
}
