# Java Example

Example that shows how to use subset filtering and populate functionalities of the library. Note that this
example project uses a local Maven repository and not the official one (this makes testing in CI pipeline easier).

The xsd schema files are not shipped with the library, so this example provides its own copy of them: the schemas of
this repository are copied onto the example's classpath under `efti-xsd` (see [build.gradle.kts](./build.gradle.kts))
and read with `EftiSchemas.fromClasspath("/efti-xsd")` (see
[JavaExample.java](./src/main/java/eu/efti/datatools/javaexample/JavaExample.java)). In a real project you would
typically keep your own copy of the schema files under `src/main/resources`.

## Usage

First build the libraries to make them available to the example project:
```shell
cd ../..
./gradlew publishMavenPublicationToLocalMavenRepoRepository
```

Then you can run the example tests:
```shell
./gradlew test
```
