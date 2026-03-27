package com.travelai.dto;

import java.util.List;

public record Preferences(Integer budget, String style, String roomType, List<String> amenities) {}
