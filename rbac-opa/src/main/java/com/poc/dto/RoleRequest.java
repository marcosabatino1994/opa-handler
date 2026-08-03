package com.poc.dto;

import java.util.List;

public record RoleRequest(String name, List<Long> permissionIds) {}