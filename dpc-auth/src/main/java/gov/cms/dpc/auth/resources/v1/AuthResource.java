package gov.cms.dpc.auth.resources.v1;

import gov.cms.dpc.auth.resources.AbstractAuthResource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.Response;

@Produces("application/json")
@Path("/auth")
public class AuthResource extends AbstractAuthResource {
    private static final Logger logger = LoggerFactory.getLogger(AuthResource.class);

    @Inject
    public AuthResource() {
    }

    @Path("/")
    @POST
    public Response createAccessToken(@QueryParam("scope") String scope,
                                    @QueryParam("grant_type") String grantType,
                                    @QueryParam("client_assertion_type") String clientAssertionType,
                                    @QueryParam("client_assertion") String clientAssertion) {
        return null;
    }
}
