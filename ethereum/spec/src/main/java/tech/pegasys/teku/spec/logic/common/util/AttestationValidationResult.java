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

package tech.pegasys.teku.spec.logic.common.util;

import java.util.Optional;
import java.util.function.Supplier;

/**
 * An attestation gossip validation helper that represents the result of an attestation validation
 * check, encapsulating whether the check passed or failed.
 *
 * <p>This class is designed to be immutable and efficient.
 *
 * <ul>
 *   <li>For a successful validation, the static singleton {@link #VALID} should be used via the
 *       {@link #valid()} factory method to avoid unnecessary object creation.
 *   <li>For a failed validation, an invalid result is created using the {@link #invalid(String)} or
 *       {@link #invalid(Supplier)} factory methods. The failure reason is wrapped in a {@link
 *       Supplier} to enable lazy evaluation. This avoids the cost of constructing complex reason
 *       strings unless the reason is actually requested via {@link #getReason()}.
 * </ul>
 */
public class AttestationValidationResult {

  public static final AttestationValidationResult VALID =
      new AttestationValidationResult(true, Optional.empty());

  private final boolean isValid;
  private final Optional<Supplier<String>> reason;

  private AttestationValidationResult(
      final boolean isValid, final Optional<Supplier<String>> reason) {
    this.isValid = isValid;
    this.reason = reason;
  }

  public static AttestationValidationResult valid() {
    return VALID;
  }

  public static AttestationValidationResult invalid(final Supplier<String> reason) {
    return new AttestationValidationResult(false, Optional.of(reason));
  }

  public static AttestationValidationResult invalid(final String reason) {
    return new AttestationValidationResult(false, Optional.of(() -> reason));
  }

  public boolean isValid() {
    return isValid;
  }

  public Optional<String> getReason() {
    return reason.map(Supplier::get);
  }
}
