# Java Example

Example that shows how to use subset filtering and populate functionalities of the library. Note that this
example project uses a local Maven repository and not the official one (this makes testing in CI pipeline easier).

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
