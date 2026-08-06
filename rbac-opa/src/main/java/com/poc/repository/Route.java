package com.poc.repository;

import java.util.ArrayList;
import java.util.List;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.FetchType;
import io.quarkus.hibernate.orm.panache.PanacheEntity;

@Entity
@Table(name = "ROUTES")
public class Route extends PanacheEntity {
    public String origin;
    public String destination;

    // le modalità intermodali (es. ferro, gomma, mare): lista di stringhe
    @ElementCollection(fetch = FetchType.EAGER)
    public List<String> modes = new ArrayList<>();

    // ciclo di vita: IN_REVISIONE -> APPROVATA | RIFIUTATA
    public String status = "IN_REVISIONE";
}