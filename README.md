> Since [glsl-plugin-idea](https://github.com/rmordechay/glsl-plugin-idea) upstream is abandoned and now adding random
> slopped features, this fork is created to provide patches to fix bugs/runtime exceptions for `1.1.6`.

## Development environment

Use JDK 25 for development and the Gradle JVM in IntelliJ IDEA.
The plugin compiles against IntelliJ IDEA Community 2024.1.7 and emits Java 17
bytecode for IDEs starting at build 241 (2024.1). The sandbox uses the IDE's
bundled JetBrains Runtime.

Build versions and plugin metadata are in `gradle.properties`. Dependency and
Gradle plugin versions are in `gradle/libs.versions.toml`.

## Build and run

```sh
./gradlew buildPlugin
./gradlew runIde
```

Both commands generate the parser, PSI and lexers automatically. 

To clean and regenerate just the grammar sources, run `./gradlew generateGrammarClean`.

## Test and verify

```sh
./gradlew check
./gradlew verifyPlugin
```

`check` runs the tests and generates a Kover coverage report at `build/reports/kover/report.xml`.

`verifyPlugin` uses these IntelliJ IDEA Community targets:
- 2025.2.6.2
- 2025.1.7.1
- 2024.3.7.1
- 2024.2.6
- 2024.1.7

Verifier reports are written under `build/reports/pluginVerifier`.

GitHub Actions builds, tests and verifies the plugin with JDK 25, and uploads the
plugin archive and verification reports.

## Publishing

`./gradlew publishPlugin` uses `PUBLISH_TOKEN`. To sign the plugin, also set
`CERTIFICATE_CHAIN`, `PRIVATE_KEY` and `PRIVATE_KEY_PASSWORD`.
