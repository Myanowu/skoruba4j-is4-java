package com.myano.skoruba4j.domain.externallogin;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class PhoneNumbersTest {

  @Test
  void normalizesE164AndWaMe() {
    assertEquals("85291234567", PhoneNumbers.digitsOnly("+852 9123-4567"));
    assertEquals("+85291234567", PhoneNumbers.toE164("+852 9123-4567"));
    assertEquals("85291234567", PhoneNumbers.waMeNumber("+85291234567"));
    assertEquals("", PhoneNumbers.digitsOnly(""));
  }
}
