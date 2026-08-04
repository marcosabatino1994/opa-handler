package com.poc.controller;

import com.poc.dto.UserDto;
import com.poc.dto.UserRequest;
import com.poc.repository.AppUser;
import com.poc.repository.Role;
import java.util.List;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

@Path("/users")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class AppUserResource {

    @GET
    @Transactional
    public List<UserDto> listAll() {
        return AppUser.<AppUser>listAll().stream().map(UserDto::from).toList();
    }

    @GET
    @Path("/{id}")
    @Transactional
    public UserDto getById(@PathParam("id") Long id) {
        AppUser u = AppUser.findById(id);
        if (u == null) {
            throw new NotFoundException("User " + id + " non trovato");
        }
        return UserDto.from(u);
    }

    @POST
    @Transactional
    public Response create(UserRequest req) {
        AppUser u = new AppUser();
        u.username = req.username();
        applyRoles(u, req.roleIds());
        u.persist();
        return Response.status(Response.Status.CREATED).entity(UserDto.from(u)).build();
    }

    @PUT
    @Path("/{id}")
    @Transactional
    public UserDto update(@PathParam("id") Long id, UserRequest req) {
        AppUser u = AppUser.findById(id);
        if (u == null) {
            throw new NotFoundException("User " + id + " non trovato");
        }
        u.username = req.username();
        u.roles.clear();
        applyRoles(u, req.roleIds());
        return UserDto.from(u);
    }

    @DELETE
    @Path("/{id}")
    @Transactional
    public Response delete(@PathParam("id") Long id) {
        boolean deleted = AppUser.deleteById(id);
        if (!deleted) {
            throw new NotFoundException("User " + id + " non trovato");
        }
        return Response.noContent().build();
    }

    private void applyRoles(AppUser u, List<Long> roleIds) {
        if (roleIds == null) {
            return;
        }
        for (Long rid : roleIds) {
            Role r = Role.findById(rid);
            if (r == null) {
                throw new BadRequestException("Role " + rid + " non esiste");
            }
            u.roles.add(r);
        }
    }
}