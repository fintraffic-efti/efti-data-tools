# efti-data-tools

Command line tool and library for generating random xml documents for eFTI consignment schemas at
[reference-implementation](https://github.com/EFTI4EU/reference-implementation/tree/main/schema/xsd).

## Command line tool

Requires Java 17 or later.

Build with:
```
./gradlew build
```

Unzip [app.zip](./app/build/distributions/app.zip) and run:
```
# On *nix:
app/bin/app --help

# On Windows:
app/bin/app.bat --help
```

## Examples

These examples use gradle to simplify testing.

### Set single value

```shell
./gradlew app:run --args="-x identifier -w -s 42 -t 'consignment/deliveryEvent/actualOccurrenceDateTime:=123456789012+0000'"
```

### Unset value by overwriting with empty value

```shell
./gradlew app:run --args="-x identifier -w -s 42 -t 'consignment/deliveryEvent/actualOccurrenceDateTime:='"
```

### Set multiple identifiers to same value

```shell
./gradlew app:run --args="-x identifier -w -s 42 -t 'consignment/usedTransportEquipment/id:=ABC-123'"
```

### Set multiple identifiers to different values

```shell
./gradlew app:run --args="-x identifier -w -s 42 -t 'consignment/usedTransportEquipment[1]/id:=ABC-123' -t 'consignment/usedTransportEquipment[2]/id:=XYZ-789'"
```
