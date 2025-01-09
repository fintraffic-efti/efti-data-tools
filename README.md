# efti-data-tools

Command line tool and library for generating random xml documents for eFTI consignment schemas at
[reference-implementation](https://github.com/EFTI4EU/reference-implementation/tree/main/schema/xsd).

These tools are not part of the eFTI4EU reference implementation but may be used to help in development and testing of
eFTI implementations.

Requires Java 17 or later.

## Binaries

Get binaries from [workflow run artifacts](https://github.com/EFTI4EU/efti-data-tools/actions).

### Command line application

Unzip app zip and run command line tool with:
```
# On *nix:
app/bin/app --help

# On Windows:
app/bin/app.bat --help
```

### Libraries

Note that the libraries require org.apache.xmlbeans:xmlbeans as a runtime dependency. See [build.gradle.kts](schema/build.gradle.kts)
for the specific version.

## Examples

These examples use gradle to simplify testing. Note how the xpath expressions use local xml names and ignore namespaces.

### Get help

```shell
./gradlew app:run --args="-h"
```

### Subset filtering

```shell
./gradlew app:run --args="filter -w -x identifier -i ../xsd/examples/consignment.xml -s FI01,FI02"
```

### Populate

#### Set single value

```shell
./gradlew app:run --args="populate -x identifier -w -s 42 -t 'consignment/deliveryEvent/actualOccurrenceDateTime:=202412312359+0000'"
```

#### Unset value by overwriting with empty value

```shell
./gradlew app:run --args="populate -x identifier -w -s 42 -t 'consignment/deliveryEvent/actualOccurrenceDateTime:='"
```

#### Set multiple identifiers to same value

```shell
./gradlew app:run --args="populate -x identifier -w -s 42 -t 'consignment/usedTransportEquipment/id:=ABC-123'"
```

#### Set multiple identifiers to different values

```shell
./gradlew app:run --args="populate -x identifier -w -s 42 -t 'consignment/usedTransportEquipment[1]/id:=ABC-123' -t 'consignment/usedTransportEquipment[2]/id:=XYZ-789'"
```

#### Output both common and identifier documents with default filenames

```shell
./gradlew app:run --args="populate -x both -w -s 42
```

#### Output both common and identifier documents with custom filenames

```shell
./gradlew app:run --args="populate -x both -w -s 42 -oc my-common.xml -oi my-identifiers.xml
```

## Development

Build and run tests with:
```
./gradlew build
```
