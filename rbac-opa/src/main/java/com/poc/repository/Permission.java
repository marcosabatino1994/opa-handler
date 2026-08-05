package com.poc.repository;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import io.quarkus.hibernate.orm.panache.PanacheEntity;

@Entity
@Table(name = "PERMISSIONS")
public class Permission extends PanacheEntity {
    public String action;
    @Column(name = "RESOURCE_NAME")
    public String resource;
}