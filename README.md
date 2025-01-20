# efti-data-tools

Java libraries and command line tool for filtering subsets and generating random xml documents of eFTI consignment schemas
as defined at [reference-implementation](https://github.com/EFTI4EU/reference-implementation/tree/main/schema/xsd).

These tools are not part of the eFTI4EU reference implementation but may be used in implementing, development and testing
of eFTI applications.

Requires Java 17 or later.

## Usage

Get binaries from [releases](https://github.com/EFTI4EU/efti-data-tools/releases).

### Libraries

There are two libraries:

 1. schema - Tools for subset filtering and other xml utilities
 2. populate - Tools for populating pseudo-random consignment documents

Libraries are released as maven artifacts that you need to add to a local maven repo, for example. The process in a
nutshell:

 1. Get efti-data-tools-lib-<version>.zip from release page
 2. Unzip it
 3. Copy the whole `eu` directory into your maven repo (this can be a local repo)
 4. Add `eu.efti.datatools:schema:<version>` and/or `eu.efti.datatools:populate:<version>` as maven dependencies 

See [Java example](./example/java) for a complete example.

### Command line application

Get efti-data-tools-cli-<version>.zip from release page, unzip it and run with:
```
# On *nix:
./efti-datatools-cli-<version>/bin/app --help

# On Windows:
efti-datatools-cli-<version>\bin\app.bat --help
```

The following examples use gradle to simplify testing. Note how the xpath expressions use local xml names and ignore namespaces.

#### Get help

```shell
./gradlew app:run --args="-h"
```

#### Subset filtering

```shell
./gradlew app:run --args="filter -w -x identifier -i ../xsd/examples/consignment.xml -s FI01,FI02"
```

#### Populate documents

##### Set single value

```shell
./gradlew app:run --args="populate -x identifier -w -s 42 -t 'consignment/deliveryEvent/actualOccurrenceDateTime:=202412312359+0000'"
```

##### Unset value by overwriting with empty value

```shell
./gradlew app:run --args="populate -x identifier -w -s 42 -t 'consignment/deliveryEvent/actualOccurrenceDateTime:='"
```

##### Set multiple identifiers to same value

```shell
./gradlew app:run --args="populate -x identifier -w -s 42 -t 'consignment/usedTransportEquipment/id:=ABC-123'"
```

##### Set multiple identifiers to different values

```shell
./gradlew app:run --args="populate -x identifier -w -s 42 -t 'consignment/usedTransportEquipment[1]/id:=ABC-123' -t 'consignment/usedTransportEquipment[2]/id:=XYZ-789'"
```

##### Output both common and identifier documents with default filenames

```shell
./gradlew app:run --args="populate -x both -w -s 42
```

##### Output both common and identifier documents with custom filenames

```shell
./gradlew app:run --args="populate -x both -w -s 42 -oc my-common.xml -oi my-identifiers.xml
```

## Development

Build and run tests with:
```
./gradlew build distZip
```

### Creating releases

For example, to release version 0.1.0:
1. Set the version number in [gradle.properties](gradle.properties)
2. Commit
3. Add and push tag `v0.1.0`

Let us follow [semantic versioning](https://semver.org/).
