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
import tech.pegasys.teku.bls.keystore.model.KeyStoreData;
import tech.pegasys.teku.validator.client.restapi.apis.schema.PostKeyResult;

public class LocalValidatorImportResult extends ValidatorImportResult {
  private final String password;
  private final Optional<KeyStoreData> maybeKeystoreData;

  public Optional<KeyStoreData> getKeyStoreData() {
    return this.maybeKeystoreData;
  }

  public String getPassword() {
    return this.password;
  }

  private LocalValidatorImportResult(final Builder builder) {
    super(builder.maybePublicKey, builder.postKeyResult);
    this.password = builder.password;
    this.maybeKeystoreData = builder.maybeKeystoreData;
  }

  @Override
  public boolean equals(final Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    final LocalValidatorImportResult localValidatorImportResult = (LocalValidatorImportResult) o;
    return super.equals(o)
        && maybeKeystoreData.equals(localValidatorImportResult.maybeKeystoreData)
        && password.equals(localValidatorImportResult.password);
  }

  @Override
  public int hashCode() {
    return Objects.hash(super.hashCode(), password, maybeKeystoreData);
  }

  public static class Builder {
    private Optional<BLSPublicKey> maybePublicKey = Optional.empty();
    private final PostKeyResult postKeyResult;
    private final String password;
    private Optional<KeyStoreData> maybeKeystoreData = Optional.empty();

    public Builder(final PostKeyResult postKeyResult, final String password) {
      this.postKeyResult = postKeyResult;
      this.password = password;
    }

    public Builder publicKey(final Optional<BLSPublicKey> publicKey) {
      this.maybePublicKey = publicKey;
      return this;
    }

    public Builder keyStoreData(final Optional<KeyStoreData> keyStoreData) {
      this.maybeKeystoreData = keyStoreData;
      return this;
    }

    public LocalValidatorImportResult build() {
      return new LocalValidatorImportResult(this);
    }
  }
}
