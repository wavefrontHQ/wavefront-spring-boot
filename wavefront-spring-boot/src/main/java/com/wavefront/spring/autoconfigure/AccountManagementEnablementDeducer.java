package com.wavefront.spring.autoconfigure;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * Utility to deduce if the {@link AccountManagementEnvironmentPostProcessor} should be
 * enabled in the current context.
 *
 * @author Madhura Bhave
 */
final class AccountManagementEnablementDeducer {

  private static final Set<String> SKIPPED_STACK_ELEMENTS;

  static {
    Set<String> skipped = new LinkedHashSet<>();
    skipped.add("org.junit.runners.");
    skipped.add("org.junit.platform.");
    skipped.add("org.springframework.boot.test.");
    skipped.add("cucumber.runtime.");
    SKIPPED_STACK_ELEMENTS = Collections.unmodifiableSet(skipped);
  }

  private AccountManagementEnablementDeducer() {
  }

  /**
   * Checks if a specific {@link StackTraceElement} in the current thread's stacktrace
   * should cause account management to be enabled.
   * @param thread the current thread
   * @return {@code true} if account management should be enabled
   */
  public static boolean shouldEnable(Thread thread) {
    for (StackTraceElement element : thread.getStackTrace()) {
      if (isSkippedStackElement(element)) {
        return false;
      }
    }
    return true;
  }

  private static boolean isSkippedStackElement(StackTraceElement element) {
    for (String skipped : SKIPPED_STACK_ELEMENTS) {
      if (element.getClassName().startsWith(skipped)) {
        return true;
      }
    }
    return false;
  }

}
