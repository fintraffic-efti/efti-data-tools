package eu.efti.datatools.schema

import java.io.File
import javax.xml.validation.Schema

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

    val schemas: EftiSchemas by lazy { EftiSchemas.fromDirectory(xsdDirectory) }

    val consignmentCommonSchema: XmlSchemaElement get() = schemas.xmlSchema(EftiSchemaId.CONSIGNMENT_COMMON)

    val consignmentIdentifierSchema: XmlSchemaElement get() = schemas.xmlSchema(EftiSchemaId.CONSIGNMENT_IDENTIFIER)

    val javaCommonSchema: Schema get() = schemas.javaSchema(EftiSchemaId.CONSIGNMENT_COMMON)

    val javaIdentifiersSchema: Schema get() = schemas.javaSchema(EftiSchemaId.CONSIGNMENT_IDENTIFIER)
}
