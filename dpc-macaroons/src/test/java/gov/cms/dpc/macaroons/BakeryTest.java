package gov.cms.dpc.macaroons;

import com.github.nitram509.jmacaroons.Macaroon;
import gov.cms.dpc.macaroons.exceptions.BakeryException;
import gov.cms.dpc.macaroons.store.MemoryRootKeyStore;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class
BakeryTest {

    private static MacaroonBakery bakery;

    @BeforeAll
    static void setup() {
        bakery = new MacaroonBakery("http://localhost", new MemoryRootKeyStore(new SecureRandom()), Collections.emptyList(), Collections.emptyList());
    }

    @Test
    void testSerializationDeserialization() {
        macaroonSerializationTest(false);
    }

    @Test
    void testBase64EncodeDecode() {
        macaroonSerializationTest(true);
    }

    @Test
    void testCaveatParsing() {
        final List<MacaroonCaveat> caveatList = Collections.singletonList(
                new MacaroonCaveat("test_id",
                        MacaroonCaveat.Operator.EQ, "1234"));
        final Macaroon testMacaroon = bakery
                .createMacaroon(caveatList);

        assertArrayEquals(caveatList.toArray(), bakery.getCaveats(testMacaroon).toArray(), "Should have equal caveats");
    }

    @Test
    void testThirdPartyCaveat() {
        assertThrows(UnsupportedOperationException.class, () ->
                bakery
                        .createMacaroon(Collections.singletonList(
                                new MacaroonCaveat("http://test.local",
                                        "test_third_id", MacaroonCaveat.Operator.NEQ,
                                        "wrong value"))));
    }

    private static void macaroonSerializationTest(boolean base64) {
        final Macaroon testMacaroon = bakery
                .createMacaroon(Collections.singletonList(
                        new MacaroonCaveat("test_id",
                                MacaroonCaveat.Operator.EQ, "1234")));

        final byte[] macaroonBytes = bakery.serializeMacaroon(testMacaroon, base64);
        final Macaroon mac2 = bakery.deserializeMacaroon(new String(macaroonBytes, StandardCharsets.UTF_8));
        assertEquals(testMacaroon, mac2, "Macaroons should be equal");
    }

    @Test
    void testDefaultCaveatChecking() {

        final CaveatVerifier verifier = caveat -> {
            if (caveat.toString().equals("test_id = 1234")) {
                return Optional.empty();
            }
            return Optional.of("Caveat is not satisfied");
        };
        final MacaroonBakery caveatBakery = new MacaroonBakery.MacaroonBakeryBuilder("http://test.local", new MemoryRootKeyStore(new SecureRandom()))
                .addDefaultVerifier(verifier)
                .build();

        final Macaroon macaroon = caveatBakery
                .createMacaroon(Collections.singletonList(
                        new MacaroonCaveat("test_id",
                                MacaroonCaveat.Operator.EQ, "1234")));

        caveatBakery.verifyMacaroon(macaroon);

        // Add an additional caveat and try to validate again, which should fail
        final Macaroon macaroon1 = caveatBakery.addCaveats(macaroon, new MacaroonCaveat("expires", MacaroonCaveat.Operator.LT, "now"));

        assertThrows(BakeryException.class, () -> caveatBakery.verifyMacaroon(macaroon1));

        // Add a verifier and try again
        caveatBakery.verifyMacaroon(macaroon1, "expires < now");

        // Add an incorrect verifier, which should fail
        assertThrows(BakeryException.class, () -> caveatBakery.verifyMacaroon(macaroon1, "expires < wrong"), "Verification should fail");
    }

    @Test
    void testDefaultCaveatSuppliers() {

        final MacaroonCaveat test_caveat = new MacaroonCaveat("test_caveat", MacaroonCaveat.Operator.EQ, "1");
        final CaveatSupplier testSupplier = () -> test_caveat;
        final CaveatVerifier testVerifier = (caveat) -> {
            if (caveat.getKey().equals("test_caveat")) {
                assertEquals(caveat, test_caveat, "Caveats should match");
            }
            return Optional.empty();
        };

        final MacaroonBakery caveatBakery = new MacaroonBakery.MacaroonBakeryBuilder("http://test.local", new MemoryRootKeyStore(new SecureRandom()))
                .addDefaultCaveatSupplier(testSupplier)
                .addDefaultVerifier(testVerifier)
                .build();

        final Macaroon macaroon = caveatBakery
                .createMacaroon(Collections.singletonList(
                        new MacaroonCaveat("test_id",
                                MacaroonCaveat.Operator.EQ, "1234")));

        final List<MacaroonCaveat> macCaveats = bakery.getCaveats(macaroon);
        assertEquals(2, macCaveats.size(), "Should have two caveats");

        caveatBakery.verifyMacaroon(macaroon, "test_id = 1234");
    }
}
