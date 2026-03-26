package com.travelai.dto;

import java.util.List;
import java.util.Map;

public record ChatResult(String message, List<Map<String, Object>> accommodations) {}
