package com.travelai.model;

import jakarta.persistence.*;
import lombok.Data;
import java.time.LocalDateTime;

@Entity
@Data
public class SearchHistory {
	@Id @GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne
	@JoinColumn(name = "user_id")
	private User user;

	private String query;
	
	@Column(columnDefinition = "TEXT")
	private String resultSummary;

	private LocalDateTime timestamp;
}
