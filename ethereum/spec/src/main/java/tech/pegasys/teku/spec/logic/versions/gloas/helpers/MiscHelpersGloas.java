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

package tech.pegasys.teku.spec.logic.versions.gloas.helpers;

import static com.google.common.base.Preconditions.checkArgument;
import static tech.pegasys.teku.spec.config.SpecConfigGloas.BUILDER_INDEX_FLAG;
import static tech.pegasys.teku.spec.logic.common.helpers.MathHelpers.bytesToUInt64;
import static tech.pegasys.teku.spec.logic.common.helpers.MathHelpers.uint64ToBytes;

import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.ints.IntList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.NavigableMap;
import java.util.Optional;
import java.util.TreeMap;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.bls.BLSPublicKey;
import tech.pegasys.teku.infrastructure.crypto.Hash;
import tech.pegasys.teku.infrastructure.ssz.SszList;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.kzg.KZGCell;
import tech.pegasys.teku.kzg.KZGCellWithColumnId;
import tech.pegasys.teku.spec.config.SpecConfigElectra;
import tech.pegasys.teku.spec.config.SpecConfigGloas;
import tech.pegasys.teku.spec.datastructures.blobs.DataColumnSidecar;
import tech.pegasys.teku.spec.datastructures.blobs.versions.fulu.MatrixEntry;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBeaconBlockHeader;
import tech.pegasys.teku.spec.datastructures.blocks.SlotAndBlockRoot;
import tech.pegasys.teku.spec.datastructures.epbs.versions.gloas.ExecutionPayloadBid;
import tech.pegasys.teku.spec.datastructures.epbs.versions.gloas.ExecutionPayloadEnvelope;
import tech.pegasys.teku.spec.datastructures.epbs.versions.gloas.SignedExecutionPayloadEnvelope;
import tech.pegasys.teku.spec.datastructures.execution.BlobAndCellProofs;
import tech.pegasys.teku.spec.datastructures.execution.ExecutionRequests;
import tech.pegasys.teku.spec.datastructures.state.Validator;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.BeaconState;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.versions.gloas.BeaconStateGloas;
import tech.pegasys.teku.spec.datastructures.state.versions.electra.PendingDeposit;
import tech.pegasys.teku.spec.datastructures.type.SszKZGCommitment;
import tech.pegasys.teku.spec.datastructures.type.SszKZGProof;
import tech.pegasys.teku.spec.logic.common.helpers.MiscHelpers;
import tech.pegasys.teku.spec.logic.versions.fulu.helpers.MiscHelpersFulu;
import tech.pegasys.teku.spec.schemas.SchemaDefinitionsGloas;

public class MiscHelpersGloas extends MiscHelpersFulu {
  private static final Logger LOG = LogManager.getLogger();

  public static MiscHelpersGloas required(final MiscHelpers miscHelpers) {
    return miscHelpers
        .toVersionGloas()
        .orElseThrow(
            () ->
                new IllegalArgumentException(
                    "Expected Gloas misc helpers but got: "
                        + miscHelpers.getClass().getSimpleName()));
  }

  private final SpecConfigGloas specConfigGloas;
  private final SchemaDefinitionsGloas schemaDefinitionsGloas;
  // EIP-8261: the gas limit schedule, navigable by epoch
  private final NavigableMap<UInt64, UInt64> epochToScheduledGasLimit = new TreeMap<>();

  public MiscHelpersGloas(
      final SpecConfigGloas specConfig,
      final PredicatesGloas predicates,
      final SchemaDefinitionsGloas schemaDefinitionsGloas) {
    super(specConfig, predicates, schemaDefinitionsGloas);
    this.specConfigGloas = specConfig;
    this.schemaDefinitionsGloas = schemaDefinitionsGloas;
    specConfig
        .getGasLimitSchedule()
        .forEach(entry -> epochToScheduledGasLimit.put(entry.epoch(), entry.gasLimit()));
  }

  public UInt64 convertBuilderIndexToValidatorIndex(final UInt64 builderIndex) {
    return UInt64.valueOf(builderIndex.longValue() | BUILDER_INDEX_FLAG.longValue());
  }

  public UInt64 convertValidatorIndexToBuilderIndex(final UInt64 validatorIndex) {
    return UInt64.valueOf(validatorIndex.longValue() & ~BUILDER_INDEX_FLAG.longValue());
  }

  /**
   * get_scheduled_gas_limit
   *
   * <p>EIP-8261: returns the scheduled gas limit at a given epoch, if any.
   */
  public Optional<UInt64> getScheduledGasLimit(final UInt64 epoch) {
    return Optional.ofNullable(epochToScheduledGasLimit.floorEntry(epoch)).map(Map.Entry::getValue);
  }

  @Override
  public boolean isAvailabilityOfBlobSidecarsRequiredAtEpoch(
      final UInt64 currentEpoch, final UInt64 epoch) {
    return false;
  }

  public boolean isExecutionPayloadEnvelopeAvailable() {
    return true;
  }

  public boolean isPayloadAttestationAvailable() {
    return true;
  }

  /**
   * compute_proposer_indices is refactored to use compute_balance_weighted_selection as a helper
   * for the balance-weighted sampling process.
   */
  @Override
  public List<Integer> computeProposerIndices(
      final BeaconState state,
      final UInt64 epoch,
      final Bytes32 epochSeed,
      final IntList activeValidatorIndices) {
    final UInt64 startSlot = computeStartSlotAtEpoch(epoch);
    return IntStream.range(0, specConfig.getSlotsPerEpoch())
        .mapToObj(
            i -> {
              final Bytes32 seed =
                  Hash.sha256(Bytes.concatenate(epochSeed, uint64ToBytes(startSlot.plus(i))));
              return computeBalanceWeightedSelection(state, activeValidatorIndices, seed, 1, true)
                  .getInt(0);
            })
        .toList();
  }

  /**
   * compute_balance_weighted_selection
   *
   * <p>Return ``size`` indices sampled by effective balance, using ``indices`` as candidates. If
   * ``shuffle_indices`` is ``True``, candidate indices are themselves sampled from ``indices`` by
   * shuffling it, otherwise ``indices`` is traversed in order.
   */
  public IntList computeBalanceWeightedSelection(
      final BeaconState state,
      final IntList indices,
      final Bytes32 seed,
      final int size,
      final boolean shuffleIndices) {
    final int total = indices.size();
    checkArgument(total > 0, "Size of indices must be greater than 0");
    final SszList<Validator> validators = state.getValidators();
    final List<UInt64> effectiveBalances =
        indices.intStream().mapToObj(index -> validators.get(index).getEffectiveBalance()).toList();
    final IntList selected = new IntArrayList();
    int i = 0;
    Bytes32 randomBytes = Bytes32.ZERO;
    while (selected.size() < size) {
      final int offset = (i % 16) * 2;
      if (offset == 0) {
        randomBytes = Hash.sha256(Bytes.concatenate(seed, uint64ToBytes(Math.floorDiv(i, 16L))));
      }
      int nextIndex = i % total;
      if (shuffleIndices) {
        nextIndex = computeShuffledIndex(nextIndex, total, seed);
      }
      final UInt64 weight = effectiveBalances.get(nextIndex).times(MAX_RANDOM_VALUE);
      final UInt64 randomValue = bytesToUInt64(randomBytes.slice(offset, 2));
      final UInt64 threshold =
          SpecConfigElectra.required(specConfig).getMaxEffectiveBalanceElectra().times(randomValue);
      if (weight.isGreaterThanOrEqualTo(threshold)) {
        selected.add(indices.getInt(nextIndex));
      }
      i++;
    }
    return selected;
  }

  public List<DataColumnSidecar> constructDataColumnSidecars(
      final SignedExecutionPayloadEnvelope signedExecutionPayload,
      final List<BlobAndCellProofs> blobAndCellProofsList) {
    final List<List<MatrixEntry>> extendedMatrix = computeExtendedMatrix(blobAndCellProofsList);
    if (extendedMatrix.isEmpty()) {
      return Collections.emptyList();
    }
    final ExecutionPayloadEnvelope executionPayload = signedExecutionPayload.getMessage();
    return constructDataColumnSidecarsInternal(
        builder ->
            builder
                .beaconBlockRoot(executionPayload.getBeaconBlockRoot())
                .slot(executionPayload.getSlot()),
        extendedMatrix);
  }

  @Override
  public List<DataColumnSidecar> constructDataColumnSidecars(
      final Optional<SignedBeaconBlockHeader> maybeSignedBeaconBlockHeader,
      final SlotAndBlockRoot slotAndBlockRoot,
      final Optional<SszList<SszKZGCommitment>> maybeSszKZGCommitments,
      final Optional<List<Bytes32>> maybeKzgCommitmentsInclusionProof,
      final List<BlobAndCellProofs> blobAndCellProofsList) {
    final List<List<MatrixEntry>> extendedMatrix = computeExtendedMatrix(blobAndCellProofsList);
    if (extendedMatrix.isEmpty()) {
      return Collections.emptyList();
    }
    return constructDataColumnSidecarsInternal(
        builder ->
            builder
                .slot(slotAndBlockRoot.getSlot())
                .beaconBlockRoot(slotAndBlockRoot.getBlockRoot()),
        extendedMatrix);
  }

  @Override
  public List<DataColumnSidecar> reconstructAllDataColumnSidecars(
      final Collection<DataColumnSidecar> existingSidecars) {
    if (existingSidecars.size() < (specConfigGloas.getNumberOfColumns() / 2)) {
      final Optional<DataColumnSidecar> maybeSidecar = existingSidecars.stream().findAny();
      throw new IllegalArgumentException(
          String.format(
              "Number of sidecars must be greater than or equal to the half of column count, slot: %s; columns: found %s, needed at least %d",
              maybeSidecar.isPresent() ? maybeSidecar.get().getSlot().toString() : "unknown",
              existingSidecars.size(),
              specConfigGloas.getNumberOfColumns() / 2));
    }
    final List<List<MatrixEntry>> columnBlobEntries =
        existingSidecars.stream()
            .sorted(Comparator.comparing(DataColumnSidecar::getIndex))
            .map(
                sidecar ->
                    IntStream.range(0, sidecar.getColumn().size())
                        .mapToObj(
                            rowIndex ->
                                schemaDefinitionsGloas
                                    .getMatrixEntrySchema()
                                    .create(
                                        sidecar.getColumn().get(rowIndex),
                                        sidecar.getKzgProofs().get(rowIndex).getKZGProof(),
                                        sidecar.getIndex(),
                                        UInt64.valueOf(rowIndex)))
                        .toList())
            .toList();
    final List<List<MatrixEntry>> blobColumnEntries = transpose(columnBlobEntries);
    final List<List<MatrixEntry>> extendedMatrix = recoverMatrix(blobColumnEntries);
    final DataColumnSidecar anyExistingSidecar =
        existingSidecars.stream().findFirst().orElseThrow();
    return constructDataColumnSidecarsInternal(
        builder ->
            builder
                .slot(anyExistingSidecar.getSlot())
                .beaconBlockRoot(anyExistingSidecar.getBeaconBlockRoot()),
        extendedMatrix);
  }

  public boolean isBidBuildingOnFullParent(
      final BeaconStateGloas state, final ExecutionPayloadBid bid) {
    return bid.getParentBlockHash().equals(state.getLatestExecutionPayloadBid().getBlockHash());
  }

  public boolean isExecutionRequestsRootMatchingLatestBid(
      final BeaconStateGloas state, final ExecutionRequests executionRequests) {
    return executionRequests
        .hashTreeRoot()
        .equals(state.getLatestExecutionPayloadBid().getExecutionRequestsRoot());
  }

  // Check if a pending deposit with a valid signature is in the queue for the given pubkey.
  public boolean isPendingValidator(
      final List<PendingDeposit> pendingDeposits, final BLSPublicKey pubkey) {
    for (final PendingDeposit pendingDeposit : pendingDeposits) {
      if (!pendingDeposit.getPublicKey().equals(pubkey)) {
        continue;
      }
      if (isValidDepositSignature(
          pendingDeposit.getPublicKey(),
          pendingDeposit.getWithdrawalCredentials(),
          pendingDeposit.getAmount(),
          pendingDeposit.getSignature())) {
        return true;
      }
    }
    return false;
  }

  /**
   * verify_data_column_sidecar
   *
   * @param dataColumnSidecar the data column sidecar to verify
   * @param kzgCommitments the KZG commitments from the execution payload bid
   * @return true if the sidecar structure is valid
   */
  public boolean verifyDataColumnSidecarWithCommitments(
      final DataColumnSidecar dataColumnSidecar, final SszList<SszKZGCommitment> kzgCommitments) {
    // The column length must be equal to the number of commitments/proofs
    if (dataColumnSidecar.getColumn().size() != kzgCommitments.size()
        || dataColumnSidecar.getColumn().size() != dataColumnSidecar.getKzgProofs().size()) {
      LOG.trace(
          "DataColumnSidecar has unequal data column ({}), kzg commitments ({}), and kzg proofs ({}) sizes",
          dataColumnSidecar.getColumn().size(),
          kzgCommitments.size(),
          dataColumnSidecar.getKzgProofs().size());
      return false;
    }
    return true;
  }

  @Override
  public boolean verifyDataColumnSidecar(final DataColumnSidecar dataColumnSidecar) {
    final int numberOfColumns = specConfigGloas.getNumberOfColumns();

    // The sidecar index must be within the valid range
    if (!dataColumnSidecar.getIndex().isLessThan(numberOfColumns)) {
      LOG.trace(
          "DataColumnSidecar has invalid index {}. Should be less than {}",
          dataColumnSidecar.getIndex(),
          numberOfColumns);
      return false;
    }

    if (dataColumnSidecar.getColumn().size() != dataColumnSidecar.getKzgProofs().size()) {
      LOG.trace(
          "DataColumnSidecar has unequal data column ({}) and kzg proofs ({}) sizes",
          dataColumnSidecar.getColumn().size(),
          dataColumnSidecar.getKzgProofs().size());
      return false;
    }

    // A sidecar for zero blobs is invalid
    if (dataColumnSidecar.getColumn().isEmpty()) {
      LOG.trace("DataColumnSidecar has empty column");
      return false;
    }
    return true;
  }

  /**
   * verify_data_column_sidecar_kzg_proofs
   *
   * @param dataColumnSidecar the data column sidecar to verify
   * @param kzgCommitments the KZG commitments from the execution payload bid
   * @return true if the KZG proofs are valid
   */
  @Override
  public boolean verifyDataColumnSidecarKzgProofs(
      final DataColumnSidecar dataColumnSidecar, final SszList<SszKZGCommitment> kzgCommitments) {

    // The column index also represents the cell index
    final List<KZGCellWithColumnId> cellWithIds =
        IntStream.range(0, dataColumnSidecar.getColumn().size())
            .mapToObj(
                rowIndex ->
                    KZGCellWithColumnId.fromCellAndColumn(
                        new KZGCell(dataColumnSidecar.getColumn().get(rowIndex).getBytes()),
                        dataColumnSidecar.getIndex().intValue()))
            .collect(Collectors.toList());

    // Batch verify that the cells match the corresponding commitments and proofs
    return getKzg()
        .verifyCellProofBatch(
            kzgCommitments.stream().map(SszKZGCommitment::getKZGCommitment).toList(),
            cellWithIds,
            dataColumnSidecar.getKzgProofs().stream().map(SszKZGProof::getKZGProof).toList());
  }

  @Override
  public boolean shouldIncrementNodeSlotWhenAttestationsAreDue() {
    return false;
  }

  // in Gloas, increment the node slot when payload attestations are due
  @Override
  public boolean shouldIncrementNodeSlotWhenPayloadAttestationsAreDue() {
    return true;
  }

  @Override
  public Optional<MiscHelpersGloas> toVersionGloas() {
    return Optional.of(this);
  }
}
