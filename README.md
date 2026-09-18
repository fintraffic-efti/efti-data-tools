# efti-data-tools

Java libraries and command line tool for filtering subsets and generating random xml documents of eFTI consignment schemas
as defined at [reference-implementation](https://github.com/fintraffic-efti/reference-implementation/tree/main/schema/xsd).

These tools may be used in implementing, development and testing of eFTI applications.

Requires Java 17 or later.

> [!IMPORTANT]
> The xsd schema files are **not** bundled with the libraries or the command line application. Get your own copy
> of the schemas fomr https://github.com/EFTI4EU/reference-implementation/tree/main/schema/xsd.

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
`consignment-common.xsd`, `types/types.xsd` and `codes/codes.xsd` for v0, or `FTI010s.xsd` together with its
`FTI010s_urn_eu_move_*.xsd` files for v1.

### Supported schema versions

Two versions of the eFTI schemas are supported. They are structurally very different from each other, so not every
feature is available for both.

| Schema | `EftiSchemaId` | Main xsd | Document element |
|---|---|---|---|
| v0 consignment common | `CONSIGNMENT_COMMON` | `consignment-common.xsd` | `consignment` |
| v0 consignment identifier | `CONSIGNMENT_IDENTIFIER` | `consignment-identifier.xsd` | `consignment` |
| v1 consignment common | `CMDS_RESPONSE_V1` | `FTI010/FTI010s.xsd` | `FTI010GetCmdsResponse` |

The v1 schemas are organised into one directory per message type, so the paths above are relative to the root of
the v1 schemas: point the library and the command line application at the directory that contains the semantic
versions for both `FTI010` and `eFTI XM SubMap`.

#### In a library

Place the schema files on the classpath, keeping their directory structure, and point `EftiSchema` at the classpath
root under which they live. In a Gradle or Maven project this is typically a directory under `src/main/resources`:

```
src/main/resources/efti-xsd/consignment-common.xsd
src/main/resources/efti-xsd/consignment-identifier.xsd
src/main/resources/efti-xsd/types/types.xsd
src/main/resources/efti-xsd/codes/codes.xsd
```

An `EftiSchema` instance is one schema, read from one location. The schema is read and compiled when the instance is
created, so problems with the provided xsd files are reported immediately. Compiling a schema is expensive and
instances are not cached by the library, so hold on to the instances you need, for example in a `static final` field
or in a singleton bean.

```java
static final EftiSchema COMMON_SCHEMA = EftiSchema.fromClasspath(EftiSchemaId.CONSIGNMENT_COMMON, "/efti-xsd");

Document doc = new EftiDomPopulator(COMMON_SCHEMA, 1234, RepeatablePopulateMode.MINIMUM_ONE)
        .populate();

Document filtered = COMMON_SCHEMA.filterSubsets(doc, Set.of(new SubsetId("FI01")));

// Drop elements that the schema does not declare.
Document cleaned = COMMON_SCHEMA.dropNodesNotInSchema(doc);
```

A v1 schema is read in exactly the same way, only the `EftiSchemaId` differs:

```java
static final EftiSchema COMMON_V1_SCHEMA =
        EftiSchema.fromClasspath(EftiSchemaId.CONSIGNMENT_COMMON_V1, "/efti-xsd-v1");
```

A schema can also be read from a directory of the local file system with
`EftiSchema.fromDirectory(EftiSchemaId, File)`.

If the files cannot be found, or they are not eFTI schemas of a supported version, an `EftiSchemaException` with a
description of the problem is thrown.

#### In the command line application

Unzip a complete set of eFTI xsd files somewhere and pass the root directory with `--schema-dir` (`-X`):

```shell
efti-data-tools-cli populate --schema-dir /path/to/xsd -x common
```

The schema version is **detected automatically** from the contents of the directory: a directory containing
`consignment-common.xsd` is read as v0, and one containing `FTI010/FTI010s.xsd` as v1. Point `--schema-dir` at the
schemas of a single version.

```shell
# Populate a v1 document
efti-data-tools-cli populate --schema-dir /path/to/xsd-v1 -x common

# Filter a v1 document into the EU01 subset
efti-data-tools-cli filter --schema-dir /path/to/xsd-v1 -s EU01 -i my-common.xml -o filtered.xml
```

Operations that are not available for the detected version, such as generating identifier documents for v1, fail
with an explanatory message.

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

Only available for the v0 schemas.

```shell
./gradlew app:run --args="filter -X ../xsd/v0 -w -i ../xsd/examples/consignment-common.xml -s FI01,FI02"
```

#### Populate documents

##### Set single value

```shell
./gradlew app:run --args="populate -X ../xsd/v0 -x identifier -w -p -s 42 -t 'consignment/deliveryEvent/actualOccurrenceDateTime:=202412312359+0000'"
```

##### Delete node

```shell
./gradlew app:run --args="populate -X ../xsd/v0 -x identifier -w -p -s 42 -d 'consignment/deliveryEvent/actualOccurrenceDateTime'"
```

##### Set multiple identifiers to same value

```shell
./gradlew app:run --args="populate -X ../xsd/v0 -x identifier -w -p -s 42 -t 'consignment/usedTransportEquipment/id:=ABC-123'"
```

##### Set multiple identifiers to different values

```shell
./gradlew app:run --args="populate -X ../xsd/v0 -x identifier -w -p -s 42 -t 'consignment/usedTransportEquipment[1]/id:=ABC-123' -t 'consignment/usedTransportEquipment[2]/id:=XYZ-789'"
```

##### Output both common and identifier documents with default filenames

```shell
./gradlew app:run --args="populate -X ../xsd/v0 -x both -w -p -s 42
```

##### Output both common and identifier documents with custom filenames

```shell
./gradlew app:run --args="populate -X ../xsd/v0 -x both -w -p -s 42 -oc my-common.xml -oi my-identifiers.xml
```

##### Populate a v1 document

```shell
./gradlew app:run --args="populate -X '../xsd/v1' -x main -w -p -s 42 -oc my-fti010s.xml"
```

Only `-x common` is supported for v1, because the v1 schemas have no identifier schema.

##### Filter a v1 document

```shell
./gradlew app:run --args="filter -X '../xsd/v1' -s FI01 -i my-fti010s.xml -o filtered.xml -w -p"
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
