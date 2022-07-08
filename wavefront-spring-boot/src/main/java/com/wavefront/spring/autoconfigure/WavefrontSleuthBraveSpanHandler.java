package com.wavefront.spring.autoconfigure;

import java.io.Closeable;

import brave.handler.MutableSpan;
import brave.handler.SpanHandler;
import brave.propagation.TraceContext;

import org.springframework.cloud.sleuth.brave.bridge.BraveFinishedSpan;
import org.springframework.cloud.sleuth.brave.bridge.BraveTraceContext;

class WavefrontSleuthBraveSpanHandler extends SpanHandler implements Runnable, Closeable {

  final WavefrontSleuthSpanHandler spanHandler;

  WavefrontSleuthBraveSpanHandler(WavefrontSleuthSpanHandler spanHandler) {
    this.spanHandler = spanHandler;
  }

  @Override
  public boolean end(TraceContext context, MutableSpan span, Cause cause) {
    return spanHandler.end(BraveTraceContext.fromBrave(context), BraveFinishedSpan.fromBrave(span));
  }

  @Override
  public void close() {
    this.spanHandler.close();
  }

  @Override
  public void run() {
    this.spanHandler.run();
  }
}
