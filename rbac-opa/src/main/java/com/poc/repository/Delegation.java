package com.poc.repository;

import io.quarkus.hibernate.orm.panache.PanacheEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

@Entity
@Table(name = "DELEGATIONS")
public class Delegation extends PanacheEntity {

    @Column(name = "FROM_USER")
    public String fromUser;

    @Column(name = "TO_USER")
    public String toUser;

    public String action;

    @Column(name = "RESOURCE_NAME")
    public String resource;
}
