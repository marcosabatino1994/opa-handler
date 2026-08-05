package com.poc.controller;


import com.poc.bundle.BundleService;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.HeaderParam;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.Response;

@Path("/bundles")
public class BundleResource {

    @Inject
    BundleService bundleService;

    @GET
    @Path("/rbac.tar.gz")
    @Produces("application/gzip")
    public Response getBundle(@HeaderParam("If-None-Match") String ifNoneMatch) {
        String dataJson = bundleService.buildDataJson();
        String revision = bundleService.revision(dataJson);
        String etag = "\"" + revision + "\"";

        if (etag.equals(ifNoneMatch)) {
            return Response.notModified().header("ETag", etag).build();
        }

        byte[] bundle = bundleService.buildBundle(dataJson, revision);
        return Response.ok(bundle).header("ETag", etag).build();
    }
}