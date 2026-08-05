package com.poc.controller;

import com.poc.dto.RoleDto;
import com.poc.dto.RoleRequest;
import com.poc.repository.Permission;
import com.poc.repository.Role;
import java.util.List;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

@Path("/roles")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class RoleResource {

    @GET
    @Transactional
    public List<RoleDto> listAll() {
        return Role.<Role>listAll().stream().map(RoleDto::from).toList();
    }

    @GET
    @Path("/{id}")
    @Transactional
    public RoleDto getById(@PathParam("id") Long id) {
        Role r = Role.findById(id);
        if (r == null) {
            throw new NotFoundException("Role " + id + " non trovato");
        }
        return RoleDto.from(r);
    }

    @POST
    @Transactional
    public Response create(RoleRequest req) {
        Role r = new Role();
        r.name = req.name();
        applyPermissions(r, req.permissionIds());
        r.persist();
        return Response.status(Response.Status.CREATED).entity(RoleDto.from(r)).build();
    }

    @PUT
    @Path("/{id}")
    @Transactional
    public RoleDto update(@PathParam("id") Long id, RoleRequest req) {
        Role r = Role.findById(id);
        if (r == null) {
            throw new NotFoundException("Role " + id + " non trovato");
        }
        r.name = req.name();
        r.permissions.clear();
        applyPermissions(r, req.permissionIds());
        return RoleDto.from(r);
    }

    @DELETE
    @Path("/{id}")
    @Transactional
    public Response delete(@PathParam("id") Long id) {
        boolean deleted = Role.deleteById(id);
        if (!deleted) {
            throw new NotFoundException("Role " + id + " non trovato");
        }
        return Response.noContent().build();
    }

    private void applyPermissions(Role r, List<Long> permissionIds) {
        if (permissionIds == null) {
            return;
        }
        for (Long pid : permissionIds) {
            Permission p = Permission.findById(pid);
            if (p == null) {
                throw new BadRequestException("Permission " + pid + " non esiste");
            }
            r.permissions.add(p);
        }
    }
}