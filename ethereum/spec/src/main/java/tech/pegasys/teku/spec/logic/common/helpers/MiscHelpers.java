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

package tech.pegasys.teku.spec.logic.common.helpers;

import static com.google.common.base.Preconditions.checkArgument;
import static tech.pegasys.teku.infrastructure.crypto.Hash.getSha256Instance;
import static tech.pegasys.teku.infrastructure.time.TimeUtilities.secondsToMillis;
import static tech.pegasys.teku.spec.config.SpecConfig.FAR_FUTURE_EPOCH;
import static tech.pegasys.teku.spec.logic.common.helpers.MathHelpers.bytesToUInt64;
import static tech.pegasys.teku.spec.logic.common.helpers.MathHelpers.uint64ToBytes;
import static tech.pegasys.teku.spec.logic.common.helpers.MathHelpers.uintTo4Bytes;

import com.google.common.primitives.UnsignedBytes;
import it.unimi.dsi.fastutil.ints.IntList;
import java.util.List;
import java.util.Optional;
import java.util.stream.IntStream;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import org.apache.tuweni.units.bigints.UInt256;
import tech.pegasys.teku.bls.BLSPublicKey;
import tech.pegasys.teku.bls.BLSSignature;
import tech.pegasys.teku.bls.impl.BlsException;
import tech.pegasys.teku.infrastructure.bytes.Bytes4;
import tech.pegasys.teku.infrastructure.crypto.Hash;
import tech.pegasys.teku.infrastructure.crypto.Sha256;
import tech.pegasys.teku.infrastructure.ssz.Merkleizable;
import tech.pegasys.teku.infrastructure.ssz.collections.SszByteVector;
import tech.pegasys.teku.infrastructure.ssz.primitive.SszUInt64;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.kzg.KZGCommitment;
import tech.pegasys.teku.spec.config.SpecConfig;
import tech.pegasys.teku.spec.constants.Domain;
import tech.pegasys.teku.spec.constants.NetworkConstants;
import tech.pegasys.teku.spec.datastructures.blobs.versions.deneb.BlobSidecar;
import tech.pegasys.teku.spec.datastructures.blocks.BeaconBlock;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBeaconBlock;
import tech.pegasys.teku.spec.datastructures.operations.DepositMessage;
import tech.pegasys.teku.spec.datastructures.state.ForkData;
import tech.pegasys.teku.spec.datastructures.state.SigningData;
import tech.pegasys.teku.spec.datastructures.state.Validator;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.BeaconState;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.BeaconStateCache;
import tech.pegasys.teku.spec.logic.versions.altair.helpers.MiscHelpersAltair;
import tech.pegasys.teku.spec.logic.versions.deneb.helpers.MiscHelpersDeneb;
import tech.pegasys.teku.spec.logic.versions.deneb.types.VersionedHash;
import tech.pegasys.teku.spec.logic.versions.electra.helpers.MiscHelpersElectra;
import tech.pegasys.teku.spec.logic.versions.fulu.helpers.MiscHelpersFulu;
import tech.pegasys.teku.spec.logic.versions.gloas.helpers.MiscHelpersGloas;

public class MiscHelpers {

  // Math.pow(2, 8) - 1;
  public static final UInt64 MAX_RANDOM_BYTE = UInt64.valueOf(255);

  protected final SpecConfig specConfig;

  private static final byte[] EMPTY_HASH = Bytes.EMPTY.toArrayUnsafe();

  public MiscHelpers(final SpecConfig specConfig) {
    this.specConfig = specConfig;
  }

  // compute_fork_version
  public Bytes4 computeForkVersion(final UInt64 epoch) {
    if (epoch.isGreaterThanOrEqualTo(specConfig.getHezeForkEpoch())) {
      return specConfig.getHezeForkVersion();
    } else if (epoch.isGreaterThanOrEqualTo(specConfig.getGloasForkEpoch())) {
      return specConfig.getGloasForkVersion();
    } else if (epoch.isGreaterThanOrEqualTo(specConfig.getFuluForkEpoch())) {
      return specConfig.getFuluForkVersion();
    } else if (epoch.isGreaterThanOrEqualTo(specConfig.getElectraForkEpoch())) {
      return specConfig.getElectraForkVersion();
    } else if (epoch.isGreaterThanOrEqualTo(specConfig.getDenebForkEpoch())) {
      return specConfig.getDenebForkVersion();
    } else if (epoch.isGreaterThanOrEqualTo(specConfig.getCapellaForkEpoch())) {
      return specConfig.getCapellaForkVersion();
    } else if (epoch.isGreaterThanOrEqualTo(specConfig.getBellatrixForkEpoch())) {
      return specConfig.getBellatrixForkVersion();
    } else if (epoch.isGreaterThanOrEqualTo(specConfig.getAltairForkEpoch())) {
      return specConfig.getAltairForkVersion();
    }
    return specConfig.getGenesisForkVersion();
  }

  public int computeShuffledIndex(final int index, final int indexCount, final Bytes32 seed) {
    checkArgument(index < indexCount, "CommitteeUtil.computeShuffledIndex1");

    final Sha256 sha256 = getSha256Instance();

    int indexRet = index;
    final int shuffleRoundCount = specConfig.getShuffleRoundCount();

    for (int round = 0; round < shuffleRoundCount; round++) {

      Bytes roundAsByte = Bytes.of((byte) round);

      // This needs to be unsigned modulo.
      int pivot =
          bytesToUInt64(sha256.wrappedDigest(seed, roundAsByte).slice(0, 8))
              .mod(indexCount)
              .intValue();
      int flip = Math.floorMod(pivot + indexCount - indexRet, indexCount);
      int position = Math.max(indexRet, flip);

      Bytes positionDiv256 = uintTo4Bytes(Math.floorDiv(position, 256L));
      byte[] hashBytes = sha256.digest(seed, roundAsByte, positionDiv256);

      int bitIndex = position & 0xff;
      int theByte = hashBytes[bitIndex / 8];
      int theBit = (theByte >> (bitIndex & 0x07)) & 1;
      if (theBit != 0) {
        indexRet = flip;
      }
    }

    return indexRet;
  }

  public int computeProposerIndex(
      final BeaconState state, final IntList indices, final Bytes32 seed) {
    return computeProposerIndex(state, indices, seed, specConfig.getMaxEffectiveBalance());
  }

  protected int computeProposerIndex(
      final BeaconState state,
      final IntList indices,
      final Bytes32 seed,
      final UInt64 maxEffectiveBalance) {
    checkArgument(!indices.isEmpty(), "compute_proposer_index indices must not be empty");

    final Sha256 sha256 = getSha256Instance();

    int i = 0;
    final int total = indices.size();
    byte[] hash = null;
    while (true) {
      final int candidateIndex = indices.getInt(computeShuffledIndex(i % total, total, seed));
      if (i % 32 == 0) {
        hash = sha256.digest(seed, uint64ToBytes(Math.floorDiv(i, 32L)));
      }
      final int randomByte = UnsignedBytes.toInt(hash[i % 32]);
      final UInt64 validatorEffectiveBalance =
          state.getValidators().get(candidateIndex).getEffectiveBalance();
      if (validatorEffectiveBalance
          .times(MAX_RANDOM_BYTE)
          .isGreaterThanOrEqualTo(maxEffectiveBalance.times(randomByte))) {
        return candidateIndex;
      }
      i++;
    }
  }

  public UInt64 computeEpochAtSlot(final UInt64 slot) {
    return slot.dividedBy(specConfig.getSlotsPerEpoch());
  }

  public UInt64 computeStartSlotAtEpoch(final UInt64 epoch) {
    return epoch.times(specConfig.getSlotsPerEpoch());
  }

  public UInt64 computeEndSlotAtEpoch(final UInt64 epoch) {
    return computeStartSlotAtEpoch(epoch.plus(1)).minusMinZero(1);
  }

  // compute_slot_at_time
  public UInt64 computeSlotAtTime(final UInt64 genesisTime, final UInt64 currentTime) {
    if (currentTime.isLessThan(genesisTime)) {
      return UInt64.ZERO;
    }
    return currentTime.minusMinZero(genesisTime).dividedBy(specConfig.getSecondsPerSlot());
  }

  public UInt64 computeSlotAtTimeMillis(
      final UInt64 genesisTimeMillis, final UInt64 currentTimeMillis) {
    if (currentTimeMillis.isLessThan(genesisTimeMillis)) {
      return UInt64.ZERO;
    }
    return currentTimeMillis.minus(genesisTimeMillis).dividedBy(specConfig.getSlotDurationMillis());
  }

  // compute_time_at_slot, spec function takes state, but otherwise the same.
  public UInt64 computeTimeAtSlot(final UInt64 genesisTime, final UInt64 slot) {
    final UInt64 slotsSinceGenesis = slot.minus(SpecConfig.GENESIS_SLOT);
    return genesisTime.plus(slotsSinceGenesis.times(specConfig.getSecondsPerSlot()));
  }

  // compute_time_at_slot - milliseconds version
  public UInt64 computeTimeMillisAtSlot(final UInt64 genesisTimeMillis, final UInt64 slot) {
    final UInt64 slotsSinceGenesis = slot.minus(SpecConfig.GENESIS_SLOT);
    return genesisTimeMillis.plus(slotsSinceGenesis.times(specConfig.getSlotDurationMillis()));
  }

  /**
   * Mirrors the consensus-specs {@code is_within_slot_range} helper.
   *
   * <p>{@code inclusiveEndSlotOffset} is the offset from {@code startSlot} to the final included
   * slot, not the number of slots in the range. For example, an epoch-sized range passes {@code
   * SLOTS_PER_EPOCH - 1}, covering {@code [startSlot, startSlot + SLOTS_PER_EPOCH - 1]}. The time
   * window ends at the start of the first slot after that inclusive range, with {@code
   * MAXIMUM_GOSSIP_CLOCK_DISPARITY} applied to both boundaries.
   */
  public boolean isCurrentTimeWithinInclusiveSlotRange(
      final UInt64 startSlot,
      final UInt64 inclusiveEndSlotOffset,
      final UInt64 genesisTimeSeconds,
      final UInt64 currentTimeMillis) {
    final UInt64 genesisTimeMillis = secondsToMillis(genesisTimeSeconds);
    final UInt64 startTimeMillis = computeTimeMillisAtSlot(genesisTimeMillis, startSlot);
    if (currentTimeMillis
        .plus(specConfig.getMaximumGossipClockDisparity())
        .isLessThan(startTimeMillis)) {
      return false;
    }

    final UInt64 endTimeMillis =
        computeTimeMillisAtSlot(genesisTimeMillis, startSlot.plus(inclusiveEndSlotOffset).plus(1));
    return !endTimeMillis
        .plus(specConfig.getMaximumGossipClockDisparity())
        .isLessThan(currentTimeMillis);
  }

  public boolean isSlotAtNthEpochBoundary(
      final UInt64 blockSlot, final UInt64 parentSlot, final int n) {
    checkArgument(n > 0, "Parameter n must be greater than 0");
    final UInt64 blockEpoch = computeEpochAtSlot(blockSlot);
    final UInt64 parentEpoch = computeEpochAtSlot(parentSlot);
    return blockEpoch.dividedBy(n).isGreaterThan(parentEpoch.dividedBy(n));
  }

  public UInt64 computeActivationExitEpoch(final UInt64 epoch) {
    return epoch.plus(UInt64.ONE).plus(specConfig.getMaxSeedLookahead());
  }

  public UInt64 getEarliestQueryableSlotForBeaconCommitteeAtTargetSlot(final UInt64 slot) {
    final UInt64 epoch = computeEpochAtSlot(slot);
    return getEarliestQueryableSlotForBeaconCommitteeInTargetEpoch(epoch);
  }

  public UInt64 getEarliestQueryableSlotForBeaconCommitteeInTargetEpoch(final UInt64 epoch) {
    final UInt64 earliestQueryableEpoch = epoch.minusMinZero(specConfig.getMinSeedLookahead());
    return computeStartSlotAtEpoch(earliestQueryableEpoch);
  }

  public IntList computeCommittee(
      final BeaconState state,
      final IntList indices,
      final Bytes32 seed,
      final int index,
      final int count) {
    final UInt64 indicesSize = UInt64.valueOf(indices.size());
    final int start = indicesSize.times(index).dividedBy(count).intValue();
    final int end = indicesSize.times(index + 1).dividedBy(count).intValue();
    return computeCommitteeShuffle(state, indices, seed, start, end);
  }

  private IntList computeCommitteeShuffle(
      final BeaconState state,
      final IntList indices,
      final Bytes32 seed,
      final int fromIndex,
      final int toIndex) {
    if (fromIndex < toIndex) {
      final int indexCount = indices.size();
      checkArgument(fromIndex < indexCount, "CommitteeUtil.getShuffledIndex1");
      checkArgument(toIndex <= indexCount, "CommitteeUtil.getShuffledIndex1");
    }
    return BeaconStateCache.getTransitionCaches(state)
        .getCommitteeShuffle()
        .get(seed, s -> shuffleList(indices, s))
        .subList(fromIndex, toIndex);
  }

  public List<UInt64> computeSubscribedSubnets(final UInt256 nodeId, final UInt64 epoch) {
    return IntStream.range(0, specConfig.getNetworkingConfig().getSubnetsPerNode())
        .mapToObj(index -> computeSubscribedSubnet(nodeId, epoch, index))
        .toList();
  }

  protected UInt64 computeSubscribedSubnet(
      final UInt256 nodeId, final UInt64 epoch, final int index) {

    final int nodeIdPrefix =
        nodeId
            .shiftRight(
                NetworkConstants.NODE_ID_BITS
                    - specConfig.getNetworkingConfig().getAttestationSubnetPrefixBits())
            .intValue();

    final UInt64 nodeOffset =
        UInt64.valueOf(
            nodeId.mod(specConfig.getNetworkingConfig().getEpochsPerSubnetSubscription()).toLong());

    final Bytes32 permutationSeed =
        Hash.sha256(
            uint64ToBytes(
                epoch
                    .plus(nodeOffset)
                    .dividedBy(specConfig.getNetworkingConfig().getEpochsPerSubnetSubscription())));

    final int permutedPrefix =
        computeShuffledIndex(
            nodeIdPrefix,
            1 << specConfig.getNetworkingConfig().getAttestationSubnetPrefixBits(),
            permutationSeed);

    return UInt64.valueOf(
        (permutedPrefix + index) % specConfig.getNetworkingConfig().getAttestationSubnetCount());
  }

  public UInt64 calculateNodeSubnetUnsubscriptionSlot(
      final UInt256 nodeId, final UInt64 currentSlot) {
    final int epochsPerSubnetSubscription =
        specConfig.getNetworkingConfig().getEpochsPerSubnetSubscription();
    final UInt64 nodeOffset = UInt64.valueOf(nodeId.mod(epochsPerSubnetSubscription).toLong());
    final UInt64 currentEpoch = computeEpochAtSlot(currentSlot);
    final UInt64 currentEpochRemainder = currentEpoch.mod(epochsPerSubnetSubscription);
    UInt64 nextPeriodEpoch =
        currentEpoch
            .plus(epochsPerSubnetSubscription)
            .minus(currentEpochRemainder)
            .minus(nodeOffset);
    if (nextPeriodEpoch.isLessThanOrEqualTo(currentEpoch)) {
      nextPeriodEpoch = nextPeriodEpoch.plus(epochsPerSubnetSubscription);
    }
    return computeStartSlotAtEpoch(nextPeriodEpoch);
  }

  IntList shuffleList(final IntList input, final Bytes32 seed) {
    final int[] indices = input.toIntArray();
    shuffleList(indices, seed);
    return IntList.of(indices);
  }

  public void shuffleList(final int[] input, final Bytes32 seed) {

    int listSize = input.length;
    if (listSize == 0) {
      return;
    }

    final Sha256 sha256 = getSha256Instance();

    for (int round = specConfig.getShuffleRoundCount() - 1; round >= 0; round--) {

      final Bytes roundAsByte = Bytes.of((byte) round);

      // This needs to be unsigned modulo.
      final Bytes hash = sha256.wrappedDigest(seed, roundAsByte);
      int pivot = bytesToUInt64(hash.slice(0, 8)).mod(listSize).intValue();

      byte[] hashBytes = EMPTY_HASH;
      int mirror1 = (pivot + 2) / 2;
      int mirror2 = (pivot + listSize) / 2;
      for (int i = mirror1; i <= mirror2; i++) {
        int flip, bitIndex;
        if (i <= pivot) {
          flip = pivot - i;
          bitIndex = i & 0xff;
          if (bitIndex == 0 || i == mirror1) {
            hashBytes = sha256.digest(seed, roundAsByte, uintTo4Bytes(i / 256));
          }
        } else {
          flip = pivot + listSize - i;
          bitIndex = flip & 0xff;
          if (bitIndex == 0xff || i == pivot + 1) {
            hashBytes = sha256.digest(seed, roundAsByte, uintTo4Bytes(flip / 256));
          }
        }

        int theByte = hashBytes[bitIndex / 8];
        int theBit = (theByte >> (bitIndex & 0x07)) & 1;
        if (theBit != 0) {
          int tmp = input[i];
          input[i] = input[flip];
          input[flip] = tmp;
        }
      }
    }
  }

  public Bytes computeSigningRoot(final Merkleizable object, final Bytes32 domain) {
    return new SigningData(object.hashTreeRoot(), domain).hashTreeRoot();
  }

  public Bytes computeSigningRoot(final UInt64 number, final Bytes32 domain) {
    SigningData domainWrappedObject = new SigningData(SszUInt64.of(number).hashTreeRoot(), domain);
    return domainWrappedObject.hashTreeRoot();
  }

  public Bytes32 computeSigningRoot(final Bytes bytes, final Bytes32 domain) {
    SigningData domainWrappedObject =
        new SigningData(SszByteVector.computeHashTreeRoot(bytes), domain);
    return domainWrappedObject.hashTreeRoot();
  }

  public Bytes computeDepositSigningRoot(
      final BLSPublicKey pubkey, final Bytes32 withdrawalCredentials, final UInt64 amount) {
    final Bytes32 domain = computeDomain(Domain.DEPOSIT);
    final DepositMessage depositMessage = new DepositMessage(pubkey, withdrawalCredentials, amount);
    return computeSigningRoot(depositMessage, domain);
  }

  public Bytes4 computeForkDigest(
      final Bytes4 currentVersion, final Bytes32 genesisValidatorsRoot) {
    return new Bytes4(computeForkDataRoot(currentVersion, genesisValidatorsRoot).slice(0, 4));
  }

  public Bytes32 computeDomain(final Bytes4 domainType) {
    return computeDomain(domainType, specConfig.getGenesisForkVersion(), Bytes32.ZERO);
  }

  public Bytes32 computeDomain(final Bytes4 domainType, final Bytes32 genesisValidatorsRoot) {
    return computeDomain(domainType, specConfig.getGenesisForkVersion(), genesisValidatorsRoot);
  }

  public Bytes32 computeDomain(
      final Bytes4 domainType, final Bytes4 forkVersion, final Bytes32 genesisValidatorsRoot) {
    final Bytes32 forkDataRoot = computeForkDataRoot(forkVersion, genesisValidatorsRoot);
    return Bytes32.wrap(Bytes.concatenate(domainType.getWrappedBytes(), forkDataRoot.slice(0, 28)));
  }

  protected Bytes32 computeForkDataRoot(
      final Bytes4 currentVersion, final Bytes32 genesisValidatorsRoot) {
    return new ForkData(currentVersion, genesisValidatorsRoot).hashTreeRoot();
  }

  /** is_valid_deposit_signature */
  public boolean isValidDepositSignature(
      final BLSPublicKey pubkey,
      final Bytes32 withdrawalCredentials,
      final UInt64 amount,
      final BLSSignature signature) {
    try {
      return specConfig
          .getBLSSignatureVerifier()
          .verify(
              pubkey, computeDepositSigningRoot(pubkey, withdrawalCredentials, amount), signature);
    } catch (final BlsException e) {
      return false;
    }
  }

  /** get_validator_from_deposit */
  public Validator getValidatorFromDeposit(
      final BLSPublicKey pubkey, final Bytes32 withdrawalCredentials, final UInt64 amount) {
    final UInt64 effectiveBalance =
        amount
            .minus(amount.mod(specConfig.getEffectiveBalanceIncrement()))
            .min(specConfig.getMaxEffectiveBalance());
    return new Validator(
        pubkey,
        withdrawalCredentials,
        effectiveBalance,
        false,
        FAR_FUTURE_EPOCH,
        FAR_FUTURE_EPOCH,
        FAR_FUTURE_EPOCH,
        FAR_FUTURE_EPOCH);
  }

  public boolean isMergeTransitionComplete(final BeaconState state) {
    return false;
  }

  public boolean isExecutionEnabled(final BeaconState genericState, final BeaconBlock block) {
    return false;
  }

  public boolean verifyBlobKzgProof(final BlobSidecar blobSidecar) {
    return false;
  }

  public boolean verifyBlobKzgProofBatch(final List<BlobSidecar> blobSidecars) {
    return false;
  }

  public boolean verifyBlobSidecarBlockHeaderSignatureViaValidatedSignedBlock(
      final List<BlobSidecar> blobSidecars, final SignedBeaconBlock signedBeaconBlock) {
    return blobSidecars.stream()
        .allMatch(
            blobSidecar ->
                verifyBlobSidecarBlockHeaderSignatureViaValidatedSignedBlock(
                    blobSidecar, signedBeaconBlock));
  }

  public boolean verifyBlobSidecarBlockHeaderSignatureViaValidatedSignedBlock(
      final BlobSidecar blobSidecar, final SignedBeaconBlock signedBeaconBlock) {
    throw new UnsupportedOperationException("No Blob Sidecars before Deneb");
  }

  public void verifyBlobSidecarCompleteness(
      final List<BlobSidecar> verifiedBlobSidecars, final SignedBeaconBlock signedBeaconBlock)
      throws IllegalArgumentException {
    throw new UnsupportedOperationException("No Blob Sidecars before Deneb");
  }

  public VersionedHash kzgCommitmentToVersionedHash(final KZGCommitment kzgCommitment) {
    throw new UnsupportedOperationException("No KZGCommitments before Deneb");
  }

  public UInt64 getMaxRequestBlocks() {
    return UInt64.valueOf(specConfig.getNetworkingConfig().getMaxRequestBlocks());
  }

  public int getMaxRequestDataColumnSidecars() {
    throw new UnsupportedOperationException("No Data Column Sidecars before Fulu");
  }

  public int getBlobKzgCommitmentsCount(final SignedBeaconBlock signedBeaconBlock) {
    throw new UnsupportedOperationException("No Blob KZG Commitments before Deneb");
  }

  public List<Integer> computeProposerIndices(
      final BeaconState state,
      final UInt64 epoch,
      final Bytes32 epochSeed,
      final IntList activeValidatorIndices) {
    throw new UnsupportedOperationException("No ProposerLookahead before Fulu");
  }

  public UInt64 getMaxEffectiveBalance(final Validator validator) {
    return specConfig.getMaxEffectiveBalance();
  }

  public boolean isFormerDepositMechanismDisabled(final BeaconState state) {
    return false;
  }

  // Methods used by the SlotProcessor to determine if it needs to increase the node slot
  public boolean shouldIncrementNodeSlotWhenAttestationsAreDue() {
    return true;
  }

  public boolean shouldIncrementNodeSlotWhenPayloadAttestationsAreDue() {
    return false;
  }

  public Optional<MiscHelpersAltair> toVersionAltair() {
    return Optional.empty();
  }

  public Optional<MiscHelpersDeneb> toVersionDeneb() {
    return Optional.empty();
  }

  public Optional<MiscHelpersElectra> toVersionElectra() {
    return Optional.empty();
  }

  public Optional<MiscHelpersFulu> toVersionFulu() {
    return Optional.empty();
  }

  public Optional<MiscHelpersGloas> toVersionGloas() {
    return Optional.empty();
  }
}
