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
import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.google.common.base.MoreObjects;
import tech.pegasys.teku.bls.keystore.KeyStoreValidationException;

public class Kdf {
  private final KdfFunction kdfFunction;
  private final KdfParam param;
  private final String message;

  @JsonCreator
  public Kdf(
      @JsonProperty(value = "function", required = true) final KdfFunction kdfFunction,
      @JsonProperty(value = "params", required = true)
          @JsonTypeInfo(
              use = JsonTypeInfo.Id.NAME,
              include = JsonTypeInfo.As.EXTERNAL_PROPERTY,
              property = "function")
          @JsonSubTypes({
            @JsonSubTypes.Type(value = SCryptParam.class, name = "scrypt"),
            @JsonSubTypes.Type(value = Pbkdf2Param.class, name = "pbkdf2")
          })
          final KdfParam param,
      @JsonProperty(value = "message", required = true) final String message) {
    this.kdfFunction = kdfFunction;
    this.param = param;
    this.message = message;
  }

  public Kdf(final KdfParam kdfParam) {
    this(kdfParam.getKdfFunction(), kdfParam, "");
  }

  @JsonProperty(value = "function")
  public KdfFunction getKdfFunction() {
    return kdfFunction;
  }

  @JsonProperty(value = "params")
  public KdfParam getParam() {
    return param;
  }

  @JsonProperty(value = "message")
  public String getMessage() {
    return message;
  }

  public void validate() throws KeyStoreValidationException {
    param.validate();
  }

  @Override
  public String toString() {
    return MoreObjects.toStringHelper(this)
        .add("function", kdfFunction)
        .add("params", param)
        .add("message", message)
        .toString();
  }
}
