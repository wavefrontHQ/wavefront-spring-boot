package com.wavefront.spring.actuate;

import java.net.URI;
import java.util.function.Supplier;

import org.springframework.boot.actuate.endpoint.web.annotation.ControllerEndpoint;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * An endpoint that provides access to the Wavefront dashboard.
 *
 * @author Stephane Nicoll
 */
@ControllerEndpoint(id = "wavefront")
public class WavefrontController {

  private final Supplier<URI> dashboardUrlSupplier;

  WavefrontController(Supplier<URI> dashboardUrlSupplier) {
    this.dashboardUrlSupplier = dashboardUrlSupplier;
  }

  @GetMapping("/")
  public ResponseEntity<Void> dashboard() {
    return ResponseEntity.status(HttpStatus.FOUND)
        .headers((headers) -> headers.setLocation(this.dashboardUrlSupplier.get()))
        .build();
  }

}
