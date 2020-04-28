package com.wavefront.spring.autoconfigure;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.ArrayList;
import java.util.List;

/**
 * Advanced configuration properties for Wavefront.
 *
 * @author Stephane Nicoll
 */
@ConfigurationProperties("wavefront")
public class WavefrontProperties {

  private final Application application = new Application();

  /**
   * Emit JVM Metrics or not.
   */
  private boolean includeJvmMetrics = true;

  /**
   * Custom Tags for span RED metrics
   * (https://docs.wavefront.com/tracing_integrations.html#custom-tags-for-red-metrics)
   */
  private List<String> traceDerivedCustomTagKeys = new ArrayList<>();

  public Application getApplication() {
    return this.application;
  }

  public static class Application {

    /**
     * Name of the application.
     */
    private String name = "unnamed_application";

    /**
     * Name of the service.
     */
    private String service = "unnamed_service";

    /**
     * Cluster of the service.
     */
    private String cluster;

    /**
     * Shard of the service.
     */
    private String shard;

    public String getName() {
      return this.name;
    }

    public void setName(String name) {
      this.name = name;
    }

    public String getService() {
      return this.service;
    }

    public void setService(String service) {
      this.service = service;
    }

    public String getCluster() {
      return this.cluster;
    }

    public void setCluster(String cluster) {
      this.cluster = cluster;
    }

    public String getShard() {
      return this.shard;
    }

    public void setShard(String shard) {
      this.shard = shard;
    }

  }

  public boolean isIncludeJvmMetrics() {
    return includeJvmMetrics;
  }

  public void setIncludeJvmMetrics(boolean includeJvmMetrics) {
    this.includeJvmMetrics = includeJvmMetrics;
  }

  public List<String> getTraceDerivedCustomTagKeys() {
    return traceDerivedCustomTagKeys;
  }

  public void setTraceDerivedCustomTagKeys(List<String> traceDerivedCustomTagKeys) {
    this.traceDerivedCustomTagKeys = traceDerivedCustomTagKeys;
  }
}
