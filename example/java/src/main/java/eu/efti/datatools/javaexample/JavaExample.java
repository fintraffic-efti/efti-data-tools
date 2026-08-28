package eu.efti.datatools.javaexample;

import eu.efti.datatools.schema.EftiSchemas;
import eu.efti.datatools.schema.SubsetUtil;
import eu.efti.datatools.schema.XmlSchemaElement;
import org.w3c.dom.Document;

import java.util.Set;
import java.util.stream.Collectors;

public class JavaExample {
    /**
     * The eFTI xsd files are not shipped with the libraries, so this example ships its own copy of them on the
     * classpath under /efti-xsd, see build.gradle.kts.
     * <p>
     * Compiling a schema is expensive, but repeated calls to EftiSchemas.fromClasspath with the same root return the
     * same instance, and each instance compiles a given schema at most once.
     */
    public static final EftiSchemas SCHEMAS = EftiSchemas.fromClasspath("/efti-xsd");

    public static Document filterCommonSubsets(Document doc, Set<String> subsets) {
        return SubsetUtil.filterCommonSubsets(
                SCHEMAS,
                doc,
                subsets.stream().map(XmlSchemaElement.SubsetId::new).collect(Collectors.toSet()));
    }
}
