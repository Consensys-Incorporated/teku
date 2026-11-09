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

package tech.pegasys.teku.statetransition.attestation;

import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.ethereum.events.SlotEventsChannel;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.ssz.SszList;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.datastructures.attestation.ValidatableAttestation;
import tech.pegasys.teku.spec.datastructures.operations.Attestation;
import tech.pegasys.teku.spec.datastructures.operations.AttestationData;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.BeaconState;
import tech.pegasys.teku.spec.logic.common.helpers.MiscHelpers;
import tech.pegasys.teku.storage.client.RecentChainData;

public abstract class AggregatingAttestationPool implements SlotEventsChannel {
  private static final Logger LOG = LogManager.getLogger();
  protected final Spec spec;
  protected final RecentChainData recentChainData;

  AggregatingAttestationPool(final Spec spec, final RecentChainData recentChainData) {
    this.spec = spec;
    this.recentChainData = recentChainData;
  }

  /**
   * Default maximum number of attestations to store in the pool.
   *
   * <p>With 2 million active validators, we'd expect around 62_500 attestations per slot; so 3
   * slots worth of attestations is almost 187_500.
   *
   * <p>Strictly to cache all attestations for a full 2 epochs is significantly larger than this
   * cache.
   */
  public static final int DEFAULT_MAXIMUM_ATTESTATION_COUNT = 187_500;

  /** The valid attestation retention period is 64 slots in deneb */
  public static final long ATTESTATION_RETENTION_SLOTS = 64;

  public abstract int getSize();

  public abstract void add(ValidatableAttestation attestation);

  public abstract SszList<Attestation> getAttestationsForBlock(
      BeaconState stateAtBlockSlot, AttestationForkChecker forkChecker);

  public abstract Optional<Attestation> createAggregateFor(
      Bytes32 attestationHashTreeRoot, Optional<UInt64> committeeIndex);

  public abstract List<Attestation> getAttestations(
      Optional<UInt64> maybeSlot, Optional<UInt64> maybeCommitteeIndex);

  public abstract void onAttestationsIncludedInBlock(
      UInt64 slot, Iterable<Attestation> attestations);

  public abstract void onReorg(UInt64 commonAncestorSlot);

  /**
   * Ensures that the committees size is set in the attestation. This is needed for the
   *
   * @return false if it was not possible to set the committees size but was required, true
   *     otherwise.
   */
  protected boolean ensureCommitteesSizeInAttestation(final ValidatableAttestation attestation) {
    return ensureCommitteesSizeInAttestation(
        attestation, () -> retrieveStateForAttestation(attestation.getData()));
  }

  protected boolean ensureCommitteesSizeInAttestation(
      final ValidatableAttestation attestation,
      final Supplier<Optional<BeaconState>> stateSupplier) {
    if (attestation.getCommitteesSize().isPresent()
        || !attestation.getAttestation().requiresCommitteeBits()) {
      return true;
    }

    final Optional<BeaconState> maybeState = stateSupplier.get();
    if (maybeState.isEmpty()) {
      return false;
    }

    attestation.saveCommitteesSize(maybeState.get());

    return true;
  }

  protected Optional<BeaconState> retrieveStateForAttestation(
      final AttestationData attestationData) {
    // we can use the first state of the epoch to get committees for an attestation
    final MiscHelpers miscHelpers = spec.atSlot(attestationData.getSlot()).miscHelpers();
    final Optional<UInt64> maybeEpoch = recentChainData.getCurrentEpoch();
    // the only reason this can happen is we don't have a store yet.
    if (maybeEpoch.isEmpty()) {
      return Optional.empty();
    }
    final UInt64 currentEpoch = maybeEpoch.get();
    final UInt64 attestationEpoch = miscHelpers.computeEpochAtSlot(attestationData.getSlot());

    LOG.debug("currentEpoch {}, attestationEpoch {}", currentEpoch, attestationEpoch);
    if (attestationEpoch.equals(currentEpoch)
        || attestationEpoch.equals(currentEpoch.minusMinZero(1))) {

      try {
        return recentChainData.getBestState().map(SafeFuture::getImmediately);
      } catch (final IllegalStateException e) {
        LOG.debug("Couldn't retrieve state for attestation at slot {}", attestationData.getSlot());
        return Optional.empty();
      }
    }

    // attestation is not from the current or previous epoch
    // this is really an edge case because the current or previous epoch is at least 31 slots
    // and the attestation is only valid for 64 slots, so it may be epoch-2 but not beyond.
    final UInt64 attestationEpochStartSlot = miscHelpers.computeStartSlotAtEpoch(attestationEpoch);
    LOG.debug("State at slot {} needed", attestationEpochStartSlot);
    try {
      // Assuming retrieveStateInEffectAtSlot and getBeaconCommitteesSize are thread-safe
      return recentChainData
          .retrieveStateInEffectAtSlot(attestationEpochStartSlot)
          .getImmediately();
    } catch (final IllegalStateException e) {
      LOG.debug(
          "Couldn't retrieve state in effect at slot {} for attestation at slot {}",
          attestationEpochStartSlot,
          attestationData.getSlot());
      return Optional.empty();
    }
  }
}
