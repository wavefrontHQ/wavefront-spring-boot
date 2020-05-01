package com.wavefront.spring.autoconfigure;

import org.junit.jupiter.api.Test;

import java.util.HashMap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

/**
 * Unit tests for {@link HeartbeatMetricKey}.
 * https://github.com/wavefrontHQ/wavefront-proxy/blob/master/proxy/src/test/java/com/wavefront/agent/listeners/tracing/HeartbeatMetricKeyTest.java
 * TODO: Move to Wavefront SDK Java (https://github.com/wavefrontHQ/wavefront-sdk-java)
 *
 * @author Hao Song (songhao@vmware.com).
 */
public class HeartbeatMetricKeyTests {

  @Test
  public void testEqual() {
    HeartbeatMetricKey key1 = new HeartbeatMetricKey("app", "service", "cluster", "shard",
        "source", new HashMap<String, String>() {{
      put("tenant", "tenant1");
    }});
    HeartbeatMetricKey key2 = new HeartbeatMetricKey("app", "service", "cluster", "shard",
        "source", new HashMap<String, String>() {{
      put("tenant", "tenant1");
    }});
    assertEquals(key1, key2);
    assertEquals(key1.hashCode(), key2.hashCode());
  }

  @Test
  public void testNotEqual() {
    HeartbeatMetricKey key1 = new HeartbeatMetricKey("app1", "service", "cluster", "shard",
        "source", new HashMap<>());
    HeartbeatMetricKey key2 = new HeartbeatMetricKey("app2", "service", "none", "shard",
        "source", new HashMap<>());
    assertNotEquals(key1.hashCode(), key2.hashCode());
    assertNotEquals(key1, key2);
  }
}