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

package tech.pegasys.teku.cli.slashingprotection;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static tech.pegasys.teku.cli.slashingprotection.RepairCommand.computeRepairSlot;
import static tech.pegasys.teku.cli.slashingprotection.RepairCommand.findNetworkMismatch;
import static tech.pegasys.teku.cli.slashingprotection.RepairCommand.getStateResourceForComputingSlot;
import static tech.pegasys.teku.cli.slashingprotection.RepairCommand.isConfirmed;
import static tech.pegasys.teku.cli.slashingprotection.RepairCommand.repair;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BooleanSupplier;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import tech.pegasys.teku.cli.slashingprotection.RepairCommand.RepairOutcome;
import tech.pegasys.teku.data.SlashingProtectionRepairer;
import tech.pegasys.teku.ethereum.signingrecord.ValidatorSigningRecord;
import tech.pegasys.teku.infrastructure.logging.SubCommandLogger;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.networks.Eth2NetworkConfiguration;
import tech.pegasys.teku.networks.StateBoostrapConfig;
import tech.pegasys.teku.spec.TestSpecFactory;
import tech.pegasys.teku.spec.networks.Eth2Network;

class RepairCommandTest {
  private static final String INITIAL_STATE = "https://example.com/initial.ssz";
  private static final String GENESIS_STATE = "https://example.com/genesis.ssz";
  private static final Bytes32 MAINNET_ROOT =
      Bytes32.fromHexString("0x4b363db94e286120d76eb905340fdd4e54bfe9f06bf33ff6cf5ad27f511bfe95");
  private static final Bytes32 HOODI_ROOT =
      Bytes32.fromHexString("0x212f13fc4df078b6cb7db228f1c8307566dcecf900867401a92023d7ba99cb5f");
  private static final UInt64 REPAIR_SLOT = UInt64.valueOf(1234);
  private static final UInt64 REPAIR_EPOCH = UInt64.valueOf(38);
  private static final String PUBKEY_A =
      "a99a76ed7796f7be22d5b7e85deeb7c5677e88e511e0b337618f8c4eb61349b4bf2d153f649f7b53359fe8b94a38e44c";
  private static final String PUBKEY_B =
      "b89bebc699769726a318c8e9971bd3171297c61aea4a6578a7a4f94b547dcba5bac16a89108b6b6a1fe3695d1a874a0b";
  private static final String PUBKEY_C =
      "a3a32b0f8b4ddb83f1a0a853d81dd725dfe577d4f4c3db8ece52ce2b026eca84815c1a7e8e92a4de3d755733bf7e4a9b";

  @Test
  void shouldNotLoadStateWhenSlotIsSupplied() {
    assertThat(
            getStateResourceForComputingSlot(
                Optional.of(UInt64.valueOf(1234)), bootstrapConfig(INITIAL_STATE, GENESIS_STATE)))
        .isEmpty();
  }

  @Test
  void shouldPreferInitialState() {
    assertThat(
            getStateResourceForComputingSlot(
                Optional.empty(), bootstrapConfig(INITIAL_STATE, GENESIS_STATE)))
        .contains(INITIAL_STATE);
  }

  @Test
  void shouldFallBackToGenesisState() {
    assertThat(
            getStateResourceForComputingSlot(
                Optional.empty(), bootstrapConfig(null, GENESIS_STATE)))
        .contains(GENESIS_STATE);
  }

  @Test
  void shouldHaveNoStateWhenNetworkHasNone() {
    assertThat(getStateResourceForComputingSlot(Optional.empty(), bootstrapConfig(null, null)))
        .isEmpty();
  }

  @Test
  void shouldUseBundledStateForMainnet() {
    final StateBoostrapConfig mainnet =
        Eth2NetworkConfiguration.builder(Eth2Network.MAINNET).build().getNetworkBoostrapConfig();
    assertThat(getStateResourceForComputingSlot(Optional.empty(), mainnet))
        .hasValueSatisfying(resource -> assertThat(resource).endsWith("mainnet-genesis.ssz"));
  }

  @Test
  void shouldReportRecordsFromAnotherNetwork(@TempDir final Path tempDir) throws IOException {
    writeRecord(tempDir, PUBKEY_A, Optional.of(MAINNET_ROOT));
    writeRecord(tempDir, PUBKEY_B, Optional.of(HOODI_ROOT));
    writeRecord(tempDir, PUBKEY_C, Optional.empty());

    final Optional<String> error =
        findNetworkMismatch(createRepairer(tempDir), HOODI_ROOT, "hoodi");

    assertThat(error)
        .hasValueSatisfying(
            message ->
                assertThat(message)
                    .startsWith(
                        "1 slashing protection record(s) were written for a different network"
                            + " than hoodi (genesis validators root "
                            + HOODI_ROOT
                            + "), for example "
                            + PUBKEY_A)
                    .endsWith("No records were changed."));
  }

  @Test
  void shouldNotReportWhenRecordsMatchNetwork(@TempDir final Path tempDir) throws IOException {
    writeRecord(tempDir, PUBKEY_A, Optional.of(HOODI_ROOT));
    writeRecord(tempDir, PUBKEY_B, Optional.empty());

    assertThat(findNetworkMismatch(createRepairer(tempDir), HOODI_ROOT, "hoodi")).isEmpty();
  }

  @Test
  void shouldNotReportWhenNoRecordHasGenesisValidatorsRoot(@TempDir final Path tempDir)
      throws IOException {
    writeRecord(tempDir, PUBKEY_A, Optional.empty());

    assertThat(findNetworkMismatch(createRepairer(tempDir), HOODI_ROOT, "hoodi")).isEmpty();
  }

  @Test
  void shouldUseCurrentSlotPlusOneEpochAsRepairSlot() {
    final UInt64 genesisTime = UInt64.valueOf(1_000_000);
    // minimal: 6 second slots, 8 slots per epoch; 3 seconds into slot 20
    assertThat(
            computeRepairSlot(
                TestSpecFactory.createMinimalPhase0(), genesisTime, genesisTime.plus(6 * 20 + 3)))
        .isEqualTo(UInt64.valueOf(28));
    // mainnet: 12 second slots, 32 slots per epoch
    assertThat(
            computeRepairSlot(
                TestSpecFactory.createMainnetPhase0(), genesisTime, genesisTime.plus(12 * 100)))
        .isEqualTo(UInt64.valueOf(132));
  }

  @Test
  void shouldOnlyAcceptYesAsConfirmation() {
    assertThat(isConfirmed("yes")).isTrue();
    assertThat(isConfirmed("YES")).isTrue();
    assertThat(isConfirmed("Yes")).isTrue();
    assertThat(isConfirmed("y")).isFalse();
    assertThat(isConfirmed("no")).isFalse();
    assertThat(isConfirmed("yes please")).isFalse();
  }

  @Test
  void shouldStopBeforeConfirmingWhenNetworkDoesNotMatch(@TempDir final Path tempDir)
      throws IOException {
    writeRawFile(tempDir, PUBKEY_A, "garbage: [");
    final SubCommandLogger log = mock(SubCommandLogger.class);
    final AtomicBoolean asked = new AtomicBoolean();

    final RepairOutcome outcome =
        repair(
            createRepairer(tempDir),
            Optional.of("wrong network"),
            false,
            REPAIR_SLOT,
            REPAIR_EPOCH,
            answer(asked, true),
            log);

    assertThat(outcome).isEqualTo(RepairOutcome.NETWORK_MISMATCH);
    assertThat(asked).isFalse();
    verify(log).error("wrong network");
    assertThat(Files.readString(tempDir.resolve(PUBKEY_A + ".yml"))).isEqualTo("garbage: [");
  }

  @Test
  void shouldNotAskWhenNothingNeedsUpdating(@TempDir final Path tempDir) throws IOException {
    writeRecord(tempDir, PUBKEY_A, Optional.of(HOODI_ROOT));
    final AtomicBoolean asked = new AtomicBoolean();

    assertThat(
            repair(
                createRepairer(tempDir),
                Optional.empty(),
                false,
                REPAIR_SLOT,
                REPAIR_EPOCH,
                answer(asked, true),
                mock(SubCommandLogger.class)))
        .isEqualTo(RepairOutcome.NOTHING_TO_UPDATE);
    assertThat(asked).isFalse();
  }

  @Test
  void shouldNotWriteOrAskWhenCheckOnly(@TempDir final Path tempDir) throws IOException {
    writeRawFile(tempDir, PUBKEY_A, "garbage: [");
    final SubCommandLogger log = mock(SubCommandLogger.class);
    final AtomicBoolean asked = new AtomicBoolean();

    final RepairOutcome outcome =
        repair(
            createRepairer(tempDir),
            Optional.empty(),
            true,
            REPAIR_SLOT,
            REPAIR_EPOCH,
            answer(asked, true),
            log);

    assertThat(outcome).isEqualTo(RepairOutcome.CHECK_ONLY);
    assertThat(asked).isFalse();
    verify(log).display("Updates have been skipped as --check-only-enabled was set.");
    assertThat(Files.readString(tempDir.resolve(PUBKEY_A + ".yml"))).isEqualTo("garbage: [");
  }

  @Test
  void shouldNotWriteWhenCancelled(@TempDir final Path tempDir) throws IOException {
    writeRawFile(tempDir, PUBKEY_A, "garbage: [");
    final SubCommandLogger log = mock(SubCommandLogger.class);
    final AtomicBoolean asked = new AtomicBoolean();

    final RepairOutcome outcome =
        repair(
            createRepairer(tempDir),
            Optional.empty(),
            false,
            REPAIR_SLOT,
            REPAIR_EPOCH,
            answer(asked, false),
            log);

    assertThat(outcome).isEqualTo(RepairOutcome.CANCELLED);
    assertThat(asked).isTrue();
    verify(log).display("Operation cancelled.");
    assertThat(Files.readString(tempDir.resolve(PUBKEY_A + ".yml"))).isEqualTo("garbage: [");
  }

  @Test
  void shouldWriteWhenConfirmed(@TempDir final Path tempDir) throws IOException {
    writeRawFile(tempDir, PUBKEY_A, "garbage: [");
    final AtomicBoolean asked = new AtomicBoolean();

    final RepairOutcome outcome =
        repair(
            createRepairer(tempDir),
            Optional.empty(),
            false,
            REPAIR_SLOT,
            REPAIR_EPOCH,
            answer(asked, true),
            mock(SubCommandLogger.class));

    assertThat(outcome).isEqualTo(RepairOutcome.UPDATED);
    assertThat(asked).isTrue();
    assertThat(
            ValidatorSigningRecord.fromBytes(
                Bytes.wrap(Files.readAllBytes(tempDir.resolve(PUBKEY_A + ".yml")))))
        .isEqualTo(
            new ValidatorSigningRecord(Optional.empty(), REPAIR_SLOT, REPAIR_EPOCH, REPAIR_EPOCH));
  }

  private BooleanSupplier answer(final AtomicBoolean asked, final boolean confirmed) {
    return () -> {
      asked.set(true);
      return confirmed;
    };
  }

  private void writeRawFile(final Path dir, final String pubkey, final String content)
      throws IOException {
    Files.writeString(dir.resolve(pubkey + ".yml"), content);
  }

  private SlashingProtectionRepairer createRepairer(final Path dir) {
    return SlashingProtectionRepairer.create(mock(SubCommandLogger.class), dir, false);
  }

  private void writeRecord(
      final Path dir, final String pubkey, final Optional<Bytes32> genesisValidatorsRoot)
      throws IOException {
    final ValidatorSigningRecord record =
        new ValidatorSigningRecord(genesisValidatorsRoot, UInt64.ONE, UInt64.ONE, UInt64.ONE);
    Files.write(dir.resolve(pubkey + ".yml"), record.toBytes().toArrayUnsafe());
  }

  private StateBoostrapConfig bootstrapConfig(
      final String initialState, final String genesisState) {
    return new StateBoostrapConfig(
        Optional.ofNullable(genesisState),
        Optional.ofNullable(initialState),
        Optional.empty(),
        false,
        false);
  }
}
