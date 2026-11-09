/*
 * Copyright (c) 2026 Consensys Incorporated. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on
 * an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations under the License.
 */

package tech.pegasys.teku.statetransition.validation;

import static org.assertj.core.api.AssertionsForInterfaceTypes.assertThat;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

public class InternalValidationResultTest {

  @Test
  void shouldPrintAcceptToString() {
    assertThat(InternalValidationResult.ACCEPT.toString())
        .isEqualTo("InternalValidationResult{validationResultCode=ACCEPT}");
  }

  @Test
  void shouldPrintRejectToString() {
    assertThat(
            InternalValidationResult.create(ValidationResultCode.REJECT, "good reason").toString())
        .isEqualTo(
            "InternalValidationResult{validationResultCode=REJECT, description=good reason}");
  }

  @Test
  void isAccept() {
    assertTrue(InternalValidationResult.ACCEPT.isAccept());
    assertFalse(InternalValidationResult.IGNORE.isAccept());
    assertFalse(InternalValidationResult.reject("").isAccept());
    assertFalse(InternalValidationResult.SAVE_FOR_FUTURE.isAccept());
  }

  @Test
  void isIgnore() {
    assertFalse(InternalValidationResult.ACCEPT.isIgnore());
    assertTrue(InternalValidationResult.IGNORE.isIgnore());
    assertFalse(InternalValidationResult.reject("").isIgnore());
    assertFalse(InternalValidationResult.SAVE_FOR_FUTURE.isIgnore());
  }

  @Test
  void isReject() {
    assertFalse(InternalValidationResult.ACCEPT.isReject());
    assertFalse(InternalValidationResult.IGNORE.isReject());
    assertTrue(InternalValidationResult.reject("").isReject());
    assertFalse(InternalValidationResult.SAVE_FOR_FUTURE.isReject());
  }

  @Test
  void isSaveForFuture() {
    assertFalse(InternalValidationResult.ACCEPT.isSaveForFuture());
    assertFalse(InternalValidationResult.IGNORE.isSaveForFuture());
    assertFalse(InternalValidationResult.reject("").isSaveForFuture());
    assertTrue(InternalValidationResult.SAVE_FOR_FUTURE.isSaveForFuture());
  }

  @Test
  void isNotProcessable() {
    assertFalse(InternalValidationResult.ACCEPT.isNotProcessable());
    assertTrue(InternalValidationResult.IGNORE.isNotProcessable());
    assertTrue(InternalValidationResult.reject("").isNotProcessable());
    assertFalse(InternalValidationResult.SAVE_FOR_FUTURE.isNotProcessable());
  }
}
