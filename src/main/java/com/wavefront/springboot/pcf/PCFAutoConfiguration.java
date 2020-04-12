package com.wavefront.springboot.pcf;

import com.wavefront.sdk.common.application.ApplicationTags;
import com.wavefront.springboot.WavefrontSpringBootAutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigureBefore;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.json.JsonParser;
import org.springframework.boot.json.JsonParserFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import java.util.Map;
import java.util.HashMap;
import javax.annotation.Nullable;

@Configuration
@AutoConfigureBefore(WavefrontSpringBootAutoConfiguration.class)
public class PCFAutoConfiguration {

  @Bean
  @ConditionalOnMissingBean(ApplicationTags.class)
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
    /**
     * custom tags part - in this case, for PCF
     */
    String vcap_json = env.getProperty("VCAP_APPLICATION");
    if(vcap_json != null && vcap_json.trim().length() > 0) {
      // try to parse and retrieve PCF VCAP application envs.
      JsonParser jsonParser = JsonParserFactory.getJsonParser();
      Map<String, Object> vcapMap = jsonParser.parseMap(vcap_json);
      Map<String, String> custTags = new HashMap<String, String>();
      custTags.put("application_id", (String) vcapMap.get("application_id"));
      custTags.put("application_name", (String) vcapMap.get("application_name"));
      custTags.put("instance_id", (String) vcapMap.get("instance_id"));
      custTags.put("process_id", (String) vcapMap.get("process_id"));
      custTags.put("process_type", (String) vcapMap.get("process_type"));
      custTags.put("organization_name", (String) vcapMap.get("organization_name"));
      custTags.put("space_name", (String) vcapMap.get("space_name"));
      builder.customTags(custTags);
    }
    return builder.build();
  }

  /**
   * This should not be changed as it mirrors what's in @see <a href=https://docs.spring.io/spring-boot/docs/current/reference/htmlsingle/#production-ready-metrics-export-wavefront>Spring Boot for Wavefront</a>.
   */
  static final String PROPERTY_FILE_PREFIX = "management.metrics.export.wavefront.";
  /**
   * Name of the application (otherwise we use "springboot"}.
   */
  static final String PROPERTY_FILE_KEY_WAVEFRONT_APPLICATION = PROPERTY_FILE_PREFIX + "application.name";
  /**
   * Name of the service (otherwise we use "unnamed_service")".
   */
  static final String PROPERTY_FILE_KEY_WAVEFRONT_SERVICE = PROPERTY_FILE_PREFIX + "application.service";
  /**
   * Cluster of the service (otherwise we use null"}.
   */
  static final String PROPERTY_FILE_KEY_WAVEFRONT_CLUSTER = PROPERTY_FILE_PREFIX + "application.cluster";
  /**
   * Shard of the service (otherwise we use null)".
   */
  static final String PROPERTY_FILE_KEY_WAVEFRONT_SHARD = PROPERTY_FILE_PREFIX + "application.shard";
}
