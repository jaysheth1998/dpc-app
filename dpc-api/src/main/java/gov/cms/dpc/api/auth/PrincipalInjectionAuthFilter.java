package gov.cms.dpc.api.auth;

import ca.uhn.fhir.rest.client.api.IGenericClient;
import io.dropwizard.auth.Authenticator;
import org.hl7.fhir.dstu3.model.Organization;

import javax.annotation.Priority;
import javax.ws.rs.Priorities;
import javax.ws.rs.core.UriInfo;

/**
 * Implementation of {@link DPCAuthFilter} that is use when an {@link io.dropwizard.auth.Auth} annotated method is called.
 * This simply passes the {@link Organization} to the method and assumes that it handles all of the necessary security controls and such.
 */
@Priority(Priorities.AUTHENTICATION)
public class PrincipalInjectionAuthFilter extends DPCAuthFilter {

    PrincipalInjectionAuthFilter(IGenericClient client, Authenticator<DPCAuthCredentials, OrganizationPrincipal> auth) {
        super(client, auth);
    }

    @Override
    protected DPCAuthCredentials buildCredentials(String macaroon, Organization resource, UriInfo uriInfo) {
        return new DPCAuthCredentials(macaroon, resource, null, null);
    }
}
