package eu.efti.datatools.javaexample;

import eu.efti.datatools.populate.EftiDomPopulator;
import eu.efti.datatools.populate.RepeatablePopulateMode;
import eu.efti.datatools.schema.EftiSchemas;
import eu.efti.datatools.schema.XmlUtil;
import org.junit.jupiter.api.Test;
import org.w3c.dom.Document;
import org.xml.sax.SAXException;

import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import java.io.*;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertNotEquals;

public class JavaExampleTest {
    @Test
    public void shouldFilterSubsetsOnPopulatedDocument() {
        var populator = new EftiDomPopulator(1234, RepeatablePopulateMode.MINIMUM_ONE);
        var originalDoc = populator.populate(EftiSchemas.getConsignmentCommonSchema(), List.of(), true);

        var filteredDoc = JavaExample.filterCommonSubsets(originalDoc, Set.of("FI01", "FI02"));

        var originalXml = XmlUtil.serializeToString(originalDoc, true);
        var filteredXml = XmlUtil.serializeToString(filteredDoc, true);

        System.out.println("### Original");
        System.out.println(originalXml);
        System.out.println("### Filtered");
        System.out.println(filteredXml);

        assertNotEquals(originalXml, filteredXml);
    }

    @Test
    public void shouldFilterSubsetsOnExampleDocument() {
        var originalXml = readXml("../../xsd/examples/consignment-common.xml");
        var originalDoc = deserializeToDocument(originalXml);

        var filteredDoc = JavaExample.filterCommonSubsets(originalDoc, Set.of("FI01", "FI02"));

        var formattedOriginalXml = XmlUtil.serializeToString(originalDoc, true);
        var filteredXml = XmlUtil.serializeToString(filteredDoc, true);

        System.out.println("### Original");
        System.out.println(formattedOriginalXml);
        System.out.println("### Filtered");
        System.out.println(filteredXml);

        assertNotEquals(formattedOriginalXml, filteredXml);
    }

    private static String readXml(String path) {
        var file = new File(path);
        try (var resourceInputStream = file.toURI().toURL().openStream()) {
            if (resourceInputStream == null) {
                throw new IllegalArgumentException("Resource not found: " + path);
            }
            var reader = new BufferedReader(new InputStreamReader(resourceInputStream));
            return reader.lines().collect(Collectors.joining(""));
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private static Document deserializeToDocument(String xml) {
        try {
            var factory = DocumentBuilderFactory.newInstance();
            factory.setNamespaceAware(true);
            var builder = factory.newDocumentBuilder();
            return builder.parse(new ByteArrayInputStream(xml.getBytes()));
        } catch (ParserConfigurationException | IOException | SAXException e) {
            throw new RuntimeException(e);
        }
    }
}
