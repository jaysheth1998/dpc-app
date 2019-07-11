package gov.cms.dpc.auth.resources;

import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;

@Produces("application/json")
public abstract class AbstractBaseResource {

    protected AbstractBaseResource() {
//        Not used
    }

    @Path("/auth")
    public abstract AbstractAuthResource authOperations();

    /**
     * Returns the current API version
     *
     * @return - {@link String} version number
     */
    @Path("/version")
    @Produces(MediaType.TEXT_PLAIN)
    @GET
    public abstract String version();
}
