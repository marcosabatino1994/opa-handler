package com.poc.dto;

import com.poc.repository.Delegation;

public record DelegationDto(Long id, String fromUser, String toUser, String action, String resource) {

    public static DelegationDto from(Delegation d) {
        return new DelegationDto(d.id, d.fromUser, d.toUser, d.action, d.resource);
    }
}
