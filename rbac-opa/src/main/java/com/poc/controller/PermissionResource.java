package com.poc.controller;

import com.poc.repository.Permission;
import java.util.List;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

@Path("/permissions")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class PermissionResource {

    @GET
    public List<Permission> listAll() {
        return Permission.listAll();
    }

    @GET
    @Path("/{id}")
    public Permission getById(@PathParam("id") Long id) {
        Permission p = Permission.findById(id);
        if (p == null) {
            throw new NotFoundException("Permission " + id + " non trovato");
        }
        return p;
    }

    @POST
    @Transactional
    public Response create(Permission input) {
        Permission p = new Permission();
        p.action = input.action;
        p.resource = input.resource;
        p.persist();
        return Response.status(Response.Status.CREATED).entity(p).build();
    }

    @PUT
    @Path("/{id}")
    @Transactional
    public Permission update(@PathParam("id") Long id, Permission input) {
        Permission p = Permission.findById(id);
        if (p == null) {
            throw new NotFoundException("Permission " + id + " non trovato");
        }
        p.action = input.action;
        p.resource = input.resource;
        // niente persist(): l'entita e "managed", le modifiche si salvano al commit
        return p;
    }

    @DELETE
    @Path("/{id}")
    @Transactional
    public Response delete(@PathParam("id") Long id) {
        boolean deleted = Permission.deleteById(id);
        if (!deleted) {
            throw new NotFoundException("Permission " + id + " non trovato");
        }
        return Response.noContent().build();
    }
}