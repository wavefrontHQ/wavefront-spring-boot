# Wavefront Spring Boot Starter

This project provides a Spring Boot starter for Wavefront. Adding the starter to a project
that uses the Actuator is all that is required to emit metrics, histograms, and traces to
a Wavefront cluster. If you do not have a Wavefront account yet, the starter will
auto-negotiate one for you and save the api token in your home directory at
`~/.wavefront_freemium`.  

## Table of Content

* [Prerequisites](#Prerequisites)
* [Getting Started](#getting-started)
* [Custom Configuration](#custom-configuration)
* [FAQ](#faq)

## Prerequisites

* Java 8 or above.
* Spring Boot 2.3 or above

This starter reuses the [existing Wavefront support](https://docs.spring.io/spring-boot/docs/current/reference/html/production-ready-features.html#production-ready-metrics-export-wavefront)
in Spring Boot and therefore requires the Actuator (i.e. `spring-boot-starter-actuator`).

This starter also configures [Spring Cloud Sleuth](https://spring.io/projects/spring-cloud-sleuth)
to send traces to Wavefront. To use this feature, consider adding
`org.springframework.cloud:spring-cloud-starter-sleuth`.

## Getting Started

At this time, there is no release of this starter yet and you will need to build the
project first. Invoke the following in the root directory:

```shell script
$ ./mvnw clean install
```

A sample is available to showcase a basic usage of the starter. To start a simple webapp
on `localhost:8080`, invoke the following in the root directory:

```shell script
$ ./mvnw spring-boot:run -pl wavefront-spring-boot-sample
```

If you have an existing Spring Boot application, make sure first it uses Spring Boot 2.3
or later and then add the following dependency to your `pom.xml:

```xml
<dependency>
  <groupId>com.wavefront</groupId>
  <artifactId>wavefront-spring-boot-starter</artifactId>
  <version>2.0.0-SNAPSHOT</version>
</dependency>
```

Or to your `build.gradle` if you are using Gradle:

```
dependencies {
  ...
  implementation 'com.wavefront:wavefront-spring-boot-starter:2.0.0-SNAPSHOT'
  
}
```

To make sure your project brings the blessed versions of required dependencies, a Bill Of
Materials is provided. You could configure the project as follows:

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

Every time the application starts, either an account is auto-negotiated, or it is restored
from `~/.wavefront_freemium`. At the end of the startup phase, a message is logged with a
single-use login URL. Use it to log in to the Wavefront service and access the data that
has been collected so far.

Here is an example message when an existing account is restored from `~/.wavefront_freemium`:

```text
Your existing Wavefront account information has been restored from disk.

To share this account, make sure the following is added to your configuration:

	management.metrics.export.wavefront.api-token=2c96d63a-abcd-efgh-ijhk-841611451e07
	management.metrics.export.wavefront.uri=https://wavefront.surf

Connect to your Wavefront dashboard using this one-time use link:
https://wavefront.surf/us/example
```

If you want to send traces as well, additional dependencies are required, see the
[Spring Cloud Sleuth project](https://spring.io/projects/spring-cloud-sleuth) for more
details.

To get started, consider adding the following dependency to your `pom.xml`:

```xml
<dependency>
  <groupId>org.springframework.cloud</groupId>
  <artifactId>spring-cloud-starter-sleuth</artifactId>
  <version>2.2.2.RELEASE</version>
</dependency>
```

Or to your `build.gradle` if you are using Gradle:

```
dependencies {
  ...
  implementation 'org.springframework.cloud:spring-cloud-starter-sleuth:2.2.2.RELEASE'

}
```

## Custom Configuration

Wavefront uses a secret token to ingest data. Once an account has been auto-negotiated it
is restored from `~/.wavefront_freemium` as long as you run the app from the same machine and
user. If that file is removed, a new account will be created the next time you start the
app. To prevent that from happening, create a login for your account (see the FAQ) and
store the settings in your `application.properties`.

Every time the app starts, it logs the configuration you need to add to
`application.properties`. If you copy that, the account management will stop and the
application will simply use that configuration.

IF you have a web application, you can also expose the Wavefront Actuator endpoint at 
`/actuator/wavefront` to access your Wavefront Dashboard:

```properties
management.endpoints.web.exposure.include=health,info,...,wavefront
```

If you have more than one application, you may want to at least specify the names of the
application and the service in `application.properties`. Here is simple example:

```properties 
wavefront.application.name=my-application
wavefront.application.service=my-service
```

Or if you are using yaml:

```yaml
wavefront:
  application:
    name: my-application
    service: my-service
```

If you have set `spring.application.name` in your application, it is automatically used
as the service name.

The cluster and shard can also be specified the same way. That information is used to
tag metrics and traces out-of-the-box. If you want to take full control over the
`ApplicationTags`, you can create your own `@Bean`. If you want to customize the instance
that is auto-configured, consider adding an `ApplicationTagsBuilderCustomizer` bean.

For further customization of how metrics are exported, check [the Spring Boot reference
guide](https://docs.spring.io/spring-boot/docs/current/reference/html/production-ready-features.html#production-ready-metrics-export-wavefront).


## License

[Open Source License](open_source_licenses.txt)

## FAQ

#### What is the difference between the free cluster and a Wavefront trial?

The free cluster supports limited data ingestion throughput with a 5-day retention and no
SLA guarantees. It allows developers to try out Wavefront without having to sign-up or
provide an email address.

The [Wavefront trial](https://www.wavefront.com/sign-up/) allows you to experience the
full power of the Wavefront platform by bringing in data from your cloud environments
(AWS/GCP/Azure/vSphere), Kubernetes, over 200 integrations, and large-scale service fleets
into a single observability platform. We ask that you tell us more about yourself when
signing up for a trial.

One you've signed up, you can [retrieve an API token](https://docs.wavefront.com/users_account_managing.html#generate-an-api-token)
and configure it in your `application.properties`

#### What is the retention and Service Level Agreement (SLA) on the free cluster?

While this is subject to changes at any time, we currently retain 5 days of data and offer
no SLA on the free Wavefront cluster. Production Wavefront clusters currently offer 18
months of full-resolution (no downsampling) data retention for metrics, 6 months for
histograms, and 30 days for spans. We also have a 99.95% uptime guarantee, as well as High
Availability (HA) and Disaster Recovery (DR) options.

Reach out to us on our public [Slack channel](https://www.wavefront.com/join-public-slack)
for more information.

#### Why do I not see a link to access the Wavefront service on start-up?

* You do not see a single-use login link if you are using a Wavefront cluster that does
not support automatic account provisioning, which currently only applies to our free
cluster. Therefore, make sure you log in to your cluster via the login page or SSO.
* You have configured an API token explicitly in your `application.properties`. Make sure
to also add `wavefront.freemium-account=true` or create a login for your account.
* If you have a web application, expose the `wavefront` actuator endpoint to easily get
access to your dashboard

#### How do I make sure I send data to the same account all the time (across multiple machines and deployments)?

* If you are just trying out Wavefront, see [Manage Service Accounts](
https://docs.wavefront.com/accounts.html#service-accounts) to create a service account that has a
static token for reporting. Once you have the token, add it to `application.properties`.
* If you are using Wavefront in a larger deployment, sign-up for a Wavefront trial at
www.wavefront.com and see [Manage Service Accounts](
https://docs.wavefront.com/accounts.html#service-accounts) to learn how to create a service account.
Next, add the token and URL to `application.properties`. We are more than happy to help
you with sizing and designing large-scale collection architectures for metrics,
histograms, and traces.
 
#### How do I setup an email/password login to the account?

When you sign-in to your account via the single-use link, click the gear icon on the
top-right and select Account Management. Next, you can invite yourself by email (a password
setup link is sent to your email address, and you can use it to set up a password).

If you have set `wavefront.freemium-account=true` in your `application.properties`, make
sure to remove it so that a single-use login URL is no longer requested on startup.

#### How do I get help?

We have a public [Slack channel](https://www.wavefront.com/join-public-slack) as well as
in-product help, [documentation](https://docs.wavefront.com/), and chat.
