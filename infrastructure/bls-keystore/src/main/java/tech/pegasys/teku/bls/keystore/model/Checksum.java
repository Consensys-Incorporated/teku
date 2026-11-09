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

public class Checksum {
  private final ChecksumFunction checksumFunction;
  private final EmptyParam emptyParam;
  private final Bytes message;

  @JsonCreator
  public Checksum(
      @JsonProperty(value = "function", required = true) final ChecksumFunction checksumFunction,
      @JsonProperty(value = "params", required = true) final EmptyParam emptyParam,
      @JsonProperty(value = "message", required = true) final Bytes message) {
    this.checksumFunction = checksumFunction;
    this.emptyParam = emptyParam;
    this.message = message;
  }

  public Checksum(final Bytes message) {
    this(ChecksumFunction.SHA256, new EmptyParam(), message);
  }

  @JsonProperty(value = "function")
  public ChecksumFunction getChecksumFunction() {
    return checksumFunction;
  }

  @JsonProperty(value = "params")
  public EmptyParam getEmptyParam() {
    return emptyParam;
  }

  @JsonProperty(value = "message")
  public Bytes getMessage() {
    return message;
  }

  @Override
  public String toString() {
    return MoreObjects.toStringHelper(this)
        .add("function", checksumFunction)
        .add("param", emptyParam)
        .add("message", message)
        .toString();
  }
}
