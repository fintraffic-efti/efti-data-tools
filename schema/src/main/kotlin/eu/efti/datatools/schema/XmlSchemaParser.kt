package eu.efti.datatools.schema

import eu.efti.datatools.schema.XmlSchemaElement.XmlAttribute
import eu.efti.datatools.schema.XmlSchemaElement.XmlCardinality
import eu.efti.datatools.schema.XmlSchemaElement.XmlName
import eu.efti.datatools.schema.XmlSchemaElement.XmlType
import org.apache.xmlbeans.SchemaLocalElement
import org.apache.xmlbeans.SchemaParticle
import org.apache.xmlbeans.SchemaProperty
import org.apache.xmlbeans.SchemaType
import org.apache.xmlbeans.SchemaTypeSystem
import org.apache.xmlbeans.SimpleValue
import javax.xml.namespace.QName

object XmlSchemaParser {
    fun parse(xmlBeansSchema: SchemaTypeSystem, documentType: XmlName): XmlSchemaElement {
        val documentSchema = checkNotNull(
            xmlBeansSchema.documentTypes().find {
                it.contentModel.name.namespaceURI == documentType.namespaceURI &&
                    it.contentModel.name.localPart == documentType.localPart
            },
        ) { "Not found: $documentType" }

        require(documentSchema.contentType == SchemaType.ELEMENT_CONTENT) {
            "Unsupported document type ${documentSchema.contentType}"
        }

        return parseElement(documentSchema.contentModel)
    }

    private fun parseElement(particle: SchemaParticle): XmlSchemaElement {
        require(particle.particleType == SchemaParticle.ELEMENT) {
            "Unsupported particle type ${particle.particleType}"
        }

        val base = toXmlElement((particle as SchemaLocalElement))

        val schemaType = (particle as SchemaParticle).type
        val schemaContentModel = schemaType?.contentModel
        val children = if (schemaContentModel != null) {
            when (schemaContentModel.particleType) {
                SchemaParticle.ALL, SchemaParticle.CHOICE, SchemaParticle.SEQUENCE -> {
                    val children = schemaContentModel.particleChildren
                    children.map { c -> parseElement(c) }
                }

                SchemaParticle.ELEMENT -> listOf(parseElement(schemaContentModel))

                else -> null
            }
        } else {
            null
        }

        return if (children != null) base.copy(children = children) else base
    }

    private fun toXmlElement(schemaElement: SchemaLocalElement): XmlSchemaElement = XmlSchemaElement(
        name = XmlName(schemaElement.name.namespaceURI, schemaElement.name.localPart),
        type = toXmlType(schemaElement.type),
        cardinality = XmlCardinality(
            schemaElement.minOccurs?.longValueExact() ?: 1,
            schemaElement.maxOccurs?.longValueExact(),
        ),
        children = emptyList(),
        subsets = schemaElement
            .annotation
            ?.applicationInformation
            ?.asSequence()
            ?.flatMap { appInfo ->
                appInfo
                    .selectChildren(QName("efti")).asSequence()
                    .flatMap { efti -> efti.selectChildren(QName("subset")).asSequence() }
            }
            ?.map { subset -> (subset.selectAttribute(QName("id")) as SimpleValue).stringValue }
            ?.filterNotNull()
            ?.map(::SubsetId)
            ?.toSet()
            ?: emptySet(),
        fixedValue = schemaElement.defaultText?.takeIf { schemaElement.isFixed },
    )

    private fun toXmlType(type: SchemaType): XmlType {
        val formattedAttrs = type.attributeProperties?.map { attr ->
            XmlAttribute(
                name = XmlName(attr.name.namespaceURI, attr.name.localPart),
                type = XmlType(
                    name = toXmlName(attr.type),
                    enumerationValues = attr.type.enumerationValues
                        ?.map { e -> e.stringValue }
                        ?: emptyList(),
                    attributes = emptyList(),
                    baseTypes = collectBaseTypes(attr.type, emptyList()),
                    isTextContentType = isTextContentType(attr.type),
                ),
                fixedValue = attr.defaultText?.takeIf { attr.hasFixed() != SchemaProperty.NEVER },
            )
        } ?: emptyList()

        return XmlType(
            name = toXmlName(type),
            enumerationValues = type.enumerationValues
                ?.map { e -> e.stringValue }
                ?: emptyList(),
            attributes = formattedAttrs,
            baseTypes = collectBaseTypes(type, emptyList()),
            isTextContentType = isTextContentType(type),
        )
    }

    /**
     * Name of the given type, or null if the type is anonymous. Types that are declared inline in an element or an
     * attribute, as the v1 schemas do for example for `DateTimeString`, do not have a name.
     */
    private fun toXmlName(type: SchemaType): XmlName? =
        type.name?.let { name -> XmlName(name.namespaceURI, name.localPart) }

    private fun isTextContentType(schemaType: SchemaType) =
        schemaType.contentType == SchemaType.SIMPLE_CONTENT || schemaType.isSimpleType

    private tailrec fun collectBaseTypes(type: SchemaType, accumulator: List<XmlType>): List<XmlType> {
        val base = type.baseType
        // Simplify base types list by leaving out the "anyType". Anonymous base types have no name, but they are
        // still meaningful, so they are kept.
        return if (base != null && !isXsdAnyType(base)) {
            collectBaseTypes(base, accumulator + toXmlType(base))
        } else {
            accumulator
        }
    }

    private fun isXsdAnyType(type: SchemaType): Boolean = type.name?.let { name ->
        name.namespaceURI == "http://www.w3.org/2001/XMLSchema" && name.localPart == "anyType"
    } ?: false
}
