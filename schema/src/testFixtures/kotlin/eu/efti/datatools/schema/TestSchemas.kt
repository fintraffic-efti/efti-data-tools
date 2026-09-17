package eu.efti.datatools.schema

import java.io.File

/**
 * Schemas for tests of this repository. The xsd files are not packaged into the artifacts anymore, so tests read
 * them from the `xsd/v0` and `xsd/v1` directories of the repository. The directories are passed in by the build, see
 * `data-tools.kotlin-conventions.gradle.kts`.
 */
object TestSchemas {
    val xsdDirectory: File = directoryFromSystemProperty("eu.efti.datatools.test.xsdDir")

    /**
     * Root directory of the v1 schemas, that is, the directory that contains the `FTI010` and `eFTI XM SubMap`
     * directories.
     */
    val xsdV1Directory: File = directoryFromSystemProperty("eu.efti.datatools.test.xsdV1Dir")

    val common: EftiSchema by lazy { EftiSchema.fromDirectory(EftiSchemaId.CONSIGNMENT_COMMON, xsdDirectory) }

    val identifier: EftiSchema by lazy { EftiSchema.fromDirectory(EftiSchemaId.CONSIGNMENT_IDENTIFIER, xsdDirectory) }

    val commonV1: EftiSchema by lazy {
        EftiSchema.fromDirectory(EftiSchemaId.CONSIGNMENT_COMMON_V1, xsdV1Directory)
    }

    private fun directoryFromSystemProperty(name: String): File = File(
        checkNotNull(System.getProperty(name)) {
            "System property $name must point at an xsd directory of the repository"
        },
    )
}
