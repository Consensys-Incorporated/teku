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

package tech.pegasys.teku.test.acceptance;

import static org.assertj.core.api.Assertions.assertThat;

import com.google.common.io.Resources;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Locale;
import org.apache.tuweni.bytes.Bytes32;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import tech.pegasys.teku.infrastructure.bytes.Bytes20;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.test.acceptance.dsl.AcceptanceTestBase;
import tech.pegasys.teku.test.acceptance.dsl.GenesisGenerator.InitialStateData;
import tech.pegasys.teku.test.acceptance.dsl.TekuBeaconNode;
import tech.pegasys.teku.test.acceptance.dsl.TekuNodeConfigBuilder;
import tech.pegasys.teku.test.acceptance.dsl.TekuValidatorNode;
import tech.pegasys.teku.test.acceptance.dsl.tools.deposits.ValidatorKeystores;

public class BlockProposalAcceptanceTest extends AcceptanceTestBase {
  private static final URL JWT_FILE = Resources.getResource("auth/ee-jwt-secret.hex");

  @ParameterizedTest(name = "ssz_encode={0}")
  @ValueSource(booleans = {true, false})
  void shouldHaveCorrectFeeRecipientAndGraffiti(final boolean useSszBlocks) throws Exception {
    final String networkName = "swift";

    final ValidatorKeystores validatorKeystores =
        createValidatorKeyGenerator().generateValidatorKeys(8);

    final InitialStateData genesis =
        createGenesisGenerator()
            .network(networkName)
            .withAltairEpoch(UInt64.ZERO)
            .withBellatrixEpoch(UInt64.ZERO)
            .withCapellaEpoch(UInt64.ZERO)
            .validatorKeys(validatorKeystores)
            .generate();

    final String defaultFeeRecipient = "0xFE3B557E8Fb62b89F4916B721be55cEb828dBd73";
    final String userGraffiti = "My block \uD83D\uDE80"; // 13 bytes
    final TekuBeaconNode beaconNode =
        createTekuBeaconNode(
            TekuNodeConfigBuilder.createBeaconNode()
                .withStubExecutionEngine()
                .withJwtSecretFile(JWT_FILE)
                .withNetwork(networkName)
                .withInitialState(genesis)
                .withAltairEpoch(UInt64.ZERO)
                .withBellatrixEpoch(UInt64.ZERO)
                .withCapellaEpoch(UInt64.ZERO)
                .withValidatorProposerDefaultFeeRecipient(defaultFeeRecipient)
                .build());
    final TekuValidatorNode validatorClient =
        createValidatorNode(
            TekuNodeConfigBuilder.createValidatorClient()
                .withReadOnlyKeystorePath(validatorKeystores)
                .withValidatorProposerDefaultFeeRecipient(defaultFeeRecipient)
                .withInteropModeDisabled()
                .withBeaconNodes(beaconNode)
                .withBeaconNodeSszBlocksEnabled(useSszBlocks)
                .withGraffiti(userGraffiti)
                .withNetwork("auto")
                .build());

    beaconNode.start();
    validatorClient.start();

    beaconNode.waitForBlockSatisfying(
        block -> {
          final Bytes20 feeRecipient =
              block
                  .getMessage()
                  .getBody()
                  .getOptionalExecutionPayload()
                  .orElseThrow()
                  .getFeeRecipient();
          assertThat(feeRecipient.toHexString().toLowerCase(Locale.ROOT))
              .isEqualTo(defaultFeeRecipient.toLowerCase(Locale.ROOT));
          final Bytes32 graffiti = block.getMessage().getBody().getGraffiti();
          final String graffitiMessage =
              new String(
                  Arrays.copyOfRange(
                      graffiti.toArray(), 0, 32 - graffiti.numberOfTrailingZeroBytes()),
                  StandardCharsets.UTF_8);
          // 13 bytes + 1 byte
          assertThat(graffitiMessage).startsWith(userGraffiti + " ");
          // 18 bytes left, so 12 bytes client footprint: ELxxxxTKxxxx. 20 bytes with full commits
          // doesn't fit
          assertThat(graffitiMessage).contains("TK");
          // stub execution endpoint.
          assertThat(graffitiMessage).contains("SB0000");
        });
  }
}
