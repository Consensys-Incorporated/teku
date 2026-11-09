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

import java.net.URL;
import java.util.Objects;
import java.util.Optional;
import tech.pegasys.teku.bls.BLSPublicKey;
import tech.pegasys.teku.validator.client.restapi.apis.schema.PostKeyResult;

public class ExternalValidatorImportResult extends ValidatorImportResult {

  private final Optional<URL> signerUrl;

  public Optional<URL> getSignerUrl() {
    return this.signerUrl;
  }

  private ExternalValidatorImportResult(final Builder builder) {
    super(builder.maybePublicKey, builder.postKeyResult);
    this.signerUrl = builder.maybeSignerUrl;
  }

  @Override
  public boolean equals(final Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    final ExternalValidatorImportResult externalValidatorImportResult =
        (ExternalValidatorImportResult) o;
    return super.equals(o) && signerUrl.equals(externalValidatorImportResult.signerUrl);
  }

  @Override
  public int hashCode() {
    return Objects.hash(super.hashCode(), signerUrl);
  }

  public static class Builder {
    private Optional<BLSPublicKey> maybePublicKey = Optional.empty();
    private final PostKeyResult postKeyResult;
    private final Optional<URL> maybeSignerUrl;

    public Builder(final PostKeyResult postKeyResult, final Optional<URL> signerUrl) {
      this.postKeyResult = postKeyResult;
      this.maybeSignerUrl = signerUrl;
    }

    public Builder publicKey(final Optional<BLSPublicKey> publicKey) {
      this.maybePublicKey = publicKey;
      return this;
    }

    public ExternalValidatorImportResult build() {
      return new ExternalValidatorImportResult(this);
    }
  }
}
