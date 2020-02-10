# Wavefront Spring Boot Auto-Configure

`wavefront-spring-boot-autoconfigure` uses Spring Boot's `@EnableAutoConfiguration` which configures various Spring components to emit metrics, histograms and traces to the Wavefront service. The objective is to remove any code changes necessary (apart from configuration or custom reporting dimensions) to enable full observability for a Spring Boot application. 

## Content

1. [Main Content](https://github.com/wavefrontHQ/wavefront-spring-boot-autoconfigure)
2. [Integrate with Maven](#integrate-with-maven)
3. [Auto Configuration](#auto-configuration)
4. [Custom Configuration](#custom-configuration)
5. [FAQ](#faq)


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

## Custom Configuration

For customers who already have a Wavefront trial or account (or even to reuse the same free Wavefront account across machines or deployments), you can specify the URL and token via wavefront.properties.

```properties
url=(e.g. wavefront.surf)
token=(UUID of your Direct Ingestion Token)
```

There are also other (optional) settings that one can configure:

```properties
proxy.host=(wavefront ingestion proxy address or DNS)
proxy.port=(wavefront proxy port)
proxy.histogram_port=(wavefront proxy port for histogram)
proxy.tracing_port=(wavefront proxy port for spans)

reporting.duration=(reporting duration, intervals between spring micrometer reporting)
reporting.prefix=(reporting.prefix for spring micrometer)
reporting.source=(hostname to use for reporting, defaults to machine host name)
reporting.traces=(boolean, wheter to report traces or just micrometer metrics)

application.name=(defaults to "springboot")
application.service=(defaults to "unnamed_service")
application.cluster=(defaults to nothing)
application.shard=(defaults to nothing)
```

You can also supply your own bean for WavefrontSender, WavefrontConfig and ApplicationTags for code-based configuration.

## FAQ

### What is the difference between the free Spring Boot cluster and a Wavefront trial?

The free cluster supports limited data ingestion throughput with a 5-day retention and no SLA guarantees. It allows Spring Boot developers to try out Wavefront without having to supply an email address or sign-up.

A [Wavefront trial](https://www.wavefront.com/sign-up/) allows anybody to experience the full-power of the Wavefront platform by bringing in data from your cloud environments (AWS/GCP/Azure/vSphere), Kubernetes, over 200 integrations, and large-scale service fleets into a single observability platform. We ask that you tell us more about yourself when signing up for a trial.

### What is the retention and SLA on the free cluster?

While this is subject to changes at any time, we currently retain 5 days of data and offer no SLA on the free Wavefront cluster. Production Wavefront clusters currently offers standard 18 months of full-resolution (no downsampling) data retention for metrics as well as 6 months for histograms and 30 days for spans. We also have 99.95% uptime guarantee, as well as HA and DR options.

Talk to us for more information.

### Why do I not see a link to access the Wavefront service on start-up?

1. You do not see a single-use login link if you're using any Wavefront cluster that does not support automatic account provisioning (which currently only applies to our free cluster). You will need to login to your cluster the "normal" way (via the login page or SSO).
2. You do not have a valid token (check that the file .wavefront_token in your home directory has a valid token or delete it to automatically provision a new account on the free cluster). Note that you will lose access to your existing account if you do not have a backup to the token file.

If you do see:

```text
w.s.WavefrontSpringBootAutoConfiguration : Activating Wavefront Spring Micrometer Reporting...
w.s.WavefrontSpringBootAutoConfiguration : Activating Wavefront OpenTracing Tracer...
```

Reporting to the Wavefront service is setup properly nonetheless (even if the login link is absent).

### How do I make sure I send data to the same account all the time (across multiple machines and deployments)?

1. If you are just trying out Wavefront, you can check out the instructions at https://docs.wavefront.com/service_accounts.html to create a service account to have a static token for reporting (and adding it to wavefront.properties, i.e. token=<your_token>).
2. If you wish to use Wavefront in a larger deployment context, sign-up for a Wavefront trial at www.wavefront.com and see https://docs.wavefront.com/service_accounts.html to learn about how to create a service account (also adding it to wavefront.properties and adding the proper url, for example url=longboard.wavefront.com). We would be more than happy to help with sizing and designing large-scale collection architectures for metrics, histograms and traces.
 
### How do I setup an email/password login to the account?

When you have signed-in to your account via the single-use link, click the gear icon on the top-right and select User Management, there you can invite yourself by email (a password setup link would be sent to your email address and you can use it to setup a password).

### How do a get help?

We have a public [Slack channel](https://www.wavefront.com/join-public-slack) as well as in-product help, documentation and chat.