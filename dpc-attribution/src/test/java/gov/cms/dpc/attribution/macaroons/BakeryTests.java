package gov.cms.dpc.attribution.macaroons;

import com.github.nitram509.jmacaroons.Macaroon;
import com.typesafe.config.Config;
import com.typesafe.config.ConfigBeanFactory;
import com.typesafe.config.ConfigFactory;
import gov.cms.dpc.attribution.DPCAttributionConfiguration;
import gov.cms.dpc.attribution.config.TokenPolicy;
import gov.cms.dpc.macaroons.MacaroonBakery;
import gov.cms.dpc.macaroons.MacaroonCaveat;
import gov.cms.dpc.macaroons.exceptions.BakeryException;
import gov.cms.dpc.macaroons.store.MemoryRootKeyStore;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.security.SecureRandom;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertThrows;

class BakeryTests {

    private static final String ORGANIZATION_ID = "0c527d2e-2e8a-4808-b11d-0fa06baf8254";
    private static final String BAD_ORG_ID = "0c527d2e-2e8a-4808-b11d-0fa06baf8252";

    private MacaroonBakery bakery;

    @BeforeEach
    void setup() {
        // Setup the config
        final DPCAttributionConfiguration config = new DPCAttributionConfiguration();
        config.setPublicServerURL("http://test.cms");
        config.setTokenPolicy(generateTokenPolicy());
        bakery = new BakeryProvider(config, new MemoryRootKeyStore(new SecureRandom())).get();
    }


    @Test
    void testOrganizationToken() {

        final Macaroon macaroon = bakery
                .createMacaroon(Collections.singletonList(
                        new MacaroonCaveat("organization_id",
                                MacaroonCaveat.Operator.EQ, ORGANIZATION_ID)));

        assertThrows(BakeryException.class, () -> bakery.verifyMacaroon(macaroon, String.format("organization_id = %s", BAD_ORG_ID)));
    }

    private TokenPolicy generateTokenPolicy() {
        final Config config = ConfigFactory.load();
        return ConfigBeanFactory.create(config.getConfig("dpc.attribution.tokens"), TokenPolicy.class);
    }
}
