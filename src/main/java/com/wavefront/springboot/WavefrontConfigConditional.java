package com.wavefront.springboot;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Condition;
import org.springframework.context.annotation.ConditionContext;
import org.springframework.context.annotation.PropertySource;
import org.springframework.core.env.Environment;
import org.springframework.core.type.AnnotatedTypeMetadata;

import javax.annotation.Nullable;
import java.util.Optional;

import static com.wavefront.springboot.Initializer.*;

/**
 * Spring {@link Condition} that decides whether the {@link Initializer} would offer a
 * {@link io.micrometer.wavefront.WavefrontConfig} bean. We need certain conditions to be met before a valid
 * Wavefront Configuration object can be returned successfully.
 *
 * @author Clement Pang (clement@wavefront.com).
 */
@PropertySource(value = "classpath:wavefront.properties", ignoreResourceNotFound = true)
public class WavefrontConfigConditional implements Condition {

  private static final Logger logger = LoggerFactory.getLogger(WavefrontConfigConditional.class);

  @Override
  public boolean matches(ConditionContext context, AnnotatedTypeMetadata metadata) {
    Environment env = context.getEnvironment();
    // there are two methods to report wavefront observability data (proxy or http)
    // we bias to the proxy if it's defined
    @Nullable
    String wavefrontProxyHost = env.getProperty(PROPERTY_FILE_KEY_WAVEFRONT_PROXY_HOST);
    @Nullable
    String wavefrontToken;
    if (wavefrontProxyHost == null) {
      // we assume http reporting. defaults to wavefront.surf
      wavefrontToken = env.getProperty(PROPERTY_FILE_KEY_WAVEFRONT_TOKEN);
      if (wavefrontToken == null) {
        // attempt to read from local machine for the token to use.
        Optional<String> existingToken = getWavefrontTokenFromWellKnownFile();
        if (existingToken.isPresent()) wavefrontToken = existingToken.get();
      }
      if (wavefrontToken == null) {
        logger.warn("Cannot configure Wavefront Observability for Spring Boot (no credentials available)");
        return false;
      }
    }
    return true;
  }
}
