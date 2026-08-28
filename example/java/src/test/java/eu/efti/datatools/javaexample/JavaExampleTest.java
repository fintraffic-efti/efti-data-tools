package eu.efti.datatools.javaexample;

import eu.efti.datatools.populate.EftiDomPopulator;
import eu.efti.datatools.populate.RepeatablePopulateMode;
import kotlin.text.Charsets;
import org.junit.jupiter.api.Test;
import org.w3c.dom.Document;
import org.w3c.dom.bootstrap.DOMImplementationRegistry;
import org.w3c.dom.ls.DOMImplementationLS;
import org.xml.sax.SAXException;

import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import java.io.*;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertNotEquals;

public class JavaExampleTest {
    @Test
    public void shouldFilterSubsetsOnPopulatedDocument() {
        var populator = new EftiDomPopulator(JavaExample.COMMON_SCHEMA, 1234, RepeatablePopulateMode.MINIMUM_ONE);
        var originalDoc = populator.populate();

        var filteredDoc = JavaExample.filterCommonSubsets(originalDoc, Set.of("FI01", "FI02"));

        var originalXml = serializeToString(originalDoc);
        var filteredXml = serializeToString(filteredDoc);

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

        var formattedOriginalXml = serializeToString(originalDoc);
        var filteredXml = serializeToString(filteredDoc);

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

    private static String serializeToString(Document doc) {
        try {
            var registry = DOMImplementationRegistry.newInstance();
            var domImplLS = (DOMImplementationLS) registry.getDOMImplementation("LS");

            var lsSerializer = domImplLS.createLSSerializer();
            var domConfig = lsSerializer.getDomConfig();
            domConfig.setParameter("format-pretty-print", true);

            var byteArrayOutputStream = new ByteArrayOutputStream();
            var lsOutput = domImplLS.createLSOutput();
            lsOutput.setEncoding("UTF-8");
            lsOutput.setByteStream(byteArrayOutputStream);

            lsSerializer.write(doc, lsOutput);
            return byteArrayOutputStream.toString(Charsets.UTF_8);
        } catch (ClassNotFoundException | InstantiationException | IllegalAccessException e) {
            throw new RuntimeException(e);
        }
    }

}

