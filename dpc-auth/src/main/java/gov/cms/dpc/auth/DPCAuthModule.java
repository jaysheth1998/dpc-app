package gov.cms.dpc.auth;

import com.codahale.metrics.MetricRegistry;
import com.google.inject.Binder;
import com.google.inject.Provides;
import com.hubspot.dropwizard.guicier.DropwizardAwareModule;
import com.typesafe.config.Config;
import gov.cms.dpc.auth.resources.v1.AuthResource;
import gov.cms.dpc.auth.resources.v1.BaseResource;
import gov.cms.dpc.common.annotations.APIV1;
import gov.cms.dpc.common.annotations.ServiceBaseURL;

import javax.inject.Singleton;
import javax.servlet.http.HttpServletRequest;
import javax.ws.rs.core.Context;

public class DPCAuthModule extends DropwizardAwareModule<DPCAuthConfiguration> {

    DPCAuthModule() {
        // Not used
    }

    @Override
    public void configure(Binder binder) {
        // V1 Resources
        binder.bind(BaseResource.class);
        binder.bind(AuthResource.class);
    }

    @Provides
    @Singleton
    public MetricRegistry provideMetricRegistry() {
        return getEnvironment().metrics();
    }

    @Provides
    public Config provideConfig() {
        return getConfiguration().getConfig();
    }

    @Provides
    @APIV1
    public String provideV1URL(@ServiceBaseURL String baseURL) {
        return baseURL + "/v1";
    }

    @Provides
    @ServiceBaseURL
    public String provideBaseURL(@Context HttpServletRequest request) {
        return String.format("%s://%s:%s", request.getScheme(), request.getServerName(), request.getServerPort());
    }
}
