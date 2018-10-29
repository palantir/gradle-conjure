# Gradle-Conjure

_A set of gradle plugins that allow easy usage of the Conjure toolchain within Java projects_

## Overview

gradle-conjure is a set of Gradle plugins which allow you to define and consume Conjure-defined APIs easily.

`com.palantir.conjure` allows API definers to easily define APIs and consume and publish bindings for languages (Java, TypeScript, Python).
`com.palantir.conjure-local` allows API consumers to locally generate bindings for publish Conjure API definitions.

## Conjure Plugin

### Getting started

Please see our [getting started guide][].

### Usage

`com.palantir.conjure` provides the following tasks:
- **compileConjure** - Generates code for your API definitions in src/main/conjure/**/*.yml
- **compileConjureObjects** - Generates Java POJOs from your Conjure definitions.
- **compileConjureTypeScript** - Generates TypeScript files and a package.json from your Conjure definitions.
- **compileIr** - Converts your Conjure YML files into a single portable JSON file in IR format.
- **compileTypeScript** - Runs `npm tsc` to compile generated TypeScript files into JavaScript files.
- **publishTypeScript** - Runs `npm publish` to publish a TypeScript package generated from your Conjure definitions.

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

### Publishing
To enable publishing of your API definition for external consumption, you may use the `com.palantir.conjure-publish`
plugin instead of `com.palantir.conjure` plugin. This plugin applies `com.palantir.conjure` and also creates a new `"conjure"` publication.


## Conjure Local Plugin

### Usage

`com.palantir.conjure` provides the following tasks:
- **generateConjure** - Generates code for all API definitions in the `conjure` configuration
- **generateTypeScript** - Generates TypeScript bindings for all remote Conjure dependencies
- **generatePython** - Generates Python bindings for all remote Conjure dependencies
- **generateLanguage** - Generates 

`com.palantir.conjure-local` also exposes a `conjure` and `conjureGenerator` configurations.

## Contributing

See the [CONTRIBUTING.md](./CONTRIBUTING.md) document.

[getting started guide]: https://github.com/palantir/conjure/blob/develop/docs/getting_started.md
[Conjure Source Files Specification]: https://github.com/palantir/conjure/blob/develop/docs/spec/source_files.md
[Conjure-Java]: https://github.com/palantir/conjure-java
[Conjure-TypeScript]: https://github.com/palantir/conjure-typescript
[Conjure-Python]: https://github.com/palantir/conjure-python
