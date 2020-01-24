package com.wavefront.springboot;

import com.wavefront.opentracing.WavefrontTracer;
import com.wavefront.opentracing.reporting.Reporter;
import com.wavefront.opentracing.reporting.WavefrontSpanReporter;
import com.wavefront.sdk.common.WavefrontSender;
import com.wavefront.sdk.common.application.ApplicationTags;
import com.wavefront.sdk.direct.ingestion.WavefrontDirectIngestionClient;
import com.wavefront.sdk.proxy.WavefrontProxyClient;
import io.micrometer.core.instrument.Clock;
import io.micrometer.core.instrument.binder.jvm.ClassLoaderMetrics;
import io.micrometer.core.instrument.binder.jvm.JvmGcMetrics;
import io.micrometer.core.instrument.binder.jvm.JvmMemoryMetrics;
import io.micrometer.core.instrument.binder.jvm.JvmThreadMetrics;
import io.micrometer.core.instrument.binder.system.FileDescriptorMetrics;
import io.micrometer.core.instrument.binder.system.ProcessorMetrics;
import io.micrometer.core.instrument.binder.system.UptimeMetrics;
import io.micrometer.wavefront.WavefrontConfig;
import io.micrometer.wavefront.WavefrontMeterRegistry;
import io.opentracing.Tracer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.PropertySource;
import org.springframework.core.env.Environment;

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
public class Initializer {

  private static final Logger logger = LoggerFactory.getLogger(Initializer.class);

  /**
   * Wavefront URL that supports "freemium" accounts.
   */
  static final String WAVEFRONT_DEFAULT_INSTANCE = "https://dev.corp.wavefront.com";

  /**
   * Read from ${user.home}, when no token is specified in wavefront.properties. When we obtain a token from the server,
   * we will also save that in the file (assuming it's writable).
   */
  private static final String WAVEFRONT_TOKEN_FILENAME = ".wavefront_token";

  /**
   * This is mainly used to <b>disable</b> wavefront-reporting. If the dependency is present, we would assume reporting
   * is on-by-default. If enabled=false, we would shut-off all reporting.
   */
  private static final String PROPERTY_FILE_KEY_WAVEFRONT_ENABLED = "enabled";
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
  private static final String PROPERTY_FILE_KEY_WAVEFRONT_PROXY_HISTOGRAM_PORT = "proxy.histgoram_port";
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
  private static final String PROPERTY_FILE_KEY_WAVEFRONT_APPLICATION = "application.name";

  /**
   * Name of the service (otherwise we use {@link ApplicationContext#getApplicationName()}.
   */
  private static final String PROPERTY_FILE_KEY_WAVEFRONT_SERVICE = "application.service";

  /**
   * {@link WavefrontConfig} is used to configure micrometer but we will reuse it for spans as well if possible. If it's
   * already declared in the user's environment, we'll respect that.
   */
  @Bean
  @ConditionalOnProperty(value = "enabled", havingValue = "true", matchIfMissing = true)
  @ConditionalOnMissingBean(WavefrontConfig.class)
  @Conditional(WavefrontConfigConditional.class)
  public WavefrontConfig wavefrontConfig(Environment env) {
    // there are two methods to report wavefront observability data (proxy or http)
    // we bias to the proxy if it's defined
    @Nullable
    String wavefrontProxyHost = env.getProperty(PROPERTY_FILE_KEY_WAVEFRONT_PROXY_HOST);
    String wavefrontUri;
    @Nullable
    String wavefrontToken = null;
    int wavefrontHistogramPort = 2878;
    @Nullable
    String wavefrontSource = env.getProperty(PROPERTY_FILE_KEY_WAVEFRONT_REPORTING_SOURCE);
    if (wavefrontProxyHost == null) {
      // we assume http reporting. defaults to wavefront.surf
      wavefrontUri = env.getProperty(PROPERTY_FILE_KEY_WAVEFRONT_INSTANCE, WAVEFRONT_DEFAULT_INSTANCE);

      wavefrontToken = env.getProperty(PROPERTY_FILE_KEY_WAVEFRONT_TOKEN);
      if (wavefrontToken == null) {
        // attempt to read from local machine for the token to use.
        Optional<String> existingToken = getWavefrontTokenFromWellKnownFile();
        if (existingToken.isPresent()) wavefrontToken = existingToken.get();
      }
      if (wavefrontToken == null) {
        logger.warn("Cannot configure Wavefront Observability for Spring Boot (no credentials available)");
        return null;
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

      @Override
      public String get(String s) {
        return null;
      }
    };
  }

  static Optional<String> getWavefrontTokenFromWellKnownFile() {
    String userHomeStr = System.getProperty("user.home");
    if (userHomeStr == null || userHomeStr.length() == 0) {
      logger.debug("System.getProperty(\"user.home\") is empty, cannot obtain local Wavefront token");
      return Optional.empty();
    }
    try {
      File userHome = new File(userHomeStr);
      if (!userHome.exists()) {
        logger.debug("System.getProperty(\"user.home\") does not exist, cannot obtain local Wavefront token");
        return Optional.empty();
      }
      File wavefrontToken = new File(userHome, WAVEFRONT_TOKEN_FILENAME);
      if (wavefrontToken.exists() && wavefrontToken.canRead()) {
        try {
          List<String> tokens = Files.readAllLines(Paths.get(wavefrontToken.toURI()), StandardCharsets.UTF_8);
          UUID uuid = UUID.fromString(tokens.get(0));
          return Optional.of(uuid.toString());
        } catch (IOException ex) {
          logger.warn("Cannot read Wavefront token from: " + wavefrontToken.getAbsolutePath(), ex);
          return Optional.empty();
        } catch (IllegalArgumentException ex) {
          logger.warn("Token found in: " + wavefrontToken.getAbsolutePath() + " is not a valid Wavefront token", ex);
          return Optional.empty();
        }
      } else {
        logger.debug("No token found (or readable) in: " + wavefrontToken.getAbsolutePath());
        return Optional.empty();
      }
    } catch (RuntimeException ex) {
      logger.warn("Failed to resolve a wavefront token from: " + userHomeStr, ex);
      return Optional.empty();
    }
  }

  @Bean
  @ConditionalOnMissingBean(WavefrontMeterRegistry.class)
  public WavefrontMeterRegistry wavefrontMeterRegistry(WavefrontConfig wavefrontConfig, Environment env) {
    if (!env.getProperty(PROPERTY_FILE_KEY_WAVEFRONT_ENABLED, "true").equalsIgnoreCase("true") ||
        wavefrontConfig == null) {
      logger.info("Disabling Wavefront for Micrometer Integration via wavefront.properties (enabled != true)");
      // once we have the dependency, spring boot will try to configure it (and the apiToken would be missing).
      // we'll have to return a meter registry *in some way*, unless we have a blackhole meter registry, we'll need to
      // emit the data to a random url.
      return new WavefrontMeterRegistry(new WavefrontConfig() {

        @Override
        public String get(String s) {
          return null;
        }

        @Override
        public Duration step() {
          return Duration.ofMinutes(5);
        }

        @Override
        public String apiToken() {
          return "abcde";
        }

        @Override
        public String uri() {
          return "http://blackhole-1.iana.org";
        }
      }, Clock.SYSTEM);
    }
    logger.info("Activating Wavefront for Micrometer Integration (connection string: " + wavefrontConfig.uri() +
        ", reporting as: " + wavefrontConfig.source() + ")");

    // create a new registry
    WavefrontMeterRegistry registry = new WavefrontMeterRegistry(wavefrontConfig, Clock.SYSTEM);
    // default JVM stats
    new ClassLoaderMetrics().bindTo(registry);
    new JvmMemoryMetrics().bindTo(registry);
    new JvmGcMetrics().bindTo(registry);
    new ProcessorMetrics().bindTo(registry);
    new JvmThreadMetrics().bindTo(registry);
    new FileDescriptorMetrics().bindTo(registry);
    new UptimeMetrics().bindTo(registry);

    return registry;
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
      return new WavefrontDirectIngestionClient.Builder(wavefrontConfig.uri(), wavefrontConfig.apiToken()).build();
    }
  }

  @Bean
  @ConditionalOnMissingBean(ApplicationTags.class)
  public ApplicationTags wavefrontApplicationTags(Environment env, ApplicationContext applicationContext) {
    String applicationName = env.getProperty(PROPERTY_FILE_KEY_WAVEFRONT_APPLICATION, "springboot");
    String serviceName = env.getProperty(PROPERTY_FILE_KEY_WAVEFRONT_SERVICE, applicationContext.getApplicationName());
    if (serviceName.trim().length() == 0) {
      serviceName = "unnamed_service";
    }
    return new ApplicationTags.Builder(applicationName, serviceName).build();
  }

  @Bean
  @ConditionalOnMissingBean(Tracer.class)
  @ConditionalOnBean({WavefrontConfig.class, WavefrontSender.class, ApplicationTags.class})
  public Tracer tracer(WavefrontConfig wavefrontConfig, WavefrontSender wavefrontSender,
                       ApplicationTags applicationTags, Environment env) {
    @Nullable
    String tracingEnabled = env.getProperty(PROPERTY_FILE_KEY_WAVEFRONT_TRACING_ENABLED);
    if (tracingEnabled != null && !tracingEnabled.equalsIgnoreCase("true")) return null;

    Reporter spanReporter = new WavefrontSpanReporter.Builder().
        withSource(wavefrontConfig.source()).
        build(wavefrontSender);

    logger.info("Activating Wavefront OpenTracing Tracer (connection string: " + wavefrontConfig.uri() +
        ", reporting as: " + wavefrontConfig.source() +
        " {" + applicationTags.toPointTags().toString() + "})");

    return new WavefrontTracer.Builder(spanReporter, applicationTags).build();
  }
}
