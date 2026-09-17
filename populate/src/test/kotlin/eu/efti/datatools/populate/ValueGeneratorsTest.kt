package eu.efti.datatools.populate

import eu.efti.datatools.schema.EftiSchema
import eu.efti.datatools.schema.EftiSchemaId
import eu.efti.datatools.schema.TestSchemas
import eu.efti.datatools.schema.XmlSchemaElement
import eu.efti.datatools.schema.XmlSchemaElement.XmlName
import org.hamcrest.MatcherAssert.assertThat
import org.hamcrest.Matchers.containsString
import org.hamcrest.Matchers.empty
import org.hamcrest.Matchers.sameInstance
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class ValueGeneratorsTest {
    private val generators = ValueGenerators(EftiValueGeneratorFactory(42))

    @Test
    fun `should select generators by the document element of the schema`() {
        assertThat(
            generators.forRootElement(EftiSchemaId.CONSIGNMENT_COMMON.rootElement),
            sameInstance(generators.v0),
        )
        assertThat(
            generators.forRootElement(EftiSchemaId.CONSIGNMENT_IDENTIFIER.rootElement),
            sameInstance(generators.v0),
        )
        assertThat(
            generators.forRootElement(EftiSchemaId.CONSIGNMENT_COMMON_V1.rootElement),
            sameInstance(generators.v1),
        )
    }

    /**
     * Selection is a `when` over values, which the compiler cannot check for exhaustiveness the way it can check a
     * `when` over an enum. This test is the substitute: adding a schema without generators must fail the build.
     */
    @Test
    fun `should have generators for every schema`() {
        EftiSchemaId.entries.forEach { schemaId ->
            generators.forRootElement(schemaId.rootElement)
        }
    }

    @Test
    fun `should distinguish schemas by namespace and not only by local name`() {
        // Both v0 schemas use the local name "consignment", so the namespace is what tells them apart. A document
        // element with a known local name but an unknown namespace must not silently get any generators.
        val exception = assertThrows<IllegalArgumentException> {
            generators.forRootElement(XmlName("http://example.com/unknown", "consignment"))
        }
        assertThat(exception.message, containsString("consignment"))
        assertThat(exception.message, containsString("http://example.com/unknown"))
    }

    @Test
    fun `should fail with a helpful message for an unknown document element`() {
        val exception = assertThrows<IllegalArgumentException> {
            generators.forRootElement(XmlName("http://example.com/unknown", "unknownElement"))
        }
        assertThat(exception.message, containsString("No value generators are defined"))
        assertThat(exception.message, containsString("unknownElement"))
    }

    @Test
    fun `should not share generator rules between the schema versions`() {
        val shared = generators.v0.rules.map { it.second }.intersect(generators.v1.rules.map { it.second }.toSet())
        assertThat(shared.toList(), empty())
    }

    @Test
    fun `every v0 rule should be used when populating a v0 schema`() {
        assertThat(unusedRulesOf(generators.v0, TestSchemas.common, TestSchemas.identifier), empty())
    }

    @Test
    fun `every v1 rule should be used when populating a v1 schema`() {
        assertThat(unusedRulesOf(generators.v1, TestSchemas.commonV1), empty())
    }

    /**
     * Matchers of the rules that no element or attribute of the given schemas selects. Such a rule is either
     * obsolete or in the list of the wrong schema version, and would otherwise sit unnoticed.
     */
    private fun unusedRulesOf(
        set: ValueGeneratorSet,
        vararg schemas: EftiSchema,
    ): List<EftiDomPopulator.SchemaValueMatcher> {
        val used = schemas
            .flatMap { schema -> valueTypesOf(schema.xmlSchema) }
            .mapNotNull { (name, type) -> set.findRule(name, type)?.first }
            .toSet()
        return set.rules.map { it.first }.filterNot { it in used }
    }

    /**
     * Name and type of everything in the schema that gets a generated value: text content elements and their
     * attributes.
     */
    private fun valueTypesOf(
        element: XmlSchemaElement,
    ): List<Pair<XmlName, XmlSchemaElement.XmlType>> =
        element.type.attributes
            .filter { it.type.isTextContentType }
            .map { it.name to it.type }
            .plus(
                if (element.type.isTextContentType) listOf(element.name to element.type) else emptyList(),
            )
            .plus(element.children.flatMap(::valueTypesOf))
}
