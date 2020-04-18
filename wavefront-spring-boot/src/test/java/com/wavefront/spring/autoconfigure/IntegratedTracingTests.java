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

import java.util.List;
import java.util.UUID;
import java.util.concurrent.BlockingDeque;
import java.util.concurrent.LinkedBlockingDeque;

import com.wavefront.sdk.common.Pair;
import com.wavefront.sdk.common.annotation.Nullable;
import com.wavefront.sdk.entities.tracing.SpanLog;
import com.wavefront.sdk.entities.tracing.WavefrontTracingSpanSender;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.web.server.LocalServerPort;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.server.HandlerFunction;
import org.springframework.web.reactive.function.server.RouterFunction;
import org.springframework.web.reactive.function.server.RouterFunctions;
import org.springframework.web.reactive.function.server.ServerRequest;
import org.springframework.web.reactive.function.server.ServerResponse;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    classes = IntegratedTracingTests.Config.class,
    properties = {
        "spring.application.name=IntegratedTracingTests",
        // Adrian thinks this one should be default in spring
        "wavefront.application.name=${spring.application.name}",
        "spring.zipkin.service.name=test_service"
    }
)
public class IntegratedTracingTests {
  @Autowired BlockingDeque<SpanRecord> spanRecordQueue;
  @LocalServerPort int port;

  @Test void sendsToWavefront() throws Exception {
    WebClient.create().get()
        .uri("http://localhost:" + port + "/api/fn/10")
        .header("b3", "0000000000000001-0000000000000003-1-0000000000000002")
        .exchange().block();

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
  }

  static final class Controller implements HandlerFunction<ServerResponse> {
    @Override public Mono<ServerResponse> handle(ServerRequest serverRequest) {
      return ServerResponse.ok()
          .bodyValue(serverRequest.pathVariable("id"));
    }
  }

  @Configuration
  @EnableAutoConfiguration
  static class Config {
    @Bean WebClient webClient() {
      return WebClient.create();
    }

    @Bean RouterFunction<ServerResponse> route() {
      return RouterFunctions.route()
          .GET("/api/fn/{id}", new Controller())
          .build();
    }

    @Bean BlockingDeque<SpanRecord> spanRecordQueue() {
      return new LinkedBlockingDeque<>();
    }

    @Bean @Primary
    WavefrontTracingSpanSender wavefrontTracingSpanSender(BlockingDeque<SpanRecord> queue) {
      return (name, startMillis, durationMillis, source, traceId, spanId, parents, followsFrom, tags, spanLogs) ->
          queue.add(new SpanRecord(name, startMillis, durationMillis, source, traceId, spanId,
              parents, followsFrom, tags, spanLogs));
    }
  }

  static final class SpanRecord {
    final String name;
    final long startMillis;
    final long durationMillis;
    @Nullable final String source;
    final UUID traceId;
    final UUID spanId;
    @Nullable final List<UUID> parents;
    @Nullable final List<UUID> followsFrom;
    @Nullable final List<Pair<String, String>> tags;
    @Nullable final List<SpanLog> spanLogs;

    SpanRecord(
        String name,
        long startMillis,
        long durationMillis,
        @Nullable String source,
        UUID traceId, UUID spanId,
        @Nullable List<UUID> parents,
        @Nullable List<UUID> followsFrom,
        @Nullable List<Pair<String, String>> tags,
        @Nullable List<SpanLog> spanLogs
    ) {
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
