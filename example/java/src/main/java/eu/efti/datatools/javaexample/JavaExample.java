package eu.efti.datatools.javaexample;

import eu.efti.datatools.schema.SubsetUtil;
import eu.efti.datatools.schema.XmlSchemaElement;
import org.w3c.dom.Document;

import java.util.Set;
import java.util.stream.Collectors;

public class JavaExample {
    public static Document filterCommonSubsets(Document doc, Set<String> subsets) {
        return SubsetUtil.filterCommonSubsets(
                doc,
                subsets.stream().map(XmlSchemaElement.SubsetId::new).collect(Collectors.toSet()));
    }
}
