/*
 * Copyright Consensys Software Inc., 2026
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

import com.google.common.base.MoreObjects;
import com.google.errorprone.annotations.FormatMethod;
import java.util.Objects;
import java.util.Optional;
import tech.pegasys.teku.statetransition.validation.ValidationResultCode.ValidationResultSubCode;

public class InternalValidationResult {

  public static final InternalValidationResult ACCEPT =
      InternalValidationResult.create(ValidationResultCode.ACCEPT);
  public static final InternalValidationResult IGNORE =
      InternalValidationResult.create(ValidationResultCode.IGNORE);

  public static final InternalValidationResult SAVE_FOR_FUTURE =
      InternalValidationResult.create(ValidationResultCode.SAVE_FOR_FUTURE);

  private final ValidationResultCode validationResultCode;
  private final Optional<String> description;
  private final Optional<ValidationResultSubCode> validationResultSubCode;

  private InternalValidationResult(
      final ValidationResultCode validationResultCode,
      final Optional<ValidationResultSubCode> validationResultSubCode,
      final Optional<String> description) {
    this.validationResultCode = validationResultCode;
    this.validationResultSubCode = validationResultSubCode;
    this.description = description;
  }

  static InternalValidationResult create(final ValidationResultCode validationResultCode) {
    return new InternalValidationResult(validationResultCode, Optional.empty(), Optional.empty());
  }

  public static InternalValidationResult create(
      final ValidationResultCode validationResultCode, final String description) {
    return new InternalValidationResult(
        validationResultCode, Optional.empty(), Optional.of(description));
  }

  public static InternalValidationResult create(
      final ValidationResultCode validationResultCode,
      final ValidationResultSubCode subCode,
      final String description) {
    return new InternalValidationResult(
        validationResultCode, Optional.of(subCode), Optional.of(description));
  }

  @FormatMethod
  public static InternalValidationResult reject(
      final String descriptionTemplate, final Object... args) {
    return create(ValidationResultCode.REJECT, String.format(descriptionTemplate, args));
  }

  @FormatMethod
  public static InternalValidationResult ignore(
      final String descriptionTemplate, final Object... args) {
    return create(ValidationResultCode.IGNORE, String.format(descriptionTemplate, args));
  }

  public static InternalValidationResult ignore(final String description) {
    return create(ValidationResultCode.IGNORE, description);
  }

  @FormatMethod
  public static InternalValidationResult ignore(
      final ValidationResultSubCode validationResultSubCode,
      final String descriptionTemplate,
      final Object... args) {
    return create(
        ValidationResultCode.IGNORE,
        validationResultSubCode,
        String.format(descriptionTemplate, args));
  }

  @FormatMethod
  public static InternalValidationResult saveForFuture(
      final String descriptionTemplate, final Object... args) {
    return create(ValidationResultCode.SAVE_FOR_FUTURE, String.format(descriptionTemplate, args));
  }

  public static InternalValidationResult saveForFuture(final String description) {
    return create(ValidationResultCode.SAVE_FOR_FUTURE, description);
  }

  public ValidationResultCode code() {
    return validationResultCode;
  }

  public Optional<ValidationResultSubCode> getValidationResultSubCode() {
    return validationResultSubCode;
  }

  public Optional<String> getDescription() {
    return description;
  }

  @Override
  public boolean equals(final Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    final InternalValidationResult that = (InternalValidationResult) o;
    return validationResultCode == that.validationResultCode
        && Objects.equals(description, that.description);
  }

  public boolean isAccept() {
    return this.validationResultCode.equals(ValidationResultCode.ACCEPT);
  }

  public boolean isNotProcessable() {
    return isIgnore() || isReject();
  }

  public boolean isIgnore() {
    return this.validationResultCode.equals(ValidationResultCode.IGNORE);
  }

  public boolean isIgnoreAlreadySeen() {
    return isIgnore()
        && this.validationResultSubCode
            .map(subCode -> subCode.equals(ValidationResultSubCode.IGNORE_ALREADY_SEEN))
            .orElse(false);
  }

  public boolean isIgnoreEquivocationDetected() {
    return isIgnore()
        && this.validationResultSubCode
            .map(subCode -> subCode.equals(ValidationResultSubCode.IGNORE_EQUIVOCATION_DETECTED))
            .orElse(false);
  }

  public boolean isReject() {
    return this.validationResultCode.equals(ValidationResultCode.REJECT);
  }

  public boolean isSaveForFuture() {
    return this.validationResultCode.equals(ValidationResultCode.SAVE_FOR_FUTURE);
  }

  @Override
  public int hashCode() {
    return Objects.hash(validationResultCode, description);
  }

  @Override
  public String toString() {
    MoreObjects.ToStringHelper helper =
        MoreObjects.toStringHelper(this).add("validationResultCode", validationResultCode);

    description.ifPresent(s -> helper.add("description", s));

    return helper.toString();
  }
}
