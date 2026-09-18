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
     * Parsed representation of the schema.
     *
     * Note that this carries subset annotations only for schemas that declare them inline, see [subsetAwareSchema].
     *
     * This is a low level accessor, most users should not need it.
     */
    val xmlSchema: XmlSchemaElement = readXmlSchema(id.mainXsdPath, id.rootElement)

    /**
     * Schema for xml validation.
     */
    val javaSchema: Schema = readJavaSchema()

    /**
     * The parsed schema with subset annotations resolved.
     *
     * For most schemas this is [xmlSchema] itself, because they declare their subsets inline. The v1 message schemas
     * declare none, so their subsets are read from a separate schema and matched by `eFTI_ID`, see
     * [EftiSchemaId.subsetSource].
     *
     * Reading the separate subset schema is expensive, so it is done only when subsets are actually used. Populating
     * documents never needs it.
     *
     * This is a low level accessor, most users should not need it.
     */
    val subsetAwareSchema: XmlSchemaElement by lazy {
        when (val subsetSource = id.subsetSource) {
            null -> xmlSchema
            else -> withSubsetsFrom(subsetSource)
        }
    }

    /**
     * All subset ids that are declared on the direct children of the document element of the schema.
     *
     * This is a low level accessor, most users should not need it.
     */
    val subsetIds: Set<SubsetId> by lazy {
        subsetAwareSchema.children.flatMap(XmlSchemaElement::subsets).toSet()
    }

    /**
     * Create a copy of the given document and drop all elements that are not included in the given subsets. The
     * subset ids are not validated.
     *
     * @param doc document of this schema
     * @param subsets set of subsets to keep
     * @return new document containing only elements that are included in the given subsets
     * @throws IllegalArgumentException if `doc` does not conform to this schema
     * @throws UnsupportedOperationException if this schema does not declare subsets
     */
    fun filterSubsets(doc: Document, subsets: Set<SubsetId>): Document {
        XmlUtil.validate(doc, javaSchema)?.let { error ->
            throw IllegalArgumentException("Input document is not valid: $error")
        }

        return XmlUtil.clone(doc).also { cloned ->
            SubsetUtil.dropNodesNotInSubsets(subsets, subsetAwareSchema, cloned.firstChild)
            XmlUtil.validate(cloned, javaSchema)
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

    /**
     * Read the subset schema and copy its subsets onto the elements of [xmlSchema], matching the two by `eFTI_ID`.
     */
    private fun withSubsetsFrom(subsetSource: SubsetSource): XmlSchemaElement = SubsetOverlay.apply(
        target = xmlSchema,
        subsetSchema = readXmlSchema(subsetSource.xsdPath, subsetSource.rootElement),
        subsetSchemaDescription = """"${subsetSource.xsdPath}" in ${source.description}""",
        targetDescription = """"${id.mainXsdPath}"""",
    )

    private fun readXmlSchema(xsdPath: String, rootElement: XmlSchemaElement.XmlName): XmlSchemaElement {
        val typeSystem = try {
            compileXsd(xsdPath)
        } catch (e: XmlException) {
            throw EftiSchemaException(schemaReadErrorMessage(e), e)
        }

        return try {
            XmlSchemaParser.parse(typeSystem, rootElement)
        } catch (e: IllegalStateException) {
            throw EftiSchemaException(
                """
                   Schema file "$xsdPath" in ${source.description} does not declare the expected document 
                   element "${rootElement.localPart}" in namespace "${rootElement.namespaceURI}". Please check that 
                   the provided schema files are eFTI schemas of a supported version.
                """.trimIndent(),
                e,
            )
        }
    }

    private fun compileXsd(mainXsdPath: String): SchemaTypeSystem {
        // XmlBeans parses the main xsd from a stream, so it has no base url to resolve xsd:import against and the
        // system ids it asks for are the plain schema locations. They are relative to the main xsd, which is not
        // necessarily at the root of the source, for example "FTI010/FTI010s.xsd".
        val baseDirectory = mainXsdPath.substringBeforeLast('/', "")

        // When reading schema from input stream, XmlBeans will try to load referenced schemas (xsd:import) over
        // network by default. Let's define an entity resolver that resolves system ids of referenced schemas
        // into input streams of the source.
        val xmlOptions = XmlOptions().also {
            it.setEntityResolver { _, systemId ->
                InputSource(source.openStream(toRelativePath(systemId, baseDirectory)))
            }
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

        private fun toRelativePath(systemId: String, baseDirectory: String): String {
            val path = when {
                // IDE tooling may resolve imports into this form.
                systemId.startsWith(LOCAL_PROJECT_PREFIX) -> systemId.removePrefix(LOCAL_PROJECT_PREFIX)

                else -> systemId
            }

            // Schema locations are relative to the importing xsd. Only prefix paths that are actually relative, so
            // that an absolute path or some other url is still passed through unchanged.
            return if (baseDirectory.isEmpty() || path.startsWith("/") || path.contains("://")) {
                path
            } else {
                "$baseDirectory/$path"
            }
        }
    }
}
