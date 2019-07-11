package gov.cms.dpc.auth.resources;

import gov.cms.dpc.auth.resources.v1.AuthResource;
import org.junit.jupiter.api.Test;

import javax.ws.rs.core.Response;

class AuthResourceTest {

    /**
     * Test
     */
    @Test
    void testSimpleAuth() {
        final var authResource = new AuthResource();
        final Response response = authResource.createAccessToken("", "", "", "");
    }

}