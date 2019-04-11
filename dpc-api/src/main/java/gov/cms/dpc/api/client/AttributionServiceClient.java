package gov.cms.dpc.api.client;

import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.parser.IParser;
import com.fasterxml.jackson.databind.ObjectMapper;
import gov.cms.dpc.common.interfaces.AttributionEngine;
import gov.cms.dpc.fhir.FHIRExtractors;
import gov.cms.dpc.fhir.FHIRMediaTypes;
import gov.cms.dpc.api.DPAPIConfiguration;
import gov.cms.dpc.api.annotations.AttributionService;
import org.eclipse.jetty.http.HttpStatus;
import org.hl7.fhir.dstu3.model.Bundle;
import org.hl7.fhir.dstu3.model.Patient;
import org.hl7.fhir.dstu3.model.Practitioner;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;
import javax.ws.rs.WebApplicationException;
import javax.ws.rs.client.Entity;
import javax.ws.rs.client.Invocation;
import javax.ws.rs.client.WebTarget;
import javax.ws.rs.core.Response;
import java.util.List;
import java.util.Optional;
import java.util.Set;

@SuppressWarnings("unchecked")
public class AttributionServiceClient implements AttributionEngine {
    private static final String GROUP_PROVIDER_FMT = "Group/%s/%s";

    private final WebTarget client;
    private final IParser parser;

    @Inject
    public AttributionServiceClient(@AttributionService WebTarget client, FhirContext ctx) {
        this.client = client;
        this.parser = ctx.newJsonParser();
    }

    @Override
    public Optional<List<String>> getAttributedPatientIDs(Practitioner provider) {
        final Invocation invocation = this.client
                .path(String.format("Group/%s", provider))
                .request(FHIRMediaTypes.FHIR_JSON)
                .buildGet();
        try (Response response = invocation.invoke()) {
            if (!HttpStatus.isSuccess(response.getStatus())) {
                throw new WebApplicationException(response.getStatusInfo().getReasonPhrase(), HttpStatus.NOT_FOUND_404);
            }

            return Optional.of((List<String>) response.readEntity(List.class));
        } catch (Exception e) {
            throw new WebApplicationException(e, HttpStatus.NOT_FOUND_404);
        }
    }

    @Override
    public void addAttributionRelationship(Practitioner provider, Patient patient) {
        final Invocation invocation = this.client
                .path(String.format(GROUP_PROVIDER_FMT, provider, patient))
                .request(FHIRMediaTypes.FHIR_JSON)
                .buildPut(null);
        handleNonBodyResponse(invocation);
    }

    @Override
    public void addAttributionRelationships(Bundle attributionBundle) {
        final Invocation invocation = this.client
                .path("Group")
                .request(FHIRMediaTypes.FHIR_JSON)
                .buildPost(Entity.entity(parser.encodeResourceToString(attributionBundle), FHIRMediaTypes.FHIR_JSON));

        handleNonBodyResponse(invocation);
    }

    @Override
    public void removeAttributionRelationship(Practitioner provider, Patient patient) {
        final Invocation invocation = this.client
                .path(String.format(GROUP_PROVIDER_FMT, provider, patient))
                .request(FHIRMediaTypes.FHIR_JSON)
                .buildDelete();
        handleNonBodyResponse(invocation);
    }

    @Override
    public boolean isAttributed(Practitioner provider, Patient patient) {
        final Invocation invocation = this.client
                .path(String.format(GROUP_PROVIDER_FMT, provider, patient))
                .request(FHIRMediaTypes.FHIR_JSON)
                .buildGet();

        try (Response response = invocation.invoke()) {
            if (!HttpStatus.isSuccess(response.getStatus())) {
                return false;
            }
        } catch (Exception e) {
            throw new WebApplicationException(e, HttpStatus.INTERNAL_SERVER_ERROR_500);
        }
        return true;
    }

    /**
     * Helper method to handle throwing an exception is one exists.
     * Used for anything that doesn't return a usable body (such as PUT, or DELETE)
     *
     * @param invocation - {@link Invocation} call to make to remove service
     */
    private static void handleNonBodyResponse(Invocation invocation) {
        try (Response response = invocation.invoke()) {
            if (!HttpStatus.isSuccess(response.getStatus())) {
                throw new WebApplicationException(response.getStatusInfo().getReasonPhrase(), HttpStatus.INTERNAL_SERVER_ERROR_500);
            }
        } catch (Exception e) {
            throw new WebApplicationException(e, HttpStatus.INTERNAL_SERVER_ERROR_500);
        }
    }

}