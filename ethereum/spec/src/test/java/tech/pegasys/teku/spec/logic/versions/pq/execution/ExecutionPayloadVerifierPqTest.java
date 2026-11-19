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

package tech.pegasys.teku.spec.logic.versions.pq.execution;

import static org.assertj.core.api.Assertions.assertThat;
import static tech.pegasys.teku.spec.config.SpecConfigGloas.BUILDER_INDEX_SELF_BUILD;
import static tech.pegasys.teku.spec.constants.PqConstants.LEAF_OFFSET_EXECUTION_PAYLOAD_ENVELOPE;

import com.google.common.io.Resources;
import java.util.ArrayList;
import java.util.List;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.bls.BLSSignatureVerifier;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.SpecMilestone;
import tech.pegasys.teku.spec.SpecVersion;
import tech.pegasys.teku.spec.TestSpecFactory;
import tech.pegasys.teku.spec.config.SpecConfigGloas;
import tech.pegasys.teku.spec.config.SpecConfigPq;
import tech.pegasys.teku.spec.constants.Domain;
import tech.pegasys.teku.spec.datastructures.blocks.BeaconBlockHeader;
import tech.pegasys.teku.spec.datastructures.epbs.versions.gloas.ExecutionPayloadEnvelope;
import tech.pegasys.teku.spec.datastructures.epbs.versions.gloas.SignedExecutionPayloadEnvelope;
import tech.pegasys.teku.spec.datastructures.execution.ExecutionRequestsDataCodec;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.versions.pq.BeaconStatePq;
import tech.pegasys.teku.spec.datastructures.state.versions.pq.BuilderPq;
import tech.pegasys.teku.spec.datastructures.state.versions.pq.ValidatorPq;
import tech.pegasys.teku.spec.datastructures.type.SszXmssSignature;
import tech.pegasys.teku.spec.logic.versions.pq.crypto.XmssSignatureVerifier;
import tech.pegasys.teku.spec.logic.versions.pq.helpers.BeaconStateAccessorsPq;
import tech.pegasys.teku.spec.logic.versions.pq.helpers.MiscHelpersPq;
import tech.pegasys.teku.spec.logic.versions.pq.helpers.PredicatesPq;
import tech.pegasys.teku.spec.schemas.SchemaDefinitionsPq;
import tech.pegasys.teku.spec.util.DataStructureUtil;

class ExecutionPayloadVerifierPqTest {

  private final Spec spec = TestSpecFactory.createMinimalPq();
  private final DataStructureUtil dataStructureUtil = new DataStructureUtil(spec);
  private final SpecVersion specVersion = spec.forMilestone(SpecMilestone.PQ);
  private final SpecConfigPq config = SpecConfigPq.required(specVersion.getConfig());
  private final SchemaDefinitionsPq schemaDefinitions =
      SchemaDefinitionsPq.required(specVersion.getSchemaDefinitions());
  private final PredicatesPq predicates = new PredicatesPq(config);
  private final MiscHelpersPq miscHelpers =
      new MiscHelpersPq(config, predicates, schemaDefinitions);
  private final BeaconStateAccessorsPq accessors =
      new BeaconStateAccessorsPq(config, schemaDefinitions, predicates, miscHelpers);

  private final List<List<Object>> calls = new ArrayList<>();
  private final XmssSignatureVerifier xmssVerifier =
      (pubkey, leafIndex, message, signature) -> {
        calls.add(List.of(pubkey, leafIndex, message, signature));
        return true;
      };
  private final ExecutionPayloadVerifierPq verifier =
      new ExecutionPayloadVerifierPq(
          miscHelpers,
          accessors,
          new ExecutionRequestsDataCodec(schemaDefinitions.getPqExecutionRequestsSchema()),
          xmssVerifier);

  private final UInt64 blockSlot = UInt64.valueOf(11);

  @Test
  void selfBuiltEnvelopeIsVerifiedWithTheProposersKey() throws Exception {
    final BeaconStatePq state = stateWithLatestBlock(UInt64.valueOf(5));
    final SignedExecutionPayloadEnvelope envelope = envelope(BUILDER_INDEX_SELF_BUILD);

    assertThat(verify(state, envelope)).isTrue();

    final Bytes32 proposerKey = ((ValidatorPq) state.getValidators().get(5)).getXmssPublicKey();
    assertThat(calls).containsExactly(expectedCall(state, proposerKey, envelope));
  }

  @Test
  void builtEnvelopeIsVerifiedWithTheBuildersKey() throws Exception {
    final Bytes32 builderKey = dataStructureUtil.randomBytes32();
    final BeaconStatePq state =
        stateWithLatestBlock(UInt64.valueOf(5))
            .updatedPq(
                mutable ->
                    mutable
                        .getBuilders()
                        .append(
                            new BuilderPq(
                                builderKey,
                                SpecConfigGloas.PAYLOAD_BUILDER_VERSION,
                                dataStructureUtil.randomEth1Address(),
                                UInt64.ONE,
                                UInt64.ZERO,
                                SpecConfigGloas.FAR_FUTURE_EPOCH)));
    final SignedExecutionPayloadEnvelope envelope = envelope(UInt64.ZERO);

    assertThat(verify(state, envelope)).isTrue();
    assertThat(calls).containsExactly(expectedCall(state, builderKey, envelope));
  }

  @Test
  void blsSignedEnvelopeIsRejected() throws Exception {
    final BeaconStatePq state = stateWithLatestBlock(UInt64.valueOf(5));
    final SignedExecutionPayloadEnvelope envelope =
        schemaDefinitions
            .getSignedExecutionPayloadEnvelopeSchema()
            .create(envelopeMessage(BUILDER_INDEX_SELF_BUILD), dataStructureUtil.randomSignature());

    assertThat(verify(state, envelope)).isFalse();
    assertThat(calls).isEmpty();
  }

  private boolean verify(final BeaconStatePq state, final SignedExecutionPayloadEnvelope envelope) {
    return verifier.verifyExecutionPayloadEnvelopeSignature(
        state, envelope, BLSSignatureVerifier.NOOP, blockSlot);
  }

  private List<Object> expectedCall(
      final BeaconStatePq state,
      final Bytes32 pubkey,
      final SignedExecutionPayloadEnvelope envelope) {
    final Bytes32 domain =
        accessors.getDomain(
            state.getForkInfo(), Domain.BEACON_BUILDER, miscHelpers.computeEpochAtSlot(blockSlot));
    return List.of(
        pubkey,
        miscHelpers.computeXmssLeafIndex(blockSlot, LEAF_OFFSET_EXECUTION_PAYLOAD_ENVELOPE),
        Bytes32.wrap(miscHelpers.computeSigningRoot(envelope.getMessage(), domain)),
        envelope.getXmssSignature());
  }

  private SignedExecutionPayloadEnvelope envelope(final UInt64 builderIndex) {
    final byte[] signature = new byte[2404];
    signature[0] = 9;
    return schemaDefinitions
        .getPqBlockSchemas()
        .getSignedExecutionPayloadEnvelopeSchema()
        .create(envelopeMessage(builderIndex), new SszXmssSignature(Bytes.wrap(signature)));
  }

  private ExecutionPayloadEnvelope envelopeMessage(final UInt64 builderIndex) {
    return schemaDefinitions
        .getExecutionPayloadEnvelopeSchema()
        .create(
            dataStructureUtil.randomExecutionPayload(blockSlot),
            dataStructureUtil.randomExecutionRequests(blockSlot),
            builderIndex,
            dataStructureUtil.randomBytes32(),
            dataStructureUtil.randomBytes32());
  }

  private BeaconStatePq stateWithLatestBlock(final UInt64 proposerIndex) throws Exception {
    return fixtureGenesis()
        .updatedPq(
            mutable -> {
              mutable.setSlot(blockSlot);
              mutable.setLatestBlockHeader(
                  new BeaconBlockHeader(
                      blockSlot,
                      proposerIndex,
                      dataStructureUtil.randomBytes32(),
                      Bytes32.ZERO,
                      dataStructureUtil.randomBytes32()));
            });
  }

  private BeaconStatePq fixtureGenesis() throws Exception {
    return BeaconStatePq.required(
        schemaDefinitions
            .getPqBeaconStateSchema()
            .sszDeserialize(
                Bytes.wrap(
                    Resources.toByteArray(
                        Resources.getResource(
                            "tech/pegasys/teku/spec/datastructures/state/beaconstate/versions/pq/"
                                + "pq_genesis_minimal.ssz")))));
  }
}
