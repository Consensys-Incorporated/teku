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

package tech.pegasys.teku.spec.logic.versions.fulu.helpers;

import static tech.pegasys.teku.spec.logic.common.helpers.MathHelpers.bytesToUInt64;
import static tech.pegasys.teku.spec.logic.common.helpers.MathHelpers.uint256ToBytes;
import static tech.pegasys.teku.spec.logic.common.helpers.MathHelpers.uint64ToBytes;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.primitives.Bytes;
import it.unimi.dsi.fastutil.ints.IntList;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.LongStream;
import java.util.stream.Stream;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.tuweni.bytes.Bytes32;
import org.apache.tuweni.units.bigints.UInt256;
import tech.pegasys.teku.infrastructure.bytes.Bytes4;
import tech.pegasys.teku.infrastructure.crypto.Hash;
import tech.pegasys.teku.infrastructure.ssz.SszList;
import tech.pegasys.teku.infrastructure.ssz.schema.SszListSchema;
import tech.pegasys.teku.infrastructure.ssz.tree.MerkleUtil;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.kzg.KZGCell;
import tech.pegasys.teku.kzg.KZGCellAndProof;
import tech.pegasys.teku.kzg.KZGCellID;
import tech.pegasys.teku.kzg.KZGCellWithColumnId;
import tech.pegasys.teku.spec.config.SpecConfigElectra;
import tech.pegasys.teku.spec.config.SpecConfigFulu;
import tech.pegasys.teku.spec.datastructures.blobs.DataColumnSchema;
import tech.pegasys.teku.spec.datastructures.blobs.DataColumnSidecar;
import tech.pegasys.teku.spec.datastructures.blobs.DataColumnSidecarBuilder;
import tech.pegasys.teku.spec.datastructures.blobs.DataColumnSidecarSchema;
import tech.pegasys.teku.spec.datastructures.blobs.versions.fulu.Cell;
import tech.pegasys.teku.spec.datastructures.blobs.versions.fulu.DataColumn;
import tech.pegasys.teku.spec.datastructures.blobs.versions.fulu.DataColumnSidecarFulu;
import tech.pegasys.teku.spec.datastructures.blobs.versions.fulu.MatrixEntry;
import tech.pegasys.teku.spec.datastructures.blocks.BeaconBlock;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBeaconBlock;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBeaconBlockHeader;
import tech.pegasys.teku.spec.datastructures.blocks.SlotAndBlockRoot;
import tech.pegasys.teku.spec.datastructures.blocks.blockbody.BeaconBlockBody;
import tech.pegasys.teku.spec.datastructures.blocks.blockbody.versions.deneb.BeaconBlockBodyDeneb;
import tech.pegasys.teku.spec.datastructures.blocks.blockbody.versions.deneb.BlindedBeaconBlockBodyDeneb;
import tech.pegasys.teku.spec.datastructures.blocks.blockbody.versions.electra.BeaconBlockBodySchemaElectra;
import tech.pegasys.teku.spec.datastructures.execution.BlobAndCellProofs;
import tech.pegasys.teku.spec.datastructures.state.Validator;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.BeaconState;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.versions.electra.BeaconStateElectra;
import tech.pegasys.teku.spec.datastructures.type.SszKZGCommitment;
import tech.pegasys.teku.spec.datastructures.type.SszKZGProof;
import tech.pegasys.teku.spec.logic.common.helpers.MiscHelpers;
import tech.pegasys.teku.spec.logic.common.helpers.Predicates;
import tech.pegasys.teku.spec.logic.versions.electra.helpers.MiscHelpersElectra;
import tech.pegasys.teku.spec.logic.versions.electra.helpers.PredicatesElectra;
import tech.pegasys.teku.spec.schemas.SchemaDefinitionsElectra;
import tech.pegasys.teku.spec.schemas.SchemaDefinitionsFulu;

public class MiscHelpersFulu extends MiscHelpersElectra {
  private static final Logger LOG = LogManager.getLogger();

  public static MiscHelpersFulu required(final MiscHelpers miscHelpers) {
    return miscHelpers
        .toVersionFulu()
        .orElseThrow(
            () ->
                new IllegalArgumentException(
                    "Expected Fulu misc helpers but got: "
                        + miscHelpers.getClass().getSimpleName()));
  }

  private final Predicates predicates;
  private final SpecConfigFulu specConfigFulu;
  private final SchemaDefinitionsFulu schemaDefinitionsFulu;
  private final BpoForkSchedule bpoForkSchedule;

  public MiscHelpersFulu(
      final SpecConfigFulu specConfig,
      final PredicatesElectra predicates,
      final SchemaDefinitionsFulu schemaDefinitions) {
    super(
        SpecConfigElectra.required(specConfig),
        predicates,
        SchemaDefinitionsElectra.required(schemaDefinitions));
    this.predicates = predicates;
    this.specConfigFulu = specConfig;
    this.schemaDefinitionsFulu = schemaDefinitions;
    this.bpoForkSchedule = new BpoForkSchedule(specConfig);
  }

  @Override
  public Optional<MiscHelpersFulu> toVersionFulu() {
    return Optional.of(this);
  }

  // modified to remove support for the former deposit mechanism
  @Override
  public boolean isFormerDepositMechanismDisabled(final BeaconState state) {
    return true;
  }

  // compute_fork_digest
  public Bytes4 computeForkDigest(final Bytes32 genesisValidatorsRoot, final UInt64 epoch) {
    final Bytes4 forkVersion = computeForkVersion(epoch);
    final Bytes32 baseDigest = computeForkDataRoot(forkVersion, genesisValidatorsRoot);
    final BlobParameters blobParameters = getBlobParameters(epoch);
    // Bitmask digest with hash of blob parameters
    return new Bytes4(baseDigest.xor(blobParameters.hash()).slice(0, 4));
  }

  // get_blob_parameters
  public BlobParameters getBlobParameters(final UInt64 epoch) {
    return getBpoFork(epoch)
        .orElse(
            new BlobParameters(
                specConfigFulu.getElectraForkEpoch(), specConfigFulu.getMaxBlobsPerBlock()));
  }

  // BPO
  public Optional<BlobParameters> getBpoFork(final UInt64 epoch) {
    return bpoForkSchedule.getBpoFork(epoch);
  }

  public Optional<BlobParameters> getNextBpoFork(final UInt64 epoch) {
    return bpoForkSchedule.getNextBpoFork(epoch);
  }

  public Optional<Integer> getHighestMaxBlobsPerBlockFromBpoForkSchedule() {
    return bpoForkSchedule.getHighestMaxBlobsPerBlock();
  }

  public Collection<BlobParameters> getBpoForks() {
    return bpoForkSchedule.getBpoForks();
  }

  public UInt64 computeSubnetForDataColumnSidecar(final UInt64 columnIndex) {
    return columnIndex.mod(specConfigFulu.getDataColumnSidecarSubnetCount());
  }

  public List<UInt64> computeDataColumnSidecarBackboneSubnets(
      final UInt256 nodeId, final int groupCount) {
    final Set<UInt64> columns = computeCustodyColumnIndices(nodeId, groupCount);
    return columns.stream().map(this::computeSubnetForDataColumnSidecar).toList();
  }

  public Set<UInt64> computeCustodyColumnIndices(final UInt256 nodeId, final int groupCount) {
    final List<UInt64> custodyGroups = getCustodyGroups(nodeId, groupCount);
    return custodyGroups.stream()
        .flatMap(group -> computeColumnsForCustodyGroup(group).stream())
        .collect(Collectors.toUnmodifiableSet());
  }

  public List<UInt64> computeColumnsForCustodyGroup(final UInt64 custodyGroup) {
    if (custodyGroup.isGreaterThanOrEqualTo(specConfigFulu.getNumberOfCustodyGroups())) {
      throw new IllegalArgumentException(
          String.format(
              "Custody group (%s) cannot exceed number of groups (%s)",
              custodyGroup, specConfigFulu.getNumberOfCustodyGroups()));
    }

    return IntStream.range(0, getCustodyColumnsPerGroup())
        .mapToLong(
            i -> (long) specConfigFulu.getNumberOfCustodyGroups() * i + custodyGroup.intValue())
        .mapToObj(UInt64::valueOf)
        .toList();
  }

  public int getCustodyColumnsPerGroup() {
    return specConfigFulu.getNumberOfColumns() / specConfigFulu.getNumberOfCustodyGroups();
  }

  private UInt64 computeCustodyGroupIndex(final UInt256 nodeId) {
    return bytesToUInt64(Hash.sha256(uint256ToBytes(nodeId)).slice(0, 8))
        .mod(specConfigFulu.getNumberOfCustodyGroups());
  }

  public List<UInt64> getCustodyGroups(final UInt256 nodeId, final int custodyGroupCount) {
    if (custodyGroupCount > specConfigFulu.getNumberOfCustodyGroups()) {
      throw new IllegalArgumentException(
          String.format(
              "Custody group count (%s) cannot exceed number of groups (%s)",
              custodyGroupCount, specConfigFulu.getNumberOfCustodyGroups()));
    }

    // Skip computation if all groups are custodied
    if (custodyGroupCount == specConfigFulu.getNumberOfCustodyGroups()) {
      return LongStream.range(0, custodyGroupCount).mapToObj(UInt64::valueOf).toList();
    }

    return Stream.iterate(nodeId, this::incrementByModule)
        .map(this::computeCustodyGroupIndex)
        .distinct()
        .limit(custodyGroupCount)
        .sorted()
        .toList();
  }

  public boolean isSuperNode(final int groupCount) {
    return groupCount == specConfigFulu.getNumberOfCustodyGroups();
  }

  private UInt256 incrementByModule(final UInt256 n) {
    if (n.equals(UInt256.MAX_VALUE)) {
      return UInt256.ZERO;
    } else {
      return n.plus(1);
    }
  }

  public UInt64 getValidatorsCustodyRequirement(
      final BeaconState state, final Set<UInt64> validatorIndices) {
    final UInt64 totalNodeBalance =
        validatorIndices.stream()
            .map(
                proposerIndex -> {
                  final Validator validator = state.getValidators().get(proposerIndex.intValue());
                  return validator.getEffectiveBalance();
                })
            .reduce(UInt64.ZERO, UInt64::plus);
    final UInt64 count =
        totalNodeBalance.dividedBy(specConfigFulu.getBalancePerAdditionalCustodyGroup());
    return count
        .max(specConfigFulu.getValidatorCustodyRequirement())
        .min(specConfigFulu.getNumberOfCustodyGroups());
  }

  public boolean verifyDataColumnSidecar(final DataColumnSidecar dataColumnSidecar) {
    if (!(dataColumnSidecar instanceof DataColumnSidecarFulu dataColumnSidecarFulu)) {
      LOG.trace(
          "DataColumnSidecar is a {}, expected a Fulu data column sidecar",
          dataColumnSidecar.getClass().getSimpleName());
      return false;
    }
    final int numberOfColumns = specConfigFulu.getNumberOfColumns();
    final UInt64 epoch = computeEpochAtSlot(dataColumnSidecarFulu.getSlot());

    if (!dataColumnSidecarFulu.getIndex().isLessThan(numberOfColumns)) {
      LOG.trace(
          "DataColumnSidecar has invalid index {}. Should be less than {}",
          dataColumnSidecarFulu.getIndex(),
          numberOfColumns);
      return false;
    }
    if (dataColumnSidecarFulu.getKzgCommitments().isEmpty()) {
      LOG.trace("DataColumnSidecar has no kzg commitments");
      return false;
    }

    final int kzgCommitmentsSize = dataColumnSidecarFulu.getKzgCommitments().size();
    if (kzgCommitmentsSize > getBlobParameters(epoch).maxBlobsPerBlock()) {
      LOG.trace(
          "DataColumnSidecar has too many commitments when compared to the BPO for epoch {}",
          epoch);
      return false;
    }
    if (dataColumnSidecar.getColumn().size() != kzgCommitmentsSize) {
      LOG.trace(
          "DataColumnSidecar has unequal data column ({}) and kzg commitments ({}) sizes",
          dataColumnSidecar.getColumn().size(),
          kzgCommitmentsSize);
      return false;
    }
    if (dataColumnSidecar.getColumn().size() != dataColumnSidecar.getKzgProofs().size()) {
      LOG.trace(
          "DataColumnSidecar has unequal data column ({}) and kzg proofs ({}) sizes",
          dataColumnSidecar.getColumn().size(),
          dataColumnSidecar.getKzgProofs().size());
      return false;
    }
    return true;
  }

  public boolean verifyDataColumnSidecarKzgProofs(final DataColumnSidecar dataColumnSidecar) {
    return verifyDataColumnSidecarKzgProofs(
        dataColumnSidecar, DataColumnSidecarFulu.required(dataColumnSidecar).getKzgCommitments());
  }

  public boolean verifyDataColumnSidecarKzgProofs(
      final DataColumnSidecar dataColumnSidecar, final SszList<SszKZGCommitment> kzgCommitments) {
    final List<KZGCellWithColumnId> cellWithIds =
        IntStream.range(0, dataColumnSidecar.getColumn().size())
            .mapToObj(
                rowIndex ->
                    KZGCellWithColumnId.fromCellAndColumn(
                        new KZGCell(dataColumnSidecar.getColumn().get(rowIndex).getBytes()),
                        dataColumnSidecar.getIndex().intValue()))
            .collect(Collectors.toList());
    return getKzg()
        .verifyCellProofBatch(
            kzgCommitments.stream().map(SszKZGCommitment::getKZGCommitment).toList(),
            cellWithIds,
            dataColumnSidecar.getKzgProofs().stream().map(SszKZGProof::getKZGProof).toList());
  }

  public boolean verifyDataColumnSidecarKzgProofsBatch(
      final List<DataColumnSidecar> dataColumnSidecars) {

    final List<KZGCellWithColumnId> cellWithIds =
        dataColumnSidecars.stream()
            .flatMap(
                dataColumnSidecar ->
                    IntStream.range(0, dataColumnSidecar.getColumn().size())
                        .mapToObj(
                            rowIndex ->
                                KZGCellWithColumnId.fromCellAndColumn(
                                    new KZGCell(
                                        dataColumnSidecar.getColumn().get(rowIndex).getBytes()),
                                    dataColumnSidecar.getIndex().intValue())))
            .toList();
    return getKzg()
        .verifyCellProofBatch(
            dataColumnSidecars.stream()
                .flatMap(
                    sidecar -> DataColumnSidecarFulu.required(sidecar).getKzgCommitments().stream())
                .map(SszKZGCommitment::getKZGCommitment)
                .toList(),
            cellWithIds,
            dataColumnSidecars.stream()
                .flatMap(sidecar -> sidecar.getKzgProofs().stream())
                .map(SszKZGProof::getKZGProof)
                .toList());
  }

  public boolean verifyDataColumnSidecarInclusionProof(final DataColumnSidecar dataColumnSidecar) {
    final DataColumnSidecarFulu dataColumnSidecarFulu =
        DataColumnSidecarFulu.required(dataColumnSidecar);
    if (dataColumnSidecarFulu.getKzgCommitments().isEmpty()) {
      return false;
    }
    return predicates.isValidMerkleBranch(
        dataColumnSidecarFulu.getKzgCommitments().hashTreeRoot(),
        dataColumnSidecarFulu.getKzgCommitmentsInclusionProof(),
        specConfigFulu.getKzgCommitmentsInclusionProofDepth().intValue(),
        getBlockBodyKzgCommitmentsGeneralizedIndex(),
        dataColumnSidecarFulu.getBlockBodyRoot());
  }

  public int getBlockBodyKzgCommitmentsGeneralizedIndex() {
    return (int)
        BeaconBlockBodySchemaElectra.required(schemaDefinitionsFulu.getBeaconBlockBodySchema())
            .getBlobKzgCommitmentsGeneralizedIndex();
  }

  public List<Bytes32> computeDataColumnKzgCommitmentsInclusionProof(
      final BeaconBlockBody beaconBlockBody) {
    return MerkleUtil.constructMerkleProof(
        beaconBlockBody.getBackingNode(), getBlockBodyKzgCommitmentsGeneralizedIndex());
  }

  public List<DataColumnSidecar> constructDataColumnSidecars(
      final SignedBeaconBlock signedBeaconBlock,
      final List<BlobAndCellProofs> blobAndCellProofsList) {
    return constructDataColumnSidecars(
        signedBeaconBlock.getMessage(),
        signedBeaconBlock.asHeader(),
        computeExtendedMatrix(blobAndCellProofsList));
  }

  public List<DataColumnSidecar> constructDataColumnSidecars(
      final Optional<SignedBeaconBlockHeader> maybeSignedBeaconBlockHeader,
      final SlotAndBlockRoot slotAndBlockRoot,
      final Optional<SszList<SszKZGCommitment>> maybeSszKZGCommitments,
      final Optional<List<Bytes32>> maybeKzgCommitmentsInclusionProof,
      final List<BlobAndCellProofs> blobAndCellProofsList) {
    final List<List<MatrixEntry>> extendedMatrix = computeExtendedMatrix(blobAndCellProofsList);
    return constructDataColumnSidecarsInternal(
        builder ->
            builder
                .kzgCommitments(maybeSszKZGCommitments.orElseThrow())
                .signedBlockHeader(maybeSignedBeaconBlockHeader.orElseThrow())
                .kzgCommitmentsInclusionProof(maybeKzgCommitmentsInclusionProof.orElseThrow()),
        extendedMatrix);
  }

  public List<List<MatrixEntry>> computeExtendedMatrix(
      final List<BlobAndCellProofs> blobAndCellProofsList) {
    return IntStream.range(0, blobAndCellProofsList.size())
        .parallel()
        .mapToObj(
            blobIndex -> {
              final BlobAndCellProofs blobAndCellProofs = blobAndCellProofsList.get(blobIndex);
              final List<KZGCell> kzgCells =
                  getKzg().computeCells(blobAndCellProofs.blob().getBytes());
              final List<MatrixEntry> row = new ArrayList<>();
              for (int cellIndex = 0; cellIndex < kzgCells.size(); ++cellIndex) {
                row.add(
                    schemaDefinitionsFulu
                        .getMatrixEntrySchema()
                        .create(
                            kzgCells.get(cellIndex),
                            blobAndCellProofs.cellProofs().get(cellIndex),
                            cellIndex,
                            blobIndex));
              }
              return row;
            })
        .toList();
  }

  @VisibleForTesting
  public List<DataColumnSidecar> constructDataColumnSidecars(
      final BeaconBlock beaconBlock,
      final SignedBeaconBlockHeader signedBeaconBlockHeader,
      final List<List<MatrixEntry>> extendedMatrix) {
    if (extendedMatrix.isEmpty()) {
      return Collections.emptyList();
    }

    final SszList<SszKZGCommitment> sszKZGCommitments;
    final List<Bytes32> kzgCommitmentsInclusionProof;
    if (beaconBlock.isBlinded()) {
      final BlindedBeaconBlockBodyDeneb beaconBlockBody =
          BlindedBeaconBlockBodyDeneb.required(beaconBlock.getBody());
      sszKZGCommitments = beaconBlockBody.getBlobKzgCommitments();
      kzgCommitmentsInclusionProof = computeDataColumnKzgCommitmentsInclusionProof(beaconBlockBody);
    } else {
      final BeaconBlockBodyDeneb beaconBlockBody =
          BeaconBlockBodyDeneb.required(beaconBlock.getBody());
      sszKZGCommitments = beaconBlockBody.getBlobKzgCommitments();
      kzgCommitmentsInclusionProof = computeDataColumnKzgCommitmentsInclusionProof(beaconBlockBody);
    }

    return constructDataColumnSidecarsInternal(
        builder ->
            builder
                .kzgCommitments(sszKZGCommitments)
                .signedBlockHeader(signedBeaconBlockHeader)
                .kzgCommitmentsInclusionProof(kzgCommitmentsInclusionProof),
        extendedMatrix);
  }

  // get_data_column_sidecars
  protected List<DataColumnSidecar> constructDataColumnSidecarsInternal(
      final Consumer<DataColumnSidecarBuilder> dataColumnSidecarBuilderModifier,
      final List<List<MatrixEntry>> extendedMatrix) {
    if (extendedMatrix.isEmpty()) {
      return Collections.emptyList();
    }

    final DataColumnSchema dataColumnSchema = schemaDefinitionsFulu.getDataColumnSchema();
    final DataColumnSidecarSchema<?> dataColumnSidecarSchema =
        schemaDefinitionsFulu.getDataColumnSidecarSchema();
    final SszListSchema<SszKZGProof, ?> kzgProofsSchema =
        dataColumnSidecarSchema.getKzgProofsSchema();

    final int columnCount = extendedMatrix.getFirst().size();

    return IntStream.range(0, columnCount)
        .mapToObj(
            cellID -> {
              List<MatrixEntry> columnData =
                  extendedMatrix.stream().map(row -> row.get(cellID)).toList();
              List<Cell> columnCells = columnData.stream().map(MatrixEntry::getCell).toList();

              SszList<SszKZGProof> columnProofs =
                  columnData.stream()
                      .map(MatrixEntry::getKzgProof)
                      .map(SszKZGProof::new)
                      .collect(kzgProofsSchema.collector());
              final DataColumn dataColumn = dataColumnSchema.create(columnCells);

              return dataColumnSidecarSchema.create(
                  builder -> {
                    builder
                        .index(UInt64.valueOf(cellID))
                        .column(dataColumn)
                        .kzgProofs(columnProofs);
                    dataColumnSidecarBuilderModifier.accept(builder);
                  });
            })
        .toList();
  }

  public List<DataColumnSidecar> reconstructAllDataColumnSidecars(
      final Collection<DataColumnSidecar> existingSidecars) {
    if (existingSidecars.size() < (specConfigFulu.getNumberOfColumns() / 2)) {
      final Optional<DataColumnSidecar> maybeSidecar = existingSidecars.stream().findAny();
      throw new IllegalArgumentException(
          String.format(
              "Number of sidecars must be greater than or equal to the half of column count, slot: %s; columns: found %s, needed at least %d",
              maybeSidecar.isPresent() ? maybeSidecar.get().getSlot().toString() : "unknown",
              existingSidecars.size(),
              specConfigFulu.getNumberOfColumns() / 2));
    }
    final List<List<MatrixEntry>> columnBlobEntries =
        existingSidecars.stream()
            .sorted(Comparator.comparing(DataColumnSidecar::getIndex))
            .map(
                sidecar ->
                    IntStream.range(0, sidecar.getColumn().size())
                        .mapToObj(
                            rowIndex ->
                                schemaDefinitionsFulu
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
    final DataColumnSidecarFulu anyExistingSidecar =
        DataColumnSidecarFulu.required(existingSidecars.stream().findFirst().orElseThrow());
    return constructDataColumnSidecarsInternal(
        builder ->
            builder
                .kzgCommitments(anyExistingSidecar.getKzgCommitments())
                .signedBlockHeader(anyExistingSidecar.getSignedBlockHeader())
                .kzgCommitmentsInclusionProof(
                    anyExistingSidecar.getKzgCommitmentsInclusionProof().asListUnboxed()),
        extendedMatrix);
  }

  /**
   * Return the recovered extended matrix.
   *
   * <p>This helper demonstrates how to apply ``recover_cells_and_kzg_proofs``.
   *
   * <p>The data structure for storing cells is implementation-dependent.
   */
  public List<List<MatrixEntry>> recoverMatrix(final List<List<MatrixEntry>> partialMatrix) {
    return IntStream.range(0, partialMatrix.size())
        .parallel()
        .mapToObj(
            blobIndex -> {
              final List<KZGCellWithColumnId> cellWithColumnIds =
                  partialMatrix.get(blobIndex).stream()
                      .filter(entry -> entry.getRowIndex().intValue() == blobIndex)
                      .map(
                          entry ->
                              new KZGCellWithColumnId(
                                  new KZGCell(entry.getCell().getBytes()),
                                  new KZGCellID(entry.getColumnIndex())))
                      .toList();
              final List<KZGCellAndProof> kzgCellAndProofs =
                  getKzg().recoverCellsAndProofs(cellWithColumnIds);
              return IntStream.range(0, kzgCellAndProofs.size())
                  .mapToObj(
                      kzgCellAndProofIndex ->
                          schemaDefinitionsFulu
                              .getMatrixEntrySchema()
                              .create(
                                  kzgCellAndProofs.get(kzgCellAndProofIndex).cell(),
                                  kzgCellAndProofs.get(kzgCellAndProofIndex).proof(),
                                  kzgCellAndProofIndex,
                                  blobIndex))
                  .toList();
            })
        .toList();
  }

  public int getSamplingGroupCount(final int custodyRequirement) {
    return Math.max(custodyRequirement, specConfigFulu.getSamplesPerSlot());
  }

  public static <T> List<List<T>> transpose(final List<List<T>> matrix) {
    final int rowCount = matrix.size();
    final int colCount = matrix.getFirst().size();
    final List<List<T>> ret =
        Stream.generate(() -> (List<T>) new ArrayList<T>(rowCount)).limit(colCount).toList();

    for (int row = 0; row < rowCount; row++) {
      if (matrix.get(row).size() != colCount) {
        throw new IllegalArgumentException("Different number columns in the matrix");
      }
      for (int col = 0; col < colCount; col++) {
        final T val = matrix.get(row).get(col);
        ret.get(col).add(row, val);
      }
    }
    return ret;
  }

  @Override
  public int getMaxRequestDataColumnSidecars() {
    return specConfigFulu.getMaxRequestDataColumnSidecars();
  }

  @Override
  public boolean isAvailabilityOfBlobSidecarsRequiredAtEpoch(
      final UInt64 currentEpoch, final UInt64 epoch) {
    return !epoch.isGreaterThanOrEqualTo(specConfigFulu.getFuluForkEpoch());
  }

  public boolean isAvailabilityOfDataColumnSidecarsRequiredAtEpoch(
      final UInt64 currentEpoch, final UInt64 epoch) {
    return epoch.isGreaterThanOrEqualTo(computeDataColumnSidecarRetentionStartEpoch(currentEpoch));
  }

  /**
   * The first epoch of the data column sidecar retention window at {@code currentEpoch}, without
   * the {@code FULU_FORK_EPOCH} floor.
   */
  public UInt64 computeDataColumnSidecarRetentionStartEpoch(final UInt64 currentEpoch) {
    return currentEpoch.minusMinZero(specConfigFulu.getMinEpochsForDataColumnSidecarsRequests());
  }

  // compute_proposer_indices
  @Override
  public List<Integer> computeProposerIndices(
      final BeaconState state,
      final UInt64 epoch,
      final Bytes32 epochSeed,
      final IntList activeValidatorIndices) {
    final UInt64 startSlot = computeStartSlotAtEpoch(epoch);
    return IntStream.range(0, specConfigFulu.getSlotsPerEpoch())
        .mapToObj(
            i -> {
              final Bytes32 seed =
                  Hash.sha256(
                      Bytes.concat(
                          epochSeed.toArray(), uint64ToBytes(startSlot.plus(i)).toArray()));
              return computeProposerIndex(state, activeValidatorIndices, seed);
            })
        .toList();
  }

  // initialize_proposer_lookahead
  public List<UInt64> initializeProposerLookahead(
      final BeaconStateElectra state, final BeaconStateAccessorsFulu beaconAccessors) {
    final UInt64 currentEpoch = computeEpochAtSlot(state.getSlot());
    return IntStream.rangeClosed(0, specConfigFulu.getMinSeedLookahead())
        .flatMap(
            i ->
                beaconAccessors.getBeaconProposerIndices(state, currentEpoch.plus(i)).stream()
                    .mapToInt(Integer::intValue))
        .mapToObj(UInt64::valueOf)
        .toList();
  }
}
