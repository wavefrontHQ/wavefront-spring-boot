# Wavefront for Spring Boot

This project provides a Spring Boot starter for Wavefront. Add the starter to a project to send metrics, histograms, and traces to a Wavefront cluster. If you don't have a Wavefront account, the starter will create a freemium account for you and save the API token in your home directory at `~/.wavefront_freemium`.

## Table of Content

* [Prerequisites](#prerequisites)
* [Getting Started](#getting-started)
* [Building](#building)
* [Custom Configuration](#custom-configuration)
* [Documentation](#documentation)
* [License](#license)
* [Getting Support](#getting-support)

## Prerequisites

* Spring Boot 2.3 or above
* Java 8 or above
* Maven 3.3+ or Gradle 6.3 or later\
  See [System Requirements](https://docs.spring.io/spring-boot/docs/2.3.x/reference/html/getting-started.html#getting-started-system-requirements) in the Spring Boot documentation.

> Note: This starter reuses the [existing Wavefront support](https://docs.spring.io/spring-boot/docs/2.3.x/reference/html/production-ready-features.html#production-ready-metrics-export-wavefront)
in Spring Boot and provides the Actuator (i.e., `spring-boot-starter-actuator`).

## Getting Started

The easiest way to get started is to create a new project on [start.spring.io](https://start.spring.io).
Select Spring Boot `2.3.0` or later and define the other parameters for your project.
Click "Add dependency" and select `Wavefront` from the dependency list.

If you want to opt-in for tracing support, add the "[Spring Cloud Sleuth](https://spring.io/projects/spring-cloud-sleuth)" entry as well.

If you already have a Spring Boot application, you can also use [start.spring.io](https://start.spring.io) to explore a new project from your browser.
This allows you to see the setup for the Spring Boot generation your project is using.

For completeness, here is what you should follow to configure your project.

> **Note**: The 2.0.2 version is the latest version of the starter. The Wavefront for Spring Boot dependency needs to be compatible with the Spring Boot release version. See [System Requirements](https://docs.wavefront.com/wavefront_springboot.html#versionCompatibility) to get the correct dependency version.

Configure your project using Maven or Gradle.

**Maven**

- The core setup consists of importing the `wavefront-spring-boot-bom` Bill Of Materials (BOM).

  ```
  <dependencyManagement>
    <dependencies>
      <dependency>
        <groupId>com.wavefront</groupId>
        <artifactId>wavefront-spring-boot-bom</artifactId>
        <version>2.0.2</version>
        <type>pom</type>
        <scope>import</scope>
      </dependency>
    </dependencies>
  </dependencyManagement>
  ```

- Add the `wavefront-spring-boot-starter` to your project.

  ```
  <dependency>
    <groupId>com.wavefront</groupId>
    <artifactId>wavefront-spring-boot-starter</artifactId>
  </dependency>
  ```

**Gradle**

- If you are using Gradle, make sure your project uses the `io.spring.dependency-management` plugin and add the following to your build.gradle file:

  ```
  dependencyManagement {
    imports {
      mavenBom "com.wavefront:wavefront-spring-boot-bom:2.0.2"
    }
  }
  ```

- Add the `wavefront-spring-boot-starter` to your project.

  ```
  dependencies {
    ...
    implementation 'com.wavefront:wavefront-spring-boot-starter'

  }
  ```

Each time you restart your application, it either creates a new freemium account, or it restores from `~/.wavefront_freemium`.
At the end of the startup phase, the console displays a message with a login URL.
Use it to log in to the Wavefront service and access the data that has been collected so far.

Here is an example message when an existing account is restored from `~/.wavefront_freemium`:

```text
Your existing Wavefront account information has been restored from disk.

management.metrics.export.wavefront.api-token=2c96d63a-abcd-efgh-ijhk-841611451e07
management.metrics.export.wavefront.uri=https://wavefront.surf

Connect to your Wavefront dashboard using this one-time use link:
https://wavefront.surf/us/example
```

## Tracing Support

If you'd like to send traces to Wavefront, you can do so using [Spring Cloud Sleuth](https://spring.io/projects/spring-cloud-sleuth) or [OpenTracing](https://opentracing.io/).

**Spring Cloud Sleuth**

Each Spring Boot generation has a matching Spring Cloud generation.
See [Getting Started on the Spring Cloud documentation](https://spring.io/projects/spring-cloud#getting-started) for details.

After you've added the spring-cloud-dependencies BOM, you can add Spring Cloud Sleuth as follows:

- Maven: Add the following dependency to the `pom.xml` file

  ```
  <dependency>
    <groupId>org.springframework.cloud</groupId>
    <artifactId>spring-cloud-starter-sleuth</artifactId>
  </dependency>
  ```

- Gradle: Add the following dependency to the `build.gradle` file:

  ```
  dependencies {
    ...
    implementation 'org.springframework.cloud:spring-cloud-starter-sleuth'
  }
  ```

**OpenTracing**

Configure your `pom.xml` file or the `build.gradle` file.

- Maven: Add the following dependencies to your `pom.xml` file:

  ```
  <dependency>
    <groupId>io.opentracing.contrib</groupId>
    <artifactId>opentracing-spring-cloud-starter</artifactId>
    <version>0.5.7</version>
  </dependency>
  ```

- Gradle: Add the following dependencies to the `build.gradle` file:

  ```
  dependencies {
    ...
    implementation 'io.opentracing.contrib:opentracing-spring-cloud-starter:0.5.7'
  }
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

* The [Wavefront documentation](https://docs.wavefront.com/wavefront_springboot.html) includes a tutorial and instructions for examining services and traces inside Wavefront. 
* You can [customize existing Spring Boot applications](https://docs.wavefront.com/wavefront_springboot.html#optional-custom-configurations) to send data to Wavefront. 

## License

[Open Source License](open_source_licenses.txt)

## Getting Support

* Reach out to us on [Slack](https://www.wavefront.com/slack-us) and join the #springboot public channel.
* If you run into any issues, let us know by creating a GitHub issue.
* If you didn't find the information you are looking for in our [Wavefront Documentation](https://docs.wavefront.com/) create a GitHub issue or PR in our [docs repository](https://github.com/wavefrontHQ/docs).
