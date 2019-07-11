package gov.cms.dpc.auth.resources.v1;

import gov.cms.dpc.auth.resources.AbstractAuthResource;
import gov.cms.dpc.auth.resources.AbstractBaseResource;

import javax.inject.Inject;
import javax.ws.rs.Path;


@Path("/v1")
public class BaseResource extends AbstractBaseResource {
    private final AbstractAuthResource authResource;

    @Inject
    public BaseResource(AuthResource authResource) {
        this.authResource = authResource;
    }

    @Override
    public String version() {
        return "Version 1";
    }

    @Override
    public AbstractAuthResource authOperations() {
        return this.authResource;
    }

}
