package eu.efti.datatools.schema

import java.io.File
import java.io.InputStream
import java.net.URL

/**
 * Locates eFTI xsd files under some root. The schema files are provided by the user of this library, they are not
 * shipped with it.
 *
 * Implementations must be immutable and must implement `equals`/`hashCode`, because they are used as cache keys by
 * [EftiSchemas].
 */
interface XsdSource {
    /**
     * Human readable description of the root, used in error messages.
     */
    val description: String

    /**
     * Open a stream to the xsd file at the given root relative path, for example `consignment-common.xsd` or
     * `types/types.xsd`.
     * @throws EftiSchemaException if the file cannot be found
     */
    fun openStream(relativePath: String): InputStream

    /**
     * Resolve the given root relative path into a url. The url is used as the base url when resolving `xsd:import`
     * of the referenced document, so it must point at the actual location of the file.
     * @throws EftiSchemaException if the file cannot be found
     */
    fun urlFor(relativePath: String): URL
}

/**
 * Locates xsd files on the classpath under the given root, for example `/` or `/efti-xsd`. This is the intended way
 * of providing schemas for library users: place the schema files on the classpath and point this source at their
 * root directory.
 */
class ClasspathXsdSource
@JvmOverloads
constructor(
    rootPath: String = "/",
    private val classLoader: ClassLoader = defaultClassLoader(),
) : XsdSource {
    private val normalizedRoot: String = normalizeRoot(rootPath)

    override val description: String get() = "classpath root \"/$normalizedRoot\""

    override fun equals(other: Any?): Boolean = this === other ||
        (other is ClasspathXsdSource && normalizedRoot == other.normalizedRoot && classLoader == other.classLoader)

    override fun hashCode(): Int = 31 * normalizedRoot.hashCode() + classLoader.hashCode()

    override fun toString(): String = "ClasspathXsdSource($description)"

    override fun openStream(relativePath: String): InputStream =
        classLoader.getResourceAsStream(resourceName(relativePath))
            ?: throw notFound(relativePath)

    override fun urlFor(relativePath: String): URL =
        classLoader.getResource(resourceName(relativePath))
            ?: throw notFound(relativePath)

    private fun resourceName(relativePath: String): String =
        normalizedRoot.let { root ->
            val path = relativePath.trim('/')
            if (root.isEmpty()) path else "$root/$path"
        }

    private fun notFound(relativePath: String) = EftiSchemaException(
        "Schema file \"$relativePath\" was not found on the classpath as \"${resourceName(relativePath)}\"." +
            " Make sure that a complete set of eFTI xsd files is available on the classpath under $description.",
    )

    companion object {
        private fun defaultClassLoader(): ClassLoader =
            Thread.currentThread().contextClassLoader
                ?: checkNotNull(ClasspathXsdSource::class.java.classLoader) { "No class loader available" }

        private fun normalizeRoot(rootPath: String): String = rootPath.trim().trim('/')
    }
}

/**
 * Locates xsd files in a directory of the local file system. This is the intended way of providing schemas for the
 * command line application: unzip a complete set of eFTI xsd files somewhere and point this source at the root
 * directory.
 */
class DirectoryXsdSource(directory: File) : XsdSource {
    private val root: File = directory.absoluteFile.normalize()

    init {
        if (!root.isDirectory) {
            throw EftiSchemaException(
                "Schema directory \"$root\" does not exist or is not a directory. It should contain a complete set" +
                    " of eFTI xsd files, for example \"${EftiSchemaId.CONSIGNMENT_COMMON.mainXsdPath}\" and the" +
                    " files it imports.",
            )
        }
    }

    override val description: String get() = "directory \"$root\""

    override fun equals(other: Any?): Boolean = this === other || (other is DirectoryXsdSource && root == other.root)

    override fun hashCode(): Int = root.hashCode()

    override fun toString(): String = "DirectoryXsdSource($description)"

    override fun openStream(relativePath: String): InputStream = resolve(relativePath).inputStream()

    override fun urlFor(relativePath: String): URL = resolve(relativePath).toURI().toURL()

    private fun resolve(relativePath: String): File {
        val file = File(root, relativePath.trim('/')).normalize()
        if (!file.isFile) {
            throw EftiSchemaException(
                "Schema file \"$relativePath\" was not found at \"$file\". Make sure that a complete set of eFTI" +
                    " xsd files is available in $description.",
            )
        }
        return file
    }
}
