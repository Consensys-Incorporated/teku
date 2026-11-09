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

package tech.pegasys.teku.spec.datastructures.epbs.versions.gloas;

import static com.google.common.base.Preconditions.checkState;

import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.bls.BLSSignature;
import tech.pegasys.teku.infrastructure.ssz.containers.Container2;
import tech.pegasys.teku.infrastructure.ssz.tree.TreeNode;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.datastructures.execution.ExecutionPayload;
import tech.pegasys.teku.spec.datastructures.execution.ExecutionRequests;
import tech.pegasys.teku.spec.datastructures.type.SszSignature;
import tech.pegasys.teku.spec.schemas.SchemaDefinitionsGloas;

public class SignedBlindedExecutionPayloadEnvelope
    extends Container2<
        SignedBlindedExecutionPayloadEnvelope, BlindedExecutionPayloadEnvelope, SszSignature> {

  SignedBlindedExecutionPayloadEnvelope(
      final SignedBlindedExecutionPayloadEnvelopeSchema schema,
      final BlindedExecutionPayloadEnvelope message,
      final BLSSignature signature) {
    super(schema, message, new SszSignature(signature));
  }

  SignedBlindedExecutionPayloadEnvelope(
      final SignedBlindedExecutionPayloadEnvelopeSchema type, final TreeNode backingNode) {
    super(type, backingNode);
  }

  public BlindedExecutionPayloadEnvelope getMessage() {
    return getField0();
  }

  public BLSSignature getSignature() {
    return getField1().getSignature();
  }

  @Override
  public SignedBlindedExecutionPayloadEnvelopeSchema getSchema() {
    return (SignedBlindedExecutionPayloadEnvelopeSchema) super.getSchema();
  }

  public ExecutionRequests getExecutionRequests() {
    return getMessage().getExecutionRequests();
  }

  public UInt64 getSlot() {
    return getMessage().getSlot();
  }

  public Bytes32 getBeaconBlockRoot() {
    return getMessage().getBeaconBlockRoot();
  }

  public SignedExecutionPayloadEnvelope unblind(
      final SchemaDefinitionsGloas schemaDefinitions, final ExecutionPayload executionPayload) {
    final SignedExecutionPayloadEnvelope signedExecutionPayloadEnvelope =
        schemaDefinitions
            .getSignedExecutionPayloadEnvelopeSchema()
            .create(getMessage().unblind(schemaDefinitions, executionPayload), getSignature());
    checkState(
        signedExecutionPayloadEnvelope.hashTreeRoot().equals(hashTreeRoot()),
        "unblinded signed execution payload envelope root does not match original envelope root");
    return signedExecutionPayloadEnvelope;
  }
}
