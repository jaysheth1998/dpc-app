package gov.cms.dpc.api.auth;

import org.hl7.fhir.dstu3.model.Organization;

import java.security.Principal;

/**
 * Simple wrapper class which ensures that the {@link Organization} resource implements {@link Principal}
 */
public class OrganizationPrincipal implements Principal {

    private final Organization organization;

    OrganizationPrincipal(Organization organization) {
        this.organization = organization;
    }

    public Organization getOrganization() {
        return organization;
    }

    @Override
    public String getName() {
        return organization.getName();
    }
}
