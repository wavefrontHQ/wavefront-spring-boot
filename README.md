# Wavefront for Spring Boot 3

[![CI Status][ci-img]][ci] [![Released Version][maven-img]][maven]

This project provides a Spring Boot 3 starter for Wavefront. Add the starter to a project to send metrics, histograms, and traces to a Wavefront cluster. If you don't have a Wavefront account, the starter creates a freemium account for you and saves the API token in your home directory at `~/.wavefront_freemium`.

> **Note**: Spring Boot 2 users should refer to the [README](https://github.com/wavefrontHQ/wavefront-spring-boot/tree/master) on the `master` branch.

## Table of Contents

* [Prerequisites](#prerequisites)
* [Getting Started](#getting-started)
* [Building](#building)
* [Custom Configuration](#custom-configuration)
* [Documentation](#documentation)
* [License](#license)
* [Getting Support](#getting-support)

## Prerequisites

* Spring Boot 3.0 or above
* Java 17 or above
* Maven 3.5+ or Gradle 7.5+\
  See [System Requirements](https://docs.spring.io/spring-boot/docs/3.0.x/reference/html/getting-started.html#getting-started.system-requirements) in the Spring Boot documentation.

> **Note**: This starter reuses the existing Wavefront support for [Metrics](https://docs.spring.io/spring-boot/docs/3.0.x/reference/html/actuator.html#actuator.metrics.export.wavefront) and [Tracing](https://docs.spring.io/spring-boot/docs/3.0.x/reference/html/actuator.html#actuator.micrometer-tracing)
in Spring Boot and provides the Actuator (i.e., `spring-boot-starter-actuator`).

## Getting Started

There are several ways to start:

### Create a New Project

The easiest way to get started is to:
1. Create a new project on [start.spring.io](https://start.spring.io).
1. Select Spring Boot `3.0.0` or later and define the other parameters for your project.
1. Click "Add dependency" and select `Wavefront` from the dependency list. 
1. To include tracing support, add the `Distributed Tracing` dependency as well.

If you don't already have a Wavefront account and want a freemium one generated for you, follow the steps under [**Maven**](#maven-install) or [**Gradle**](#gradle-install) to add the Wavefront Spring Boot BOM.

### Update an Existing Application

If you already have a Spring Boot application, you can use [start.spring.io](https://start.spring.io) to explore a new project from your browser.
This allows you to see the setup for the Spring Boot generation your project is using.

Next, follow the steps under [**Maven**](#maven-install) or [**Gradle**](#gradle-install) to add the Wavefront Spring Boot BOM.

#### Maven <a name="maven-install"></a>

Follow these steps:

1. Import the `wavefront-spring-boot-bom` Bill Of Materials (BOM).
    Example:
      ```
      <dependencyManagement>
        <dependencies>
          <dependency>
            <groupId>com.wavefront</groupId>
            <artifactId>wavefront-spring-boot-bom</artifactId>
            <version>3.0.1</version>
            <type>pom</type>
            <scope>import</scope>
          </dependency>
        </dependencies>
      </dependencyManagement>
      ```

1. Add the `wavefront-spring-boot-starter` to your project.

    Example:
      ```
      <dependency>
        <groupId>com.wavefront</groupId>
        <artifactId>wavefront-spring-boot-starter</artifactId>
      </dependency>
      ```

#### Gradle <a name="gradle-install"></a>

Follow these steps:

1. Make sure your project uses the `io.spring.dependency-management` plugin and add the following to your `build.gradle` file:

    ```
      dependencyManagement {
        imports {
          mavenBom "com.wavefront:wavefront-spring-boot-bom:3.0.1"
        }
      }
    ```

1. Add the `wavefront-spring-boot-starter` to your project.

    ```
      dependencies {
        ...
        implementation 'com.wavefront:wavefront-spring-boot-starter'

      }
    ```

---
Each time you restart your application, it either creates a new freemium account, or it restores from `~/.wavefront_freemium`.
At the end of the startup phase, the console displays a message with a login URL.
Use it to log in to the Wavefront service and access the data that has been collected so far.

Here is an example message when an existing account is restored from `~/.wavefront_freemium`:

```text
Your existing Wavefront account information has been restored from disk.
To share this account, make sure the following is added to your configuration:

management.wavefront.api-token=2c96d63a-abcd-efgh-ijhk-841611451e07
management.wavefront.uri=https://wavefront.surf

Connect to your Wavefront dashboard using this one-time use link:
https://wavefront.surf/us/example
```

## Tracing Support

If you'd like to send traces to Wavefront, you can do so using [Micrometer Tracing](https://micrometer.io/docs/tracing). Follow these steps:

1. Choose a Micrometer Tracer for your usecase. For instructions, see [Micrometer's Supported Tracer documentation](https://micrometer.io/docs/tracing#_supported_tracers). For example, to use the Brave Tracer:

   - Maven: Add the following dependency to the `pom.xml` file

     ```
     <dependency>
       <groupId>io.micrometer</groupId>
       <artifactId>micrometer-tracing-bridge-brave</artifactId>
     </dependency>
     ```

   - Gradle: Add the following dependency to the `build.gradle` file:

     ```
     dependencies {
       ...
       implementation 'io.micrometer:micrometer-tracing-bridge-brave'
     }
     ```

1. Add the Micrometer Tracing Reporter for Wavefront:

   - Maven: Add the following dependency to the `pom.xml` file

     ```
     <dependency>
       <groupId>io.micrometer</groupId>
       <artifactId>micrometer-tracing-reporter-wavefront</artifactId>
     </dependency>
     ```

   - Gradle: Add the following dependency to the `build.gradle` file:

     ```
     dependencies {
       ...
       implementation 'io.micrometer:micrometer-tracing-reporter-wavefront'
     }
     ```
     
1. For demo purposes, configure distributed tracing sampling to 100% in `application.properties`:

   ```
   management.tracing.sampling.probability=1.0
   ```

## Building
To build the latest state of this project, invoke the following command from the root directory:

```shell script
$ ./mvnw clean install
```

The project has a sample that showcases the basic usage of the starter.
It starts a web app on `localhost:8080`.
Invoke the following command from the root directory:

```shell script
$ ./mvnw spring-boot:run -pl wavefront-spring-boot-sample
```

## Documentation

* The [Wavefront documentation](https://docs.wavefront.com/wavefront_springboot3.html) includes a tutorial and instructions for examining services and traces inside Wavefront.
* You can [customize existing Spring Boot applications](https://docs.wavefront.com/wavefront_springboot3.html#custom-configurations) to send data to Wavefront.

## License

[Open Source License](open_source_licenses.txt)

## Getting Support

* If you run into any issues, let us know by creating a GitHub issue.
* If you didn't find the information you are looking for in our [Wavefront Documentation](https://docs.wavefront.com/) create a GitHub issue or PR in our [docs repository](https://github.com/wavefrontHQ/docs).

[ci-img]: https://github.com/wavefrontHQ/wavefront-spring-boot/actions/workflows/maven.yml/badge.svg?branch=springboot3
[ci]: https://github.com/wavefrontHQ/wavefront-spring-boot/actions/workflows/maven.yml?query=branch%3aspringboot3
[maven-img]: https://img.shields.io/maven-central/v/com.wavefront/wavefront-spring-boot.svg?maxAge=604800
[maven]: https://search.maven.org/search?q=wavefront-spring-boot
