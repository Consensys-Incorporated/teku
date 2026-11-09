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

package tech.pegasys.teku.validator.client.signer;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;
import static tech.pegasys.teku.validator.client.signer.ExternalSigner.FORK_INFO;

import com.google.common.io.Resources;
import java.io.IOException;
import java.util.Map;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.infrastructure.json.JsonUtil;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.TestSpecFactory;
import tech.pegasys.teku.spec.datastructures.blocks.BeaconBlock;
import tech.pegasys.teku.spec.datastructures.state.ForkInfo;
import tech.pegasys.teku.spec.signatures.SigningRootUtil;
import tech.pegasys.teku.spec.util.DataStructureUtil;
import tech.pegasys.teku.validator.api.signer.AggregationSlotWrapper;
import tech.pegasys.teku.validator.api.signer.SignType;
import tech.pegasys.teku.validator.api.signer.SyncAggregatorSelectionDataWrapper;
import tech.pegasys.teku.validator.api.signer.SyncCommitteeMessageWrapper;

class SigningRequestBodyTest {
  private Spec spec;
  private DataStructureUtil dataStructureUtil;

  @Test
  void altairVoluntaryExitExternalSignerMessage() throws IOException {
    spec = TestSpecFactory.createMinimalAltair();
    dataStructureUtil = new DataStructureUtil(spec);
    checkSigningMessageStructure(
        new SigningRequestBody(
            dataStructureUtil.randomBytes32(),
            SignType.VOLUNTARY_EXIT,
            Map.of(
                SignType.VOLUNTARY_EXIT.getName(),
                dataStructureUtil.randomVoluntaryExit(),
                FORK_INFO,
                dataStructureUtil.randomForkInfo())));
  }

  @Test
  void altairAggregationSlotExternalSignerMessage() throws IOException {
    spec = TestSpecFactory.createMinimalAltair();
    dataStructureUtil = new DataStructureUtil(spec);
    checkSigningMessageStructure(
        new SigningRequestBody(
            dataStructureUtil.randomBytes32(),
            SignType.AGGREGATION_SLOT,
            Map.of(
                SignType.AGGREGATION_SLOT.getName(),
                new AggregationSlotWrapper(dataStructureUtil.randomSlot()),
                FORK_INFO,
                dataStructureUtil.randomForkInfo())));
  }

  @Test
  void altairAggregateAndProofExternalSignerMessage() throws IOException {
    spec = TestSpecFactory.createMinimalAltair();
    dataStructureUtil = new DataStructureUtil(spec);
    checkSigningMessageStructure(
        new SigningRequestBody(
            dataStructureUtil.randomBytes32(),
            SignType.AGGREGATE_AND_PROOF,
            Map.of(
                SignType.AGGREGATE_AND_PROOF.getName(),
                dataStructureUtil.randomAggregateAndProof(),
                FORK_INFO,
                dataStructureUtil.randomForkInfo())));
  }

  @Test
  void altairSyncCommitteeSelectionProofExternalSignerMessage() throws IOException {
    spec = TestSpecFactory.createMinimalAltair();
    dataStructureUtil = new DataStructureUtil(spec);
    checkSigningMessageStructure(
        new SigningRequestBody(
            dataStructureUtil.randomBytes32(),
            SignType.SYNC_COMMITTEE_SELECTION_PROOF,
            Map.of(
                SignType.SYNC_COMMITTEE_SELECTION_PROOF.getName(),
                new SyncAggregatorSelectionDataWrapper(
                    dataStructureUtil.randomSlot(), dataStructureUtil.randomUInt64(64L)),
                FORK_INFO,
                dataStructureUtil.randomForkInfo())));
  }

  @Test
  void altairSyncCommitteeContributionAndProofExternalSignerMessage() throws IOException {
    spec = TestSpecFactory.createMinimalAltair();
    dataStructureUtil = new DataStructureUtil(spec);
    checkSigningMessageStructure(
        new SigningRequestBody(
            dataStructureUtil.randomBytes32(),
            SignType.SYNC_COMMITTEE_CONTRIBUTION_AND_PROOF,
            Map.of(
                SignType.SYNC_COMMITTEE_CONTRIBUTION_AND_PROOF.getName(),
                dataStructureUtil.randomContributionAndProof(),
                FORK_INFO,
                dataStructureUtil.randomForkInfo())));
  }

  @Test
  void altairSyncCommitteeMessageExternalSignerMessage() throws IOException {
    spec = TestSpecFactory.createMinimalAltair();
    dataStructureUtil = new DataStructureUtil(spec);
    checkSigningMessageStructure(
        new SigningRequestBody(
            dataStructureUtil.randomBytes32(),
            SignType.SYNC_COMMITTEE_MESSAGE,
            Map.of(
                SignType.SYNC_COMMITTEE_MESSAGE.getName(),
                new SyncCommitteeMessageWrapper(
                    dataStructureUtil.randomBytes32(), dataStructureUtil.randomSlot()),
                FORK_INFO,
                dataStructureUtil.randomForkInfo())));
  }

  @Test
  void altairAggregationExternalSignerMessage() throws IOException {
    spec = TestSpecFactory.createMinimalAltair();
    dataStructureUtil = new DataStructureUtil(spec);
    checkSigningMessageStructure(
        new SigningRequestBody(
            dataStructureUtil.randomBytes32(),
            SignType.AGGREGATION_SLOT,
            Map.of(
                SignType.AGGREGATION_SLOT.getName(),
                new AggregationSlotWrapper(dataStructureUtil.randomSlot()),
                FORK_INFO,
                dataStructureUtil.randomForkInfo())));
  }

  @Test
  void altairValidatorRegistrationExternalSignerMessage() throws IOException {
    spec = TestSpecFactory.createMinimalAltair();
    dataStructureUtil = new DataStructureUtil(spec);
    checkSigningMessageStructure(
        new SigningRequestBody(
            dataStructureUtil.randomBytes32(),
            SignType.VALIDATOR_REGISTRATION,
            Map.of(
                SignType.VALIDATOR_REGISTRATION.getName(),
                dataStructureUtil.randomValidatorRegistration())));
  }

  @Test
  void phase0AttestationExternalSignerMessage() throws IOException {
    spec = TestSpecFactory.createMinimalPhase0();
    dataStructureUtil = new DataStructureUtil(spec);
    checkSigningMessageStructure(
        new SigningRequestBody(
            dataStructureUtil.randomBytes32(),
            SignType.ATTESTATION,
            Map.of(
                SignType.ATTESTATION.getName(),
                dataStructureUtil.randomAttestationData(),
                FORK_INFO,
                dataStructureUtil.randomForkInfo())));
  }

  @Test
  void phase0BlockExternalSignerMessage() throws IOException {
    spec = TestSpecFactory.createMinimalPhase0();
    dataStructureUtil = new DataStructureUtil(spec);
    final BeaconBlock block = dataStructureUtil.randomBeaconBlock();
    final ExternalSignerBlockRequestProvider blockRequestProvider =
        new ExternalSignerBlockRequestProvider(spec, block);
    final SigningRootUtil signingRootUtil = new SigningRootUtil(spec);
    final ForkInfo forkInfo = dataStructureUtil.randomForkInfo();
    checkSigningMessageStructure(
        new SigningRequestBody(
            signingRootUtil.signingRootForSignBlock(block, forkInfo),
            blockRequestProvider.getSignType(),
            blockRequestProvider.getBlockMetadata(Map.of(FORK_INFO, forkInfo))));
  }

  @Test
  void altairBlockExternalSignerMessage() throws IOException {
    spec = TestSpecFactory.createMinimalAltair();
    dataStructureUtil = new DataStructureUtil(spec);
    final BeaconBlock block = dataStructureUtil.randomBeaconBlock();
    final ExternalSignerBlockRequestProvider blockRequestProvider =
        new ExternalSignerBlockRequestProvider(spec, block);
    final SigningRootUtil signingRootUtil = new SigningRootUtil(spec);
    final ForkInfo forkInfo = dataStructureUtil.randomForkInfo();
    checkSigningMessageStructure(
        new SigningRequestBody(
            signingRootUtil.signingRootForSignBlock(block, forkInfo),
            blockRequestProvider.getSignType(),
            blockRequestProvider.getBlockMetadata(Map.of(FORK_INFO, forkInfo))));
  }

  @Test
  void bellatrixBlockExternalSignerMessage() throws IOException {
    spec = TestSpecFactory.createMinimalBellatrix();
    dataStructureUtil = new DataStructureUtil(spec);
    final BeaconBlock block = dataStructureUtil.randomBeaconBlock();
    final ExternalSignerBlockRequestProvider blockRequestProvider =
        new ExternalSignerBlockRequestProvider(spec, block);
    final SigningRootUtil signingRootUtil = new SigningRootUtil(spec);
    final ForkInfo forkInfo = dataStructureUtil.randomForkInfo();
    checkSigningMessageStructure(
        new SigningRequestBody(
            signingRootUtil.signingRootForSignBlock(block, forkInfo),
            blockRequestProvider.getSignType(),
            blockRequestProvider.getBlockMetadata(Map.of(FORK_INFO, forkInfo))));
  }

  private void checkSigningMessageStructure(final SigningRequestBody requestBody)
      throws IOException {
    final String expectedJsonFile = getCurrentMethod(1) + ".json";
    final String prettyBody =
        JsonUtil.prettySerialize(
            requestBody, requestBody.getJsonTypeDefinition(spec.getGenesisSchemaDefinitions()));
    final String expected =
        Resources.toString(
            Resources.getResource(SigningRequestBodyTest.class, expectedJsonFile), UTF_8);
    assertThat(prettyBody).isEqualToIgnoringNewLines(expected);
  }

  private static String getCurrentMethod(final int skip) {
    return Thread.currentThread().getStackTrace()[1 + 1 + skip].getMethodName();
  }
}
