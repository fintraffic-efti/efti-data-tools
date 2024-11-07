package eu.efti.datatools.app

import com.beust.jcommander.*
import eu.efti.datatools.populate.EftiDomPopulator
import eu.efti.datatools.populate.EftiDomPopulator.TextContentOverride
import eu.efti.datatools.populate.RepeatablePopulateMode
import eu.efti.datatools.populate.SchemaConversion.commonToIdentifiers
import eu.efti.datatools.schema.EftiSchemas
import eu.efti.datatools.schema.XmlUtil
import eu.efti.datatools.schema.XmlUtil.serializeToString
import org.w3c.dom.Document
import java.io.File
import javax.xml.validation.Schema
import kotlin.system.exitProcess

class TextContentOverrideConverter : IStringConverter<TextContentOverride> {
    override fun convert(raw: String): TextContentOverride {
        val parts = raw.split(XPATH_VALUE_SEPARATOR)
        if (parts.size != 2) {
            throw ParameterException(""""$raw" should be of the form: <xpath-expression>$XPATH_VALUE_SEPARATOR<value>""")
        }
        val (xpathString, valueString) = parts.map(String::trim)

        val xpath = TextContentOverride.XPathRawAndCompiled.tryToParse(xpathString)
            ?: throw ParameterException("Invalid xpath: $raw")

        return TextContentOverride(xpath, valueString)
    }

    companion object {
        private const val XPATH_VALUE_SEPARATOR = ":="
    }
}

class TextContentOverrideValidator : IParameterValidator {
    override fun validate(name: String, value: String) {
        try {
            TextContentOverrideConverter().convert(value)
        } catch (e: ParameterException) {
            throw ParameterException("Parameter $name: ${e.message}")
        }
    }
}

enum class SchemaOption {
    both, common, identifier
}

class Args {
    @Parameter(names = ["--help", "-h"], help = true, description = "Print this help.")
    var help = false

    @Parameter(
        names = ["--seed", "-s"],
        description = "Seed to use, by default a random seed is used. Identical seeds should produce identical documents for a given version of the application."
    )
    var seed: Long? = null

    @Parameter(
        names = ["--schema", "-x"],
        description = "Schema to use"
    )
    var schema: SchemaOption = SchemaOption.common

    @Parameter(
        names = ["--repeatable-mode", "-r"],
        description = "How many instances of a repeatable element should be generated"
    )
    var repeatableMode: RepeatablePopulateMode = RepeatablePopulateMode.MINIMUM_ONE

    @Parameter(
        names = ["--text-overrides", "-t"],
        converter = TextContentOverrideConverter::class,
        validateWith = [TextContentOverrideValidator::class],
        description = """Override to apply to the populated document. The format is "<xpath-expression>:=<value>". Expression can use local xml names, namespaces can be ignored. If multiple instances of this parameter are defined, then each override will be applied in the given order."""
    )
    var textOverrides: List<TextContentOverride> = emptyList()

    @Parameter(names = ["--output", "-o", "-oc"], required = false, description = "Output file for common.")
    var pathCommon: String? = null

    @Parameter(names = ["--output-identifiers", "-oi"], required = false, description = "Output file for identifiers.")
    var pathIdentifiers: String? = null

    @Parameter(names = ["--overwrite", "-w"], required = false, description = "Overwrite existing document.")
    var overwrite: Boolean = false

    @Parameter(names = ["--pretty", "-p"], required = false, description = "Pretty print.")
    var pretty: Boolean = true
}

fun main(argv: Array<String>) {
    val args = Args()
    val parser = JCommander.newBuilder()
        .addObject(args)
        .build()
    try {
        parser.parse(*argv)
    } catch (e: ParameterException) {
        System.err.println(e.message)
        exitProcess(1)
    }
    if (args.seed == null) {
        args.seed = randomShortSeed()
    }
    if (args.pathCommon == null && args.schema in setOf(SchemaOption.both, SchemaOption.common)) {
        args.pathCommon = "consignment-${args.seed}-common.xml"
    }
    if (args.pathIdentifiers == null && args.schema in setOf(SchemaOption.both, SchemaOption.identifier)) {
        args.pathIdentifiers = "consignment-${args.seed}-identifiers.xml"
    }

    if (args.help) {
        parser.usage()
    } else {

        println("Generating with:")
        println(
            listOf(
                "schema" to args.schema,
                "seed" to args.seed,
                "repeatable mode" to args.repeatableMode.name,
                "overrides" to args.textOverrides.map { """Set "${it.xpath.raw}" to "${it.value}"""" },
                "output common" to args.pathCommon,
                "output identifiers" to args.pathIdentifiers,
                "overwrite" to args.overwrite,
                "pretty" to args.pretty,
            )
                .filter { it.second != null }
                .joinToString("\n") { (label, value) -> """  * $label: $value""" }
        )

        val fileCommon = args.pathCommon?.let(::File)
        val fileIdentifiers = args.pathIdentifiers?.let(::File)
        if (!args.overwrite) {
            if (fileCommon?.exists() == true) {
                println("Output file ${args.pathCommon} already exists")
                exitProcess(1)
            }
            if (fileIdentifiers?.exists() == true) {
                println("Output file ${args.pathIdentifiers} already exists")
                exitProcess(1)
            }
        }

        val doc = EftiDomPopulator(checkNotNull(args.seed), args.repeatableMode)
            .populate(
                schema = when (args.schema) {
                    SchemaOption.both -> EftiSchemas.readConsignmentCommonSchema()
                    SchemaOption.common -> EftiSchemas.readConsignmentCommonSchema()
                    SchemaOption.identifier -> EftiSchemas.readConsignmentIdentifiersSchema()
                },
                overrides = args.textOverrides,
                namespaceAware = false,
            )

        val validateAndWrite = documentValidatorAndWriter(args.pretty)

        when (args.schema) {
            SchemaOption.both -> {
                val identifiers = commonToIdentifiers(doc)
                validateAndWrite(EftiSchemas.javaCommonSchema, doc, checkNotNull(fileCommon))
                validateAndWrite(EftiSchemas.javaIdentifiersSchema, identifiers, checkNotNull(fileIdentifiers))
            }

            SchemaOption.common -> {
                validateAndWrite(EftiSchemas.javaCommonSchema, doc, checkNotNull(fileCommon))
            }

            SchemaOption.identifier -> {
                validateAndWrite(EftiSchemas.javaIdentifiersSchema, doc, checkNotNull(fileIdentifiers))
            }
        }
    }
}

private fun documentValidatorAndWriter(prettyPrint: Boolean): (schema: Schema, doc: Document, file: File) -> Unit =
    { schema, doc, file ->
        XmlUtil.validate(doc, schema)?.also {
            throw IllegalStateException("Application produced an invalid document. Please report the parameters and the this error message to the maintainers. Validation error: $it")
        }
        file.printWriter().use { out ->
            out.print(serializeToString(doc, prettyPrint = prettyPrint))
        }
    }

private fun randomShortSeed() =
    System.currentTimeMillis().let { number -> number - (number / 100000 * 100000) }