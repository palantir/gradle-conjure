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
        retrofitCompletableFutures = true
    }
}
```

## com.palantir.conjure-publish
To enable publishing of your API definition for external consumption, add the `com.palantir.conjure-publish` which applies `com.palantir.conjure` and also creates a new `"conjure"` publication.


## com.palantir.conjure-local

### Tasks

- **generateConjure** - Generates code for all Conjure dependencies 
- **generateTypeScript** - Generates TypeScript bindings for all Conjure dependencies
- **generatePython** - Generates Python bindings for all Conjure dependencies
- **generate\<Language\>** - Task rule which will generates \<Language> bindings for all Conjure dependencies, where \<Language\> is the name of the generator to be used

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

```diff+gradle
 dependencies {
     conjure 'com.company.product:some-api:1.0.0'
     conjure 'com.company.other.product:other-api:1.0.0'
    
+    conjureGenerators 'com.palantir.conjure.postman:conjure-postman:0.1.0'
}
```

For each generator specified referenced by the configuration you must also add a project with the corresponding name
```diff+properties

 include 'conjure-api'
+ include 'conjure-api:postman'
```

## Contributing

See the [CONTRIBUTING.md](./CONTRIBUTING.md) document.

[getting started guide]: https://github.com/palantir/conjure/blob/develop/docs/getting_started.md
[RFC 002]: https://github.com/palantir/conjure/blob/develop/docs/rfc/002-contract-for-conjure-generators.md
[Conjure Source Files Specification]: https://github.com/palantir/conjure/blob/develop/docs/spec/source_files.md
[Conjure-Java]: https://github.com/palantir/conjure-java
[Conjure-TypeScript]: https://github.com/palantir/conjure-typescript
[Conjure-Python]: https://github.com/palantir/conjure-python
