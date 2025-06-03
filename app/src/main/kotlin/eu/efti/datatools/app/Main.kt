package eu.efti.datatools.app

import com.beust.jcommander.IParameterValidator
import com.beust.jcommander.IStringConverter
import com.beust.jcommander.JCommander
import com.beust.jcommander.Parameter
import com.beust.jcommander.ParameterException
import com.beust.jcommander.Parameters
import eu.efti.datatools.populate.EftiDomPopulator
import eu.efti.datatools.populate.EftiDomPopulator.DeleteNodeOverride
import eu.efti.datatools.populate.EftiDomPopulator.TextContentOverride
import eu.efti.datatools.populate.EftiDomPopulator.XPathRawAndCompiled
import eu.efti.datatools.populate.RepeatablePopulateMode
import eu.efti.datatools.populate.SchemaConversion.commonToIdentifiers
import eu.efti.datatools.schema.EftiSchemas.consignmentCommonSchema
import eu.efti.datatools.schema.EftiSchemas.consignmentIdentifierSchema
import eu.efti.datatools.schema.EftiSchemas.javaCommonSchema
import eu.efti.datatools.schema.EftiSchemas.javaIdentifiersSchema
import eu.efti.datatools.schema.SubsetUtil.filterCommonSubsets
import eu.efti.datatools.schema.XmlSchemaElement.SubsetId
import eu.efti.datatools.schema.XmlUtil
import eu.efti.datatools.schema.XmlUtil.deserializeToDocument
import eu.efti.datatools.schema.XmlUtil.serializeToString
import org.w3c.dom.Document
import java.io.File
import java.io.FileInputStream
import java.io.InputStreamReader
import javax.xml.validation.Schema
import kotlin.system.exitProcess

class TextContentOverrideConverter : IStringConverter<TextContentOverride> {
    override fun convert(raw: String): TextContentOverride {
        val parts = raw.split(XPATH_VALUE_SEPARATOR)
        if (parts.size != 2) {
            throw ParameterException(""""$raw" should be of the form: <xpath-expression>$XPATH_VALUE_SEPARATOR<value>""")
        }
        val (xpathString, valueString) = parts.map(String::trim)

        val xpath = XPathRawAndCompiled.tryToParse(xpathString)
            ?: throw ParameterException("Invalid xpath: $raw")

        return TextContentOverride(xpath, valueString)
    }

    companion object {
        private const val XPATH_VALUE_SEPARATOR = ":="
    }
}

class DeleteNodeOverrideConverter : IStringConverter<DeleteNodeOverride> {
    override fun convert(raw: String): DeleteNodeOverride {
        val xpath = XPathRawAndCompiled.tryToParse(raw)
            ?: throw ParameterException("Invalid xpath: $raw")

        return DeleteNodeOverride(xpath)
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

class DeleteNodeOverrideValidator : IParameterValidator {
    override fun validate(name: String, value: String) {
        try {
            DeleteNodeOverrideConverter().convert(value)
        } catch (e: ParameterException) {
            throw ParameterException("Parameter $name: ${e.message}")
        }
    }
}

class CommandMain {
    @Parameter(names = ["--help", "-h"], help = true, description = "Print this help.")
    var help = false
}

abstract class CommonArgs {
    @Parameter(names = ["--overwrite", "-w"], required = false, description = "Overwrite existing documents.")
    var overwrite: Boolean = false

    @Parameter(names = ["--pretty", "-p"], required = false, description = "Pretty print.")
    var pretty: Boolean = true
}

@Parameters(commandDescription = "Filter subsets on consignment document")
class CommandFilter : CommonArgs() {
    @Parameter(names = ["--input", "-i"], required = true, description = "Input file.")
    var inputPath: String? = null

    @Parameter(names = ["--output", "-o"], required = false, description = "Output file.")
    var outputPath: String? = null

    @Parameter(
        names = ["--subset-id", "-s"],
        required = true,
        description = "List of subset ids to use for filtering. A document element is dropped if it is not included in any of the given subsets."
    )
    var subsetIds: List<String> = emptyList()
}

@Parameters(commandDescription = "Populate random documents")
class CommandPopulate : CommonArgs() {
    enum class SchemaOption {
        both, common, identifier
    }

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
        names = ["--delete-overrides", "-d"],
        converter = DeleteNodeOverrideConverter::class,
        validateWith = [DeleteNodeOverrideValidator::class],
        description = """Override to apply to the populated document. The format is "<xpath-expression>". Expression can use local xml names, namespaces can be ignored. If multiple instances of this parameter are defined, then each override will be applied in the given order."""
    )
    var deleteOverrides: List<DeleteNodeOverride> = emptyList()

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
}

fun main(argv: Array<String>) {
    val mainArgs = CommandMain()
    val filterArgs = CommandFilter()
    val populateArgs = CommandPopulate()
    val parser = JCommander.newBuilder()
        .addObject(mainArgs)
        .addCommand("filter", filterArgs)
        .addCommand("populate", populateArgs)
        .build()

    try {
        parser.parse(*argv)
    } catch (e: ParameterException) {
        System.err.println(e.message)
        exitProcess(1)
    }

    if (mainArgs.help) {
        parser.usage()
    } else if (parser.parsedCommand == "filter") {
        doFilter(filterArgs)
    } else if (parser.parsedCommand == "populate") {
        doPopulate(populateArgs)
    }
}

private fun doFilter(args: CommandFilter) {
    if (args.outputPath == null) {
        args.outputPath = "filtered.xml"
    }

    println("Filtering with:")
    println(
        listOf(
            "subsets" to args.subsetIds.joinToString(", "),
            "input" to args.inputPath,
            "output" to args.outputPath,
            "overwrite" to args.overwrite,
            "pretty" to args.pretty,
        )
            .filter { it.second != null }
            .joinToString("\n") { (label, value) -> """  * $label: $value""" }
    )

    val outputFile = args.outputPath?.let(::File)
    if (!args.overwrite) {
        if (outputFile?.exists() == true) {
            println("Output file ${args.outputPath} already exists")
            exitProcess(1)
        }
    }
    if (args.subsetIds.isEmpty()) {
        println("At least one subset id is required")
        exitProcess(1)
    }


    val subsets = args.subsetIds.map(::SubsetId).toSet()
    val doc = deserializeToDocument(InputStreamReader(FileInputStream(checkNotNull(args.inputPath))).readText())

    val validateAndWrite = documentValidatorAndWriter(args.pretty)

    validateAndWrite(javaCommonSchema, filterCommonSubsets(doc, subsets), checkNotNull(outputFile))
}

private fun doPopulate(args: CommandPopulate) {
    if (args.seed == null) {
        args.seed = randomShortSeed()
    }
    if (args.pathCommon == null && args.schema in setOf(
            CommandPopulate.SchemaOption.both,
            CommandPopulate.SchemaOption.common
        )
    ) {
        args.pathCommon = "consignment-${args.seed}-common.xml"
    }
    if (args.pathIdentifiers == null && args.schema in setOf(
            CommandPopulate.SchemaOption.both,
            CommandPopulate.SchemaOption.identifier
        )
    ) {
        args.pathIdentifiers = "consignment-${args.seed}-identifiers.xml"
    }

    val overrides = args.deleteOverrides + args.textOverrides

    println("Generating with:")
    println(
        listOf(
            "schema" to args.schema,
            "seed" to args.seed,
            "repeatable mode" to args.repeatableMode.name,
            "overrides" to overrides.map {
                when (it) {
                    is DeleteNodeOverride -> """Delete "${it.xpath.raw}""""
                    is TextContentOverride -> """Set "${it.xpath.raw}" to "${it.value}""""
                }
            },
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
                CommandPopulate.SchemaOption.both -> consignmentCommonSchema
                CommandPopulate.SchemaOption.common -> consignmentCommonSchema
                CommandPopulate.SchemaOption.identifier -> consignmentIdentifierSchema
            },
            overrides = overrides,
            namespaceAware = false,
        )

    val validateAndWrite = documentValidatorAndWriter(args.pretty)

    when (args.schema) {
        CommandPopulate.SchemaOption.both -> {
            val identifiers = commonToIdentifiers(doc)
            validateAndWrite(javaCommonSchema, doc, checkNotNull(fileCommon))
            validateAndWrite(javaIdentifiersSchema, identifiers, checkNotNull(fileIdentifiers))
        }

        CommandPopulate.SchemaOption.common -> {
            validateAndWrite(javaCommonSchema, doc, checkNotNull(fileCommon))
        }

        CommandPopulate.SchemaOption.identifier -> {
            validateAndWrite(javaIdentifiersSchema, doc, checkNotNull(fileIdentifiers))
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