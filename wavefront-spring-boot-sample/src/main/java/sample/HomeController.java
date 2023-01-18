package sample;

import org.springframework.cloud.sleuth.Span;
import org.springframework.cloud.sleuth.Tracer;;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class HomeController {
  final Tracer tracer;

  public HomeController(Tracer tracer) {
    this.tracer = tracer;
  }

  @GetMapping("/")
  public String home() {
    // The span generated from this endpoint has span.kind, which causes `_spanSecondaryId` to be
    // added since we're attaching logs/events to it.
    int sleepDuration = syntheticWork();
    tracer.currentSpan().event("sleepDuration1: " + sleepDuration + "ms");
    childMethod();
    sleepDuration = syntheticWork();
    tracer.currentSpan().event("sleepDuration2: " + sleepDuration + "ms");
    return "Hello World!";
  }

  @GetMapping("/oops")
  public void error() {
    throw new IllegalStateException("Test exception");
  }

  private static int syntheticWork() {
    // sleep a maximum of 150ms
    double sleepDuration = Math.random() * 150;
    try {
      Thread.sleep((long) sleepDuration);
    } catch (InterruptedException e) {
      e.printStackTrace();
    }
    return (int) sleepDuration;
  }

  private void childMethod() {
    // The span generated below does not have span.kind, so it tests that span events/logs work when
    // `_spanSecondaryId` isn't used.
    Span span = tracer.nextSpan().name("childMethod").start();
    int sleepDuration = syntheticWork();
    span.tag("sleepDuration", sleepDuration + "ms");
    span.event("spanlog/event from child");
    span.end();
  }

}
