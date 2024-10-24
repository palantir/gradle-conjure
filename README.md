<p align="right">
<a href="https://autorelease.general.dmz.palantir.tech/palantir/gradle-conjure"><img src="https://img.shields.io/badge/Perform%20an-Autorelease-success.svg" alt="Autorelease"></a>
</p>

# Gradle Conjure ![Bintray](https://img.shields.io/bintray/v/palantir/releases/gradle-conjure.svg) [![License](https://img.shields.io/badge/License-Apache%202.0-lightgrey.svg)](https://opensource.org/licenses/Apache-2.0)

_Gradle Conjure is a build tool which allows defining and generating code for Conjure APIs in Java projects._

## Overview

gradle-conjure is a set of Gradle plugins which allow you to define and consume Conjure-defined APIs easily.

- [`com.palantir.conjure`](#compalantirconjure) allows API authors to easily define APIs and generate bindings for Java, TypeScript and Python.
- [`com.palantir.conjure-publish`](#compalantirconjure-publish) allows API authors to publish a Conjure definition as a single self-contained file.
- [`com.palantir.conjure-local`](#compalantirconjure-local) allows API consumers to locally generate bindings for Conjure API definitions.

## com.palantir.conjure

To see how to add gradle-conjure to an existing project, please see our [getting started guide][].

### Tasks

- **compileConjure** - Generates code for your API definitions in src/main/conjure/**/*.yml
- **compileConjureObjects** - Generates Java POJOs from your Conjure definitions.
- **compileConjureTypeScript** - Generates TypeScript files and a package.json from your Conjure definitions.
- **compileIr** - Converts your Conjure YML files into a single portable JSON file in IR format.
- **compileTypeScript** - Runs `npm tsc` to compile generated TypeScript files into JavaScript files.
- **publishTypeScript** - Runs `npm publish` to publish a TypeScript package generated from your Conjure definitions.

### Extension

`com.palantir.conjure` also exposes a `conjure` extension, which allows you to configure the behaviour of each supported
generator. You configure the generator by specifying properties in a corresponding named closure. These properties
are converted into command line options or flags and passed on to the generator CLI.

The supported closures are:
- `java` - Configuration for [Conjure-Java][]
- `typescript` - Configuration for [Conjure-TypeScript][]
- `python` - Configuration for [Conjure-Python][]

The following is example usage of the extension.

```groovy
conjure {
    typescript {
        version = "0.0.0"
    }

    java {
        useImmutableBytes = true
    }
}
```

### Service dependencies

To help consumers correlate generated Conjure API artifacts with a real server that implements this API, the `com.palantir.conjure` plugin supports embedding optional 'service dependencies' in generated artifacts. (Requires gradle-conjure 4.6.2+.)

This information can be defined using the `serviceDependencies` extension on your API project. You must specify the 'group' and 'name' of the server that implements this API, along with a minimum, maximum and recommended version for the server.

```gradle
apply plugin: 'com.palantir.conjure'

serviceDependencies {
    serviceDependency {
        productGroup = 'com.palantir.group'
        productName = 'foo'
        minimumVersion = "${project.version}"
        maximumVersion = "${project.version.tokenize('.')[0]}.x.x"
        recommendedVersion = "${project.version}"
    }
}
```

For conjure-typescript, this information is passed as an extra flag, `--productDependencies=your-project/build/service-dependencies.json`, which is used to embed information in the resultant package.json.

For conjure-java, this information is directly embedded into the Jar for the `-jersey` and `-dialogue` projects.  It is stored as a manifest property, `Sls-Recommended-Product-Dependencies`, which can be detected by [sls-packaging](https://github.com/palantir/sls-packaging).

### Typescript

Typescript projects provide generateNpmrc task that generates .npmrc for publishing to configured repository. Credentials are configured using properties `username` and `password` and you can provide custom registry with `registryUri` parameter 
```groovy
generateNpmrc.registryUri = "https://my-custom-registry.com"
generateNpmrc.token = "<registry-token>" // System.env.<TOKEN>
```
Alternatively you can use username and password and the plugin will perform the login operation to obtain a publish token for you.
```groovy
generateNpmrc.username = "<username>" // System.env.<USERNAME>
generateNpmrc.password = "<password>" // System.env.<PASSWORD>
```

## com.palantir.conjure-publish
To enable publishing of your API definition for external consumption, add the `com.palantir.conjure-publish` which applies `com.palantir.conjure` and also creates a new `"conjure"` publication.


## com.palantir.conjure-local

### Tasks

- **generateConjure** - Generates code for all Conjure dependencies
- **generateTypeScript** - Generates TypeScript bindings for all Conjure dependencies
- **generatePython** - Generates Python bindings for all Conjure dependencies
- **generate\<language\>** - Task rule which will generates \<language> bindings for all Conjure dependencies, where \<language\> is the name of the generator to be used

### Configurations

- **`conjure`** - Configuration for adding Conjure API dependencies
- **`conjureGenerators`** - Configuration for adding generator dependencies

Using the `conjure` extension you can depend upon multiple Conjure APIs at once
```gradle
dependencies {
    conjure 'com.company.product:some-api:1.0.0'
    conjure 'com.company.other.product:other-api:1.0.0'
}
```

Using the `conjureGenerators` extension allows you to use use any Conjure generator which conforms to [RFC 002][]

```diff
 dependencies {
     conjure 'com.company.product:some-api:1.0.0'
     conjure 'com.company.other.product:other-api:1.0.0'

+    conjureGenerators 'com.palantir.conjure.postman:conjure-postman:0.1.0'
 }
```

For each generator specified referenced by the configuration you must also add a project with the corresponding name
```diff
 include 'conjure-api'
+include 'conjure-api:postman'
```

## com.palantir.conjure-java-local

`com.palantir.conjure-java-local` helps to generate Java code from the conjure definition other services publish.

```gradle
apply plugin: 'com.palantir.conjure-java-local'

conjure {
    java {
        addFlag 'objects'
        addFlag 'strictObjects'
        // addFlag 'undertow' as an implementer
        // addFlag 'dialogue' as a consumer
    }
}

dependencies {
    conjure 'com.company.product:some-api@conjure.json'
}

subprojects {
    dependencies {
        // api 'com.palantir.conjure.java:conjure-undertow-lib' as an implementer
        // implementation 'com.palantir.dialogue:dialogue-target' as a consumer
    }
}
```

## Contributing

See the [CONTRIBUTING.md](./CONTRIBUTING.md) document.

[getting started guide]: https://palantir.github.io/conjure/#/docs/getting_started
[RFC 002]: https://palantir.github.io/conjure/#/docs/rfc/002-contract-for-conjure-generators
[Conjure Source Files Specification]: https://palantir.github.io/conjure/#/docs/spec/conjure_definitions
[Conjure-Java]: https://github.com/palantir/conjure-java
[Conjure-TypeScript]: https://github.com/palantir/conjure-typescript
[Conjure-Python]: https://github.com/palantir/conjure-python
