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

package tech.pegasys.teku.spec.datastructures.blocks.blockbody.versions.gloas;

import java.util.Optional;
import tech.pegasys.teku.infrastructure.ssz.SszList;
import tech.pegasys.teku.spec.datastructures.blocks.blockbody.BeaconBlockBody;
import tech.pegasys.teku.spec.datastructures.blocks.blockbody.versions.electra.BeaconBlockBodyElectra;
import tech.pegasys.teku.spec.datastructures.epbs.versions.gloas.PayloadAttestation;
import tech.pegasys.teku.spec.datastructures.epbs.versions.gloas.SignedExecutionPayloadBid;
import tech.pegasys.teku.spec.datastructures.execution.ExecutionPayload;
import tech.pegasys.teku.spec.datastructures.execution.ExecutionPayloadSummary;
import tech.pegasys.teku.spec.datastructures.execution.ExecutionRequests;
import tech.pegasys.teku.spec.datastructures.execution.versions.deneb.ExecutionPayloadDeneb;
import tech.pegasys.teku.spec.datastructures.type.SszKZGCommitment;

public interface BeaconBlockBodyGloas extends BeaconBlockBodyElectra {
  static BeaconBlockBodyGloas required(final BeaconBlockBody body) {
    return body.toVersionGloas()
        .orElseThrow(
            () ->
                new IllegalArgumentException(
                    "Expected Gloas block body but got " + body.getClass().getSimpleName()));
  }

  @Override
  BeaconBlockBodySchemaGloas<?> getSchema();

  SignedExecutionPayloadBid getSignedExecutionPayloadBid();

  SszList<PayloadAttestation> getPayloadAttestations();

  ExecutionRequests getParentExecutionRequests();

  @Override
  default Optional<ExecutionPayload> getOptionalExecutionPayload() {
    return Optional.empty();
  }

  @Override
  default Optional<ExecutionPayloadSummary> getOptionalExecutionPayloadSummary() {
    return Optional.empty();
  }

  @Override
  default Optional<SignedExecutionPayloadBid> getOptionalSignedExecutionPayloadBid() {
    return Optional.of(getSignedExecutionPayloadBid());
  }

  @Override
  default Optional<SszList<PayloadAttestation>> getOptionalPayloadAttestations() {
    return Optional.of(getPayloadAttestations());
  }

  @Override
  default Optional<ExecutionRequests> getOptionalParentExecutionRequests() {
    return Optional.of(getParentExecutionRequests());
  }

  @Override
  default ExecutionPayloadDeneb getExecutionPayload() {
    throw new UnsupportedOperationException("execution_payload field was removed in Gloas");
  }

  @Override
  default SszList<SszKZGCommitment> getBlobKzgCommitments() {
    return getSignedExecutionPayloadBid().getMessage().getBlobKzgCommitments();
  }

  @Override
  default ExecutionRequests getExecutionRequests() {
    throw new UnsupportedOperationException("execution_requests field was removed in Gloas");
  }

  @Override
  default Optional<BeaconBlockBodyGloas> toVersionGloas() {
    return Optional.of(this);
  }
}
