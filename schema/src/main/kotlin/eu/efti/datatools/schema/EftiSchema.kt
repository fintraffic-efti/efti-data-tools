package eu.efti.datatools.schema

import org.apache.xmlbeans.SchemaTypeSystem
import org.apache.xmlbeans.XmlBeans
import org.apache.xmlbeans.XmlException
import org.apache.xmlbeans.XmlObject
import org.apache.xmlbeans.XmlOptions
import org.w3c.dom.Document
import org.xml.sax.InputSource
import java.io.File
import javax.xml.XMLConstants
import javax.xml.validation.Schema
import javax.xml.validation.SchemaFactory

/**
 * A single eFTI consignment schema, read from the xsd files that the user has made available through an [XsdSource].
 *
 * The schema is read and compiled when the instance is created, so problems with the provided xsd files are
 * reported immediately as [EftiSchemaException]. Compiling a schema is expensive and instances are not cached by
 * this library, so the user should hold on to the instances they need, for example in a `static final` field or in
 * a singleton bean.
 *
 * @param source location of the xsd files
 * @param id the schema to read from [source]
 * @throws EftiSchemaException if the schema cannot be read from the given source
 */
class EftiSchema(val source: XsdSource, val id: EftiSchemaId) {
    /**
     * Parsed representation of the schema, including subset annotations.
     *
     * This is a low level accessor, most users should not need it.
     */
    val xmlSchema: XmlSchemaElement = readXmlSchema()

    /**
     * Schema for xml validation.
     */
    val javaSchema: Schema = readJavaSchema()

    /**
     * All subset ids that are declared on the direct children of the document element of the schema.
     *
     * This is a low level accessor, most users should not need it.
     */
    val subsetIds: Set<SubsetId> = xmlSchema.children.flatMap(XmlSchemaElement::subsets).toSet()

    /**
     * Create a copy of the given document and drop all elements that are not included in the given subsets. The
     * subset ids are not validated.
     *
     * Subset filtering requires a schema that declares eFTI subsets in its annotations. Not all eFTI schemas do,
     * see [EftiSchemaId.supportsSubsets].
     *
     * @param doc document of this schema
     * @param subsets set of subsets to keep
     * @return new document containing only elements that are included in the given subsets
     * @throws IllegalArgumentException if `doc` does not conform to this schema
     * @throws UnsupportedOperationException if this schema does not declare subsets
     */
    fun filterSubsets(doc: Document, subsets: Set<SubsetId>): Document {
        requireSubsetSupport()

        XmlUtil.validate(doc, javaSchema)?.let { error ->
            throw IllegalArgumentException("Input document is not valid: $error")
        }

        return XmlUtil.clone(doc).also { cloned ->
            SubsetUtil.dropNodesNotInSubsets(subsets, xmlSchema, cloned.firstChild)
            XmlUtil.validate(cloned, javaSchema)
        }
    }

    /**
     * True if subset filtering can be used with this schema, that is, the schema declares eFTI subsets in its
     * annotations.
     */
    val supportsSubsets: Boolean get() = id.supportsSubsets && subsetIds.isNotEmpty()

    /**
     * @throws UnsupportedOperationException if this schema does not support subset filtering
     */
    private fun requireSubsetSupport() {
        if (!supportsSubsets) {
            throw UnsupportedOperationException(
                """
                   Schema $id (eFTI ${id.version}) does not declare eFTI subsets, so subset filtering is not 
                   available for it. Subset filtering is currently supported for the eFTI 
                   ${EftiSchemaVersion.V0} schemas only.
                """.trimIndent(),
            )
        }
    }

    /**
     * @param subsetId subset id to look for
     * @return true if this schema declares the given subset
     */
    fun hasSubset(subsetId: SubsetId): Boolean = subsetId in subsetIds

    /**
     * Create a copy of the given document and drop all elements that this schema does not declare.
     *
     * Neither the input nor the result is validated: the input is expected to contain elements that the schema does
     * not declare, and the result can still be invalid, for example if a required element is missing from the input.
     *
     * @param doc document to filter
     * @param namespaceAware if false, elements are matched by local name only, ignoring their namespace
     * @return new document containing only elements that this schema declares
     */
    @JvmOverloads
    fun dropNodesNotInSchema(doc: Document, namespaceAware: Boolean = true): Document =
        XmlUtil.clone(doc).also { cloned ->
            XmlUtil.dropNodesRecursively(
                schema = xmlSchema,
                node = cloned.firstChild,
                namespaceAware = namespaceAware,
            ) { _, maybeSchemaElement -> maybeSchemaElement == null }
        }

    override fun toString(): String = "EftiSchema($id, ${source.description})"

    private fun readJavaSchema(): Schema = try {
        SchemaFactory.newInstance(XMLConstants.W3C_XML_SCHEMA_NS_URI).newSchema(source.urlFor(id.mainXsdPath))
    } catch (e: org.xml.sax.SAXException) {
        throw EftiSchemaException(schemaReadErrorMessage(e), e)
    }

    private fun readXmlSchema(): XmlSchemaElement {
        val typeSystem = try {
            compileXsd(id.mainXsdPath)
        } catch (e: XmlException) {
            throw EftiSchemaException(schemaReadErrorMessage(e), e)
        }

        return try {
            XmlSchemaParser.parse(typeSystem, id.rootElement)
        } catch (e: IllegalStateException) {
            throw EftiSchemaException(
                """
                   Schema file "${id.mainXsdPath}" in ${source.description} does not declare the expected document 
                   element "${id.rootElement.localPart}" in namespace "${id.namespaceURI}". Please check that the 
                   provided schema files are eFTI schemas of a supported version.
                """.trimIndent(),
                e,
            )
        }
    }

    private fun compileXsd(mainXsdPath: String): SchemaTypeSystem {
        // When reading schema from input stream, XmlBeans will try to load referenced schemas (xsd:import) over
        // network by default. Let's define an entity resolver that resolves system ids of referenced schemas
        // into input streams of the source.
        val xmlOptions = XmlOptions().also {
            it.setEntityResolver { _, systemId -> InputSource(source.openStream(toRelativePath(systemId))) }
        }

        return source.openStream(mainXsdPath).use { mainXsd ->
            XmlBeans.compileXsd(
                arrayOf(XmlObject.Factory.parse(mainXsd, xmlOptions)),
                XmlBeans.getContextTypeLoader(),
                xmlOptions,
            )
        }
    }

    private fun schemaReadErrorMessage(cause: Exception): String =
        """
           Failed to read schema "${id.mainXsdPath}" from ${source.description}. Please check that a complete set 
           of eFTI xsd files, including the files imported by "${id.mainXsdPath}", is available there. Cause: 
           ${cause.message}
        """.trimIndent()

    companion object {
        /**
         * Schema read from the classpath under the given root path, for example `/` or `/efti-xsd`.
         * @throws EftiSchemaException if the schema cannot be read
         */
        @JvmStatic
        fun fromClasspath(id: EftiSchemaId, rootPath: String): EftiSchema =
            EftiSchema(ClasspathXsdSource(rootPath), id)

        /**
         * Schema read from the classpath of the given class loader, under the given root path.
         * @throws EftiSchemaException if the schema cannot be read
         */
        @JvmStatic
        fun fromClasspath(id: EftiSchemaId, rootPath: String, classLoader: ClassLoader): EftiSchema =
            EftiSchema(ClasspathXsdSource(rootPath, classLoader), id)

        /**
         * Schema read from the given directory of the local file system.
         * @throws EftiSchemaException if the directory does not exist or the schema cannot be read
         */
        @JvmStatic
        fun fromDirectory(id: EftiSchemaId, directory: File): EftiSchema =
            EftiSchema(DirectoryXsdSource(directory), id)

        private const val LOCAL_PROJECT_PREFIX = "project://local/"

        private fun toRelativePath(systemId: String): String = when {
            // IDE tooling may resolve imports into this form.
            systemId.startsWith(LOCAL_PROJECT_PREFIX) -> systemId.removePrefix(LOCAL_PROJECT_PREFIX)

            else -> systemId
        }
    }
}
