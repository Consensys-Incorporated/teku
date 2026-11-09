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
import org.apache.tuweni.bytes.Bytes;

public class CipherParam {
  private final Bytes iv;

  @JsonCreator
  public CipherParam(@JsonProperty(value = "iv", required = true) final Bytes iv) {
    this.iv = iv;
  }

  @JsonProperty(value = "iv")
  public Bytes getIv() {
    return iv;
  }

  @Override
  public String toString() {
    return MoreObjects.toStringHelper(this).add("iv", iv).toString();
  }
}
