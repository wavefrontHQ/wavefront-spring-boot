# Wavefront Spring Boot Auto-Configure

`wavefront-spring-boot-autoconfigure` uses Spring Boot's `@EnableAutoConfiguration` which configures various Spring components to emit metrics, histograms and traces to the Wavefront service. The objective is to remove any code changes necessary (apart from configuration or custom reporting dimensions) to enable full observability for a Spring Boot application. 



## Content

1. [Main Content](https://github.com/wavefrontHQ/wavefront-spring-boot-autoconfigure)
2. [Integrate with Maven](#integrate-with-maven)
3. [Auto Configuration](#auto-configuration)
4. [Externalized Configuration](#externalized-configuration)
5. [Dubbo Annotation-Driven (Chinese)](https://github.com/mercyblitz/blogs/blob/master/java/dubbo/Dubbo-Annotation-Driven.md)
6. [Dubbo Externalized Configuration (Chinese)](https://github.com/mercyblitz/blogs/blob/master/java/dubbo/Dubbo-Externalized-Configuration.md)



## Integrate with Maven

You can introduce the latest `wavefront-spring-boot-autoconfigure` to your project by adding the following dependency to your pom.xml

```xml
<dependency>
    <groupId>com.wavefront</groupId>
    <artifactId>wavefront-spring-boot-autoconfigure</artifactId>
    <version>2.0.0</version>
</dependency>
```

## Auto Configuration

By default, configuration of the integration is completely seamless and will negotiate a free but throughput limited account with the Wavefront service. This is achieved by contacting the Wavefront service at https://wavefront.surf to auto create an account for the current session.

Upon the start-up of the application, a single-use login URL is presented which can be used to login to the Wavefront service and access collected data via the browser:

```text
w.s.WavefrontSpringBootAutoConfiguration : ======================================================================================================
w.s.WavefrontSpringBootAutoConfiguration : See Wavefront Application Observability Data (one-time use link): https://wavefront.surf/us/XXXXXXXXXX
w.s.WavefrontSpringBootAutoConfiguration : ======================================================================================================
```

As Wavefront uses a secret token for ingesting data, a local file is used (defaulting to ~/.wavefront_token) to store this token which enables repeated deployments (or local runs of a Spring Boot service) to reuse the same account on the Wavefront service. It is important to note that if the file is deleted (or if the service is moved to another machine) that a new account would be negotiated and hence continuity of data would not be preserved. For information on how to use a consistent token/URL to report data to Wavefront, see the following section on configuring the integration.

## Configuration

For customers who already have a Wavefront trial or account (or even to reuse the same free Wavefront account across machines or deployments), you can specify the URL and token via wavefront.properties.

```properties
url=wavefront.surf
token=UUID of your Direct Ingestion Token
```

There are also other (optional) settings that one can configure:

```properties
proxy.host=wavefront ingestion proxy address or DNS
proxy.port=wavefront proxy port
proxy.
```