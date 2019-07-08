package gov.cms.dpc.api.resources.v1;

import ca.uhn.fhir.rest.api.MethodOutcome;
import ca.uhn.fhir.rest.client.api.IGenericClient;
import com.codahale.metrics.annotation.Timed;
import gov.cms.dpc.api.resources.AbstractPractionerResource;
import org.hl7.fhir.dstu3.model.Bundle;
import org.hl7.fhir.dstu3.model.IdType;
import org.hl7.fhir.dstu3.model.Patient;
import org.hl7.fhir.dstu3.model.Practitioner;

import javax.inject.Inject;
import javax.ws.rs.WebApplicationException;
import javax.ws.rs.core.Response;
import java.util.UUID;

public class PractitionerResource extends AbstractPractionerResource {

    private final IGenericClient client;

    @Inject
    PractitionerResource(IGenericClient client) {
        this.client = client;
    }

    @Override
    @Timed
    public Bundle getPractitioners(String providerNPI) {
        return this.client
                .search()
                .forResource(Practitioner.class)
                .encodedJson()
                .where(Patient.IDENTIFIER.exactly().identifier(providerNPI))
                .returnBundle(Bundle.class)
                .encodedJson()
                .execute();
    }

    @Override
    @Timed
    public Practitioner submitProvider(Practitioner provider) {
        final MethodOutcome outcome = this.client
                .create()
                .resource(provider)
                .encodedJson()
                .execute();

        final Practitioner resource = (Practitioner) outcome.getResource();
        if (resource == null) {
            throw new WebApplicationException("Unable to submit provider", Response.Status.INTERNAL_SERVER_ERROR);
        }
        return resource;
    }

    @Override
    @Timed
    public Practitioner getProvider(UUID providerID) {
        return this.client
                .read()
                .resource(Practitioner.class)
                .withId(providerID.toString())
                .encodedJson()
                .execute();
    }

    @Override
    @Timed
    public Response deleteProvider(UUID providerID) {
        this.client
                .delete()
                .resourceById(new IdType("Practitioner", providerID.toString()))
                .encodedJson()
                .execute();

        return Response.ok().build();
    }

    @Override
    @Timed
    public Practitioner updateProvider(UUID providerID, Practitioner provider) {
        return null;
    }
}
