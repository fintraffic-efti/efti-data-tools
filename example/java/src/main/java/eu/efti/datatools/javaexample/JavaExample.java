package eu.efti.datatools.javaexample;

import eu.efti.datatools.schema.EftiSchema;
import eu.efti.datatools.schema.EftiSchemaId;
import eu.efti.datatools.schema.SubsetId;
import org.w3c.dom.Document;

import java.util.Set;
import java.util.stream.Collectors;

public class JavaExample {
    /**
     * The eFTI xsd files are not shipped with the libraries, so this example ships its own copy of them on the
     * classpath under /efti-xsd, see build.gradle.kts.
     * <p>
     * The schema is read and compiled when the instance is created. That is expensive and the library does not
     * cache instances, so hold on to the instances you need.
     */
    public static final EftiSchema COMMON_SCHEMA =
            EftiSchema.fromClasspath(EftiSchemaId.CONSIGNMENT_COMMON, "/efti-xsd");

    public static Document filterCommonSubsets(Document doc, Set<String> subsets) {
        return COMMON_SCHEMA.filterSubsets(
                doc,
                subsets.stream().map(SubsetId::new).collect(Collectors.toSet()));
    }
}
