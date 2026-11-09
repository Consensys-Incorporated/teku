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

package tech.pegasys.teku.services.beaconchain;

import static tech.pegasys.teku.infrastructure.logging.StatusLogger.STATUS_LOG;
import static tech.pegasys.teku.networks.Eth2NetworkConfiguration.FINALIZED_STATE_URL_PATH;

import java.io.IOException;
import java.util.Objects;
import java.util.Optional;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.NotNull;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.exceptions.InvalidConfigurationException;
import tech.pegasys.teku.infrastructure.http.UrlSanitizer;
import tech.pegasys.teku.infrastructure.ssz.sos.SszDeserializeException;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.config.SpecConfig;
import tech.pegasys.teku.spec.datastructures.state.AnchorPoint;
import tech.pegasys.teku.spec.datastructures.state.Checkpoint;
import tech.pegasys.teku.spec.datastructures.state.CheckpointState;
import tech.pegasys.teku.spec.datastructures.state.Fork;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.BeaconState;
import tech.pegasys.teku.spec.datastructures.util.ChainDataLoader;
import tech.pegasys.teku.spec.logic.common.statetransition.exceptions.EpochProcessingException;
import tech.pegasys.teku.spec.logic.common.statetransition.exceptions.SlotProcessingException;
import tech.pegasys.teku.spec.logic.versions.gloas.helpers.MiscHelpersGloas;
import tech.pegasys.teku.storage.api.StorageQueryChannel;
import tech.pegasys.teku.storage.api.StorageUpdateChannel;
import tech.pegasys.teku.storage.api.WeakSubjectivityUpdate;
import tech.pegasys.teku.weaksubjectivity.WeakSubjectivityCalculator;
import tech.pegasys.teku.weaksubjectivity.config.WeakSubjectivityConfig;

public class WeakSubjectivityInitializer {

  private static final Logger LOG = LogManager.getLogger();

  public Optional<AnchorPoint> loadInitialAnchorPoint(
      final Spec spec, final Optional<String> initialStateResource) {
    return initialStateResource.map(
        stateResource -> {
          final String sanitizedResource = UrlSanitizer.sanitizePotentialUrl(stateResource);
          try {
            return getAnchorPoint(spec, stateResource, sanitizedResource);
          } catch (final IOException | SszDeserializeException ex) {
            if (!UrlSanitizer.urlContainsNonEmptyPath(stateResource)) {
              final String stateResourceWithPath =
                  UrlSanitizer.appendPath(stateResource, FINALIZED_STATE_URL_PATH);
              final String sanitizedResourceWithPath =
                  UrlSanitizer.sanitizePotentialUrl(stateResourceWithPath);
              LOG.info(
                  String.format(
                      "Failed to load initial state from %s. Will try to load initial state from %s instead.",
                      sanitizedResource, sanitizedResourceWithPath));
              try {
                return getAnchorPoint(spec, stateResourceWithPath, sanitizedResourceWithPath);
              } catch (final IOException | SszDeserializeException exWithPath) {
                throw new InvalidConfigurationException(
                    String.format(
                        "Failed to load initial state from %s", sanitizedResourceWithPath),
                    exWithPath);
              }
            } else {
              throw new InvalidConfigurationException(
                  String.format("Failed to load initial state from %s", sanitizedResource), ex);
            }
          }
        });
  }

  @NotNull
  private AnchorPoint getAnchorPoint(
      final Spec spec, final String stateResource, final String sanitizedResource)
      throws IOException {
    STATUS_LOG.loadingInitialStateResource(sanitizedResource);
    final BeaconState state = ChainDataLoader.loadState(spec, stateResource);
    if (state.getSlot().isGreaterThan(state.getLatestBlockHeader().getSlot())) {
      STATUS_LOG.warningUnexpectedInitialState(
          spec.computeEpochAtSlot(state.getSlot()),
          state.getSlot(),
          state.getLatestBlockHeader().getSlot());
    }
    final AnchorPoint anchor = AnchorPoint.fromInitialState(spec, state);
    STATUS_LOG.loadedInitialStateResource(
        state.hashTreeRoot(),
        anchor.getRoot(),
        state.getSlot(),
        anchor.getEpoch(),
        anchor.getEpochStartSlot());
    return anchor;
  }

  public SafeFuture<WeakSubjectivityConfig> finalizeAndStoreConfig(
      final WeakSubjectivityConfig config,
      final StorageQueryChannel storageQueryChannel,
      final StorageUpdateChannel storageUpdateChannel) {
    return storageQueryChannel
        .getWeakSubjectivityState()
        .thenCompose(
            storedState -> {
              WeakSubjectivityConfig updatedConfig = config;

              final Optional<Checkpoint> storedWsCheckpoint = storedState.getCheckpoint();
              Optional<Checkpoint> newWsCheckpoint = updatedConfig.getWeakSubjectivityCheckpoint();

              // Reconcile supplied config with stored configuration
              Optional<WeakSubjectivityConfig> configToPersist = Optional.empty();
              if (newWsCheckpoint.isPresent()
                  && !Objects.equals(storedWsCheckpoint, newWsCheckpoint)) {
                // We have a new ws checkpoint, so we need to persist it
                configToPersist = Optional.of(updatedConfig);
              } else if (storedState.getCheckpoint().isPresent()) {
                // We haven't supplied a new ws checkpoint, so use the stored value
                updatedConfig =
                    updatedConfig.updated(
                        b -> b.weakSubjectivityCheckpoint(storedState.getCheckpoint()));
              }

              final WeakSubjectivityConfig finalizedConfig = updatedConfig;

              // Persist changes as necessary
              if (configToPersist.isPresent()) {
                final Checkpoint updatedCheckpoint =
                    configToPersist.get().getWeakSubjectivityCheckpoint().orElseThrow();

                // Persist changes
                LOG.info("Update stored weak subjectivity checkpoint to: {}", updatedCheckpoint);
                WeakSubjectivityUpdate update =
                    WeakSubjectivityUpdate.setWeakSubjectivityCheckpoint(updatedCheckpoint);
                return storageUpdateChannel
                    .onWeakSubjectivityUpdate(update)
                    .thenApply(__ -> finalizedConfig);
              }

              return SafeFuture.completedFuture(finalizedConfig);
            });
  }

  public void validateAnchorIsWithinWeakSubjectivityPeriod(
      final AnchorPoint initialAnchor,
      final UInt64 currentSlot,
      final Spec spec,
      final WeakSubjectivityCalculator weakSubjectivityCalculator) {
    final UInt64 checkpointSlot =
        spec.computeStartSlotAtEpoch(initialAnchor.getCheckpoint().getEpoch());
    final BeaconState stateAtCheckpoint;
    if (initialAnchor.getState().getSlot().isLessThan(checkpointSlot)) {
      // play empty slot up to checkpoint slot
      try {
        stateAtCheckpoint = spec.processSlots(initialAnchor.getState(), checkpointSlot);
      } catch (SlotProcessingException | EpochProcessingException e) {
        throw new RuntimeException(e);
      }
    } else {
      stateAtCheckpoint = initialAnchor.getState();
    }
    final CheckpointState checkpointState =
        CheckpointState.create(
            spec,
            initialAnchor.getCheckpoint(),
            initialAnchor.getBlockSummary(),
            stateAtCheckpoint);
    if (!weakSubjectivityCalculator.isWithinWeakSubjectivityPeriod(checkpointState, currentSlot)) {
      throw new IllegalStateException(
          "Cannot sync outside of weak subjectivity period. Consider re-syncing your node using --checkpoint-sync-url or use --ignore-weak-subjectivity-period-enabled to ignore this check.");
    }
  }

  /**
   * A Gloas genesis block is EMPTY: its execution payload bid names the execution layer genesis
   * block as parent and commits to no payload, as in the consensus-specs genesis helper,
   * eth-beacon-genesis and Teku's {@code GenesisGenerator}. Genesis states written by earlier Teku
   * versions made the genesis block FULL and relied on a synthetic genesis payload that no longer
   * exists, so a node started on one would never import the slot-1 block. Anchors after genesis are
   * left alone.
   */
  public void validateGloasGenesisAnchor(final AnchorPoint anchor) {
    if (!anchor.getBlockSlot().equals(SpecConfig.GENESIS_SLOT)) {
      return;
    }
    final boolean genesisBlockIsFull =
        anchor.getState().toVersionGloas().map(MiscHelpersGloas::isParentBlockFull).orElse(false);
    if (genesisBlockIsFull) {
      throw new InvalidConfigurationException(
          "The Gloas genesis state commits to the execution layer genesis block as the genesis "
              + "block's own execution payload (latest_execution_payload_bid.block_hash equals "
              + "latest_block_hash). This layout was produced by earlier Teku versions and is no "
              + "longer supported: the genesis block must be EMPTY, with a zero bid block hash, as "
              + "produced by this Teku version and by eth-beacon-genesis. Regenerate the genesis "
              + "state and wipe the beacon node database.");
    }
  }

  public void validateInitialAnchor(
      final AnchorPoint initialAnchor,
      final UInt64 currentSlot,
      final Spec spec,
      final Optional<WeakSubjectivityCalculator> maybeWsCalculator) {
    final Fork expectedFork = spec.getForkSchedule().getFork(initialAnchor.getEpoch());
    final Fork loadedFork = initialAnchor.getState().getFork();
    if (!isSameForkInfo(expectedFork, loadedFork)) {
      throw new InvalidConfigurationException(
          String.format(
              "The fork from the initial-state (%s) does not match the configured fork schedule (%s).\nPlease check that network in configuration matches the loaded state.",
              loadedFork, expectedFork));
    }

    maybeWsCalculator.ifPresent(
        wsCalculator ->
            validateAnchorIsWithinWeakSubjectivityPeriod(
                initialAnchor, currentSlot, spec, wsCalculator));

    if (initialAnchor.isGenesis()) {
      validateGloasGenesisAnchor(initialAnchor);
      // Skip extra validations for genesis state
      return;
    }

    final UInt64 slotsBetweenBlockAndEpochStart =
        initialAnchor.getEpochStartSlot().minus(initialAnchor.getBlockSlot());
    final UInt64 anchorEpoch = initialAnchor.getEpoch();
    final UInt64 currentEpoch = spec.computeEpochAtSlot(currentSlot);

    if (initialAnchor.getBlockSlot().isGreaterThanOrEqualTo(currentSlot)) {
      throw new IllegalStateException(
          String.format(
              "The provided initial state appears to be from a future slot (%s). Please check that the initial state corresponds to a finalized checkpoint on the target chain.",
              initialAnchor.getBlockSlot()));
    } else if (anchorEpoch.plus(2).isGreaterThan(currentEpoch)) {
      throw new IllegalStateException(
          "The provided initial state is too recent. Please check that the initial state corresponds to a finalized checkpoint.");
    }

    if (slotsBetweenBlockAndEpochStart.isGreaterThan(UInt64.ZERO)) {
      Level level = slotsBetweenBlockAndEpochStart.isGreaterThan(2) ? Level.WARN : Level.INFO;
      STATUS_LOG.warnOnInitialStateWithSkippedSlots(
          level,
          initialAnchor.getSlot(),
          initialAnchor.getEpoch(),
          initialAnchor.getEpochStartSlot());
    }
  }

  private boolean isSameForkInfo(final Fork expectedFork, final Fork loadedFork) {
    // at genesis there's inconsistency in reference tests where previous version is the same as
    // current version this will cater for that, otherwise the fork check won't work at genesis
    // because real networks set them differently if not starting from phase0
    if (loadedFork.getEpoch().isZero()) {
      return expectedFork.getEpoch().equals(loadedFork.getEpoch())
          && expectedFork.getCurrentVersion().equals(loadedFork.getCurrentVersion());
    }
    return expectedFork.equals(loadedFork);
  }
}
