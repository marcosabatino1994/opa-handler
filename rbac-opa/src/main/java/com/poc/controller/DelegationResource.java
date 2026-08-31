package com.poc.controller;

import com.poc.dto.DelegationDto;
import com.poc.dto.DelegationRequest;
import com.poc.repository.AppUser;
import com.poc.repository.Delegation;
import com.poc.repository.Permission;
import com.poc.repository.Role;
import java.util.List;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

@Path("/delegations")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class DelegationResource {

    @GET
    @Transactional
    public List<DelegationDto> listAll() {
        return Delegation.<Delegation>listAll().stream().map(DelegationDto::from).toList();
    }

    @POST
    @Transactional
    public Response create(DelegationRequest req) {
        AppUser from = AppUser.find("username", req.fromUser()).firstResult();
        if (from == null) {
            throw new BadRequestException("User '" + req.fromUser() + "' non esiste");
        }

        AppUser to = AppUser.find("username", req.toUser()).firstResult();
        if (to == null) {
            throw new BadRequestException("User '" + req.toUser() + "' non esiste");
        }

        boolean fromHasPermission = from.roles.stream()
                .flatMap(r -> r.permissions.stream())
                .anyMatch(p -> p.action.equals(req.action()) && p.resource.equals(req.resource()));
        if (!fromHasPermission) {
            throw new BadRequestException(
                    "User '" + req.fromUser() + "' non ha il permesso " + req.action() + ":" + req.resource());
        }

        boolean alreadyExists = Delegation.find("fromUser = ?1 and toUser = ?2 and action = ?3 and resource = ?4",
                req.fromUser(), req.toUser(), req.action(), req.resource()).count() > 0;
        if (alreadyExists) {
            throw new BadRequestException("Delega già esistente");
        }

        Delegation d = new Delegation();
        d.fromUser = req.fromUser();
        d.toUser = req.toUser();
        d.action = req.action();
        d.resource = req.resource();
        d.persist();

        return Response.status(Response.Status.CREATED).entity(DelegationDto.from(d)).build();
    }

    @DELETE
    @Path("/{id}")
    @Transactional
    public Response delete(@PathParam("id") Long id) {
        boolean deleted = Delegation.deleteById(id);
        if (!deleted) {
            throw new NotFoundException("Delega " + id + " non trovata");
        }
        return Response.noContent().build();
    }
}
