package gov.cms.dpc.aggregation.engine;

import ca.uhn.fhir.parser.DataFormatException;
import ca.uhn.fhir.rest.server.exceptions.BaseServerResponseException;
import ca.uhn.fhir.rest.server.exceptions.ResourceNotFoundException;
import gov.cms.dpc.bluebutton.client.BlueButtonClient;
import gov.cms.dpc.queue.exceptions.JobQueueFailure;
import io.github.resilience4j.retry.Retry;
import io.github.resilience4j.retry.RetryConfig;
import io.github.resilience4j.retry.transformer.RetryTransformer;
import io.reactivex.Flowable;
import org.hl7.fhir.dstu3.model.*;
import org.reactivestreams.Publisher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * A resource fetcher will fetch resources of particular type from passed {@link BlueButtonClient}
 */
class ResourceFetcher {
    private static final Logger logger = LoggerFactory.getLogger(ResourceFetcher.class);
    private BlueButtonClient blueButtonClient;
    private RetryConfig retryConfig;
    private UUID jobID;
    private ResourceType resourceType;

    /**
     * Create a context for fetching FHIR resources
     * @param blueButtonClient - client to BlueButton to use
     * @param jobID - the jobID for logging and reporting
     * @param resourceType - the resource type to fetch
     *
     */
    ResourceFetcher(BlueButtonClient blueButtonClient,
                           UUID jobID,
                           ResourceType resourceType,
                    OperationsConfig config) {
        this.blueButtonClient = blueButtonClient;
        this.retryConfig = RetryConfig.custom()
                .maxAttempts(config.getRetryCount())
                .build();
        this.jobID = jobID;
        this.resourceType = resourceType;
    }

    /**
     * Fetch all the resources for a specific patient. If errors are encountered from BlueButton,
     * a OperationalOutcome resource is used.
     *
     * @param patientID to use
     * @return a flow with all the resources for specific patient
     */
    Flowable<Resource> fetchResources(String patientID) {
        Retry retry = Retry.of("bb-resource-fetcher", this.retryConfig);
        return Flowable.fromCallable(() -> {
            logger.debug("Fetching first {} from BlueButton for {}", resourceType.toString(), patientID);
            final Resource firstFetched = fetchFirst(patientID);
            if (ResourceType.Coverage.equals(resourceType) || ResourceType.ExplanationOfBenefit.equals(resourceType)) {
                return fetchAllBundles(patientID, (Bundle)firstFetched);
            } else {
                logger.debug("Done fetching {} for {}", resourceType.toString(), patientID);
                return List.of(firstFetched);
            }
        })
                .compose(RetryTransformer.of(retry))
                .onErrorResumeNext((Throwable error) -> handleError(patientID, error))
                .flatMap(Flowable::fromIterable);
    }

    /**
     * Given a bundle, return a list of resources in the passed in bundle and all
     * the resources from the next bundles.
     *
     * @param patientID to fetch for
     * @param firstBundle of resources. Included in the result list
     * @return a list of all the resources in the first bundle and all next bundles
     */
    private List<Resource> fetchAllBundles(String patientID, Bundle firstBundle) {
        final var resources = new ArrayList<Resource>();
        addResources(resources, firstBundle);

        // Loop until no more next bundles
        var bundle = firstBundle;
        while (bundle.getLink(Bundle.LINK_NEXT) != null) {
            logger.debug("Fetching next bundle {} from BlueButton for {}", resourceType.toString(), patientID);
            bundle = blueButtonClient.requestNextBundleFromServer(bundle);
            addResources(resources, bundle);
        }

        logger.debug("Done fetching bundles {} for {}", resourceType.toString(), patientID);
        return resources;
    }

    /**
     * Turn an error into a flow.
     * @param patientID the flow
     * @param error the error
     * @return a Flowable of list of resources
     */
    private Publisher<List<Resource>> handleError(String patientID, Throwable error) {
        if (error instanceof JobQueueFailure) {
            // JobQueueFailure is an internal error. Just pass it along as an error.
            return Flowable.error(error);
        }

        // Other errors should be turned into OperationalOutcome and just recorded.
        logger.error("Turning error into OperationalOutcome. Error is: ", error);
        final var operationOutcome = formOperationOutcome(patientID, error);
        return Flowable.just(List.of(operationOutcome));
    }

    /**
     * Based on resourceType, fetch a resource or a bundle of resources.
     *
     * @param patientID of the resource to fetch
     * @return either a single resource or the first bundle of resources
     */
    private Resource fetchFirst(String patientID) {
        switch (resourceType) {
            case Patient:
                return blueButtonClient.requestPatientFromServer(patientID);
            case ExplanationOfBenefit:
                return blueButtonClient.requestEOBFromServer(patientID);
            case Coverage:
                return blueButtonClient.requestCoverageFromServer(patientID);
            default:
                throw new JobQueueFailure(jobID, "Unexpected resource type: " + resourceType.toString());
        }
    }

    /**
     * Add resources in a bundle to a list
     *
     * @param resources - the list to add resources to
     * @param bundle - the bundle to extract resources from
     */
    private void addResources(ArrayList<Resource> resources, Bundle bundle) {
        bundle.getEntry().forEach((entry) -> {
            final var resource = entry.getResource();
            if (resource.getResourceType() != resourceType) {
                throw new DataFormatException(String.format("Unexepected resource type: got %s expected: %s", resource.getResourceType().toString(), resourceType.toString()));
            }
            resources.add(resource);
        });
    }

    /**
     * Create a {@link OperationOutcome} resource from an exception with a patient
     *
     * @param ex - the exception to turn into a Operational Outcome
     * @return an operation outcome
     */
    private OperationOutcome formOperationOutcome(String patientID, Throwable ex) {
        String details;
        if (ex instanceof ResourceNotFoundException) {
            details = String.format("%s resource not found in Blue Button for id: %s", resourceType.toString(), patientID);
        } else if (ex instanceof BaseServerResponseException) {
            final var serverException = (BaseServerResponseException) ex;
            details = String.format("Blue Button error fetching %s resource. HTTP return code: %s", resourceType.toString(), serverException.getStatusCode());
        } else {
            details = String.format("Internal error: %s", ex.getMessage());
        }

        final var patientLocation = List.of(new StringType("Patient"), new StringType("id"), new StringType(patientID));
        final var outcome = new OperationOutcome();
        outcome.addIssue()
                .setSeverity(OperationOutcome.IssueSeverity.ERROR)
                .setCode(OperationOutcome.IssueType.EXCEPTION)
                .setDetails(new CodeableConcept().setText(details))
                .setLocation(patientLocation);
        return outcome;
    }
}
