package com.poc.dto;

import com.poc.repository.Role;
import java.util.List;

public record RoleDto(Long id, String name, List<PermissionDto> permissions) {
    public static RoleDto from(Role r) {
        List<PermissionDto> perms = r.permissions.stream()
                .map(PermissionDto::from)
                .toList();
        return new RoleDto(r.id, r.name, perms);
    }
}