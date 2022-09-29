package com.wavefront.spring.autoconfigure;

import com.wavefront.sdk.common.WavefrontSender;
import com.wavefront.sdk.common.application.ApplicationTags;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.time.Duration;
import java.util.Collections;
import java.util.UUID;

import io.micrometer.tracing.TraceContext;
import io.micrometer.tracing.test.simple.SimpleSpan;

import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

/**
 * Tests for {@link WavefrontSpanHandler}.
 *
 * @author Moritz Halbritter
 */
class WavefrontSpanHandlerTest {

  private WavefrontSender sender;

  private WavefrontSpanHandler sut;

  @BeforeEach
  void setUp() {
    this.sender = mock(WavefrontSender.class);
    this.sut = new WavefrontSpanHandler(10000, sender, SpanMetrics.NOOP, "source",
        new ApplicationTags.Builder("application", "service").build(), Collections.emptySet());
  }

  @AfterEach
  void tearDown() {
    this.sut.close();
  }

  @Test
  void sends() throws Exception {
    TraceContext traceContext = new DummyTraceContext();
    SimpleSpan span = new SimpleSpan();
    sut.end(traceContext, span);
    sut.close();

    verify(sender).sendSpan(eq("defaultOperation"), anyLong(), anyLong(), eq("source"),
        eq(UUID.fromString("00000000-0000-0000-7fff-ffffffffffff")),
        eq(UUID.fromString("00000000-0000-0000-7fff-ffffffffffff")), any(), any(), any(), any());
  }

  @Test
  void stopsInTime() throws IOException {
    await().pollDelay(Duration.ofMillis(10)).atMost(Duration.ofMillis(100)).until(() -> {
      sut.close();
      return true;
    });

    verify(sender).flush();
    verify(sender).close();
  }


  static class DummyTraceContext implements TraceContext {
    @Override
    public String traceId() {
      return "7fffffffffffffff";
    }

    @Override
    public String parentId() {
      return null;
    }

    @Override
    public String spanId() {
      return "7fffffffffffffff";
    }

    @Override
    public Boolean sampled() {
      return true;
    }
  }
}
