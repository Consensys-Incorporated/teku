/*
 * Copyright Consensys Software Inc., 2026
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

import static java.util.concurrent.TimeUnit.MINUTES;
import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.Optional;
import org.apache.tuweni.bytes.Bytes32;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.api.response.EventType;
import tech.pegasys.teku.ethereum.execution.types.Eth1Address;
import tech.pegasys.teku.infrastructure.async.Waiter;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBeaconBlock;
import tech.pegasys.teku.test.acceptance.dsl.AcceptanceTestBase;
import tech.pegasys.teku.test.acceptance.dsl.BesuNode;
import tech.pegasys.teku.test.acceptance.dsl.GenesisGenerator.InitialStateData;
import tech.pegasys.teku.test.acceptance.dsl.TekuBeaconNode;
import tech.pegasys.teku.test.acceptance.dsl.TekuBeaconNode.FastConfirmationEventData;
import tech.pegasys.teku.test.acceptance.dsl.TekuNodeConfigBuilder;
import tech.pegasys.teku.test.acceptance.dsl.tools.deposits.ValidatorKeystores;

/**
 * End-to-end proof of the fast confirmation FCU safe-hash integration against a real execution
 * layer. With {@code --Xfast-confirmation-enabled} and a real EL (which marks payloads {@code
 * VALID}, so blocks become eligible for confirmation), Teku:
 *
 * <ul>
 *   <li>keeps advancing the confirmed root as new blocks are confirmed (it is not frozen), and
 *   <li>sends the confirmed block's execution hash as the {@code engine_forkchoiceUpdated} {@code
 *       safe_block_hash} (get_safe_execution_block_hash), so the EL's {@code "safe"} block tracks
 *       the fast-confirmed root.
 * </ul>
 *
 * <p>The confirmed root sitting <em>strictly</em> ahead of the finalized block — the core value of
 * fast confirmation — is already proven deterministically by the {@code fork_choice} {@code
 * fast_confirmation} reference tests (e.g. the {@code current_epoch}/{@code previous_epoch}
 * scenarios assert {@code confirmed_epoch + 1 >= current_epoch} while finality lags by at least two
 * epochs). This end-to-end test instead covers the one piece the reference vectors cannot: the
 * confirmed root's execution hash being delivered to a real EL as its safe block. The strict
 * confirmed-ahead-of-finalized gap is a mainnet-timing property (there finality lags confirmation
 * by ~2 epochs / ~64 slots); on the compressed "swift" schedule (SLOTS_PER_EPOCH=4, 4 validators)
 * finality runs neck-and-neck with confirmation, so that gap is not reliably observable here and is
 * intentionally not asserted.
 */
public class FastConfirmationAcceptanceTest extends AcceptanceTestBase {

  private static final String NETWORK_NAME = "swift";
  private static final Eth1Address WITHDRAWAL_ADDRESS =
      Eth1Address.fromHexString("0xeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeee");

  private BesuNode eth1Node;
  private TekuBeaconNode tekuNode;

  @BeforeEach
  void setup() throws Exception {
    eth1Node =
        createBesuNode(
            config ->
                config
                    .withMergeSupport()
                    .withGenesisFile("besu/mergedGenesis.json")
                    .withJwtTokenAuthorization(JWT_FILE));
    eth1Node.start();

    final ValidatorKeystores validatorKeys =
        createValidatorKeyGenerator().generateValidatorKeys(4, WITHDRAWAL_ADDRESS);
    final InitialStateData initialStateData =
        createGenesisGenerator()
            .network(NETWORK_NAME)
            .withAltairEpoch(UInt64.ZERO)
            .withBellatrixEpoch(UInt64.ZERO)
            .withTotalTerminalDifficulty(0)
            .genesisExecutionPayloadHeaderSource(eth1Node::createGenesisExecutionPayload)
            .validatorKeys(validatorKeys)
            .generate();
    tekuNode =
        createTekuBeaconNode(
            TekuNodeConfigBuilder.createBeaconNode()
                .withNetwork(NETWORK_NAME)
                .withAltairEpoch(UInt64.ZERO)
                .withBellatrixEpoch(UInt64.ZERO)
                .withTerminalBlockHash(DEFAULT_EL_GENESIS_HASH, 0)
                .withInitialState(initialStateData)
                .withStartupTargetPeerCount(0)
                .withReadOnlyKeystorePath(validatorKeys)
                .withValidatorProposerDefaultFeeRecipient(
                    "0xFE3B557E8Fb62b89F4916B721be55cEb828dBd73")
                .withExecutionEngine(eth1Node)
                .withJwtSecretFile(JWT_FILE)
                .withFastConfirmationEnabled()
                .build());
    tekuNode.start();
  }

  @Test
  void safeBlockHashShouldTrackFastConfirmationConfirmedRoot() throws Exception {
    tekuNode.startEventListener(EventType.fast_confirmation);

    // Run across several epochs so fast confirmation is exercised.
    tekuNode.waitForNewFinalization();

    // Fast confirmation is running and confirming newer blocks over time (the confirmed root
    // advances, it is not stuck at the finalized block).
    Waiter.waitFor(
        () -> assertThat(tekuNode.getLatestFastConfirmationEvent()).isPresent(), 2, MINUTES);
    final UInt64 earlyConfirmedSlot =
        tekuNode.getLatestFastConfirmationEvent().orElseThrow().slot();
    Waiter.waitFor(
        () ->
            assertThat(tekuNode.getLatestFastConfirmationEvent().orElseThrow().slot())
                .isGreaterThan(earlyConfirmedSlot),
        2,
        MINUTES);

    // The confirmed root is canonical and its execution hash is exactly the block the EL treats as
    // "safe": confirmed root -> get_safe_execution_block_hash -> FCU safe_block_hash -> EL. Retried
    // because the confirmed root and the EL's safe block both advance each slot, so a single
    // snapshot can be a slot apart.
    Waiter.waitFor(
        () -> {
          final FastConfirmationEventData confirmed =
              tekuNode.getLatestFastConfirmationEvent().orElseThrow();
          final Optional<SignedBeaconBlock> confirmedBlock =
              tekuNode.getBlockAtSlot(confirmed.slot());
          assertThat(confirmedBlock).isPresent();
          // The confirmed root is canonical (the block at its slot).
          assertThat(confirmedBlock.get().getRoot()).isEqualTo(confirmed.block());
          final Bytes32 confirmedExecutionHash =
              confirmedBlock
                  .get()
                  .getMessage()
                  .getBody()
                  .getOptionalExecutionPayload()
                  .orElseThrow()
                  .getBlockHash();

          final JsonNode safeBlock = eth1Node.getExecutionBlock("safe");
          assertThat(safeBlock).isNotNull();
          assertThat(safeBlock.get("hash").asText())
              .isEqualTo(confirmedExecutionHash.toHexString());
        },
        3,
        MINUTES);
  }
}
