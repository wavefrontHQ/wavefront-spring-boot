package com.wavefront.spring.autoconfigure;

import com.wavefront.spring.autoconfigure.AccountManagementIntegrationTests.TestConfiguration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests to validate account management does not kick in in a test.
 *
 * @author Stephane Nicoll
 */
@SpringBootTest(classes = TestConfiguration.class)
@ExtendWith(OutputCaptureExtension.class)
class AccountManagementIntegrationTests {

  @Test
  void accountManagementIsNotProvisionedInTest(CapturedOutput output) {
    assertThat(output).doesNotContain("Wavefront account");
  }

  @ImportAutoConfiguration(WavefrontAutoConfiguration.class)
  static class TestConfiguration {

  }

}
