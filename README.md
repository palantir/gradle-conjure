# Gradle-Conjure

A gradle plugin that allows easy usage of the Conjure toolchain within Java projects

## Getting Started
To apply the plugin, in your top level `build.gradle` file, add a buildscript dependency on Conjure.

```gradle
buildscript {
    repositories {
        maven {

            url 'https://dl.bintray.com/palantir/releases/'
        }
    }
    dependencies {
        classpath 'com.palantir.gradle.conjure:gradle-conjure:4.0.0'
    }
}
```

Then create a new project for your API with the following directory structure
```groovy
your-project-api
├── build.gradle
├── your-project-api-objects/
├── your-project-api-jersey/
└── src/
    └── main/
        └── conjure/
            └── your-project-api.yml

```

With the following `build.gradle`
```groovy
apply plugin: 'com.palantir.conjure'
```

Then update your `settings.gradle`
```diff
 rootProject.name = 'your-project'

 include 'your-project'
+include 'your-project-api'
+include 'your-project-api:your-project-api-objects'
+include 'your-project-api:your-project-api-jersey'
```
_Note, you can omit any of these projects if you don't need the generated code (gradle-conjure just looks at the project name suffix to figure out where to put generated code).  For example, if you only want to generate Java objects, you can just add the `your-project-api-objects` project and omit the others._

For guidance on how to write `your-project-api.yml` please see the [Conjure Specification](https://github.com/palantir/conjure/blob/develop/specification.md). 

Once applied you can configure the versions of the generators to use manually or through the use of dependency recommendation.

##### Manual Configuration
Add the following to the root gradle file.
```groovy
subprojects {
    configurations.all {
        resolutionStrategy {
            force 'com.palantir.conjure:conjure:4.0.0'
            force 'com.palantir.conjure.java:conjure-java:1.0.0'
            force 'com.palantir.conjure.typescript:conjure-typescript:3.0.0'
        }
    }
}
```

##### Dependency Recommendation
If you are using nebula.dependency-recommender, add the following to the version recommendation file.
```
com.palantir.conjure:conjure = 4.0.0
com.palantir.conjure.java:conjure-java = 1.0.0
com.palantir.conjure.typescript:conjure-typescript = 3.0.0
```

## Usage 

Gradle-Conjure provides the following tasks:
- compileConjure - Generates code for your API definitions in src/main/conjure/**/*.yml
- compileConjureObjects - Generates Java POJOs from your Conjure definitions.
- compileConjureTypeScript - Generates TypeScript files and a package.json from your Conjure definitions.
- compileIr - Converts your Conjure YML files into a single portable JSON file in IR format.
- compileTypeScript - Runs `npm tsc` to compile generated TypeScript files into JavaScript files.
- publishTypeScript - Runs `npm publish` to publish a TypeScript package generated from your Conjure definitions.

## Contributing

See the [CONTRIBUTING.md](./CONTRIBUTING.md) document.
