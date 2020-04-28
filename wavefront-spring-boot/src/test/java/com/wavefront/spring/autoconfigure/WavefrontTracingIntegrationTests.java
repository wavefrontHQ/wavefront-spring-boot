/*
 * Copyright 2013-2019 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.wavefront.spring.autoconfigure;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.BlockingDeque;
import java.util.concurrent.LinkedBlockingDeque;

import com.wavefront.sdk.common.Pair;
import com.wavefront.sdk.common.WavefrontSender;
import com.wavefront.sdk.entities.histograms.HistogramGranularity;
import com.wavefront.sdk.entities.tracing.SpanLog;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.autoconfigure.web.reactive.AutoConfigureWebTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.springframework.web.reactive.function.server.HandlerFunction;
import org.springframework.web.reactive.function.server.RouterFunction;
import org.springframework.web.reactive.function.server.RouterFunctions;
import org.springframework.web.reactive.function.server.ServerRequest;
import org.springframework.web.reactive.function.server.ServerResponse;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests for tracing.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    classes = WavefrontTracingIntegrationTests.Config.class,
    properties = {
        "management.metrics.export.wavefront.api-token=dummy",
        "wavefront.application.name=IntegratedTracingTests",
        "spring.zipkin.service.name=test_service"
    })
@AutoConfigureWebTestClient
public class WavefrontTracingIntegrationTests {

  @Autowired
  private WebTestClient client;

  @Autowired
  private BlockingDeque<SpanRecord> spanRecordQueue;

  @Autowired
  private BlockingDeque<String> metricAndHistogramRecordQueue;

  @Test
  void sendsToWavefront() throws Exception {
    this.client.get()
        .uri("/api/fn/10")
        .header("b3", "0000000000000001-0000000000000003-1-0000000000000002")
        .exchange().expectStatus().isOk();

    SpanRecord spanRecord = spanRecordQueue.take();
    assertThat(spanRecord.traceId)
        .hasToString("00000000-0000-0000-0000-000000000001");
    assertThat(spanRecord.parents).extracting(UUID::toString)
        .containsExactly("00000000-0000-0000-0000-000000000003");
    assertThat(spanRecord.followsFrom).isNull();
    // This tests that RPC spans do not share the same span ID
    assertThat(spanRecord.spanId.toString())
        .isNotEqualTo("00000000-0000-0000-0000-000000000003")
        .matches("^[0-9a-f]{8}\\b-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-\\b[0-9a-f]{12}$");
    assertThat(spanRecord.name)
        .isEqualTo("GET /api/fn/{id}");

    // spot check the unit is valid (millis not micros)
    long currentTime = System.currentTimeMillis();
    assertThat(spanRecord.startMillis)
        .isGreaterThan(currentTime - 5000)
        .isLessThan(currentTime);
    // Less than a millis should round up to 1, but the test could take longer than 1ms
    assertThat(spanRecord.durationMillis).isPositive();

    assertThat(spanRecord.tags).containsExactly(
        Pair.of("application", "IntegratedTracingTests"),
        Pair.of("service", "test_service"),
        Pair.of("cluster", "none"),
        Pair.of("shard", "none"),
        Pair.of("http.method", "GET"),
        Pair.of("http.path", "/api/fn/10"),
        Pair.of("mvc.controller.class", "Controller"),
        Pair.of("span.kind", "server")
    );

    // Wait for 60s to let RED metrics being reported.
    Thread.sleep(60000);
    assertThat(metricAndHistogramRecordQueue.isEmpty()).isFalse();
  }

  @Configuration
  @EnableAutoConfiguration
  static class Config {

    @Bean
    RouterFunction<ServerResponse> route() {
      return RouterFunctions.route()
          .GET("/api/fn/{id}", new Controller())
          .build();
    }

    @Bean
    BlockingDeque<SpanRecord> spanRecordQueue() {
      return new LinkedBlockingDeque<>();
    }

    @Bean
    BlockingDeque<String> metricAndHistogramRecordQueue() {
      return new LinkedBlockingDeque<>();
    }

    @Bean
    @Primary
    WavefrontSender wavefrontSender(BlockingDeque<SpanRecord> spanRecordQueue,
                                    BlockingDeque<String> metricAndHistogramRecordQueue) {
      return new WavefrontSender() {
        @Override
        public String getClientId() {
          return null;
        }

        @Override
        public void flush() throws IOException {

        }

        @Override
        public int getFailureCount() {
          return 0;
        }

        @Override
        public void sendDistribution(String name, List<Pair<Double, Integer>> centroids,
                                     Set<HistogramGranularity> histogramGranularities,
                                     Long timestamp, String source, Map<String, String> tags) throws IOException {
          metricAndHistogramRecordQueue.add(name);
        }

        @Override
        public void sendMetric(String name, double value, Long timestamp, String source,
                               Map<String, String> tags) throws IOException {
          metricAndHistogramRecordQueue.add(name);
        }

        @Override
        public void sendFormattedMetric(String point) throws IOException {

        }

        @Override
        public void sendSpan(String name, long startMillis, long durationMillis, String source,
                             UUID traceId, UUID spanId, List<UUID> parents, List<UUID> followsFrom,
                             List<Pair<String, String>> tags, List<SpanLog> spanLogs) throws IOException {
          spanRecordQueue.add(new SpanRecord(name, startMillis, durationMillis, source, traceId,
              spanId, parents, followsFrom, tags, spanLogs));
        }

        @Override
        public void close() throws IOException {

        }
      };
    }
  }

  static final class Controller implements HandlerFunction<ServerResponse> {

    @Override
    public Mono<ServerResponse> handle(ServerRequest serverRequest) {
      return ServerResponse.ok()
          .bodyValue(serverRequest.pathVariable("id"));
    }

  }

  static final class SpanRecord {
    private final String name;

    private final long startMillis;

    private final long durationMillis;

    private final String source;

    private final UUID traceId;

    private final UUID spanId;

    private final List<UUID> parents;

    private final List<UUID> followsFrom;

    private final List<Pair<String, String>> tags;

    private final List<SpanLog> spanLogs;

    SpanRecord(String name, long startMillis, long durationMillis, String source,
        UUID traceId, UUID spanId, List<UUID> parents, List<UUID> followsFrom,
        List<Pair<String, String>> tags, List<SpanLog> spanLogs) {
      this.name = name;
      this.startMillis = startMillis;
      this.durationMillis = durationMillis;
      this.source = source;
      this.traceId = traceId;
      this.spanId = spanId;
      this.parents = parents;
      this.followsFrom = followsFrom;
      this.tags = tags;
      this.spanLogs = spanLogs;
    }

  }

}
