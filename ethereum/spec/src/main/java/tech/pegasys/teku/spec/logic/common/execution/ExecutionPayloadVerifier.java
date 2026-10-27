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

package tech.pegasys.teku.spec.logic.common.execution;

import java.util.Optional;
import tech.pegasys.teku.bls.BLSSignatureVerifier;
import tech.pegasys.teku.spec.datastructures.epbs.versions.gloas.SignedExecutionPayloadEnvelope;
import tech.pegasys.teku.spec.datastructures.state.AnchorPoint;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.BeaconState;
import tech.pegasys.teku.spec.logic.versions.bellatrix.block.OptimisticExecutionPayloadExecutor;

public interface ExecutionPayloadVerifier {

  // verify_execution_payload_envelope
  void verifyExecutionPayloadEnvelope(
      SignedExecutionPayloadEnvelope signedEnvelope,
      BeaconState state,
      BLSSignatureVerifier signatureVerifier,
      Optional<? extends OptimisticExecutionPayloadExecutor> payloadExecutor)
      throws ExecutionPayloadVerificationException;

  /** The caller must supply its trusted, state-only finalized anchor. */
  void verifyCheckpointAnchorExecutionPayloadEnvelope(
      SignedExecutionPayloadEnvelope signedEnvelope,
      AnchorPoint anchor,
      BLSSignatureVerifier signatureVerifier,
      Optional<? extends OptimisticExecutionPayloadExecutor> payloadExecutor)
      throws ExecutionPayloadVerificationException;

  // verify_execution_payload_envelope_signature
  boolean verifyExecutionPayloadEnvelopeSignature(
      BeaconState state,
      SignedExecutionPayloadEnvelope signedEnvelope,
      BLSSignatureVerifier signatureVerifier);
}
