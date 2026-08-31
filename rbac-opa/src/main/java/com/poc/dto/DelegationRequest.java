package com.poc.dto;

public record DelegationRequest(String fromUser, String toUser, String action, String resource) {}
