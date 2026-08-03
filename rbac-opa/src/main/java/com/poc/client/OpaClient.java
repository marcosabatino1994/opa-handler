package com.poc.client;

import com.poc.dto.OpaRequest;
import com.poc.dto.OpaResponse;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import org.eclipse.microprofile.rest.client.inject.RegisterRestClient;

@RegisterRestClient(configKey = "opa")
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
public interface OpaClient {

    @POST
    @Path("/v1/data/authz/allow")
    OpaResponse checkAllow(OpaRequest request);
}