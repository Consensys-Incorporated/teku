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

package tech.pegasys.teku.bls.keystore.model;

import static com.google.common.base.Preconditions.checkNotNull;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.common.base.MoreObjects;
import org.apache.tuweni.bytes.Bytes;
import tech.pegasys.teku.bls.keystore.KeyStoreValidationException;
import tech.pegasys.teku.bls.keystore.PasswordUtils;

public abstract class KdfParam {
  private final Integer dklen;
  private final Bytes salt;

  public KdfParam(final Integer dklen, final Bytes salt) {
    this.dklen = dklen;
    this.salt = salt;
  }

  @JsonProperty(value = "dklen")
  public Integer getDkLen() {
    return dklen;
  }

  public abstract KdfFunction getKdfFunction();

  public Bytes generateDecryptionKey(final String password) {
    return generateDecryptionKey(PasswordUtils.normalizePassword(password));
  }

  protected abstract Bytes generateDecryptionKey(final Bytes password);

  public void validate() {
    checkNotNull(getSalt(), "salt cannot be null");
    // because the EIP-2335 spec requires dklen >= 32
    if (dklen < 32) {
      throw new KeyStoreValidationException("Generated key length parameter dklen must be >= 32.");
    }
  }

  @JsonProperty(value = "salt")
  public Bytes getSalt() {
    return salt;
  }

  @Override
  public String toString() {
    return MoreObjects.toStringHelper(this).add("dklen", dklen).add("salt", salt).toString();
  }
}
