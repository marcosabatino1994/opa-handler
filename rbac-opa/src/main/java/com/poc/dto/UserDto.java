package com.poc.dto;

import com.poc.repository.AppUser;
import java.util.List;

public record UserDto(Long id, String username, List<RoleDto> roles) {
    public static UserDto from(AppUser u) {
        List<RoleDto> rs = u.roles.stream()
                .map(RoleDto::from)
                .toList();
        return new UserDto(u.id, u.username, rs);
    }
}