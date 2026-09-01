package com.poc.dto;

import com.poc.repository.Route;
import java.util.List;

public record RouteDto(Long id, String origin, String destination,
                       List<String> modes, String status) {
    public static RouteDto from(Route r) {
        return new RouteDto(r.id, r.origin, r.destination, List.copyOf(r.modes), r.status);
    }
}