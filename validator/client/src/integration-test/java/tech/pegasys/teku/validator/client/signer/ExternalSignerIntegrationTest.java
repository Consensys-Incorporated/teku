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

import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockserver.model.HttpRequest.request;
import static org.mockserver.model.HttpResponse.response;
import static tech.pegasys.teku.infrastructure.http.HttpStatusCodes.SC_PRECONDITION_FAILED;
import static tech.pegasys.teku.validator.client.signer.ExternalSigner.slashableAttestationMessage;
import static tech.pegasys.teku.validator.client.signer.ExternalSigner.slashableBlockMessage;
import static tech.pegasys.teku.validator.client.signer.ExternalSigner.slashableGenericMessage;
import static tech.pegasys.teku.validator.client.signer.ExternalSignerTestUtil.validateMetrics;
import static tech.pegasys.teku.validator.client.signer.ExternalSignerTestUtil.verifySignRequest;

import java.util.Map;
import org.apache.tuweni.bytes.Bytes;
import org.junit.jupiter.api.Test;
import org.mockserver.model.Delay;
import tech.pegasys.teku.bls.BLSSignature;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.TestSpecFactory;
import tech.pegasys.teku.spec.datastructures.blocks.BeaconBlock;
import tech.pegasys.teku.spec.datastructures.builder.ValidatorRegistration;
import tech.pegasys.teku.spec.datastructures.operations.AggregateAndProof;
import tech.pegasys.teku.spec.datastructures.operations.AttestationData;
import tech.pegasys.teku.spec.datastructures.operations.VoluntaryExit;
import tech.pegasys.teku.validator.api.signer.AggregationSlotWrapper;
import tech.pegasys.teku.validator.api.signer.RandaoRevealWrapper;
import tech.pegasys.teku.validator.api.signer.SignType;

public class ExternalSignerIntegrationTest extends AbstractExternalSignerIntegrationTest {

  @Override
  public Spec getSpec() {
    return TestSpecFactory.createMinimalPhase0();
  }

  @Test
  void failsSigningWhenSigningServiceReturnsFailureResponse() {
    final BeaconBlock block = dataStructureUtil.randomBeaconBlock(10);

    assertThatThrownBy(() -> externalSigner.signBlock(block, forkInfo).join())
        .hasCauseInstanceOf(ExternalSignerException.class)
        .hasMessageEndingWith("Invalid response status code: 404");

    validateMetrics(metricsSystem, 0, 1, 0);
  }

  @Test
  void failsSigningWhenSigningServiceTimesOut() {
    final BeaconBlock block = dataStructureUtil.randomBeaconBlock(10);
    final long ensureTimeout = 200;
    final Delay delay = new Delay(MILLISECONDS, TIMEOUT.plusMillis(ensureTimeout).toMillis());
    client.when(request()).respond(response().withDelay(delay));

    assertThatThrownBy(() -> externalSigner.signBlock(block, forkInfo).join())
        .hasCauseInstanceOf(ExternalSignerException.class)
        .hasMessageEndingWith("request timed out");

    validateMetrics(metricsSystem, 0, 0, 1);
  }

  @Test
  void failsSigningWhenSigningServiceReturnsInvalidSignatureResponse() {
    final BeaconBlock block = dataStructureUtil.randomBeaconBlock(10);
    client.when(request()).respond(response().withBody("INVALID_RESPONSE"));

    assertThatThrownBy(() -> externalSigner.signBlock(block, forkInfo).join())
        .hasCauseInstanceOf(ExternalSignerException.class)
        .hasMessageEndingWith(
            "Returned an invalid signature: Illegal character 'I' found at index 0 in hex binary representation");

    validateMetrics(metricsSystem, 0, 1, 0);
  }

  @Test
  void failsSigningBlockWhenSigningServiceRefusesToSignDueToSlashingCondition() {
    final BeaconBlock block = dataStructureUtil.randomBeaconBlock(10);
    client.when(request()).respond(response().withStatusCode(SC_PRECONDITION_FAILED));

    assertThatThrownBy(() -> externalSigner.signBlock(block, forkInfo).join())
        .hasCauseInstanceOf(ExternalSignerException.class)
        .hasMessageEndingWith(slashableBlockMessage(block.getSlot()).get());

    validateMetrics(metricsSystem, 0, 1, 0);
  }

  @Test
  void failsSigningAttestationDataWhenSigningServiceRefusesToSignDueToSlashingCondition() {
    final AttestationData attestationData = dataStructureUtil.randomAttestationData();
    client.when(request()).respond(response().withStatusCode(SC_PRECONDITION_FAILED));

    assertThatThrownBy(() -> externalSigner.signAttestationData(attestationData, forkInfo).join())
        .hasCauseInstanceOf(ExternalSignerException.class)
        .hasMessageEndingWith(slashableAttestationMessage(attestationData).get());

    validateMetrics(metricsSystem, 0, 1, 0);
  }

  @Test
  void failsSigningRandaoRevealWhenSigningServiceRefusesToSignDueToSlashingCondition() {
    final UInt64 epoch = UInt64.valueOf(7);
    client.when(request()).respond(response().withStatusCode(SC_PRECONDITION_FAILED));

    assertThatThrownBy(() -> externalSigner.createRandaoReveal(epoch, forkInfo).join())
        .hasCauseInstanceOf(ExternalSignerException.class)
        .hasMessageEndingWith(slashableGenericMessage("randao reveal").get());

    validateMetrics(metricsSystem, 0, 1, 0);
  }

  @Test
  void shouldSignsBlock() throws Exception {
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
  void shouldSignAttestationData() throws Exception {
    final AttestationData attestationData = dataStructureUtil.randomAttestationData();
    final BLSSignature expectedSignature =
        BLSSignature.fromBytesCompressed(
            Bytes.fromBase64String(
                "l1DUv3fmbvZanhCaaraMk2PKAl+33sf3UHMbxkv18CKILzzIz+Hr6hnLXCHqWQYEGKTtLcf6OLV7Z+Y21BW2bBtJHXJqqzvWkec/j0X0hWaEoWOSAs20sipO1WSIUY2m"));

    client.when(request()).respond(response().withBody(expectedSignature.toString()));

    final BLSSignature response =
        externalSigner.signAttestationData(attestationData, forkInfo).join();
    assertThat(response).isEqualTo(expectedSignature);
    final SigningRequestBody signingRequestBody =
        new SigningRequestBody(
            signingRootUtil.signingRootForSignAttestationData(attestationData, forkInfo),
            SignType.ATTESTATION,
            Map.of("fork_info", forkInfo, "attestation", attestationData));

    verifySignRequest(
        client,
        KEYPAIR.getPublicKey().toString(),
        signingRequestBody,
        getSpec().getGenesisSchemaDefinitions());

    validateMetrics(metricsSystem, 1, 0, 0);
  }

  @Test
  void shouldSignRandaoReveal() throws Exception {
    final UInt64 epoch = UInt64.valueOf(7);
    final BLSSignature expectedSignature =
        BLSSignature.fromBytesCompressed(
            Bytes.fromBase64String(
                "j7vOT7GQBnv+aIqxb0byMWNvMCXhQwAfj38UcMne7pNGXOvNZKnXQ9Knma/NOPUyAvLcRBDtew23vVtzWcm7naaTRJVvLJS6xiPOMIHOw6wNtGggzc20heZAXZAMdaKi"));
    client.when(request()).respond(response().withBody(expectedSignature.toString()));

    final BLSSignature response = externalSigner.createRandaoReveal(epoch, forkInfo).join();
    assertThat(response).isEqualTo(expectedSignature);

    final SigningRequestBody signingRequestBody =
        new SigningRequestBody(
            signingRootUtil.signingRootForRandaoReveal(epoch, forkInfo),
            SignType.RANDAO_REVEAL,
            Map.of("fork_info", forkInfo, "randao_reveal", new RandaoRevealWrapper(epoch)));
    verifySignRequest(
        client,
        KEYPAIR.getPublicKey().toString(),
        signingRequestBody,
        getSpec().getGenesisSchemaDefinitions());

    validateMetrics(metricsSystem, 1, 0, 0);
  }

  @Test
  public void shouldSignAggregationSlot() throws Exception {
    final UInt64 slot = UInt64.valueOf(7);
    final BLSSignature expectedSignature =
        BLSSignature.fromBytesCompressed(
            Bytes.fromBase64String(
                "hnCLCZlbEyzMFq2JLHl6wk4W6gpbFGoQA2N4WB+CpgqVg3gcxJpRKOswtSTU4XdSEU2x3Hf0oTlxer/gVaFwAh84Mm4VLH67LNUxVO4+o2Q5TxOD1sArnvMcOJdGMGp2"));
    client.when(request()).respond(response().withBody(expectedSignature.toString()));

    final SafeFuture<BLSSignature> future = externalSigner.signAggregationSlot(slot, forkInfo);

    assertThat(future.get()).isEqualTo(expectedSignature);

    final SigningRequestBody signingRequestBody =
        new SigningRequestBody(
            signingRootUtil.signingRootForSignAggregationSlot(slot, forkInfo),
            SignType.AGGREGATION_SLOT,
            Map.of("fork_info", forkInfo, "aggregation_slot", new AggregationSlotWrapper(slot)));
    verifySignRequest(
        client,
        KEYPAIR.getPublicKey().toString(),
        signingRequestBody,
        getSpec().getGenesisSchemaDefinitions());
    validateMetrics(metricsSystem, 1, 0, 0);
  }

  @Test
  public void shouldSignAggregateAndProof() throws Exception {
    final AggregateAndProof aggregateAndProof = dataStructureUtil.randomAggregateAndProof();
    final BLSSignature expectedSignature =
        BLSSignature.fromBytesCompressed(
            Bytes.fromBase64String(
                "kHbIuvFcS/kDppbCj0ILOU27ZjSU1P2wPsOKBBwGaz1uvXQxtUXQAdbybN1zotZqCs6pstChIIxDS/WgAZH2z4yX2cM/cM/iKayT2rZZJuu31V2uxP1AYVcyHLEMtF07"));

    client.when(request()).respond(response().withBody(expectedSignature.toString()));
    final BLSSignature response =
        externalSigner.signAggregateAndProof(aggregateAndProof, forkInfo).join();

    assertThat(response).isEqualTo(expectedSignature);

    final SigningRequestBody signingRequestBody =
        new SigningRequestBody(
            signingRootUtil.signingRootForSignAggregateAndProof(aggregateAndProof, forkInfo),
            SignType.AGGREGATE_AND_PROOF,
            Map.of("fork_info", forkInfo, "aggregate_and_proof", aggregateAndProof));
    verifySignRequest(
        client,
        KEYPAIR.getPublicKey().toString(),
        signingRequestBody,
        getSpec().getGenesisSchemaDefinitions());
    validateMetrics(metricsSystem, 1, 0, 0);
  }

  @Test
  public void shouldSignVoluntaryExit() throws Exception {
    final VoluntaryExit voluntaryExit = dataStructureUtil.randomVoluntaryExit();
    final BLSSignature expectedSignature =
        BLSSignature.fromBytesCompressed(
            Bytes.fromBase64String(
                "g9JMIY7595zlrapmwbnCLj8+WX7ry3yfBwNNPQ9mRJ0m+rXTwgDpmsxpzs+kX4F8Bg+KRz+v5BPKEAWkeh8bJBDX7psiELLI3q9WmCX95MXT080jByrtYLdz1Qy3OUKK"));
    client.when(request()).respond(response().withBody(expectedSignature.toString()));
    final BLSSignature response = externalSigner.signVoluntaryExit(voluntaryExit, forkInfo).join();
    assertThat(response).isEqualTo(expectedSignature);

    final SigningRequestBody signingRequestBody =
        new SigningRequestBody(
            signingRootUtil.signingRootForSignVoluntaryExit(voluntaryExit, forkInfo),
            SignType.VOLUNTARY_EXIT,
            Map.of("fork_info", forkInfo, "voluntary_exit", voluntaryExit));
    verifySignRequest(
        client,
        KEYPAIR.getPublicKey().toString(),
        signingRequestBody,
        getSpec().getGenesisSchemaDefinitions());
    validateMetrics(metricsSystem, 1, 0, 0);
  }

  @Test
  public void shouldSignValidatorRegistration() throws Exception {
    final ValidatorRegistration validatorRegistration =
        dataStructureUtil.randomValidatorRegistration();
    final BLSSignature expectedSignature =
        BLSSignature.fromBytesCompressed(
            Bytes.fromBase64String(
                "pTYaqzqFTKb4bOX8kc8vEFj6z/eLbYH9+uGeFFxtklCUlPqugzAQyc7y/8KPcBPJBzRv5Knuph2wnGIyY2c0YbQzblvfXlPGjhBMhL/t8iaS4uF5mYvrZDKefXoNF9TB"));
    client.when(request()).respond(response().withBody(expectedSignature.toString()));
    final BLSSignature response =
        externalSigner.signValidatorRegistration(validatorRegistration).join();
    assertThat(response).isEqualTo(expectedSignature);

    final SigningRequestBody signingRequestBody =
        new SigningRequestBody(
            signingRootUtil.signingRootForValidatorRegistration(validatorRegistration),
            SignType.VALIDATOR_REGISTRATION,
            Map.of(SignType.VALIDATOR_REGISTRATION.getName(), validatorRegistration));
    verifySignRequest(
        client,
        KEYPAIR.getPublicKey().toString(),
        signingRequestBody,
        getSpec().getGenesisSchemaDefinitions());
    validateMetrics(metricsSystem, 1, 0, 0);
  }
}
