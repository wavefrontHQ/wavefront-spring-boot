package com.wavefront.springboot;

import com.wavefront.opentracing.WavefrontTracer;
import com.wavefront.opentracing.reporting.Reporter;
import com.wavefront.opentracing.reporting.WavefrontSpanReporter;
import com.wavefront.sdk.common.WavefrontSender;
import com.wavefront.sdk.common.application.ApplicationTags;
import com.wavefront.sdk.direct.ingestion.WavefrontDirectIngestionClient;
import com.wavefront.sdk.proxy.WavefrontProxyClient;
import io.micrometer.core.instrument.Clock;
import io.micrometer.wavefront.WavefrontConfig;
import io.micrometer.wavefront.WavefrontMeterRegistry;
import io.opentracing.Tracer;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.PropertySource;
import org.springframework.core.env.Environment;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import javax.annotation.Nullable;
import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Configuration
// properties file for additional (optional) configuration
@PropertySource(value = "classpath:wavefront.properties", ignoreResourceNotFound = true)
// disable entirely if enabled is not true (defaults to true).
@ConditionalOnProperty(value = "enabled", havingValue = "true", matchIfMissing = true)
public class WavefrontSpringBootAutoConfiguration {

  private static final Log logger = LogFactory.getLog(WavefrontSpringBootAutoConfiguration.class);

  /**
   * Wavefront URL that supports "freemium" accounts.
   */
  static final String WAVEFRONT_DEFAULT_INSTANCE = "https://wavefront.surf";

  /**
   * Read from ${user.home}, when no token is specified in wavefront.properties. When we obtain a token from the server,
   * we will also save that in the file (assuming it's writable).
   */
  static final String WAVEFRONT_TOKEN_FILENAME = ".wavefront_token";
  /**
   * URL for http/https-based reporting. Should be a valid URL.
   */
  public static final String PROPERTY_FILE_KEY_WAVEFRONT_INSTANCE = "url";
  /**
   * Token for http/https-based reporting. Should be a UUID.
   */
  public static final String PROPERTY_FILE_KEY_WAVEFRONT_TOKEN = "token";
  /**
   * Wavefront Proxy host name. See: https://github.com/wavefrontHQ/wavefront-proxy
   */
  public static final String PROPERTY_FILE_KEY_WAVEFRONT_PROXY_HOST = "proxy.host";
  /**
   * Metrics port (defaults to 2878).
   */
  private static final String PROPERTY_FILE_KEY_WAVEFRONT_PROXY_PORT = "proxy.port";
  /**
   * Histogram port (defaults to the same as proxy, which would be 2878).
   */
  private static final String PROPERTY_FILE_KEY_WAVEFRONT_PROXY_HISTOGRAM_PORT = "proxy.histogram_port";
  /**
   * Tracing port (defaults to 30000). Refer to https://github.com/wavefrontHQ/wavefront-proxy/tree/master/proxy#set-up-a-wavefront-proxy
   */
  private static final String PROPERTY_FILE_KEY_WAVEFRONT_PROXY_TRACING_PORT = "proxy.tracing_port";
  /**
   * Reporting duration for metrics (defaults to 1 minute).
   */
  private static final String PROPERTY_FILE_KEY_WAVEFRONT_REPORTING_DURATION = "reporting.duration";
  /**
   * Prefix for metrics (only affects custom-metrics, tracing related metrics/histograms/spans are unaffected).
   */
  private static final String PROPERTY_FILE_KEY_WAVEFRONT_REPORTING_PREFIX = "reporting.prefix";
  /**
   * Source used for reporting to Wavefront (source tag on metrics/histograms/traces). Defaults to
   * {@link java.net.InetAddress#getLocalHost}.
   */
  private static final String PROPERTY_FILE_KEY_WAVEFRONT_REPORTING_SOURCE = "reporting.source";
  /**
   * Whether to report traces (defaults to true).
   */
  private static final String PROPERTY_FILE_KEY_WAVEFRONT_TRACING_ENABLED = "reporting.traces";
  /**
   * Name of the application (otherwise we use "springboot"}.
   */
  static final String PROPERTY_FILE_KEY_WAVEFRONT_APPLICATION = "application.name";
  /**
   * Name of the service (otherwise we use "unnamed_service")".
   */
  static final String PROPERTY_FILE_KEY_WAVEFRONT_SERVICE = "application.service";
  /**
   * Cluster of the service (otherwise we use null"}.
   */
  static final String PROPERTY_FILE_KEY_WAVEFRONT_CLUSTER = "application.cluster";
  /**
   * Shard of the service (otherwise we use null)".
   */
  static final String PROPERTY_FILE_KEY_WAVEFRONT_SHARD = "application.shard";

  /**
   * Howard Mar 1, 2020 internal key for onetimelink - did not exist in original
   */
  static final String PROPERTY_WAVEFRONT_ONETIMELINK = "wavefront.onetimelink";

  @Bean
  public ApplicationTags wavefrontApplicationTags(Environment env) {
    String applicationName = env.getProperty(PROPERTY_FILE_KEY_WAVEFRONT_APPLICATION, "springboot");
    String serviceName = env.getProperty(PROPERTY_FILE_KEY_WAVEFRONT_SERVICE, "unnamed_service");
    ApplicationTags.Builder builder = new ApplicationTags.Builder(applicationName, serviceName);
    @Nullable
    String clusterName = env.getProperty(PROPERTY_FILE_KEY_WAVEFRONT_CLUSTER);
    @Nullable
    String shardName = env.getProperty(PROPERTY_FILE_KEY_WAVEFRONT_SHARD);
    if (clusterName != null) {
      builder.cluster(clusterName);
    }
    if (shardName != null) {
      builder.shard(shardName);
    }
    return builder.build();
  }

  /**
   * {@link WavefrontConfig} is used to configure micrometer but we will reuse it for spans as
   * well if possible. If it's already declared in the user's environment, we'll respect that.
   *
   * <p>Change List</p>
   * <ul>
   *     <li>Changes in getWavefronttokenFromWellKnownFile to retrieve both uri and token.</li>
   *     <li>Changes in logic to first check the uri and token from well known file.</li>
   *     <li>When there is no uri or token found, the original logic would assume.</li>
   * </ul>
   */
  @Bean
  @ConditionalOnMissingBean
  @Conditional(WavefrontConfigConditional.class)
  public WavefrontConfig wavefrontConfig(Environment env, ApplicationTags applicationTags) {
    // there are two methods to report wavefront observability data (proxy or http)
    // we bias to the proxy if it's defined
    @Nullable
    String wavefrontProxyHost = env.getProperty(PROPERTY_FILE_KEY_WAVEFRONT_PROXY_HOST);
    @Nullable
    String wavefrontUri = null;
    @Nullable
    String oneTimeLink = null;
    @Nullable
    String wavefrontToken = null;
    int wavefrontHistogramPort = 2878;
    @Nullable
    String wavefrontSource = env.getProperty(PROPERTY_FILE_KEY_WAVEFRONT_REPORTING_SOURCE);
    if (wavefrontProxyHost == null) {
      boolean manualToken = true;
      // attempt to read from local machine for the url and token to use first.
      Optional<String[]> existingUrlToken = getWavefrontUriTokenFromWellKnownFile();

      // first try to locate the uri from property file (always precedence)
      wavefrontUri = env.getProperty(PROPERTY_FILE_KEY_WAVEFRONT_INSTANCE, WAVEFRONT_DEFAULT_INSTANCE);
      if(wavefrontUri == null) {
        if (existingUrlToken.isPresent()) {
          wavefrontUri = existingUrlToken.get()[0];
          if (wavefrontUri != null && !wavefrontUri.startsWith("http")) {
            // init the uri for new registration
            wavefrontUri = env.getProperty(PROPERTY_FILE_KEY_WAVEFRONT_INSTANCE, WAVEFRONT_DEFAULT_INSTANCE);
            // proceed to generate account registration url
            manualToken = false;
          }
        }
      }
      // post process uri by adding https prefix if not found
      if (wavefrontUri != null && !wavefrontUri.startsWith("http")) {
        wavefrontUri = "https://" + wavefrontUri;
      }
      wavefrontToken = env.getProperty(PROPERTY_FILE_KEY_WAVEFRONT_TOKEN);
      if (wavefrontToken == null) {
        if(existingUrlToken.isPresent()) {
          wavefrontToken = existingUrlToken.get()[1];
        }
      }
      // if token is still not found, raise warning.
      if (wavefrontToken == null) {
        logger.warn("Cannot configure Wavefront Observability for Spring Boot (no credentials available)");
        return null;
      }
      RestTemplateBuilder restTemplateBuilder = new RestTemplateBuilder();
      restTemplateBuilder.setConnectTimeout(Duration.ofSeconds(10));
      restTemplateBuilder.setReadTimeout(Duration.ofSeconds(10));
      RestTemplate restTemplate = restTemplateBuilder.build();
      UriComponentsBuilder uriComponentsBuilder = UriComponentsBuilder.
          fromUriString(wavefrontUri).path(
          "/api/v2/trial/spring-boot-autoconfigure").
          queryParam("application", applicationTags.getApplication()).
          queryParam("service", applicationTags.getService());
      if (applicationTags.getCluster() != null) {
        uriComponentsBuilder.queryParam("cluster", applicationTags.getCluster());
      }
      if (applicationTags.getShard() != null) {
        uriComponentsBuilder.queryParam("shard", applicationTags.getShard());
      }
      uriComponentsBuilder.queryParam("t", wavefrontToken);
      if (!manualToken) {
        try {
          AccountProvisioningResponse resp = restTemplate.getForObject(
              uriComponentsBuilder.build().toUri(),
              AccountProvisioningResponse.class);
          if (resp != null && resp.getUrl() != null) {
            uriComponentsBuilder = UriComponentsBuilder.
                fromUriString(wavefrontUri).path(resp.getUrl());
            String message = "See Wavefront Application Observability Data (one-time use link): " +
                uriComponentsBuilder.build().toUriString();
            oneTimeLink = uriComponentsBuilder.build().toUriString();

            StringBuilder sb = new StringBuilder(message.length());
            for (int i = 0; i < message.length(); i++) {
              sb.append("=");
            }
            logger.info(sb.toString());
            logger.info(message);
            logger.info(sb.toString());
          }
        } catch (RuntimeException ex) {
          // the cluster might not support generating one-time links, we'll just ignore all errors.
          if (logger.isDebugEnabled()) {
            logger.debug("Failed to invoke /api/v2/trial/spring-boot-autoconfigure on: " + wavefrontUri, ex);
          }
          logger.warn("Cannot obtain Wavefront one-time use login link, go to: " + wavefrontUri +
              " to see collected data (or ensure your credentials are still valid)");
        }
      }
    } else {
      // we will use proxy-based reporting.
      String wavefrontProxyPort = env.getProperty(PROPERTY_FILE_KEY_WAVEFRONT_PROXY_PORT, "2878");
      String histogramPortStr = env.getProperty(PROPERTY_FILE_KEY_WAVEFRONT_PROXY_HISTOGRAM_PORT, wavefrontProxyPort);
      try {
        wavefrontHistogramPort = Integer.parseInt(histogramPortStr);
      } catch (RuntimeException ex) {
        throw new IllegalArgumentException("Cannot parse Wavefront histogram port: " + histogramPortStr);
      }
      wavefrontUri = "proxy://" + wavefrontProxyHost + ":" + wavefrontProxyPort;
    }
    String finalWavefrontToken = wavefrontToken;
    int finalWavefrontHistogramPort = wavefrontHistogramPort;
    String finalWavefrontUri = wavefrontUri;
    String finalOneTimeLink = oneTimeLink;
    return new WavefrontConfig() {

      @Override
      public Duration step() {
        String duration = env.getProperty(PROPERTY_FILE_KEY_WAVEFRONT_REPORTING_DURATION, "60");
        try {
          return Duration.ofSeconds(Integer.parseInt(duration));
        } catch (IllegalArgumentException ex) {
          throw new IllegalArgumentException("Invalid Wavefront duration: \"" + duration + "\"", ex);
        }
      }

      @Override
      public String prefix() {
        return env.getProperty(PROPERTY_FILE_KEY_WAVEFRONT_REPORTING_PREFIX, "");
      }

      @Override
      public String uri() {
        return finalWavefrontUri;
      }

      @Override
      public int distributionPort() {
        return finalWavefrontHistogramPort;
      }

      @Override
      public String apiToken() {
        return finalWavefrontToken;
      }

      @Override
      public String source() {
        if (wavefrontSource == null) {
          return WavefrontConfig.super.source();
        } else {
          return wavefrontSource;
        }
      }

      @Override
      public boolean reportMinuteDistribution() {
        return true;
      }

      @Override
      public boolean reportHourDistribution() {
        return false;
      }

      @Override
      public boolean reportDayDistribution() {
        return false;
      }

      @Override
      public String globalPrefix() {
        return env.getProperty(PROPERTY_FILE_KEY_WAVEFRONT_REPORTING_PREFIX, "");
      }

      /**
       * <p>Changes</p>
       * <ul>
       *     <li>Added logic to return finalOneTimeLink given.</li>
       * </ul>
       * @param s
       * @return
       */
      @Override
      public String get(String s) {
        if(s.equalsIgnoreCase(PROPERTY_WAVEFRONT_ONETIMELINK)) {
          return finalOneTimeLink;
        }
        return null;
      }
    };
  }

  static Optional<String[]> getWavefrontUriTokenFromWellKnownFile() {
    String userHomeStr = System.getProperty("user.home");
    if (userHomeStr == null || userHomeStr.length() == 0) {
      if (logger.isDebugEnabled()) {
        logger.debug("System.getProperty(\"user.home\") is empty, cannot obtain local Wavefront token");
      }
      return Optional.empty();
    }
    try {
      File userHome = new File(userHomeStr);
      if (!userHome.exists()) {
        if (logger.isDebugEnabled()) {
          logger.debug("System.getProperty(\"user.home\") does not exist, cannot obtain local Wavefront token");
        }
        return Optional.empty();
      }
      File wavefrontToken = new File(userHome, WAVEFRONT_TOKEN_FILENAME);
      if (wavefrontToken.exists() && wavefrontToken.canRead()) {
        try {
          List<String> tokens = Files.readAllLines(Paths.get(wavefrontToken.toURI()), StandardCharsets.UTF_8);
          String uri = tokens.get(0);
          UUID uuid = UUID.fromString(tokens.get(1));
          String[] result = new String[2];
          result[0] = uri;
          result[1] = uuid.toString();
          return Optional.of(result);
        } catch (IOException ex) {
          logger.warn("Cannot read Wavefront token from: " + wavefrontToken.getAbsolutePath(), ex);
          return Optional.empty();
        } catch (IllegalArgumentException ex) {
          logger.warn("Token found in: " + wavefrontToken.getAbsolutePath() + " is not a valid Wavefront token", ex);
          return Optional.empty();
        }
      } else {
        if (logger.isDebugEnabled()) {
          logger.debug("No token found (or readable) in: " + wavefrontToken.getAbsolutePath());
        }
        return Optional.empty();
      }
    } catch (RuntimeException ex) {
      logger.warn("Failed to resolve a wavefront token from: " + userHomeStr, ex);
      return Optional.empty();
    }
  }

  @Bean
  @ConditionalOnBean(WavefrontConfig.class)
  public WavefrontMeterRegistry wavefrontMeterRegistry(WavefrontConfig wavefrontConfig) {
    logger.info("Activating Wavefront Spring Micrometer Reporting (connection string: " + wavefrontConfig.uri() +
        ", reporting as: " + wavefrontConfig.source() + ")");
    // create a new registry
    return new WavefrontMeterRegistry(wavefrontConfig, Clock.SYSTEM);
  }

  @Bean
  @ConditionalOnMissingBean(WavefrontSender.class)
  @ConditionalOnBean(WavefrontConfig.class)
  public WavefrontSender wavefrontSender(WavefrontConfig wavefrontConfig, Environment env) {
    int wavefrontTracingPort = 30000;
    String tracingPortStr = env.getProperty(PROPERTY_FILE_KEY_WAVEFRONT_PROXY_TRACING_PORT, "30000");
    try {
      wavefrontTracingPort = Integer.parseInt(tracingPortStr);
    } catch (RuntimeException ex) {
      throw new IllegalArgumentException("Cannot parse Wavefront tracing port: " + wavefrontTracingPort);
    }
    if (wavefrontConfig.uri().startsWith("proxy::")) {
      URI wavefrontUri = URI.create(wavefrontConfig.uri());
      return new WavefrontProxyClient.Builder(wavefrontUri.getHost()).
          metricsPort(wavefrontUri.getPort()).
          tracingPort(wavefrontTracingPort).
          distributionPort(wavefrontConfig.distributionPort()).build();
    } else {
      return new WavefrontDirectIngestionClient.Builder(wavefrontConfig.uri(),
          wavefrontConfig.apiToken()).build();
    }
  }

  @Bean
  @ConditionalOnMissingBean(Tracer.class)
  @ConditionalOnBean({WavefrontConfig.class, WavefrontSender.class, ApplicationTags.class})
  public Tracer tracer(WavefrontConfig wavefrontConfig, WavefrontSender wavefrontSender,
                       ApplicationTags applicationTags, Environment env,
                       WavefrontMeterRegistry wavefrontMeterRegistry) {
    @Nullable
    String tracingEnabled = env.getProperty(PROPERTY_FILE_KEY_WAVEFRONT_TRACING_ENABLED);
    if (tracingEnabled != null && !tracingEnabled.equalsIgnoreCase("true")) return null;

    Reporter spanReporter = new WavefrontSpanReporter.Builder().
        withSource(wavefrontConfig.source()).
        build(wavefrontSender);

    logger.info("Activating Wavefront OpenTracing Tracer (connection string: " + wavefrontConfig.uri() +
        ", reporting as: " + wavefrontConfig.source() +
        " " + applicationTags.toPointTags().toString() + ")");

    return new WavefrontTracer.Builder(spanReporter, applicationTags).build();
  }
}
