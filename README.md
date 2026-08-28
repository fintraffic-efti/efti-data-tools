# efti-data-tools

Java libraries and command line tool for filtering subsets and generating random xml documents of eFTI consignment schemas
as defined at [reference-implementation](https://github.com/fintraffic-efti/reference-implementation/tree/main/schema/xsd).

These tools may be used in implementing, development and testing of eFTI applications.

Requires Java 17 or later.

> [!IMPORTANT]
> The xsd schema files are **not** bundled with the libraries or the command line application. You always provide your
> own copy of them, which means that you can move to a new schema version without waiting for a new release of these
> tools. See [Providing the schemas](#providing-the-schemas).

## Usage

This project releases libraries and a command line application.

### Libraries

There are two libraries:

 1. schema - Tools for subset filtering and other xml utilities
 2. populate - Tools for populating pseudo-random consignment documents

Libraries are published to the Maven repository under this GitHub project at
[mvn-repo branch](https://raw.githubusercontent.com/fintraffic-efti/efti-data-tools/mvn-repo/README.md). To use them in your 
Maven/Gradle project:

 1. In your project configuration, add a Maven repository at url `https://github.com/fintraffic-efti/efti-data-tools/raw/mvn-repo`:
    * Gradle example:
      ```
      repositories {
        maven("https://github.com/fintraffic-efti/efti-data-tools/raw/mvn-repo")
        mavenCentral()
      }
      ```
    * Maven example:
      ```
      <repository>
        <id>efti-data-tools</id>
        <name>efti-data-tools repository</name>
        <url>https://github.com/fintraffic-efti/efti-data-tools/raw/mvn-repo</url>
       </repository>
      ```
 2. Add dependency `eu.efti.datatools:schema:<version>`, and if you need it, `eu.efti.datatools:populate:<version>`:
    * Gradle example:
      ```
      implementation("eu.efti.datatools:schema:0.3.0")
      ```
    * Maven example:
      ```
      <dependency>
        <groupId>eu.efti.datatools</groupId>
        <artifactId>schema</artifactId>
        <version>0.3.0</version>
      </dependency>
      ```

See [Java example](./example/java) for a complete example on library usage.

### Providing the schemas

A complete set of eFTI xsd files, for example from
[reference-implementation](https://github.com/fintraffic-efti/reference-implementation/tree/main/schema/xsd), must be
made available to the tools. "Complete" means the main schema together with everything it imports, for example
`consignment-common.xsd`, `types/types.xsd` and `codes/codes.xsd`.

#### In a library

Place the schema files on the classpath, keeping their directory structure, and point `EftiSchemas` at the classpath
root under which they live. In a Gradle or Maven project this is typically a directory under `src/main/resources`:

```
src/main/resources/efti-xsd/consignment-common.xsd
src/main/resources/efti-xsd/consignment-identifier.xsd
src/main/resources/efti-xsd/types/types.xsd
src/main/resources/efti-xsd/codes/codes.xsd
```

```java
// Compiling a schema is expensive. Repeated calls with the same root return the same instance, and each instance
// compiles a given schema at most once, so this is cheap to call.
EftiSchemas schemas = EftiSchemas.fromClasspath("/efti-xsd");

Document doc = new EftiDomPopulator(1234, RepeatablePopulateMode.MINIMUM_ONE)
        .populate(schemas, EftiSchemaId.CONSIGNMENT_COMMON);

Document filtered = SubsetUtil.filterCommonSubsets(schemas, doc, Set.of(new XmlSchemaElement.SubsetId("FI01")));
```

Schemas can also be read from a directory of the local file system with `EftiSchemas.fromDirectory(File)`.

If the files cannot be found, or they are not eFTI schemas of a supported version, an `EftiSchemaException` with a
description of the problem is thrown.

#### In the command line application

Unzip a complete set of eFTI xsd files somewhere and pass the root directory with `--schema-dir` (`-X`):

```shell
efti-data-tools-cli populate --schema-dir /path/to/xsd -x common
```

#### Migrating from 0.8.0 or earlier

Earlier versions bundled the schemas and exposed them as static fields of `EftiSchemas`. Replace those with an
`EftiSchemas` instance and an `EftiSchemaId`:

| Before | After |
| --- | --- |
| `EftiSchemas.getConsignmentCommonSchema()` | `schemas.xmlSchema(EftiSchemaId.CONSIGNMENT_COMMON)` |
| `EftiSchemas.getConsignmentIdentifierSchema()` | `schemas.xmlSchema(EftiSchemaId.CONSIGNMENT_IDENTIFIER)` |
| `EftiSchemas.getJavaCommonSchema()` | `schemas.javaSchema(EftiSchemaId.CONSIGNMENT_COMMON)` |
| `EftiSchemas.getJavaIdentifiersSchema()` | `schemas.javaSchema(EftiSchemaId.CONSIGNMENT_IDENTIFIER)` |
| `EftiSchemas.getConsignmentCommonSubsetIds()` | `schemas.subsetIds(EftiSchemaId.CONSIGNMENT_COMMON)` |
| `SubsetUtil.filterCommonSubsets(doc, subsets)` | `SubsetUtil.filterCommonSubsets(schemas, doc, subsets)` |
| `SubsetUtil.commonSchemaHasSubset(subsetId)` | `SubsetUtil.commonSchemaHasSubset(schemas, subsetId)` |
| `SchemaConversion.commonToIdentifiers(doc)` | `SchemaConversion.commonToIdentifiers(schemas, doc)` |

Command line users must add `--schema-dir`.

### Command line application

Get efti-data-tools-cli-<version>.zip from [releases](https://github.com/fintraffic-efti/efti-data-tools/releases), unzip it and run with:
```
# On *nix:
./efti-datatools-cli-<version>/bin/efti-data-tools-cli --help

# On Windows:
efti-datatools-cli-<version>\bin\efti-data-tools-cli.bat --help
```

The schema files are not included in the zip, see [Providing the schemas](#providing-the-schemas).

The following examples use gradle to simplify testing, and the schemas of this repository with `-X ../xsd`. Note how
the xpath expressions use local xml names and ignore namespaces.

#### Get help

```shell
./gradlew app:run --args="-h"
```

#### Subset filtering

```shell
./gradlew app:run --args="filter -X ../xsd -w -i ../xsd/examples/consignment-common.xml -s FI01,FI02"
```

#### Populate documents

##### Set single value

```shell
./gradlew app:run --args="populate -X ../xsd -x identifier -w -p -s 42 -t 'consignment/deliveryEvent/actualOccurrenceDateTime:=202412312359+0000'"
```

##### Delete node

```shell
./gradlew app:run --args="populate -X ../xsd -x identifier -w -p -s 42 -d 'consignment/deliveryEvent/actualOccurrenceDateTime'"
```

##### Set multiple identifiers to same value

```shell
./gradlew app:run --args="populate -X ../xsd -x identifier -w -p -s 42 -t 'consignment/usedTransportEquipment/id:=ABC-123'"
```

##### Set multiple identifiers to different values

```shell
./gradlew app:run --args="populate -X ../xsd -x identifier -w -p -s 42 -t 'consignment/usedTransportEquipment[1]/id:=ABC-123' -t 'consignment/usedTransportEquipment[2]/id:=XYZ-789'"
```

##### Output both common and identifier documents with default filenames

```shell
./gradlew app:run --args="populate -X ../xsd -x both -w -p -s 42
```

##### Output both common and identifier documents with custom filenames

```shell
./gradlew app:run --args="populate -X ../xsd -x both -w -p -s 42 -oc my-common.xml -oi my-identifiers.xml
```

## Development

Build and run tests with:
```
./gradlew build distZip
```

### Creating releases

Let us follow [semantic versioning](https://semver.org/).

For example, to release version 0.4.1:
1. Set the version number in [gradle.properties](gradle.properties) to `0.4.1`
2. Commit
3. Add and push tag `v0.4.1`
4. Publish library artifacts manually to the Maven repository:
   1. Checkout branch `mvn-repo`
   2. Download library zip from https://github.com/fintraffic-efti/efti-data-tools/releases/tag/v0.4.1
   3. Unzip the file (directory `eu` should be at root dir of the repo), existing `maven-metadata.xml*` files may
      be overwritten.
   4. Commit and push
5. Write release notes for the release by editing the release at 
   [releases/tag/v0.4.1](https://github.com/fintraffic-efti/efti-data-tools/releases/tag/v0.4.1). Go through the
   commit history after the previous release and include at least:
   * all breaking changes
   * new features
   * other interesting changes

> [!IMPORTANT]
> Note: the `mvn-repo` branch must not be merged to `main`.
