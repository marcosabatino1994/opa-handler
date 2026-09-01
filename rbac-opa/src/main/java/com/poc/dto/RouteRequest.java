package com.poc.dto;

import java.util.List;

public record RouteRequest(String origin, String destination, List<String> modes) {}