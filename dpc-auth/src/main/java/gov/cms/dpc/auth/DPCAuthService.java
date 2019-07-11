package gov.cms.dpc.auth;

import ca.mestevens.java.configuration.bundle.TypesafeConfigurationBundle;
import com.hubspot.dropwizard.guicier.GuiceBundle;
import com.squarespace.jersey2.guice.JerseyGuiceUtils;
import io.dropwizard.Application;
import io.dropwizard.setup.Bootstrap;
import io.dropwizard.setup.Environment;

public class DPCAuthService extends Application<DPCAuthConfiguration> {

    public static void main(final String[] args) throws Exception {
        new DPCAuthService().run(args);
    }

    @Override
    public String getName() {
        return "DPC Authorization Service";
    }

    @Override
    public void initialize(final Bootstrap<DPCAuthConfiguration> bootstrap) {
        // This is required for Guice to load correctly. Not entirely sure why
        // https://github.com/dropwizard/dropwizard/issues/1772
        JerseyGuiceUtils.reset();
        GuiceBundle<DPCAuthConfiguration> guiceBundle = GuiceBundle.defaultBuilder(DPCAuthConfiguration.class)
                .modules(new DPCAuthModule())
                .build();

        bootstrap.addBundle(guiceBundle);
        bootstrap.addBundle(new TypesafeConfigurationBundle("dpc.auth"));
    }

    @Override
    public void run(final DPCAuthConfiguration configuration,
                    final Environment environment) {
        // TODO: implement application
    }

}
