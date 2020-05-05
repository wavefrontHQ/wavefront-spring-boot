# Wavefront Spring Boot Starter

This project provides a Spring Boot starter for Wavefront. Add the starter to a project to emit metrics, histograms, and traces to a Wavefront cluster. If you do not have a Wavefront account, the starter will auto-negotiate a freemium account for you and save the API token in your home directory at `~/.wavefront_freemium`.

## Table of Content

* [Prerequisites](#prerequisites)
* [Getting Started](#getting-started)
* [Custom Configuration](#custom-configuration)
* [Documentation](#documentation)
* [License](#license)
* [How to Contribute](#how-to-contribute)

## Prerequisites

* Java 8 or above.
* Spring Boot 2.3 or above

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

* If you have an existing Spring Boot application, make sure to use Spring Boot 2.3 or later, and then add the following dependency to your `pom.xml` file:

    ```xml
    <dependency>
      <groupId>com.wavefront</groupId>
      <artifactId>wavefront-spring-boot-starter</artifactId>
      <version>2.0.0-RC1</version>
    </dependency>
    ```

    Or if you are using Gradle add the following dependency to your `build.gradle` file:

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

* Send traces to Wavefront using 
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

    Or if you are using Gradle add the following dependency to the `build.gradle` file:

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
    Or if you are using Gradle add the following dependencies to the `build.gradle` file:

    ```
    dependencies {
      ...
      implementation 'io.opentracing.contrib:opentracing-spring-cloud-starter:0.5.3'

    }
    ```

## Custom Configuration

* Wavefront uses a secret token to ingest data. Once an account is auto-negotiated it
is restored from `~/.wavefront_freemium` as long as the same user runs the application from the same machine. If that file is removed, a new account will be created the next time you start the
app. To prevent that from happening, create a [login for your account](#how-do-i-make-sure-i-send-data-to-the-same-account-all-the-time-across-multiple-machines-and-deployments) and
store the settings in your `application.properties`.

  Every time the app starts, it logs the configuration you need to add to
  `application.properties`. If you copy that, the account management will stop, and the
  application will simply use that configuration.

* If you have a web app, you can expose the Wavefront Actuator endpoint at 
`/actuator/wavefront` to access your Wavefront Dashboard:

  ```properties
  management.endpoints.web.exposure.include=health,info,...,wavefront
  ```

* If you have more than one application, you can specify the names of the
application and the service in `application.properties`.<br/>
Example:

  ```properties 
  wavefront.application.name=my-application
  wavefront.application.service=my-service
  ```

  Or if you are using YAML:

  ```yaml
  wavefront:
    application:
      name: my-application
      service: my-service
  ```

    Optionally:
    * If you have set `spring.application.name` in your application, it is automatically used
    as the service name.
    * The cluster and shard can also be specified the same way. That information is used to
    tag metrics and traces out-of-the-box. 
    * If you want to take full control over the
    `ApplicationTags`, you can create a `@Bean`. 
    * If you want to customize the instance
    that is auto-configured, consider adding an `ApplicationTagsBuilderCustomizer` bean.

For further customization on how metrics are exported, check [the Spring Boot reference
guide](https://docs.spring.io/spring-boot/docs/current/reference/html/production-ready-features.html#production-ready-metrics-export-wavefront).

## Documentation

For details about Wavefront, see the [Wavefront documentation](https://docs.wavefront.com/wavefront_springboot.html).

## License

[Open Source License](open_source_licenses.txt)

## How to Contribute

* Reach out to us on our public [Slack channel](https://www.wavefront.com/join-public-slack).
* If you run into any issues, let us know by creating a GitHub issue.
* If you didn't find the information you are looking for in our [Wavefront Documentation](https://docs.wavefront.com/) create a GitHub issue or PR in our [docs repository](https://github.com/wavefrontHQ/docs).
