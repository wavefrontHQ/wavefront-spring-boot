package com.wavefront.spring.autoconfigure;

import java.util.concurrent.BlockingQueue;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;

/**
 * @author Moritz Halbritter
 */
class MeterRegistrySpanMetrics implements SpanMetrics {

  private final Counter spansReceived;

  private final Counter spansDropped;

  private final Counter reportErrors;

  private final MeterRegistry meterRegistry;

  MeterRegistrySpanMetrics(MeterRegistry meterRegistry) {
    this.meterRegistry = meterRegistry;
    this.spansReceived = meterRegistry.counter("reporter.spans.received");
    this.spansDropped = meterRegistry.counter("reporter.spans.dropped");
    this.reportErrors = meterRegistry.counter("reporter.errors");
  }

  @Override
  public void reportDropped() {
    spansDropped.increment();
  }

  @Override
  public double getDropped() {
    return spansDropped.count();
  }

  @Override
  public void reportReceived() {
    spansReceived.increment();
  }

  @Override
  public void reportErrors() {
    reportErrors.increment();
  }

  @Override
  public void registerQueueSize(BlockingQueue<?> queue) {
    meterRegistry.gauge("reporter.queue.size", queue, q -> (double) q.size());
  }

  @Override
  public void registerQueueRemainingCapacity(BlockingQueue<?> queue) {
    meterRegistry.gauge("reporter.queue.remaining_capacity", queue, q -> (double) q.remainingCapacity());
  }

}
