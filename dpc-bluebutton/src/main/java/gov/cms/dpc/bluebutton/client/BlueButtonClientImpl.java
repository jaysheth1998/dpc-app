package gov.cms.dpc.bluebutton.client;

import ca.uhn.fhir.rest.client.api.IGenericClient;
import ca.uhn.fhir.rest.gclient.ICriterion;
import ca.uhn.fhir.rest.gclient.ReferenceClientParam;
import ca.uhn.fhir.rest.server.exceptions.ResourceNotFoundException;
import com.codahale.metrics.Meter;
import com.codahale.metrics.MetricRegistry;
import com.codahale.metrics.Timer;
import gov.cms.dpc.bluebutton.config.BBClientConfiguration;
import gov.cms.dpc.common.utils.MetricMaker;
import org.hl7.fhir.dstu3.model.Bundle;
import org.hl7.fhir.dstu3.model.CapabilityStatement;
import org.hl7.fhir.dstu3.model.ExplanationOfBenefit;
import org.hl7.fhir.dstu3.model.Patient;
import org.hl7.fhir.dstu3.model.Coverage;
import org.hl7.fhir.instance.model.api.IBaseResource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Map;
import java.util.function.Supplier;


public class BlueButtonClientImpl implements BlueButtonClient {

    private static final String REQUEST_PATIENT_METRIC = "requestPatient";
    private static final String REQUEST_EOB_METRIC = "requestEOB";
    private static final String REQUEST_COVERAGE_METRIC = "requestCoverage";
    private static final String REQUEST_NEXT_METRIC = "requestNextBundle";
    private static final String REQUEST_CAPABILITIES_METRIC = "requestCapabilities";
    private static final List<String> REQUEST_METRICS = List.of(REQUEST_PATIENT_METRIC, REQUEST_EOB_METRIC, REQUEST_COVERAGE_METRIC, REQUEST_NEXT_METRIC, REQUEST_CAPABILITIES_METRIC);

    private static final Logger logger = LoggerFactory.getLogger(BlueButtonClientImpl.class);

    private IGenericClient client;
    private BBClientConfiguration config;
    private Map<String, Timer> timers;
    private Map<String, Meter> exceptionMeters;

    private static String formBeneficiaryID(String fromPatientID) {
        return "Patient/" + fromPatientID;
    }

    public BlueButtonClientImpl(IGenericClient client, BBClientConfiguration config, MetricRegistry metricRegistry) {
        this.client = client;
        this.config = config;
        final var metricMaker = new MetricMaker(metricRegistry, BlueButtonClientImpl.class);
        this.exceptionMeters = metricMaker.registerMeters(REQUEST_METRICS);
        this.timers = metricMaker.registerTimers(REQUEST_METRICS);
    }

    /**
     * Queries Blue Button server for patient data
     *
     * @param patientID The requested patient's ID
     * @return {@link Patient} A FHIR Patient resource
     * @throws ResourceNotFoundException when no such patient with the provided ID exists
     */
    @Override
    public Patient requestPatientFromServer(String patientID) throws ResourceNotFoundException {
        logger.debug("Attempting to fetch patient ID {} from baseURL: {}", patientID, client.getServerBase());
        return instrumentCall(REQUEST_PATIENT_METRIC, () -> client
                .read()
                .resource(Patient.class)
                .withId(patientID)
                .execute());
    }

    /**
     * Queries Blue Button server for Explanations of Benefit associated with a given patient
     *
     * There are two edge cases to consider when pulling EoB data given a patientID:
     *  1. No patient with the given ID exists: if this is the case, BlueButton should return a Bundle with no
     *  entry, i.e. ret.hasEntry() will evaluate to false. For this case, the method will throw a
     *  {@link ResourceNotFoundException}
     *
     *  2. A patient with the given ID exists, but has no associated EoB records: if this is the case, BlueButton should
     *  return a Bundle with an entry of size 0, i.e. ret.getEntry().size() == 0. For this case, the method simply
     *  returns the Bundle it received from BlueButton to the caller, and the caller is responsible for handling Bundles
     *  that contain no EoBs.
     *
     * @param patientID The requested patient's ID
     * @return {@link Bundle} Containing a number (possibly 0) of {@link ExplanationOfBenefit} objects
     * @throws ResourceNotFoundException when the requested patient does not exist
     */
    @Override
    public Bundle requestEOBFromServer(String patientID) {
        logger.debug("Attempting to fetch EOBs for patient ID {} from baseURL: {}", patientID, client.getServerBase());
        return instrumentCall(REQUEST_EOB_METRIC, () ->
                fetchBundle(ExplanationOfBenefit.class, ExplanationOfBenefit.PATIENT.hasId(patientID), patientID));
    }

    /**
     * Queries Blue Button server for Coverage associated with a given patient
     *
     * Like for the EOB resource, there are two edge cases to consider when pulling coverage data given a patientID:
     *  1. No patient with the given ID exists: if this is the case, BlueButton should return a Bundle with no
     *  entry, i.e. ret.hasEntry() will evaluate to false. For this case, the method will throw a
     *  {@link ResourceNotFoundException}
     *
     *  2. A patient with the given ID exists, but has no associated Coverage records: if this is the case, BlueButton should
     *  return a Bundle with an entry of size 0, i.e. ret.getEntry().size() == 0. For this case, the method simply
     *  returns the Bundle it received from BlueButton to the caller, and the caller is responsible for handling Bundles
     *  that contain no coverage records.
     *
     * @param patientID The requested patient's ID
     * @return {@link Bundle} Containing a number (possibly 0) of {@link ExplanationOfBenefit} objects
     * @throws ResourceNotFoundException when the requested patient does not exist
     */
    @Override
    public Bundle requestCoverageFromServer(String patientID) throws ResourceNotFoundException {
        logger.debug("Attempting to fetch Coverage for patient ID {} from baseURL: {}", patientID, client.getServerBase());
        return instrumentCall(REQUEST_COVERAGE_METRIC, () ->
                fetchBundle(Coverage.class, Coverage.BENEFICIARY.hasId(formBeneficiaryID(patientID)), patientID));
    }

    @Override
    public Bundle requestNextBundleFromServer(Bundle bundle) throws ResourceNotFoundException {
        return instrumentCall(REQUEST_NEXT_METRIC, () -> {
            var nextURL = bundle.getLink(Bundle.LINK_NEXT).getUrl();
            logger.debug("Attempting to fetch next bundle from url: {}", nextURL);
            return client
                    .loadPage()
                    .next(bundle)
                    .execute();
        });
    }

    @Override
    public CapabilityStatement requestCapabilityStatement() throws ResourceNotFoundException {
        return instrumentCall(REQUEST_CAPABILITIES_METRIC, () -> client
                        .capabilities()
                        .ofType(CapabilityStatement.class)
                        .execute());
    }

    /**
     * Read a FHIR Bundle from BlueButton. Limits the returned size by resourcesPerRequest.
     *
     * @param resourceClass - FHIR Resource class
     * @param criterion - For the resource class the correct criterion that matches the patientID
     * @param patientID - id of patient
     * @return FHIR Bundle resource
     */
    private <T extends IBaseResource> Bundle fetchBundle(Class<T> resourceClass,
                                                         ICriterion<ReferenceClientParam> criterion,
                                                         String patientID) {
        final Bundle bundle = client.search()
                .forResource(resourceClass)
                .where(criterion)
                .count(config.getResourcesCount())
                .returnBundle(Bundle.class)
                .execute();

        // Case where patientID does not exist at all
        if(!bundle.hasEntry()) {
            throw new ResourceNotFoundException("No patient found with ID: " + patientID);
        }
        return bundle;
    }

    /**
     * Instrument a call to Blue Button.
     *
     * @param metricName - The name of the method
     * @param supplier - the call as lambda to instrumented
     * @param <T> - the type returned by the call
     * @return the value returned by the supplier (i.e. call)
     */
    private <T> T instrumentCall(String metricName, Supplier<T> supplier) {
        final var timerContext = timers.get(metricName).time();
        try {
            return supplier.get();
        } catch(Exception ex) {
            final var exceptionMeter = exceptionMeters.get(metricName);
            exceptionMeter.mark();
            throw ex;
        } finally {
            timerContext.stop();
        }
    }
}
