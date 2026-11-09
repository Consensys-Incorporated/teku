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

package tech.pegasys.teku.validator.client;

import java.util.Objects;
import java.util.Optional;
import tech.pegasys.teku.bls.BLSPublicKey;
import tech.pegasys.teku.validator.client.restapi.apis.schema.PostKeyResult;

public class ValidatorImportResult {

  private final Optional<BLSPublicKey> maybePublicKey;
  private final PostKeyResult postKeyResult;

  public Optional<BLSPublicKey> getPublicKey() {
    return this.maybePublicKey;
  }

  public PostKeyResult getPostKeyResult() {
    return this.postKeyResult;
  }

  protected ValidatorImportResult(
      final Optional<BLSPublicKey> publicKey, final PostKeyResult postKeyResult) {
    this.maybePublicKey = publicKey;
    this.postKeyResult = postKeyResult;
  }

  @Override
  public int hashCode() {
    return Objects.hash(maybePublicKey, postKeyResult);
  }

  @Override
  public boolean equals(final Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    final ValidatorImportResult validatorImportResult = (ValidatorImportResult) o;
    return maybePublicKey.equals(validatorImportResult.maybePublicKey)
        && postKeyResult.equals(validatorImportResult.postKeyResult);
  }
}
