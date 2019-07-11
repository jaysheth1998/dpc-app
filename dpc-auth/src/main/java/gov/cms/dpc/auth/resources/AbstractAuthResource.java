package gov.cms.dpc.auth.resources;

import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.Response;

@Produces("application/json")
@Path("/auth")
public abstract class AbstractAuthResource {

    @Path("/")
    @POST
    public abstract Response createAccessToken(@QueryParam("scope") String scope,
                                    @QueryParam("grant_type") String grantType,
                                    @QueryParam("client_assertion_type") String clientAssertionType,
                                    @QueryParam("client_assertion") String clientAssertion);
}
