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

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockserver.model.HttpRequest.request;
import static org.mockserver.model.HttpResponse.response;
import static tech.pegasys.teku.validator.client.signer.ExternalSigner.FORK_INFO;
import static tech.pegasys.teku.validator.client.signer.ExternalSignerTestUtil.validateMetrics;
import static tech.pegasys.teku.validator.client.signer.ExternalSignerTestUtil.verifySignRequest;

import java.util.Map;
import java.util.function.Function;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.bls.BLS;
import tech.pegasys.teku.bls.BLSSignature;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.TestSpecFactory;
import tech.pegasys.teku.spec.datastructures.blocks.BeaconBlock;
import tech.pegasys.teku.spec.datastructures.operations.versions.altair.ContributionAndProof;
import tech.pegasys.teku.spec.datastructures.operations.versions.altair.SyncAggregatorSelectionData;
import tech.pegasys.teku.spec.datastructures.operations.versions.altair.SyncCommitteeContribution;
import tech.pegasys.teku.spec.logic.common.util.SyncCommitteeUtil;
import tech.pegasys.teku.validator.api.signer.SignType;
import tech.pegasys.teku.validator.api.signer.SyncAggregatorSelectionDataWrapper;
import tech.pegasys.teku.validator.api.signer.SyncCommitteeMessageWrapper;

public class ExternalSignerAltairIntegrationTest extends AbstractExternalSignerIntegrationTest {

  private final SyncCommitteeUtil syncCommitteeUtil =
      spec.getSyncCommitteeUtilRequired(UInt64.ZERO);
  private final UInt64 slot = UInt64.ZERO;
  private final Bytes32 beaconBlockRoot = dataStructureUtil.randomBytes32();
  private final SyncAggregatorSelectionData selectionData =
      syncCommitteeUtil.createSyncAggregatorSelectionData(slot, UInt64.ZERO);
  private final BLSSignature aggregatorSignature =
      BLSSignature.fromBytesCompressed(
          Bytes.fromHexString(
              "0x8f5c34de9e22ceaa7e8d165fc0553b32f02188539e89e2cc91e2eb9077645986550d872ee3403204ae5d554eae3cac12124e18d2324bccc814775316aaef352abc0450812b3ca9fde96ecafa911b3b8bfddca8db4027f08e29c22a9c370ad933"));
  final SyncCommitteeContribution contribution =
      dataStructureUtil.randomSyncCommitteeContribution(slot, beaconBlockRoot);
  final ContributionAndProof contributionAndProof =
      syncCommitteeUtil.createContributionAndProof(
          UInt64.valueOf(11), contribution, aggregatorSignature);

  @Override
  public Spec getSpec() {
    return TestSpecFactory.createMinimalAltair();
  }

  @Test
  void shouldSignAltairBlock() throws Exception {
    final BeaconBlock block = dataStructureUtil.randomBeaconBlock(10);
    final BLSSignature expectedSignature =
        BLSSignature.fromBytesCompressed(
            Bytes.fromBase64String(
                "luIZGEgsjSbFo4MEPVeqaqqm1AnnTODcxFy9gPmdAywVmDIpqkzYed8DJ2l4zx5WAejUTox+NO5HQ4M2APMNovd7FuqnCSVUEftrL4WtJqegPrING2ZCtVTrcaUzFpUQ"));
    client.when(request()).respond(response().withBody(expectedSignature.toString()));

    final BLSSignature response = externalSigner.signBlock(block, forkInfo).join();
    assertThat(response).isEqualTo(expectedSignature);

    final ExternalSignerBlockRequestProvider externalSignerBlockRequestProvider =
        new ExternalSignerBlockRequestProvider(spec, block);

    final SigningRequestBody signingRequestBody =
        new SigningRequestBody(
            signingRootUtil.signingRootForSignBlock(block, forkInfo),
            externalSignerBlockRequestProvider.getSignType(),
            externalSignerBlockRequestProvider.getBlockMetadata(Map.of("fork_info", forkInfo)));

    verifySignRequest(
        client,
        KEYPAIR.getPublicKey().toString(),
        signingRequestBody,
        getSpec().getGenesisSchemaDefinitions());

    validateMetrics(metricsSystem, 1, 0, 0);
  }

  @Test
  public void shouldSignSyncCommitteeMessage() throws Exception {
    final Bytes expectedSigningRoot =
        signingRootFromSyncCommitteeUtils(
                slot,
                utils ->
                    utils.getSyncCommitteeMessageSigningRoot(
                        beaconBlockRoot, spec.computeEpochAtSlot(slot), forkInfo))
            .get();
    final BLSSignature expectedSignature = BLS.sign(KEYPAIR.getSecretKey(), expectedSigningRoot);
    client.when(request()).respond(response().withBody(expectedSignature.toString()));

    final BLSSignature response =
        externalSigner.signSyncCommitteeMessage(slot, beaconBlockRoot, forkInfo).join();

    assertThat(response).isEqualTo(expectedSignature);

    final SigningRequestBody signingRequestBody =
        new SigningRequestBody(
            expectedSigningRoot,
            SignType.SYNC_COMMITTEE_MESSAGE,
            Map.of(
                FORK_INFO,
                forkInfo,
                "sync_committee_message",
                new SyncCommitteeMessageWrapper(beaconBlockRoot, slot)));

    verifySignRequest(
        client,
        KEYPAIR.getPublicKey().toString(),
        signingRequestBody,
        getSpec().getGenesisSchemaDefinitions());

    validateMetrics(metricsSystem, 1, 0, 0);
  }

  @Test
  public void shouldSignSyncCommitteeSelectionProof() throws Exception {
    final Bytes expectedSigningRoot =
        signingRootFromSyncCommitteeUtils(
                slot,
                utils -> utils.getSyncAggregatorSelectionDataSigningRoot(selectionData, forkInfo))
            .get();
    final BLSSignature expectedSignature = BLS.sign(KEYPAIR.getSecretKey(), expectedSigningRoot);
    client.when(request()).respond(response().withBody(expectedSignature.toString()));

    final BLSSignature response =
        externalSigner.signSyncCommitteeSelectionProof(selectionData, forkInfo).join();

    assertThat(response).isEqualTo(expectedSignature);

    final SigningRequestBody signingRequestBody =
        new SigningRequestBody(
            expectedSigningRoot,
            SignType.SYNC_COMMITTEE_SELECTION_PROOF,
            Map.of(
                FORK_INFO,
                forkInfo,
                SignType.SYNC_AGGREGATOR_SELECTION_DATA.getName(),
                new SyncAggregatorSelectionDataWrapper(
                    selectionData.getSlot(), selectionData.getSubcommitteeIndex())));

    verifySignRequest(
        client,
        KEYPAIR.getPublicKey().toString(),
        signingRequestBody,
        getSpec().getGenesisSchemaDefinitions());

    validateMetrics(metricsSystem, 1, 0, 0);
  }

  @Test
  public void shouldSignContributionAndProof() throws Exception {
    final Bytes expectedSigningRoot =
        signingRootFromSyncCommitteeUtils(
                slot,
                utils -> utils.getContributionAndProofSigningRoot(contributionAndProof, forkInfo))
            .get();
    final BLSSignature expectedSignature = BLS.sign(KEYPAIR.getSecretKey(), expectedSigningRoot);
    client.when(request()).respond(response().withBody(expectedSignature.toString()));

    final BLSSignature response =
        externalSigner.signContributionAndProof(contributionAndProof, forkInfo).join();

    assertThat(response).isEqualTo(expectedSignature);

    final SigningRequestBody signingRequestBody =
        new SigningRequestBody(
            expectedSigningRoot,
            SignType.SYNC_COMMITTEE_CONTRIBUTION_AND_PROOF,
            Map.of(FORK_INFO, forkInfo, "contribution_and_proof", contributionAndProof));

    verifySignRequest(
        client,
        KEYPAIR.getPublicKey().toString(),
        signingRequestBody,
        getSpec().getGenesisSchemaDefinitions());

    validateMetrics(metricsSystem, 1, 0, 0);
  }

  private SafeFuture<Bytes> signingRootFromSyncCommitteeUtils(
      final UInt64 slot, final Function<SyncCommitteeUtil, Bytes> createSigningRoot) {
    return SafeFuture.of(() -> createSigningRoot.apply(spec.getSyncCommitteeUtilRequired(slot)));
  }
}
