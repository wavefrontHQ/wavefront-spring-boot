package com.wavefront.spring.actuate;

import java.net.URI;
import java.util.function.Supplier;

import org.junit.jupiter.api.Test;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

/**
 * Tests for {@link WavefrontController}.
 *
 * @author Stephane Nicoll
 */
class WavefrontControllerTests {

  @Test
  void dashboardInvokeSupplier() {
    URI location = URI.create("https://example.com");
    Supplier<URI> dashboardUrlSupplier = mock(Supplier.class);
    given(dashboardUrlSupplier.get()).willReturn(location);
    WavefrontController controller = new WavefrontController(dashboardUrlSupplier);
    ResponseEntity<Void> response = controller.dashboard();
    verify(dashboardUrlSupplier).get();
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FOUND);
    assertThat(response.getHeaders().getLocation()).isEqualTo(location);
  }

}
