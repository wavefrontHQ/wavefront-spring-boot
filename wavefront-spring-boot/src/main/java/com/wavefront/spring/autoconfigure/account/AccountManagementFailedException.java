package com.wavefront.spring.autoconfigure.account;

/**
 * Thrown if managing an account failed.
 *
 * @author Stephane Nicoll
 */
class AccountManagementFailedException extends RuntimeException {

  AccountManagementFailedException(String message) {
    super(message);
  }

}
