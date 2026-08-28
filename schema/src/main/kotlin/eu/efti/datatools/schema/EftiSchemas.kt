package eu.efti.datatools.schema

import org.apache.xmlbeans.SchemaTypeSystem
import org.apache.xmlbeans.XmlBeans
import org.apache.xmlbeans.XmlException
import org.apache.xmlbeans.XmlObject
import org.apache.xmlbeans.XmlOptions
import org.xml.sax.InputSource
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import javax.xml.XMLConstants
import javax.xml.validation.Schema
import javax.xml.validation.SchemaFactory

/**
 * Provides access to the eFTI consignment schemas that the user has made available through an [XsdSource].
 *
 * The schema files are not shipped with this library. Typical usage:
 * * library: place a complete set of eFTI xsd files on the classpath and use [fromClasspath]
 * * command line application: unzip a complete set of eFTI xsd files and use [fromDirectory]
 *
 * Schemas are compiled lazily and cached per instance, and instances themselves are cached per source, so compiling
 * a given schema happens at most once per source per JVM (unless the caches are cleared with [clearCache] or the
 * instance was created with [uncached]).
 */
class EftiSchemas private constructor(private val source: XsdSource) {
    private val xmlSchemas = ConcurrentHashMap<EftiSchemaId, XmlSchemaElement>()

    private val javaSchemas = ConcurrentHashMap<EftiSchemaId, Schema>()

    private val subsetIds = ConcurrentHashMap<EftiSchemaId, Set<XmlSchemaElement.SubsetId>>()

    /**
     * Parsed representation of the given schema, including subset annotations.
     */
    fun xmlSchema(id: EftiSchemaId): XmlSchemaElement = xmlSchemas.computeIfAbsent(id, ::readXmlSchema)

    /**
     * Schema of the given id for xml validation.
     */
    fun javaSchema(id: EftiSchemaId): Schema = javaSchemas.computeIfAbsent(id, ::readJavaSchema)

    /**
     * All subset ids that are declared on the direct children of the document element of the given schema.
     */
    fun subsetIds(id: EftiSchemaId): Set<XmlSchemaElement.SubsetId> = subsetIds.computeIfAbsent(id) {
        xmlSchema(it).children.flatMap(XmlSchemaElement::subsets).toSet()
    }

    override fun toString(): String = "EftiSchemas(${source.description})"

    private fun readJavaSchema(id: EftiSchemaId): Schema = try {
        SchemaFactory.newInstance(XMLConstants.W3C_XML_SCHEMA_NS_URI).newSchema(source.urlFor(id.mainXsdPath))
    } catch (e: org.xml.sax.SAXException) {
        throw EftiSchemaException(schemaReadErrorMessage(id, e), e)
    }

    private fun readXmlSchema(id: EftiSchemaId): XmlSchemaElement {
        val typeSystem = try {
            compileXsd(id.mainXsdPath)
        } catch (e: XmlException) {
            throw EftiSchemaException(schemaReadErrorMessage(id, e), e)
        }

        return try {
            XmlSchemaParser.parse(typeSystem, id.rootElement)
        } catch (e: IllegalStateException) {
            throw EftiSchemaException(
                "Schema file \"${id.mainXsdPath}\" in ${source.description} does not declare the expected document" +
                    " element \"${id.rootElement.localPart}\" in namespace \"${id.namespaceURI}\"." +
                    " Please check that the provided schema files are eFTI schemas of a supported version.",
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

    private fun schemaReadErrorMessage(id: EftiSchemaId, cause: Exception): String =
        "Failed to read schema \"${id.mainXsdPath}\" from ${source.description}." +
            " Please check that a complete set of eFTI xsd files, including the files imported by" +
            " \"${id.mainXsdPath}\", is available there. Cause: ${cause.message}"

    companion object {
        private val instances = ConcurrentHashMap<XsdSource, EftiSchemas>()

        /**
         * Schemas loaded from the classpath under the given root path, for example `/` or `/efti-xsd`.
         */
        @JvmStatic
        @JvmOverloads
        fun fromClasspath(rootPath: String = "/"): EftiSchemas = from(ClasspathXsdSource(rootPath))

        /**
         * Schemas loaded from the classpath of the given class loader, under the given root path.
         */
        @JvmStatic
        fun fromClasspath(rootPath: String, classLoader: ClassLoader): EftiSchemas =
            from(ClasspathXsdSource(rootPath, classLoader))

        /**
         * Schemas loaded from the given directory of the local file system.
         * @throws EftiSchemaException if the directory does not exist
         */
        @JvmStatic
        fun fromDirectory(directory: File): EftiSchemas = from(DirectoryXsdSource(directory))

        /**
         * Schemas loaded from the given source. Repeated calls with an equal source return the same instance.
         */
        @JvmStatic
        fun from(source: XsdSource): EftiSchemas = instances.computeIfAbsent(source, ::EftiSchemas)

        /**
         * Schemas loaded from the given source, bypassing the instance cache. Use this when the schema files may
         * have changed after they were first read.
         */
        @JvmStatic
        fun uncached(source: XsdSource): EftiSchemas = EftiSchemas(source)

        /**
         * Discard all cached instances. Schemas that are already held by the caller are not affected.
         */
        @JvmStatic
        fun clearCache() {
            instances.clear()
        }
    }
}

private const val LOCAL_PROJECT_PREFIX = "project://local/"

private fun toRelativePath(systemId: String): String = when {
    // IDE tooling may resolve imports into this form.
    systemId.startsWith(LOCAL_PROJECT_PREFIX) -> systemId.removePrefix(LOCAL_PROJECT_PREFIX)
    else -> systemId
}
