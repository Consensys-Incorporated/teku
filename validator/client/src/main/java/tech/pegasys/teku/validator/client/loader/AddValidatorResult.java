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

package tech.pegasys.teku.validator.client.loader;

import java.util.Objects;
import java.util.Optional;
import tech.pegasys.teku.spec.signatures.Signer;
import tech.pegasys.teku.validator.client.restapi.apis.schema.PostKeyResult;

public class AddValidatorResult {
  private final PostKeyResult result;
  private final Optional<Signer> signer;

  public AddValidatorResult(final PostKeyResult result, final Optional<Signer> signer) {
    this.result = result;
    this.signer = signer;
  }

  public PostKeyResult getResult() {
    return result;
  }

  public Optional<Signer> getSigner() {
    return signer;
  }

  @Override
  public boolean equals(final Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    final AddValidatorResult that = (AddValidatorResult) o;
    return Objects.equals(result, that.result) && Objects.equals(signer, that.signer);
  }

  @Override
  public int hashCode() {
    return Objects.hash(result, signer);
  }
}
