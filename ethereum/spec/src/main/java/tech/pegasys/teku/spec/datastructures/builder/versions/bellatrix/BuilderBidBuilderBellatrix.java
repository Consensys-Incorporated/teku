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

package tech.pegasys.teku.spec.datastructures.builder.versions.bellatrix;

import static com.google.common.base.Preconditions.checkNotNull;

import org.apache.tuweni.units.bigints.UInt256;
import tech.pegasys.teku.bls.BLSPublicKey;
import tech.pegasys.teku.infrastructure.ssz.SszList;
import tech.pegasys.teku.infrastructure.ssz.primitive.SszUInt256;
import tech.pegasys.teku.spec.datastructures.builder.BuilderBid;
import tech.pegasys.teku.spec.datastructures.builder.BuilderBidBuilder;
import tech.pegasys.teku.spec.datastructures.execution.ExecutionPayloadHeader;
import tech.pegasys.teku.spec.datastructures.execution.ExecutionRequests;
import tech.pegasys.teku.spec.datastructures.type.SszKZGCommitment;
import tech.pegasys.teku.spec.datastructures.type.SszPublicKey;

public class BuilderBidBuilderBellatrix implements BuilderBidBuilder {

  private BuilderBidSchemaBellatrix schema;

  protected ExecutionPayloadHeader header;
  protected UInt256 value;
  protected BLSPublicKey publicKey;

  public BuilderBidBuilderBellatrix schema(final BuilderBidSchemaBellatrix schema) {
    this.schema = schema;
    return this;
  }

  @Override
  public BuilderBidBuilder header(final ExecutionPayloadHeader header) {
    this.header = header;
    return this;
  }

  @Override
  public BuilderBidBuilder blobKzgCommitments(final SszList<SszKZGCommitment> blobKzgCommitments) {
    return this;
  }

  @Override
  public BuilderBidBuilder executionRequests(final ExecutionRequests executionRequests) {
    return this;
  }

  @Override
  public BuilderBidBuilder value(final UInt256 value) {
    this.value = value;
    return this;
  }

  @Override
  public BuilderBidBuilder publicKey(final BLSPublicKey publicKey) {
    this.publicKey = publicKey;
    return null;
  }

  @Override
  public BuilderBid build() {
    return new BuilderBidBellatrix(
        schema, header, SszUInt256.of(value), new SszPublicKey(publicKey));
  }

  protected void validate() {
    checkNotNull(header, "header must be specified");
    checkNotNull(value, "value must be specified");
    checkNotNull(publicKey, "publicKey must be specified");
    validateSchema();
  }

  protected void validateSchema() {
    checkNotNull(schema, "schema must be specified");
  }
}
