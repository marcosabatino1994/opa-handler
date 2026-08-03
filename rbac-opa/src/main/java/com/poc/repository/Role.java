package com.poc.repository;

import java.util.HashSet;
import java.util.Set;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.JoinTable;
import jakarta.persistence.JoinColumn;
import io.quarkus.hibernate.orm.panache.PanacheEntity;

@Entity
@Table(name = "APP_ROLES")
public class Role extends PanacheEntity {
    public String name;

    @ManyToMany
    @JoinTable(
            name = "ROLE_PERMISSIONS",
            joinColumns = @JoinColumn(name = "role_id"),
            inverseJoinColumns = @JoinColumn(name = "permission_id")
    )
    public Set<Permission> permissions = new HashSet<>();
}