package com.poc.controller;

import client.OpaClient;
import com.poc.dto.OpaInput;
import com.poc.dto.OpaRequest;
import com.poc.dto.OpaResponse;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import org.eclipse.microprofile.rest.client.inject.RestClient;

@Path("/authz")
public class AuthzResource {

    @RestClient
    OpaClient opaClient;

    @GET
    @Produces(MediaType.APPLICATION_JSON)
    public OpaResponse check(@QueryParam("user") String user,
                             @QueryParam("action") String action,
                             @QueryParam("resource") String resource,
                             @QueryParam("status") String status) {
        OpaInput input = new OpaInput(user, action, resource, status);
        OpaRequest request = new OpaRequest(input);
        return opaClient.checkAllow(request);
    }
}