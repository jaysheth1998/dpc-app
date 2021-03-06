package gov.cms.dpc.attribution.macaroons;

import gov.cms.dpc.attribution.config.TokenPolicy;
import gov.cms.dpc.macaroons.CaveatVerifier;
import gov.cms.dpc.macaroons.MacaroonCaveat;

import java.util.Optional;

import static gov.cms.dpc.attribution.macaroons.VerifierConstants.NO_MATCH;

/**
 * Implementation of {@link CaveatVerifier} which verifies that token version meets a minimum threshold
 */
public class VersionCaveatVerifier implements CaveatVerifier {

    private final int minimumVersion;

    VersionCaveatVerifier(TokenPolicy policy) {
        this.minimumVersion = policy.getVersionPolicy().getMinimumVersion();
    }

    @Override
    public Optional<String> check(MacaroonCaveat caveat) {

        if (caveat.getKey().equals(VersionCaveatSupplier.VERSION_KEY)) {
            final int tokenVersion = Integer.parseInt(caveat.getValue());
            if (tokenVersion < minimumVersion) {
                return Optional.of(String.format("Token version '%d' is not supported. Minimum is: %s", tokenVersion, minimumVersion));
            }
            return Optional.empty();
        }
        return Optional.of(NO_MATCH);
    }
}
