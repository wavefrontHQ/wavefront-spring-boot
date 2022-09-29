package com.wavefront.spring.autoconfigure;

import java.util.concurrent.BlockingQueue;

/**
 * Reports metrics from {@link WavefrontSpanHandler}.
 *
 * @author Moritz Halbritter
 * @since 1.0.0
 */
public interface SpanMetrics {

  /**
   * Is called when a span has been dropped.
   */
  void reportDropped();

  /**
   * @return the count of spans dropped.
   */
  double getDropped();

  /**
   * Is called when a span is received.
   */
  void reportReceived();

  /**
   * Is called when a span couldn't be sent.
   */
  void reportErrors();

  /**
   * Registers the size of the given {@code queue}.
   * @param queue queue which size should be registered
   */
  void registerQueueSize(BlockingQueue<?> queue);

  /**
   * Registers the remaining capacity of the given {@code queue}.
   * @param queue queue which remaining capacity should be registered
   */
  void registerQueueRemainingCapacity(BlockingQueue<?> queue);

  /**
   * No-op implementation.
   */
  SpanMetrics NOOP = new SpanMetrics() {
    @Override
    public void reportDropped() {
    }

    @Override
    public double getDropped() {
      return 0;
    }

    @Override
    public void reportReceived() {
    }

    @Override
    public void reportErrors() {
    }

    @Override
    public void registerQueueSize(BlockingQueue<?> queue) {
    }

    @Override
    public void registerQueueRemainingCapacity(BlockingQueue<?> queue) {
    }
  };

}
