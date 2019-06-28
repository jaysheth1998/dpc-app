package gov.cms.dpc.common.utils;

import org.apache.commons.lang3.tuple.Pair;
import org.hl7.fhir.dstu3.model.Bundle;
import org.hl7.fhir.dstu3.model.IdType;
import org.hl7.fhir.dstu3.model.Patient;
import org.hl7.fhir.dstu3.model.Practitioner;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Utility class for process the test attribution file.
 * Mostly used for testing, but also by the SeedCommand in the AttributionService
 */
public class SeedProcessor {

    private static final Random rand = new Random();

    private final InputStream is;

    public SeedProcessor(String fileLocation) throws FileNotFoundException {
        this.is = new FileInputStream(fileLocation);
    }

    public SeedProcessor(InputStream is) {
        this.is = is;
    }

    /**
     * Processes the provided attribution file and groups the patients by provider ID
     *
     * @return - {@link Map} of Provider IDs and a {@link List} of {@link Pair} of providerID and patientID
     * @throws IOException - throws if unable to read the input file
     */
    public Map<String, List<Pair<String, String>>> extractProviderMap() throws IOException {
        List<Pair<String, String>> providerPairs = new ArrayList<>();

        try (BufferedReader reader = new BufferedReader(new InputStreamReader(this.is, StandardCharsets.UTF_8))) {
            for (String line; (line = reader.readLine()) != null; ) {
                // We can ignore this, because it's not worth pulling in Guava just for this.
                final String[] splits = line.split(",", -1);

                providerPairs.add(Pair.of(splits[1], splits[0]));
            }
        }

        return providerPairs
                .stream()
                .collect(Collectors.groupingBy(Pair::getLeft));
    }

    /**
     * Creates a FHIR {@link Bundle} for the given {@link Map} entry, generated by the {@link SeedProcessor#extractProviderMap()} function.
     * For the {@link Bundle}, the first entry is the {@link Practitioner} resource, and all subsequent entries are {@link Patient} resources
     *
     * @param entry - {@link Map#entry(Object, Object)} representing the providerID and a {@link List} of {@link Pair} objects containing both the providerID and the patientID
     * @return - {@link Bundle} representing the {@link Patient} resources attributed to the {@link Practitioner}
     */
    public Bundle generateRosterBundle(Map.Entry<String, List<Pair<String, String>>> entry) {
        final Bundle bundle = new Bundle();

        bundle.setId(new IdType("Roster", "12345"));
        bundle.setType(Bundle.BundleType.COLLECTION);

        // Create the provider with the necessary fields
        final Practitioner practitioner = new Practitioner();
        practitioner.addIdentifier().setValue(entry.getKey());
        practitioner.addName().addGiven("Test").setFamily("Provider");
        bundle.addEntry().setResource(practitioner).setFullUrl("http://something.gov/" + practitioner.getIdentifierFirstRep().getValue());

        entry.getValue()
                .forEach((value) -> {
                    // Add some random values to the patient
                    final Patient patient = new Patient();
                    patient.addIdentifier().setValue(value.getRight());
                    patient.addName().addGiven("Tester " + rand.nextInt()).setFamily("Patient");
                    patient.setBirthDate(new GregorianCalendar(2019, Calendar.MARCH, 1).getTime());
                    final Bundle.BundleEntryComponent component = new Bundle.BundleEntryComponent();
                    component.setResource(patient);
                    component.setFullUrl("http://something.gov/" + patient.getIdentifierFirstRep().getValue());
                    bundle.addEntry(component);
                });

        return bundle;
    }
}
