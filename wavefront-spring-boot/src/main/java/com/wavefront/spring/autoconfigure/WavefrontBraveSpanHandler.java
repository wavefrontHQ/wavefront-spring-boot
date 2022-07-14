package com.wavefront.spring.autoconfigure;

import java.io.Closeable;

import brave.handler.MutableSpan;
import brave.handler.SpanHandler;
import brave.propagation.TraceContext;
import io.micrometer.tracing.brave.bridge.BraveFinishedSpan;
import io.micrometer.tracing.brave.bridge.BraveTraceContext;


class WavefrontBraveSpanHandler extends SpanHandler implements Runnable, Closeable {

  final WavefrontSpanHandler spanHandler;

  WavefrontBraveSpanHandler(WavefrontSpanHandler spanHandler) {
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
