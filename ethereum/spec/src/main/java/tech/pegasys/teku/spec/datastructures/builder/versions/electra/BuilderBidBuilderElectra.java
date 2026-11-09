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

package tech.pegasys.teku.spec.datastructures.builder.versions.electra;

import static com.google.common.base.Preconditions.checkNotNull;

import tech.pegasys.teku.infrastructure.ssz.primitive.SszUInt256;
import tech.pegasys.teku.spec.datastructures.builder.BuilderBid;
import tech.pegasys.teku.spec.datastructures.builder.BuilderBidBuilder;
import tech.pegasys.teku.spec.datastructures.builder.versions.deneb.BuilderBidBuilderDeneb;
import tech.pegasys.teku.spec.datastructures.execution.ExecutionRequests;
import tech.pegasys.teku.spec.datastructures.type.SszPublicKey;

public class BuilderBidBuilderElectra extends BuilderBidBuilderDeneb {

  private BuilderBidSchemaElectra schema;

  protected ExecutionRequests executionRequests;

  public BuilderBidBuilderElectra schema(final BuilderBidSchemaElectra schema) {
    this.schema = schema;
    return this;
  }

  @Override
  public BuilderBidBuilder executionRequests(final ExecutionRequests executionRequests) {
    this.executionRequests = executionRequests;
    return this;
  }

  @Override
  public BuilderBid build() {
    return new BuilderBidElectraImpl(
        schema,
        header,
        blobKzgCommitments,
        executionRequests,
        SszUInt256.of(value),
        new SszPublicKey(publicKey));
  }

  @Override
  protected void validate() {
    super.validate();
    checkNotNull(executionRequests, "executionRequests must be specified");
  }
}
