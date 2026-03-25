package com.travelai.dto;

import java.util.List;

public record PreferencesDTO(Integer budget, String style, String roomType, List<String> amenities) {}
