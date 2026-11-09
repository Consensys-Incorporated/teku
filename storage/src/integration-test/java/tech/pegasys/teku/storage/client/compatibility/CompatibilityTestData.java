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

package tech.pegasys.teku.storage.client.compatibility;

import static java.util.stream.Collectors.toList;
import static org.assertj.core.api.Assertions.assertThat;
import static tech.pegasys.teku.infrastructure.async.SafeFutureAssert.safeJoin;

import com.google.common.collect.ImmutableMap;
import com.google.common.io.Resources;
import java.io.File;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;
import org.apache.commons.io.FileUtils;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.bls.BLSKeyPair;
import tech.pegasys.teku.bls.BLSPublicKey;
import tech.pegasys.teku.bls.BLSSignature;
import tech.pegasys.teku.ethereum.pow.api.Deposit;
import tech.pegasys.teku.ethereum.pow.api.DepositsFromBlockEvent;
import tech.pegasys.teku.ethereum.pow.api.MinGenesisTimeBlockEvent;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBlockAndState;
import tech.pegasys.teku.spec.datastructures.forkchoice.VoteTracker;
import tech.pegasys.teku.spec.datastructures.interop.MockStartValidatorKeyPairFactory;
import tech.pegasys.teku.spec.datastructures.state.Checkpoint;
import tech.pegasys.teku.storage.api.WeakSubjectivityState;
import tech.pegasys.teku.storage.api.WeakSubjectivityUpdate;
import tech.pegasys.teku.storage.client.ChainUpdater;
import tech.pegasys.teku.storage.server.Database;
import tech.pegasys.teku.storage.server.DatabaseVersion;
import tech.pegasys.teku.storage.storageSystem.StorageSystem;

public class CompatibilityTestData {

  public static final int MAX_SLOT = 128;
  // Legacy on-disk test data stores epoch=1 for all votes. With minimal spec (SLOTS_PER_EPOCH=8),
  // epoch 1 converts to nextSlot=8.
  private static final UInt64 LEGACY_NEXT_SLOT = UInt64.valueOf(8);

  public static final ImmutableMap<UInt64, VoteTracker> EXPECTED_VOTES =
      ImmutableMap.<UInt64, VoteTracker>builder()
          .put(
              UInt64.ZERO,
              new VoteTracker(
                  Bytes32.fromHexString("0x01"),
                  Bytes32.fromHexString("0x02"),
                  false,
                  false,
                  LEGACY_NEXT_SLOT,
                  false,
                  UInt64.ZERO,
                  false))
          .put(
              UInt64.ONE,
              new VoteTracker(
                  Bytes32.fromHexString("0x03"),
                  Bytes32.fromHexString("0x04"),
                  false,
                  false,
                  LEGACY_NEXT_SLOT,
                  false,
                  UInt64.ZERO,
                  false))
          .put(
              UInt64.valueOf(2),
              new VoteTracker(
                  Bytes32.fromHexString("0x05"),
                  Bytes32.fromHexString("0x06"),
                  false,
                  false,
                  LEGACY_NEXT_SLOT,
                  false,
                  UInt64.ZERO,
                  false))
          .put(
              UInt64.valueOf(3),
              new VoteTracker(
                  Bytes32.fromHexString("0x07"),
                  Bytes32.fromHexString("0x08"),
                  false,
                  false,
                  LEGACY_NEXT_SLOT,
                  false,
                  UInt64.ZERO,
                  false))
          .put(
              UInt64.valueOf(4),
              new VoteTracker(
                  Bytes32.fromHexString("0x09"),
                  Bytes32.fromHexString("0x10"),
                  false,
                  false,
                  LEGACY_NEXT_SLOT,
                  false,
                  UInt64.ZERO,
                  false))
          .put(
              UInt64.valueOf(5),
              new VoteTracker(
                  Bytes32.fromHexString("0x11"),
                  Bytes32.fromHexString("0x12"),
                  false,
                  false,
                  LEGACY_NEXT_SLOT,
                  false,
                  UInt64.ZERO,
                  false))
          .build();

  public static final List<BLSPublicKey> PUBLIC_KEYS =
      new MockStartValidatorKeyPairFactory()
          .generateKeyPairs(0, 5).stream().map(BLSKeyPair::getPublicKey).collect(toList());
  public static final List<BLSSignature> SIGNATURES =
      List.of(
          BLSSignature.fromBytesCompressed(
              Bytes.fromBase64String(
                  "pbSSuf7h70JkzI/U157flTWPZDuaBXgRLj1HLMoCwjA4Xd0hMdGewn7G2HLZiQcNC9G6FSd1+0BT5PwknYez4ya6TccwpaGnsvWYLPf3SNIX5Ug7Yi1CF1fvEr3x9sZ0")),
          BLSSignature.fromBytesCompressed(
              Bytes.fromBase64String(
                  "pbSSuf7h70JkzI/U157flTWPZDuaBXgRLj1HLMoCwjA4Xd0hMdGewn7G2HLZiQcNC9G6FSd1+0BT5PwknYez4ya6TccwpaGnsvWYLPf3SNIX5Ug7Yi1CF1fvEr3x9sZ0")),
          BLSSignature.fromBytesCompressed(
              Bytes.fromBase64String(
                  "j7vOT7GQBnv+aIqxb0byMWNvMCXhQwAfj38UcMne7pNGXOvNZKnXQ9Knma/NOPUyAvLcRBDtew23vVtzWcm7naaTRJVvLJS6xiPOMIHOw6wNtGggzc20heZAXZAMdaKi")),
          BLSSignature.fromBytesCompressed(
              Bytes.fromBase64String(
                  "l1DUv3fmbvZanhCaaraMk2PKAl+33sf3UHMbxkv18CKILzzIz+Hr6hnLXCHqWQYEGKTtLcf6OLV7Z+Y21BW2bBtJHXJqqzvWkec/j0X0hWaEoWOSAs20sipO1WSIUY2m")));

  public static final List<DepositsFromBlockEvent> EXPECTED_DEPOSITS =
      List.of(
          DepositsFromBlockEvent.create(
              UInt64.valueOf(100),
              Bytes32.fromHexString("0x51"),
              UInt64.valueOf(5000),
              Stream.of(
                  new Deposit(
                      PUBLIC_KEYS.get(0),
                      Bytes32.fromHexString("0x81"),
                      SIGNATURES.get(0),
                      UInt64.valueOf(6000),
                      UInt64.valueOf(0)))),
          DepositsFromBlockEvent.create(
              UInt64.valueOf(260),
              Bytes32.fromHexString("0x52"),
              UInt64.valueOf(5001),
              Stream.of(
                  new Deposit(
                      PUBLIC_KEYS.get(1),
                      Bytes32.fromHexString("0x82"),
                      SIGNATURES.get(1),
                      UInt64.valueOf(6001),
                      UInt64.valueOf(1)))),
          DepositsFromBlockEvent.create(
              UInt64.valueOf(370),
              Bytes32.fromHexString("0x53"),
              UInt64.valueOf(5008),
              Stream.of(
                  new Deposit(
                      PUBLIC_KEYS.get(2),
                      Bytes32.fromHexString("0x83"),
                      SIGNATURES.get(2),
                      UInt64.valueOf(6002),
                      UInt64.valueOf(2)),
                  new Deposit(
                      PUBLIC_KEYS.get(3),
                      Bytes32.fromHexString("0x84"),
                      SIGNATURES.get(3),
                      UInt64.valueOf(6003),
                      UInt64.valueOf(3)))));
  public static final MinGenesisTimeBlockEvent EXPECTED_MIN_GENESIS_EVENT =
      new MinGenesisTimeBlockEvent(
          UInt64.valueOf(5005), UInt64.valueOf(350), Bytes32.fromHexString("0x85"));

  public static final Checkpoint EXPECTED_WEAK_SUBJECTIVITY_CHECKPOINT =
      new Checkpoint(UInt64.valueOf(3045), Bytes32.fromHexString("0x7088"));

  public static void copyTestDataTo(final DatabaseVersion version, final File targetDir)
      throws Exception {
    final File baseDir =
        new File(
                Resources.getResource(CompatibilityTestData.class, "compatibility-tests.md")
                    .toURI())
            .getParentFile();
    final File sourceDirectory = new File(baseDir, version.getValue());
    FileUtils.copyDirectory(sourceDirectory, targetDir);
  }

  public static void populateDatabaseWithTestData(final StorageSystem storageSystem) {
    final ChainUpdater chainUpdater = storageSystem.chainUpdater();
    chainUpdater.initializeGenesis();
    while (chainUpdater.getHeadSlot().isLessThan(32)) {
      chainUpdater.addNewBestBlock();
    }
    chainUpdater.finalizeCurrentChain();
    while (chainUpdater.getHeadSlot().isLessThan(MAX_SLOT)) {
      chainUpdater.addNewBestBlock();
    }

    final Database database = storageSystem.database();
    database.storeVotes(EXPECTED_VOTES);

    EXPECTED_DEPOSITS.forEach(database::addDepositsFromBlockEvent);
    database.addMinGenesisTimeBlock(EXPECTED_MIN_GENESIS_EVENT);

    database.updateWeakSubjectivityState(
        WeakSubjectivityUpdate.setWeakSubjectivityCheckpoint(
            EXPECTED_WEAK_SUBJECTIVITY_CHECKPOINT));
  }

  public static void verifyDatabaseContentMatches(
      final StorageSystem expectedSystem, final StorageSystem actualSystem) {
    final Database actualDatabase = actualSystem.database();
    final Database expectedDatabase = expectedSystem.database();
    for (int slot = MAX_SLOT; slot >= 0; slot--) {
      final Optional<SignedBlockAndState> expected =
          safeJoin(
              expectedSystem
                  .combinedChainDataClient()
                  .getSignedBlockAndStateInEffectAtSlot(UInt64.valueOf(slot)));
      final Optional<SignedBlockAndState> actual =
          safeJoin(
              actualSystem
                  .combinedChainDataClient()
                  .getSignedBlockAndStateInEffectAtSlot(UInt64.valueOf(slot)));
      assertThat(actual).isEqualTo(expected);

      final SignedBlockAndState expectedBlockAndState = expected.orElseThrow();
      // Check we can also get the block by root
      assertThat(actualDatabase.getSignedBlock(expectedBlockAndState.getRoot()))
          .contains(expectedBlockAndState.getBlock());

      assertThat(actualDatabase.getSlotForFinalizedStateRoot(expectedBlockAndState.getStateRoot()))
          .isEqualTo(
              expectedDatabase.getSlotForFinalizedStateRoot(expectedBlockAndState.getStateRoot()));

      assertThat(actualDatabase.getHotState(expectedBlockAndState.getRoot()))
          .isEqualTo(expectedDatabase.getHotState(expectedBlockAndState.getRoot()));
    }

    assertThat(actualDatabase.getVotes()).isEqualTo(expectedDatabase.getVotes());

    assertThat(actualDatabase.getMinGenesisTimeBlock()).contains(EXPECTED_MIN_GENESIS_EVENT);
    try (final Stream<DepositsFromBlockEvent> actualDeposits =
        actualDatabase.streamDepositsFromBlocks()) {
      assertThat(actualDeposits).containsExactlyElementsOf(EXPECTED_DEPOSITS);
    }

    assertThat(actualDatabase.getWeakSubjectivityState())
        .isEqualTo(
            WeakSubjectivityState.create(Optional.of(EXPECTED_WEAK_SUBJECTIVITY_CHECKPOINT)));
  }
}
