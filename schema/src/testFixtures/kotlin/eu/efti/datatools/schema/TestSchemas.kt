package eu.efti.datatools.schema

import java.io.File

/**
 * Schemas for tests of this repository. The xsd files are not packaged into the artifacts anymore, so tests read
 * them from the `xsd` directory of the repository. The directory is passed in by the build, see
 * `data-tools.kotlin-conventions.gradle.kts`.
 */
object TestSchemas {
    val xsdDirectory: File = File(
        checkNotNull(System.getProperty("eu.efti.datatools.test.xsdDir")) {
            "System property eu.efti.datatools.test.xsdDir must point at the xsd directory of the repository"
        },
    )

    val common: EftiSchema by lazy { EftiSchema.fromDirectory(EftiSchemaId.CONSIGNMENT_COMMON, xsdDirectory) }

    val identifier: EftiSchema by lazy { EftiSchema.fromDirectory(EftiSchemaId.CONSIGNMENT_IDENTIFIER, xsdDirectory) }
}
