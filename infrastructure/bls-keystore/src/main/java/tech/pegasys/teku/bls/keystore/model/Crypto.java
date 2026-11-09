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

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.common.base.MoreObjects;

public class Crypto {
  private final Kdf kdf;
  private final Checksum checksum;
  private final Cipher cipher;

  @JsonCreator
  public Crypto(
      @JsonProperty(value = "kdf", required = true) final Kdf kdf,
      @JsonProperty(value = "checksum", required = true) final Checksum checksum,
      @JsonProperty(value = "cipher", required = true) final Cipher cipher) {
    this.kdf = kdf;
    this.checksum = checksum;
    this.cipher = cipher;
  }

  @JsonProperty(value = "kdf")
  public Kdf getKdf() {
    return kdf;
  }

  @JsonProperty(value = "checksum")
  public Checksum getChecksum() {
    return checksum;
  }

  @JsonProperty(value = "cipher")
  public Cipher getCipher() {
    return cipher;
  }

  public void validate() {
    kdf.validate();
    cipher.validate();
  }

  @Override
  public String toString() {
    return MoreObjects.toStringHelper(this)
        .add("kdf", kdf)
        .add("checksum", checksum)
        .add("cipher", cipher)
        .toString();
  }
}
