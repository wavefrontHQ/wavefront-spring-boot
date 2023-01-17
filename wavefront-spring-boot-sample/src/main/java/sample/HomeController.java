package sample;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.logging.Logger;

import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationRegistry;

@RestController
public class HomeController {
  private final Logger logger = Logger.getLogger(HomeController.class.getName());

  @Autowired
  ObservationRegistry observationRegistry;

  @GetMapping("/")
  public String home() {
    return "Hello World!";
  }

  @GetMapping("/oops")
  public void error() {
    Observation observation = observationRegistry.getCurrentObservation();
    if (observation != null) {
      Observation.Event event = Observation.Event.of("synthetic.error", "a synthetic error");
      observation.event(event);
    } else {
      logger.severe("No current observation to attach event!");
    }
    throw new IllegalStateException("Test exception");
  }

}
