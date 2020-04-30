package sample;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class HomeController {

  @GetMapping("/")
  public String home() {
    return "Hello World!";
  }

  @GetMapping("/oops")
  public void error() {
    throw new IllegalStateException("Test exception");
  }

}
