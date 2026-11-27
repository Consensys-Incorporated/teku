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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static tech.pegasys.teku.infrastructure.unsigned.UInt64.ZERO;

import com.google.common.io.Resources;
import it.unimi.dsi.fastutil.ints.IntList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.LongStream;
import java.util.stream.Stream;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import tech.pegasys.teku.bls.BLSSignature;
import tech.pegasys.teku.infrastructure.bytes.Bytes4;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.kzg.KZG;
import tech.pegasys.teku.kzg.KZGCommitment;
import tech.pegasys.teku.kzg.KZGProof;
import tech.pegasys.teku.kzg.trusted_setups.TrustedSetupLoader;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.SpecMilestone;
import tech.pegasys.teku.spec.TestSpecFactory;
import tech.pegasys.teku.spec.config.BlobScheduleEntry;
import tech.pegasys.teku.spec.config.SpecConfig;
import tech.pegasys.teku.spec.config.SpecConfigFulu;
import tech.pegasys.teku.spec.datastructures.blobs.DataColumnSidecar;
import tech.pegasys.teku.spec.datastructures.blobs.DataColumnSidecarSchema;
import tech.pegasys.teku.spec.datastructures.blobs.versions.fulu.DataColumn;
import tech.pegasys.teku.spec.datastructures.blobs.versions.fulu.DataColumnSidecarFulu;
import tech.pegasys.teku.spec.datastructures.blocks.BeaconBlockHeader;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBeaconBlock;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBeaconBlockHeader;
import tech.pegasys.teku.spec.datastructures.execution.BlobAndCellProofs;
import tech.pegasys.teku.spec.datastructures.state.BeaconStateTestBuilder;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.BeaconState;
import tech.pegasys.teku.spec.logic.common.statetransition.availability.AvailabilityCheckerFactory;
import tech.pegasys.teku.spec.logic.versions.electra.helpers.PredicatesElectra;
import tech.pegasys.teku.spec.schemas.SchemaDefinitionsFulu;
import tech.pegasys.teku.spec.util.DataStructureUtil;
import tech.pegasys.teku.spec.util.KzgUtil;

public class MiscHelpersFuluTest {

  private static final Spec SPEC =
      TestSpecFactory.createMinimalFulu(
          builder ->
              builder.fuluBuilder(
                  fuluBuilder ->
                      fuluBuilder
                          .cellsPerExtBlob(128)
                          .numberOfColumns(128)
                          .numberOfCustodyGroups(128)
                          .custodyRequirement(4)
                          .validatorCustodyRequirement(8)
                          .balancePerAdditionalCustodyGroup(UInt64.valueOf(32000000000L))
                          .samplesPerSlot(16)));

  static {
    // Initialize KZG and reinitialize spec with real KZG
    final KZG kzg = KZG.getInstance(false);
    TrustedSetupLoader.loadTrustedSetupForTests(kzg);
    SPEC.reinitializeForTesting(
        AvailabilityCheckerFactory.NOOP_BLOB_SIDECAR,
        AvailabilityCheckerFactory.NOOP_DATACOLUMN_SIDECAR,
        kzg);
  }

  private final SpecConfig specConfig = SPEC.atSlot(ZERO).getConfig();
  private final SchemaDefinitionsFulu schemaDefinitionsFulu =
      SchemaDefinitionsFulu.required(SPEC.getGenesisSchemaDefinitions());
  private final SpecConfigFulu specConfigFulu =
      SpecConfigFulu.required(SPEC.getGenesisSpecConfig());
  private final MiscHelpersFulu miscHelpersFulu =
      MiscHelpersFulu.required(SPEC.forMilestone(SpecMilestone.FULU).miscHelpers());

  // Shared test data for reconstructAllDataColumnSidecars tests
  private static List<DataColumnSidecar> sharedOriginalSidecars;
  private static SignedBeaconBlock sharedSignedBeaconBlock;

  @ParameterizedTest
  @MethodSource("getComputeForkDigestFuluScenarios")
  public void computeForkDigestFuluTest(
      final Spec spec, final long epoch, final String expectedValue) {
    assertThat(
            MiscHelpersFulu.required(spec.atEpoch(UInt64.valueOf(epoch)).miscHelpers())
                .computeForkDigest(Bytes32.ZERO, UInt64.valueOf(epoch)))
        .isEqualTo(Bytes4.fromHexString(expectedValue));
  }

  @Test
  public void shouldRejectDataColumnSideCarWhenIndexTooBig() {
    final int numberOfColumns = SPEC.getNumberOfDataColumns().orElseThrow();
    final DataColumnSidecar invalidIndex =
        dataStructureUtil.randomDataColumnSidecar(
            dataStructureUtil.randomSignedBeaconBlockHeader(),
            UInt64.valueOf(numberOfColumns).increment());
    assertThat(miscHelpersFulu.verifyDataColumnSidecar(invalidIndex)).isFalse();
  }

  @Test
  public void shouldRejectIfKzgCommitmentListIsGreaterThanNumberOfBlobs() {
    final UInt64 epoch = miscHelpersFulu.computeEpochAtSlot(UInt64.ONE);
    final int maxBlobsPerBlock = miscHelpersFulu.getBlobParameters(epoch).maxBlobsPerBlock();

    final List<KZGProof> kzgProofs = dataStructureUtil.randomKZGProofs(maxBlobsPerBlock + 1);
    final List<KZGCommitment> kzgCommitments =
        dataStructureUtil.randomKZGCommitments(maxBlobsPerBlock + 1);
    final DataColumn dataColumn =
        dataStructureUtil.randomDataColumn(UInt64.ONE, maxBlobsPerBlock + 1);

    final DataColumnSidecar invalidDataColumnKzgProofs =
        dataStructureUtil.randomDataColumnSidecar(kzgProofs, kzgCommitments, dataColumn);
    assertThat(miscHelpersFulu.verifyDataColumnSidecar(invalidDataColumnKzgProofs)).isFalse();
  }

  @Test
  public void shouldRejectIfDataColumnAndKzgCommitmentsMismatch() {
    final DataColumnSidecar mismatchingDataColumnKzgCommitments =
        dataStructureUtil.randomDataColumnSidecar(
            dataStructureUtil.randomKZGCommitments(10),
            dataStructureUtil.randomDataColumn(UInt64.ONE, 5));
    assertThat(miscHelpersFulu.verifyDataColumnSidecar(mismatchingDataColumnKzgCommitments))
        .isFalse();
  }

  @Test
  void shouldRejectIfDataColumnSidecarHasNoKzgCommitments() {
    final DataColumnSidecar emptyKzgCommitments =
        dataStructureUtil.randomDataColumnSidecar(
            dataStructureUtil.randomSignedBeaconBlockWithCommitments(0), UInt64.ONE);
    assertThat(miscHelpersFulu.verifyDataColumnSidecar(emptyKzgCommitments)).isFalse();
  }

  @Test
  public void verifyDataColumnSidecar_shouldRejectSidecarOfAnotherFork() {
    final DataStructureUtil gloasData = new DataStructureUtil(TestSpecFactory.createMinimalGloas());
    final DataColumnSidecar gloasSidecar =
        gloasData.randomDataColumnSidecar(
            gloasData.randomSignedBeaconBlockWithCommitments(UInt64.ONE, 3), UInt64.ZERO);

    assertThat(miscHelpersFulu.verifyDataColumnSidecar(gloasSidecar)).isFalse();
  }

  @Test
  public void shouldRejectIfDataColumnAndKzgProofsMismatch() {
    final DataColumnSidecar invalidDataColumnKzgProofs =
        dataStructureUtil.randomDataColumnSidecar(
            dataStructureUtil.randomKZGProofs(10),
            dataStructureUtil.randomKZGCommitments(5),
            dataStructureUtil.randomDataColumn(UInt64.ONE, 5));
    assertThat(miscHelpersFulu.verifyDataColumnSidecar(invalidDataColumnKzgProofs)).isFalse();
  }

  // Scenarios from
  // https://github.com/ethereum/consensus-specs/blob/master/tests/core/pyspec/eth2spec/test/fulu/validator/test_compute_fork_digest.py
  public static Stream<Arguments> getComputeForkDigestFuluScenarios() {
    final Spec spec =
        TestSpecFactory.createMinimalFulu(
            b ->
                b.electraForkEpoch(UInt64.valueOf(9))
                    .fuluForkEpoch(UInt64.valueOf(100))
                    .electraBuilder(eb -> eb.maxBlobsPerBlockElectra(9))
                    .fuluBuilder(
                        fb ->
                            fb.blobSchedule(
                                List.of(
                                    new BlobScheduleEntry(UInt64.valueOf(100), 100),
                                    new BlobScheduleEntry(UInt64.valueOf(150), 175),
                                    new BlobScheduleEntry(UInt64.valueOf(200), 200),
                                    new BlobScheduleEntry(UInt64.valueOf(250), 275),
                                    new BlobScheduleEntry(UInt64.valueOf(300), 300)))));

    return Stream.of(
        Arguments.of(spec, 100, "44a571e8"),
        Arguments.of(spec, 101, "44a571e8"),
        Arguments.of(spec, 150, "1171afca"),
        Arguments.of(spec, 200, "427a30ab"),
        Arguments.of(spec, 250, "d5310ef1"),
        Arguments.of(spec, 299, "d5310ef1"),
        Arguments.of(spec, 300, "51d229f7"));
  }

  @Test
  public void emptyInclusionProof_shouldFailValidation() {
    final PredicatesElectra predicatesMock = mock(PredicatesElectra.class);
    when(predicatesMock.toVersionElectra()).thenReturn(Optional.of(predicatesMock));
    when(predicatesMock.isValidMerkleBranch(any(), any(), anyInt(), anyInt(), any()))
        .thenReturn(true);
    final MiscHelpersFulu miscHelpersFuluWithMockPredicates =
        new MiscHelpersFulu(specConfigFulu, predicatesMock, schemaDefinitionsFulu);
    final DataStructureUtil dataStructureUtil = new DataStructureUtil(SPEC);
    final DataColumnSidecarSchema<?> dataColumnSidecarSchema =
        SchemaDefinitionsFulu.required(schemaDefinitionsFulu).getDataColumnSidecarSchema();
    final DataColumnSidecar dataColumnSidecar =
        dataColumnSidecarSchema.create(
            builder ->
                builder
                    .index(ZERO)
                    .column(
                        SchemaDefinitionsFulu.required(schemaDefinitionsFulu)
                            .getDataColumnSchema()
                            .create(List.of()))
                    .kzgCommitments(dataColumnSidecarSchema.getKzgCommitmentsSchema().of())
                    .kzgProofs(dataColumnSidecarSchema.getKzgProofsSchema().of())
                    .signedBlockHeader(dataStructureUtil.randomSignedBeaconBlockHeader())
                    .kzgCommitmentsInclusionProof(
                        List.of(
                            dataStructureUtil.randomBytes32(),
                            dataStructureUtil.randomBytes32(),
                            dataStructureUtil.randomBytes32(),
                            dataStructureUtil.randomBytes32())));

    final DataColumnSidecarFulu dataColumnSideCarFulu =
        DataColumnSidecarFulu.required(dataColumnSidecar);
    assertThat(
            predicatesMock.isValidMerkleBranch(
                dataColumnSideCarFulu.getKzgCommitments().hashTreeRoot(),
                dataColumnSideCarFulu.getKzgCommitmentsInclusionProof(),
                specConfigFulu.getKzgCommitmentsInclusionProofDepth().intValue(),
                miscHelpersFuluWithMockPredicates.getBlockBodyKzgCommitmentsGeneralizedIndex(),
                dataColumnSideCarFulu.getBlockBodyRoot()))
        .isTrue();
    assertThat(
            miscHelpersFuluWithMockPredicates.verifyDataColumnSidecarInclusionProof(
                dataColumnSidecar))
        .isFalse();
  }

  @Test
  public void emptyInclusionProofFromRealNetwork_shouldFailValidation() {
    final Spec specMainnet = TestSpecFactory.createMainnetFulu();
    final PredicatesElectra predicatesMainnet =
        new PredicatesElectra(specMainnet.getGenesisSpecConfig());
    final SchemaDefinitionsFulu schemaDefinitionsFuluMainnet =
        SchemaDefinitionsFulu.required(specMainnet.getGenesisSchemaDefinitions());
    final SpecConfigFulu specConfigFuluMainnet =
        specMainnet.getGenesisSpecConfig().toVersionFulu().orElseThrow();
    final MiscHelpersFulu miscHelpersFuluMainnet =
        new MiscHelpersFulu(specConfigFuluMainnet, predicatesMainnet, schemaDefinitionsFuluMainnet);
    final DataColumnSidecarSchema<?> dataColumnSidecarSchema =
        SchemaDefinitionsFulu.required(schemaDefinitionsFulu).getDataColumnSidecarSchema();
    final DataColumnSidecar dataColumnSidecar =
        dataColumnSidecarSchema.create(
            builder ->
                builder
                    .index(ZERO)
                    .column(
                        SchemaDefinitionsFulu.required(schemaDefinitionsFulu)
                            .getDataColumnSchema()
                            .create(List.of()))
                    .kzgCommitments(dataColumnSidecarSchema.getKzgCommitmentsSchema().of())
                    .kzgProofs(dataColumnSidecarSchema.getKzgProofsSchema().of())
                    .signedBlockHeader(
                        new SignedBeaconBlockHeader(
                            new BeaconBlockHeader(
                                UInt64.valueOf(37),
                                UInt64.valueOf(3426),
                                Bytes32.fromHexString(
                                    "0x6d3091dae0e2a0251cc2c0d9fef846e1c6e685f18fc8a2c7734f25750c22da36"),
                                Bytes32.fromHexString(
                                    "0x715f24108254c3fcbef60c739fe702aed3ee692cb223c884b3db6e041c56c2a6"),
                                Bytes32.fromHexString(
                                    "0xbea87258cde49915c8c929b6b91fbbcde004aeaaa08a3ccdc3248dc62b0e682f")),
                            BLSSignature.fromBytesCompressed(
                                Bytes.fromHexString(
                                    "0xb4c313365edbc7cfa9319c54ecba0a8dc54c8537752c72a86c762eb0a81b3ad1eda43f0f3b19a9c9523a6a42450c1d070556e0a443d4733922765764ef5850b41d20b4f6af6cc93a70eb1023cc63473f111de772315a2726406be9dc6cb24e67"))))
                    .kzgCommitmentsInclusionProof(
                        List.of(
                            Bytes32.fromHexString(
                                "0x792930bbd5baac43bcc798ee49aa8185ef76bb3b44ba62b91d86ae569e4bb535"),
                            Bytes32.fromHexString(
                                "0xcd581849371d5f91b7d02a366b23402397007b50180069584f2bd4e14397540b"),
                            Bytes32.fromHexString(
                                "0xdb56114e00fdd4c1f85c892bf35ac9a89289aaecb1ebd0a96cde606a748b5d71"),
                            Bytes32.fromHexString(
                                "0x9535c3eb42aaf182b13b18aacbcbc1df6593ecafd0bf7d5e94fb727b2dc1f265"))));
    assertThat(miscHelpersFuluMainnet.verifyDataColumnSidecarInclusionProof(dataColumnSidecar))
        .isFalse();
  }

  private final DataStructureUtil dataStructureUtil = new DataStructureUtil(SPEC);

  @BeforeAll
  static void setUpSharedTestData() {
    final DataStructureUtil dataStructureUtil = new DataStructureUtil(SPEC);
    final MiscHelpersFulu miscHelpersFulu =
        MiscHelpersFulu.required(SPEC.forMilestone(SpecMilestone.FULU).miscHelpers());

    // Create test data once for all tests
    final List<BlobAndCellProofs> blobsAndCellProofs =
        IntStream.range(0, 4)
            .mapToObj(
                __ -> {
                  return KzgUtil.computeBlobAndCellProofs(
                      miscHelpersFulu.getKzg(), dataStructureUtil.randomValidBlob());
                })
            .toList();

    sharedSignedBeaconBlock =
        dataStructureUtil.randomSignedBeaconBlockWithCommitments(blobsAndCellProofs.size());

    sharedOriginalSidecars =
        miscHelpersFulu.constructDataColumnSidecars(
            sharedSignedBeaconBlock.getMessage(),
            sharedSignedBeaconBlock.asHeader(),
            miscHelpersFulu.computeExtendedMatrix(blobsAndCellProofs));
  }

  @ParameterizedTest(name = "{0} validator custody groups required")
  @MethodSource("getValidatorCustodyRequirementFixtures")
  public void testGetValidatorCustodyRequirement(
      final int expectedValidatorCustodyCount, final long[] validatorBalancesEth) {
    BeaconStateTestBuilder beaconStateTestBuilder =
        new BeaconStateTestBuilder(dataStructureUtil)
            .forkVersion(specConfig.getGenesisForkVersion());

    LongStream.of(validatorBalancesEth)
        .mapToObj(balance -> UInt64.valueOf(balance).times(1_000_000_000L))
        .forEach(beaconStateTestBuilder::activeConsolidatingValidator);
    BeaconState state = beaconStateTestBuilder.build();

    final Set<UInt64> validatorIndicesSet =
        IntStream.range(0, validatorBalancesEth.length)
            .mapToObj(UInt64::valueOf)
            .collect(Collectors.toSet());
    assertEquals(
        UInt64.valueOf(expectedValidatorCustodyCount),
        miscHelpersFulu.getValidatorsCustodyRequirement(state, validatorIndicesSet));
  }

  @Test
  public void computeProposerIndices_returnsListWithSlotsPerEpochSize() {
    final DataStructureUtil dataStructureUtil = new DataStructureUtil(SPEC);
    final BeaconState state = dataStructureUtil.randomBeaconState();
    final UInt64 epoch = UInt64.ONE;
    final Bytes32 epochSeed = Bytes32.random();
    final int slotsPerEpoch = SPEC.getGenesisSpecConfig().getSlotsPerEpoch();
    final List<Integer> activeValidatorIndices = IntStream.range(0, 10).boxed().toList();

    final IntList activeValidatorIndicesIntList =
        IntList.of(activeValidatorIndices.stream().mapToInt(Integer::intValue).toArray());

    List<Integer> proposerIndices =
        miscHelpersFulu.computeProposerIndices(
            state, epoch, epochSeed, activeValidatorIndicesIntList);

    assertThat(proposerIndices).hasSize(slotsPerEpoch);
  }

  @Test
  public void verifyKzgProofExampleFromDevnet() throws Exception {
    final byte[] sidecarSsz =
        Resources.toByteArray(Resources.getResource(MiscHelpersFuluTest.class, "sidecar.ssz"));
    assertThat(
            miscHelpersFulu.verifyDataColumnSidecarKzgProofs(
                schemaDefinitionsFulu
                    .getDataColumnSidecarSchema()
                    .sszDeserialize(Bytes.wrap(sidecarSsz))))
        .isTrue();
  }

  static Stream<Arguments> getValidatorCustodyRequirementFixtures() {
    return Stream.of(
        // expectedValidatorCustodyCount, validatorBalancesEth
        Arguments.of(8, new long[] {31}),
        Arguments.of(8, new long[] {32}),
        Arguments.of(8, new long[] {32, 32, 33, 34, 33, 32}),
        Arguments.of(15, new long[] {32, 32, 32, 32, 32, 32, 32, 32, 32, 32, 32, 32, 32, 32, 32}),
        Arguments.of(9, new long[] {48, 48, 48, 48, 48, 48}),
        Arguments.of(8, new long[] {48, 48}),
        Arguments.of(128, new long[] {32, 48, 1024, 2048, 1024}),
        Arguments.of(128, new long[] {2048, 2048}));
  }

  @Test
  public void reconstructAllDataColumnSidecars_withSufficientSidecars_shouldReconstructAll() {
    // Test with more than half of the sidecars (should succeed)
    final int halfSize = sharedOriginalSidecars.size() / 2;
    final List<DataColumnSidecar> partialSidecars =
        sharedOriginalSidecars.subList(halfSize - 1, sharedOriginalSidecars.size());

    final List<DataColumnSidecar> reconstructedSidecars =
        miscHelpersFulu.reconstructAllDataColumnSidecars(partialSidecars);

    // Verify that all sidecars were reconstructed
    assertThat(reconstructedSidecars).isEqualTo(sharedOriginalSidecars);
  }

  @Test
  public void reconstructAllDataColumnSidecars_withInsufficientSidecars_shouldThrowException() {
    // Test with less than half of the sidecars (should fail)
    final int halfSize = sharedOriginalSidecars.size() / 2;
    final List<DataColumnSidecar> insufficientSidecars =
        sharedOriginalSidecars.subList(0, halfSize - 1);

    assertThatThrownBy(() -> miscHelpersFulu.reconstructAllDataColumnSidecars(insufficientSidecars))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining(
            "Number of sidecars must be greater than or equal to the half of column count");
  }

  @Test
  public void reconstructAllDataColumnSidecars_withEmptyCollection_shouldThrowException() {
    assertThatThrownBy(() -> miscHelpersFulu.reconstructAllDataColumnSidecars(List.of()))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining(
            "Number of sidecars must be greater than or equal to the half of column count");
  }

  @Test
  public void reconstructAllDataColumnSidecars_withExactlyHalfSidecars_shouldSucceed() {
    // Test with exactly half of the sidecars (should succeed)
    final int halfSize = sharedOriginalSidecars.size() / 2;
    final List<DataColumnSidecar> halfSidecars =
        sharedOriginalSidecars.subList(halfSize, sharedOriginalSidecars.size());

    final List<DataColumnSidecar> reconstructedSidecars =
        miscHelpersFulu.reconstructAllDataColumnSidecars(halfSidecars);

    // Verify that all sidecars were reconstructed
    assertThat(reconstructedSidecars).isEqualTo(sharedOriginalSidecars);
  }

  @Test
  public void reconstructAllDataColumnSidecars_preservesSidecarOrdering() {
    // Test with more than half of the sidecars
    final int halfSize = sharedOriginalSidecars.size() / 2;
    final List<DataColumnSidecar> partialSidecars =
        sharedOriginalSidecars.subList(halfSize - 1, sharedOriginalSidecars.size());

    final List<DataColumnSidecar> reconstructedSidecars =
        miscHelpersFulu.reconstructAllDataColumnSidecars(partialSidecars);

    // Verify that sidecars are ordered by index
    for (int i = 0; i < reconstructedSidecars.size(); i++) {
      assertThat(reconstructedSidecars.get(i).getIndex().intValue()).isEqualTo(i);
    }
  }

  @Test
  public void reconstructAllDataColumnSidecars_withAllSidecars_shouldSucceed() {
    // Test with all sidecars (should succeed)
    final List<DataColumnSidecar> reconstructedSidecars =
        miscHelpersFulu.reconstructAllDataColumnSidecars(sharedOriginalSidecars);

    // Verify that all sidecars were reconstructed
    assertThat(reconstructedSidecars).isEqualTo(sharedOriginalSidecars);
  }
}
