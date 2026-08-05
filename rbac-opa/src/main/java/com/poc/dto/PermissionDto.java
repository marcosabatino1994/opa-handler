package com.poc.dto;

import com.poc.repository.Permission;

public record PermissionDto(Long id, String action, String resource) {
    public static PermissionDto from(Permission p) {
        return new PermissionDto(p.id, p.action, p.resource);
    }
}