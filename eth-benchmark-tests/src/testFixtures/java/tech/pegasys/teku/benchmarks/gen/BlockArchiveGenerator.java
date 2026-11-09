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

package tech.pegasys.teku.benchmarks.gen;

import java.util.Collections;
import java.util.List;
import java.util.Optional;
import tech.pegasys.teku.bls.BLSKeyPair;
import tech.pegasys.teku.bls.BLSSignatureVerifier;
import tech.pegasys.teku.infrastructure.time.SystemTimeProvider;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.TestSpecFactory;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBeaconBlock;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBlockAndState;
import tech.pegasys.teku.spec.datastructures.blocks.StateAndBlockSummary;
import tech.pegasys.teku.spec.datastructures.operations.Attestation;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.BeaconState;
import tech.pegasys.teku.spec.generator.AttestationGenerator;
import tech.pegasys.teku.spec.generator.ChainBuilder;
import tech.pegasys.teku.spec.logic.common.helpers.BeaconStateAccessors;
import tech.pegasys.teku.spec.logic.common.util.EpochAttestationSchedule;
import tech.pegasys.teku.spec.logic.common.util.ValidatorsUtil;
import tech.pegasys.teku.storage.client.ChainUpdater;
import tech.pegasys.teku.storage.client.MemoryOnlyRecentChainData;
import tech.pegasys.teku.storage.client.RecentChainData;

public class BlockArchiveGenerator {
  private final int validatorCount;
  private final int epochCount;
  private final Spec spec =
      TestSpecFactory.createMainnetAltair(
          builder -> builder.blsSignatureVerifier(BLSSignatureVerifier.NOOP));
  private final List<BLSKeyPair> validatorKeys;
  private final RecentChainData localStorage;
  private final AttestationGenerator attestationGenerator;
  private final ChainBuilder chainBuilder;
  private final ChainUpdater chainUpdater;

  private final int slotsPerEpoch;
  private final SystemTimeProvider timeProvider = new SystemTimeProvider();
  private final ValidatorsUtil validatorsUtil;
  private final BeaconStateAccessors beaconStateAccessors;

  public static void main(final String[] args) throws Exception {
    // default values if nothing is specified
    int validatorCount = 32_768;
    int epochCount = 50;
    if (args.length == 2) {
      // read cli positional args
      try {
        validatorCount = Integer.parseInt(args[0]);
      } catch (Exception e) {
        dieUsage(Optional.of("Failed to parse validatorCount: " + e.getMessage()));
      }
      try {
        epochCount = Integer.parseInt(args[1]);
      } catch (Exception e) {
        dieUsage(Optional.of("Failed to parse epochCount: " + e.getMessage()));
      }
    } else if (args.length != 0) {
      dieUsage(Optional.empty());
    }

    if (validatorCount < 1 || validatorCount > 3_276_800) {
      dieUsage(
          Optional.of(
              "Expected validator count ("
                  + validatorCount
                  + ") to be in range 0 < validatorCount < 3_276_801"));
    } else if (epochCount < 1 || epochCount > 100) {
      dieUsage(
          Optional.of(
              "Expected epoch count (" + epochCount + ") to be in range 0 < epochCount < 100"));
    }
    System.out.println("Validator count: " + validatorCount);
    System.out.println("Epochs: " + epochCount);

    // Instantiate and execute the generator
    final BlockArchiveGenerator generator = new BlockArchiveGenerator(validatorCount, epochCount);
    generator.generateBlocks();
  }

  @SuppressWarnings({"StaticAssignmentInConstructor"})
  private BlockArchiveGenerator(final int validatorCount, final int epochCount) {
    this.validatorCount = validatorCount;
    this.epochCount = epochCount;
    this.validatorsUtil = spec.getGenesisSpec().getValidatorsUtil();
    this.beaconStateAccessors = spec.getGenesisSpec().beaconStateAccessors();

    this.validatorKeys = KeyFileGenerator.readValidatorKeys(validatorCount);
    this.localStorage = MemoryOnlyRecentChainData.create(spec);
    this.attestationGenerator = new AttestationGenerator(spec, validatorKeys);
    this.slotsPerEpoch = spec.getGenesisSpecConfig().getSlotsPerEpoch();

    chainBuilder = ChainBuilder.create(spec, validatorKeys);
    chainUpdater = new ChainUpdater(localStorage, chainBuilder, spec);
    chainUpdater.initializeGenesis(false);
  }

  private static void dieUsage(final Optional<String> maybeContext) {
    maybeContext.ifPresent(System.out::println);
    System.out.println("Usage: blockArchiveGenerator <validatorCount> <epochs>");
    System.exit(2);
  }

  private void generateBlocks() throws Exception {

    final String blocksFile =
        String.format(
            "blocks_%sEpochs_%sBlocksPerEpoch_%sValidators.ssz.gz",
            epochCount, slotsPerEpoch, validatorCount);

    System.out.printf(
        "Generating blocks for %s epochs, %s slots per epoch.%n", epochCount, slotsPerEpoch);

    try (BlockIO.Writer writer = BlockIO.createFileWriter(blocksFile)) {

      for (int j = 0; j < epochCount; j++) {
        System.out.println(" => Processing epoch " + j);
        final UInt64 epoch = UInt64.valueOf(j);
        final BeaconState epochState = getBestState().orElseThrow();
        final UInt64 committeCountPerSlot =
            beaconStateAccessors.getCommitteeCountPerSlot(epochState, epoch);
        final EpochAttestationSchedule attestationCommitteeAssignments =
            validatorsUtil.getAttestationCommitteesAtEpoch(epochState, epoch, committeCountPerSlot);
        for (int i = 0; i < slotsPerEpoch; i++) {
          final UInt64 slotStart = timeProvider.getTimeInMillis();
          final UInt64 previousSlot = localStorage.getHeadSlot();
          final UInt64 currentSlot = previousSlot.plus(UInt64.ONE);
          final List<Attestation> aggregates =
              getAggregatesForSlot(previousSlot, attestationCommitteeAssignments);

          ChainBuilder.BlockOptions options =
              ChainBuilder.BlockOptions.create().setAttestations(aggregates);
          SignedBlockAndState blockAndState = chainUpdater.advanceChain(currentSlot, options);
          chainUpdater.updateBestBlock(blockAndState);
          final SignedBeaconBlock block = blockAndState.getSignedBeaconBlock().orElseThrow();
          writer.accept(block);

          System.out.println(
              "   -> Processed: "
                  + currentSlot
                  + ", "
                  + timeProvider.getTimeInMillis().minusMinZero(slotStart)
                  + " ms");
        }

        final Optional<BeaconState> bestState = getBestState();
        bestState.ifPresent(
            beaconState ->
                System.out.printf(
                    " => Epoch done: %s, Best State slot: %s, state hash: %s%n",
                    epoch, beaconState.getSlot(), beaconState.hashTreeRoot()));
      }
    } catch (IllegalArgumentException e) {
      System.out.printf("\n\nBlock archive generation failed: %s\n\n", e.getMessage());
    }
  }

  private Optional<BeaconState> getBestState() {
    return localStorage.retrieveBlockState(localStorage.getBestBlockRoot().orElse(null)).join();
  }

  private List<Attestation> getAggregatesForSlot(
      final UInt64 previousSlot, final EpochAttestationSchedule attestationCommitteeAssignments) {
    final StateAndBlockSummary stateAndBlockSummary =
        localStorage
            .getStore()
            .retrieveStateAndBlockSummary(localStorage.getBestBlockRoot().orElseThrow())
            .join()
            .orElseThrow();

    final List<Attestation> attestations =
        UInt64.ZERO.equals(previousSlot)
            ? Collections.emptyList()
            : attestationGenerator.getAttestationsForSlot(
                stateAndBlockSummary, previousSlot, attestationCommitteeAssignments);

    return AttestationGenerator.groupAndAggregateAttestations(attestations);
  }
}
