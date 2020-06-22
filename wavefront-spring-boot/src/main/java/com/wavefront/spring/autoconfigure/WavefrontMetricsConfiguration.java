package com.wavefront.spring.autoconfigure;

import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;

import com.wavefront.sdk.appagent.jvm.reporter.WavefrontJvmReporter;
import com.wavefront.sdk.common.WavefrontSender;
import com.wavefront.sdk.common.application.ApplicationTags;
import io.micrometer.core.instrument.Tag;
import io.micrometer.core.instrument.Tags;
import io.micrometer.wavefront.WavefrontConfig;
import io.micrometer.wavefront.WavefrontMeterRegistry;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.actuate.autoconfigure.metrics.MeterRegistryCustomizer;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.PropertyMapper;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Configuration for Wavefront metrics.
 *
 * @author Stephane Nicoll
 * @author Tommy Ludwig
 */
@Configuration(proxyBeanMethods = false)
@ConditionalOnBean(WavefrontSender.class)
class WavefrontMetricsConfiguration {

  @Bean
  @ConditionalOnMissingBean
  @ConditionalOnProperty(name = "wavefront.metrics.extract-jvm-metrics", matchIfMissing = true)
  WavefrontJvmReporter wavefrontJvmReporter(WavefrontSender wavefrontSender, ApplicationTags applicationTags,
      WavefrontConfig wavefrontConfig) {
    WavefrontJvmReporter reporter = new WavefrontJvmReporter.Builder(applicationTags)
        .withSource(wavefrontConfig.source()).build(wavefrontSender);
    reporter.start();
    return reporter;
  }


  @Configuration(proxyBeanMethods = false)
  @ConditionalOnClass({ WavefrontMeterRegistry.class, MeterRegistryCustomizer.class })
  static class MicrometerConfiguration {

    @Bean
    MeterRegistryCustomizer<WavefrontMeterRegistry> wavefrontTagsMeterRegistryCustomizer(
        ObjectProvider<ApplicationTags> applicationTags) {
      return (registry) -> applicationTags
          .ifUnique((appTags) -> registry.config().commonTags(createTagsFrom(appTags)));
    }

    private Iterable<Tag> createTagsFrom(ApplicationTags applicationTags) {
      Map<String, String> tags = new HashMap<>();
      PropertyMapper mapper = PropertyMapper.get().alwaysApplyingWhenNonNull();
      mapper.from(applicationTags::getApplication).to((application) -> tags.put("application", application));
      mapper.from(applicationTags::getService).to((service) -> tags.put("service", service));
      mapper.from(applicationTags::getCluster).to((cluster) -> tags.put("cluster", cluster));
      mapper.from(applicationTags::getShard).to((shard) -> tags.put("shard", shard));
      if (applicationTags.getCustomTags() != null) {
        tags.putAll(applicationTags.getCustomTags());
      }
      return Tags.of(tags.entrySet().stream().map((entry) -> Tag.of(entry.getKey(), entry.getValue()))
          .collect(Collectors.toList()));
    }
  }

}
