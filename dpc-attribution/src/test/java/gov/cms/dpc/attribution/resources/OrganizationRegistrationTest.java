package gov.cms.dpc.attribution.resources;

import ca.uhn.fhir.rest.client.api.IGenericClient;
import ca.uhn.fhir.rest.gclient.IOperationUntypedWithInput;
import ca.uhn.fhir.rest.server.exceptions.InternalErrorException;
import ca.uhn.fhir.rest.server.exceptions.UnprocessableEntityException;
import gov.cms.dpc.attribution.AbstractAttributionTest;
import gov.cms.dpc.attribution.AttributionTestHelpers;
import gov.cms.dpc.fhir.DPCIdentifierSystem;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.util.EntityUtils;
import org.eclipse.jetty.http.HttpStatus;
import org.hl7.fhir.dstu3.model.Bundle;
import org.hl7.fhir.dstu3.model.Organization;
import org.hl7.fhir.dstu3.model.Parameters;
import org.hl7.fhir.dstu3.model.StringType;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;

import static org.junit.jupiter.api.Assertions.*;

class OrganizationRegistrationTest extends AbstractAttributionTest {

    private static final String BAD_ORG_ID = "0c527d2e-2e8a-4808-b11d-0fa06baf8252";

    private final IGenericClient client;

    private OrganizationRegistrationTest() {
        this.client = AttributionTestHelpers.createFHIRClient(ctx, getServerURL());
    }

    @Test
    void testBasicRegistration() {

        // Read in the test file
        final InputStream inputStream = OrganizationRegistrationTest.class.getClassLoader().getResourceAsStream("organization.tmpl.json");
        final Bundle resource = (Bundle) ctx.newJsonParser().parseResource(inputStream);

        final Parameters parameters = new Parameters();
        parameters.addParameter().setResource(resource);

        final Organization submitted = this.client
                .operation()
                .onType(Organization.class)
                .named("submit")
                .withParameters(parameters)
                .returnResourceType(Organization.class)
                .encodedJson()
                .execute();

        assertAll(() -> assertNotNull(submitted, "Should have an org back"),
                () -> assertFalse(submitted.getEndpoint().isEmpty(), "Should have endpoints"));
    }

    @Test
    void testInvalidOrganization() {

        // Create a fake org
        final Organization resource = new Organization();
        resource.addIdentifier().setSystem(DPCIdentifierSystem.MBI.getSystem()).setValue("test-mbi");

        final Parameters parameters = new Parameters();
        parameters.addParameter().setResource(resource);

        final IOperationUntypedWithInput<Organization> operation = this.client
                .operation()
                .onType(Organization.class)
                .named("submit")
                .withParameters(parameters)
                .returnResourceType(Organization.class)
                .encodedJson();

        assertThrows(InternalErrorException.class, operation::execute, "Should fail with a 500 status");
    }

    @Test
    void testEmptyBundleSubmission() {

        final Parameters parameters = new Parameters();
        parameters.addParameter().setName("test").setValue(new StringType("nothing"));

        final IOperationUntypedWithInput<Organization> operation = this.client
                .operation()
                .onType(Organization.class)
                .named("submit")
                .withParameters(parameters)
                .returnResourceType(Organization.class)
                .encodedJson();

        assertThrows(UnprocessableEntityException.class, operation::execute, "Should be unprocessable");
    }

    @Test
    void testTokenGeneration() throws IOException {
        String macaroon;
        try (final CloseableHttpClient client = HttpClients.createDefault()) {
            final HttpPost httpPost = new HttpPost(getServerURL() + String.format("/Organization/%s/token", ORGANIZATION_ID));


            try (CloseableHttpResponse response = client.execute(httpPost)) {
                assertEquals(HttpStatus.OK_200, response.getStatusLine().getStatusCode(), "Should have found organization");
                macaroon = EntityUtils.toString(response.getEntity());
                // Verify that the first few bytes are correct, to ensure we encoded correctly.
                assertTrue(macaroon.startsWith("eyJ2IjoyLCJs"), "Should have correct starting string value");
            }
        }

        // Verify that it's correct.
        try (final CloseableHttpClient client = HttpClients.createDefault()) {
            final HttpGet httpGet = new HttpGet(getServerURL() + String.format("/Organization/%s/token/verify?token=%s", ORGANIZATION_ID, macaroon));

            try (CloseableHttpResponse response = client.execute(httpGet)) {
                assertEquals(HttpStatus.OK_200, response.getStatusLine().getStatusCode(), "Token should be valid");
            }
        }
    }

    @Test
    void testUnknownOrgTokenGeneration() throws IOException {
        try (final CloseableHttpClient client = HttpClients.createDefault()) {
            final HttpPost httpPost = new HttpPost(getServerURL() + "/Organization/1/token");

            try (CloseableHttpResponse response = client.execute(httpPost)) {
                assertEquals(HttpStatus.NOT_FOUND_404, response.getStatusLine().getStatusCode(), "Should not have found organization");
            }
        }
    }

    @Test
    void testEmptyTokenHandling() throws IOException {
        try (final CloseableHttpClient client = HttpClients.createDefault()) {
            final HttpGet httpGet = new HttpGet(getServerURL() + String.format("/Organization/%s/token/verify?token=%s", ORGANIZATION_ID, ""));

            try (CloseableHttpResponse response = client.execute(httpGet)) {
                assertEquals(HttpStatus.BAD_REQUEST_400, response.getStatusLine().getStatusCode(), "Should not be able to verify empty token");
            }
        }
    }

    @Test
    void testTokenWrongOrg() throws IOException {
        String macaroon;
        try (final CloseableHttpClient client = HttpClients.createDefault()) {
            final HttpPost httpPost = new HttpPost(getServerURL() + String.format("/Organization/%s/token", ORGANIZATION_ID));


            try (CloseableHttpResponse response = client.execute(httpPost)) {
                assertEquals(HttpStatus.OK_200, response.getStatusLine().getStatusCode(), "Should have found organization");
                macaroon = EntityUtils.toString(response.getEntity());
                // Verify that the first few bytes are correct, to ensure we encoded correctly.
                assertTrue(macaroon.startsWith("eyJ2IjoyLCJs"), "Should have correct starting string value");
            }
        }

        // Verify that it's unauthorized.
        try (final CloseableHttpClient client = HttpClients.createDefault()) {
            final HttpGet httpGet = new HttpGet(getServerURL() + String.format("/Organization/%s/token/verify?token=%s", BAD_ORG_ID, macaroon));

            try (CloseableHttpResponse response = client.execute(httpGet)) {
                assertEquals(HttpStatus.UNAUTHORIZED_401, response.getStatusLine().getStatusCode(), "Should not be valid");
            }
        }
    }
}
