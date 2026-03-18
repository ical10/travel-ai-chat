package com.travelai.model;

import jakarta.persistence.*;
import lombok.Data;
import java.util.List;

@Entity
@Table(name = "app_user")
@Data
public class User {
	@Id
	private String id; // Google sub
	private String email;
	private String name;
	private String preferences; // JSON string: {"budget": 150, "style": "beach"}
	
	@OneToMany(mappedBy = "user", cascade = CascadeType.ALL)
	private List<SearchHistory> history;
}
