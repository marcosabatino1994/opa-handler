package com.poc.controller;

import com.poc.dto.RouteDto;
import com.poc.dto.RouteRequest;
import com.poc.repository.Route;
import java.util.ArrayList;
import java.util.List;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

@Path("/routes")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class RouteResource {

    @GET
    public List<RouteDto> listAll() {
        return Route.<Route>listAll().stream().map(RouteDto::from).toList();
    }

    @GET
    @Path("/{id}")
    public RouteDto getById(@PathParam("id") Long id) {
        Route r = Route.findById(id);
        if (r == null) throw new NotFoundException("Tratta " + id + " non trovata");
        return RouteDto.from(r);
    }

    @POST
    @Transactional
    public Response create(RouteRequest input) {
        Route r = new Route();
        r.origin = input.origin();
        r.destination = input.destination();
        r.modes = input.modes() != null ? new ArrayList<>(input.modes()) : new ArrayList<>();
        r.status = "IN_REVISIONE";               // nasce sempre in revisione
        r.persist();
        return Response.status(Response.Status.CREATED).entity(RouteDto.from(r)).build();
    }

    @PUT
    @Path("/{id}")
    @Transactional
    public RouteDto update(@PathParam("id") Long id, RouteRequest input) {
        Route r = Route.findById(id);
        if (r == null) throw new NotFoundException("Tratta " + id + " non trovata");
        r.origin = input.origin();
        r.destination = input.destination();
        if (input.modes() != null) r.modes = new ArrayList<>(input.modes());
        return RouteDto.from(r);                  // dirty checking: salva al commit
    }

    @POST
    @Path("/{id}/approve")
    @Transactional
    public RouteDto approve(@PathParam("id") Long id) {
        Route r = Route.findById(id);
        if (r == null) throw new NotFoundException("Tratta " + id + " non trovata");
        r.status = "APPROVATA";
        return RouteDto.from(r);
    }

    @POST
    @Path("/{id}/reject")
    @Transactional
    public RouteDto reject(@PathParam("id") Long id) {
        Route r = Route.findById(id);
        if (r == null) throw new NotFoundException("Tratta " + id + " non trovata");
        r.status = "RIFIUTATA";
        return RouteDto.from(r);
    }

    @DELETE
    @Path("/{id}")
    @Transactional
    public Response delete(@PathParam("id") Long id) {
        boolean deleted = Route.deleteById(id);
        if (!deleted) throw new NotFoundException("Tratta " + id + " non trovata");
        return Response.noContent().build();
    }
}