package com.poc.dto;

import java.util.List;

public record UserRequest(String username, List<Long> roleIds) {}