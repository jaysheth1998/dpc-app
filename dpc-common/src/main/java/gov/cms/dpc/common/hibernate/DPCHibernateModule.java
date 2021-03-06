package gov.cms.dpc.common.hibernate;

import com.google.inject.Binder;
import com.google.inject.Provides;
import com.google.inject.Scopes;
import com.hubspot.dropwizard.guicier.DropwizardAwareModule;
import io.dropwizard.Configuration;
import io.dropwizard.db.DataSourceFactory;
import io.dropwizard.db.ManagedDataSource;

import javax.inject.Singleton;


public class DPCHibernateModule<T extends Configuration & IDPCDatabase> extends DropwizardAwareModule<T> {

    public DPCHibernateModule() {
        // Not used
    }

    @Override
    public void configure(Binder binder) {
        binder.bind(DPCHibernateBundle.class).in(Scopes.SINGLETON);
    }

    @Provides
    @Singleton
    @SuppressWarnings({"rawtypes", "unchecked"})
    DPCManagedSessionFactory getSessionFactory(DPCHibernateBundle hibernate) {
        // This is necessary because the session factory doesn't load on its own.
        // I'm really not sure how to fix this, I think it's due to the interaction with the Proxy Factory
        try {
            hibernate.run(getConfiguration(), getEnvironment());
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
        return new DPCManagedSessionFactory(hibernate.getSessionFactory());
    }

    @Provides
    @Singleton
    ManagedDataSource provideDataSource(DataSourceFactory factory) {
        return factory.build(getEnvironment().metrics(), "tested-things");
    }

    @Provides
    @Singleton
    DataSourceFactory provideFactory() {
        return getConfiguration().getDatabase();
    }
}
