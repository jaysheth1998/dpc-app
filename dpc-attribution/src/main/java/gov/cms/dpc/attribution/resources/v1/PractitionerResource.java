package gov.cms.dpc.attribution.resources.v1;

import com.codahale.metrics.annotation.ExceptionMetered;
import com.codahale.metrics.annotation.Timed;
import gov.cms.dpc.attribution.jdbi.ProviderDAO;
import gov.cms.dpc.attribution.resources.AbstractPractionerResource;
import gov.cms.dpc.common.entities.ProviderEntity;
import gov.cms.dpc.fhir.FHIRExtractors;
import gov.cms.dpc.fhir.annotations.FHIR;
import io.dropwizard.hibernate.UnitOfWork;
import io.swagger.annotations.*;
import org.hibernate.validator.constraints.NotEmpty;
import org.hl7.fhir.dstu3.model.Bundle;
import org.hl7.fhir.dstu3.model.OperationOutcome;
import org.hl7.fhir.dstu3.model.Practitioner;

import javax.inject.Inject;
import javax.ws.rs.*;
import javax.ws.rs.core.Response;
import java.util.List;
import java.util.UUID;

@FHIR
@Api(value = "Practitioner")
public class PractitionerResource extends AbstractPractionerResource {

    private final ProviderDAO dao;

    @Inject
    PractitionerResource(ProviderDAO dao) {
        this.dao = dao;
    }

    @GET
    @FHIR
    @UnitOfWork
    @Override
    @Timed
    @ExceptionMetered
    @ApiOperation(value = "Search for providers", notes = "FHIR endpoint to search for Practitioner resources." +
            "<p>If a provider NPI is given, the results are filtered accordingly. " +
            "Otherwise, the method returns all Practitioners associated to the given Organization")
    // TODO: Migrate this signature to a List<Practitioner> in DPC-302
    public Bundle getPractitioners(@ApiParam(value = "Provider NPI") @QueryParam("identifier") String providerNPI, @NotEmpty @QueryParam("_tag") String organizationTag) {
        final Bundle bundle = new Bundle();
        final List<ProviderEntity> providers = this.dao.getProviders(providerNPI, splitTag(organizationTag));

        bundle.setTotal(providers.size());
        providers.forEach(provider -> bundle.addEntry().setResource(provider.toFHIR()));

        return bundle;
    }

    @POST
    @FHIR
    @UnitOfWork
    @Override
    @Timed
    @ExceptionMetered
    @ApiOperation(value = "Register provider", notes = "FHIR endpoint to register a provider with the system")
    public Response submitProvider(Practitioner provider) {

        final ProviderEntity entity = ProviderEntity.fromFHIR(provider);
        final ProviderEntity persistedEntity = this.dao.persistProvider(entity);

        return Response.status(Response.Status.CREATED).entity(persistedEntity.toFHIR()).build();
    }

    @GET
    @Path("/{providerID}")
    @FHIR
    @UnitOfWork
    @Override
    @Timed
    @ExceptionMetered
    @ApiOperation(value = "Fetch provider", notes = "FHIR endpoint to fetch a specific Practitioner resource." +
            "<p>Note: FHIR refers to *Providers* as *Practitioners* and names the resources and endpoints accordingly")
    @ApiResponses(value = {
            @ApiResponse(code = 404, message = "No matching Practitioner resource was found", response = OperationOutcome.class)
    })
    public Practitioner getProvider(@ApiParam(value = "Practitioner resource ID", required = true) @PathParam("providerID") UUID providerID) {
        final ProviderEntity providerEntity = this.dao
                .getProvider(providerID)
                .orElseThrow(() ->
                        new WebApplicationException(String.format("Provider %s is not registered",
                                providerID), Response.Status.NOT_FOUND));

        return providerEntity.toFHIR();
    }

    @DELETE
    @Path("/{providerID}")
    @FHIR
    @UnitOfWork
    @Override
    @Timed
    @ExceptionMetered
    @ApiOperation(value = "Delete provider", notes = "FHIR endpoint to remove the given Practitioner resource")
    @ApiResponses(value = {
            @ApiResponse(code = 404, message = "No matching Practitioner resource was found", response = OperationOutcome.class)
    })
    public Response deleteProvider(@ApiParam(value = "Practitioner resource ID", required = true) @PathParam("providerID") UUID providerID) {
        try {
            final ProviderEntity provider = this.dao.getProvider(providerID).orElseThrow(() -> new WebApplicationException(String.format("Provider '%s' is not registered", providerID), Response.Status.NOT_FOUND));
            this.dao.deleteProvider(provider);
            return Response.ok().build();
        } catch (IllegalArgumentException e) {
            throw new WebApplicationException(String.format("Provider '%s' is not registered", providerID), Response.Status.NOT_FOUND);
        }
    }

    @PUT
    @Path("/{providerID}")
    @FHIR
    @UnitOfWork
    @Override
    @Timed
    @ExceptionMetered
    @ApiOperation(value = "Update provider", notes = "FHIR endpoint to update the given Practitioner resource with new values.")
    public Practitioner updateProvider(@ApiParam(value = "Practitioner resource ID", required = true) @PathParam("providerID") UUID providerID, Practitioner provider) {
        final ProviderEntity providerEntity = this.dao.persistProvider(ProviderEntity.fromFHIR(provider, providerID));
        return providerEntity.toFHIR();
    }

    private static UUID splitTag(String tag) {
        final String[] split = tag.split("\\|", -1);

        if (split.length < 2) {
            throw new IllegalArgumentException("Must have | delimiter in tag");
        }

        return FHIRExtractors.getEntityUUID(split[1]);
    }
}
