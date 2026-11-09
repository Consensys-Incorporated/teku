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
import static tech.pegasys.teku.validator.client.signer.ExternalSignerTestUtil.validateMetrics;
import static tech.pegasys.teku.validator.client.signer.ExternalSignerTestUtil.verifySignRequest;

import java.util.Map;
import org.apache.tuweni.bytes.Bytes;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.bls.BLSSignature;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.TestSpecFactory;
import tech.pegasys.teku.spec.datastructures.blocks.BeaconBlock;
import tech.pegasys.teku.spec.datastructures.blocks.BeaconBlockHeader;

public class ExternalSignerBellatrixIntegrationTest extends AbstractExternalSignerIntegrationTest {

  @Override
  public Spec getSpec() {
    return TestSpecFactory.createMinimalBellatrix();
  }

  @Test
  void shouldSignBellatrixBlock() throws Exception {
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

    final Bytes blockHeaderSigningRoot =
        signingRootUtil.signingRootForSignBlockHeader(BeaconBlockHeader.fromBlock(block), forkInfo);

    final SigningRequestBody signingRequestBody =
        new SigningRequestBody(
            blockHeaderSigningRoot,
            externalSignerBlockRequestProvider.getSignType(),
            externalSignerBlockRequestProvider.getBlockMetadata(Map.of("fork_info", forkInfo)));

    verifySignRequest(
        client,
        KEYPAIR.getPublicKey().toString(),
        signingRequestBody,
        getSpec().getGenesisSchemaDefinitions());

    validateMetrics(metricsSystem, 1, 0, 0);
  }
}
