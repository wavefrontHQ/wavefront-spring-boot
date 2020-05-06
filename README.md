# Wavefront for Spring Boot

This project provides a Spring Boot starter for Wavefront. Add the starter to a project to emit metrics, histograms, and traces to a Wavefront cluster. If you do not have a Wavefront account, the starter will auto-negotiate a freemium account for you and save the API token in your home directory at `~/.wavefront_freemium`.

## Table of Content

* [Prerequisites](#prerequisites)
* [Getting Started](#getting-started)
* [Custom Configuration](#custom-configuration)
* [Documentation](#documentation)
* [License](#license)
* [How to Contribute](#how-to-contribute)

## Prerequisites

* Spring Boot 2.3 or above
* Java 8 or above
* Maven 3.3+ or Gradle 6.3 or later
  <br/>See [System Requirements](https://docs.spring.io/spring-boot/docs/2.3.0.RC1/reference/html/getting-started.html#getting-started-system-requirements) in the Spring Boot documentation. 

> Note: This starter reuses the [existing Wavefront support](https://docs.spring.io/spring-boot/docs/current/reference/html/production-ready-features.html#production-ready-metrics-export-wavefront)
in Spring Boot and provides the Actuator (i.e., `spring-boot-starter-actuator`).

## Getting Started

* If you want to start from scratch, build the
project first. Invoke the following command in the root directory:

  ```shell script
  $ ./mvnw clean install
  ```
* Start a sample web app on `localhost:8080` to see the basic usage of the starter. Invoke the following command in the root directory:

  ```shell script
  $ ./mvnw spring-boot:run -pl wavefront-spring-boot-sample
  ```

* If you already have a Spring Boot application, make sure to use Spring Boot 2.3 or later and add the following dependency to your `pom.xml` file:

    ```xml
    <dependency>
      <groupId>com.wavefront</groupId>
      <artifactId>wavefront-spring-boot-starter</artifactId>
      <version>2.0.0-RC1</version>
    </dependency>
    ```

    If you are using Gradle add the following dependency to your `build.gradle` file:

    ```
    dependencies {
      ...
      implementation 'com.wavefront:wavefront-spring-boot-starter:2.0.0-SNAPSHOT'
      
    }
    ```

    The example given below uses the correct dependency versions.
    ```xml
    <dependencies>
      <dependency>
        <groupId>com.wavefront</groupId>
        <artifactId>wavefront-spring-boot-starter</artifactId>
      </dependency>
    </dependencies>

    <dependencyManagement>
      <dependencies>
        <dependency>
          <groupId>com.wavefront</groupId>
          <artifactId>wavefront-spring-boot</artifactId>
          <version>2.0.0-SNAPSHOT</version>
          <type>pom</type>
          <scope>import</scope>
        </dependency>
      </dependencies>
    </dependencyManagement>
    ```

* Every time the application starts, either an account is auto-negotiated, or it is restored
  from `~/.wavefront_freemium`. At the end of the startup phase, a message is logged with a
  single-use login URL. Use it to log in to the Wavefront service and access the data that
  has been collected so far.

  Here is an example message when an existing account is restored from `~/.wavefront_freemium`:

  ```text
    Your existing Wavefront account information has been restored from disk.

    management.metrics.export.wavefront.api-token=2c96d63a-abcd-efgh-ijhk-841611451e07
    management.metrics.export.wavefront.uri=https://wavefront.surf

    Connect to your Wavefront dashboard using this one-time use link:
    https://wavefront.surf/us/example
  ```

* Add the following dependency to send traces to Wavefront using 
[Spring Cloud Sleuth](https://spring.io/projects/spring-cloud-sleuth) or [OpenTracing](https://opentracing.io/).

  * **Spring Cloud Sleuth**: 
    
    Add the following dependency to your `pom.xml` file:

    ```xml
    <dependency>
      <groupId>org.springframework.cloud</groupId>
      <artifactId>spring-cloud-starter-sleuth</artifactId>
      <version>2.2.2.RELEASE</version>
    </dependency>
    ```

    If you are using Gradle add the following dependency to the `build.gradle` file:

    ```
    dependencies {
      ...
      implementation 'org.springframework.cloud:spring-cloud-starter-sleuth:2.2.2.RELEASE'

    }
    ```
  * **OpenTracing**
    
    Add the following dependencies to your `pom.xml` file:
    ```xml
      <dependency>
        <groupId>io.opentracing.contrib</groupId>
        <artifactId>opentracing-spring-cloud-starter</artifactId>
        <version>0.5.3</version>
      </dependency>
    ```
    If you are using Gradle add the following dependencies to the `build.gradle` file:

    ```
    dependencies {
      ...
      implementation 'io.opentracing.contrib:opentracing-spring-cloud-starter:0.5.3'

    }
    ```

## Documentation

* For details see the [Wavefront documentation](https://docs.wavefront.com/wavefront_springboot.html).
* See [Custom Configurations](https://docs.wavefront.com/wavefront_springboot.html#optional-custom-configurations) for details on customizing your Spring Boot application to send data to Wavefront.

## License

[Open Source License](open_source_licenses.txt)

## How to Contribute

* Reach out to us on our public [Slack channel](https://www.wavefront.com/join-public-slack).
* If you run into any issues, let us know by creating a GitHub issue.
* If you didn't find the information you are looking for in our [Wavefront Documentation](https://docs.wavefront.com/) create a GitHub issue or PR in our [docs repository](https://github.com/wavefrontHQ/docs).
