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

package tech.pegasys.teku.statetransition.validation.signatures;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static tech.pegasys.teku.infrastructure.logging.Converter.gweiToEth;
import static tech.pegasys.teku.statetransition.attestation.AggregatingAttestationPool.DEFAULT_MAXIMUM_ATTESTATION_COUNT;

import java.io.FileInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import org.hyperledger.besu.metrics.noop.NoOpMetricsSystem;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Level;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.Warmup;
import org.openjdk.jmh.infra.Blackhole;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.SpecMilestone;
import tech.pegasys.teku.spec.TestSpecFactory;
import tech.pegasys.teku.spec.datastructures.attestation.ValidatableAttestation;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBeaconBlock;
import tech.pegasys.teku.spec.datastructures.operations.Attestation;
import tech.pegasys.teku.spec.datastructures.operations.SingleAttestation;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.BeaconState;
import tech.pegasys.teku.spec.logic.common.util.BlockRewardCalculatorUtil;
import tech.pegasys.teku.spec.logic.common.util.BlockRewardCalculatorUtil.BlockRewardData;
import tech.pegasys.teku.spec.logic.versions.electra.util.AttestationUtilElectra;
import tech.pegasys.teku.spec.schemas.SchemaDefinitionsElectra;
import tech.pegasys.teku.spec.util.DataStructureUtil;
import tech.pegasys.teku.statetransition.attestation.AggregatingAttestationPool;
import tech.pegasys.teku.statetransition.attestation.AggregatingAttestationPoolV2;
import tech.pegasys.teku.statetransition.attestation.AttestationForkChecker;
import tech.pegasys.teku.statetransition.attestation.PooledAttestation;
import tech.pegasys.teku.statetransition.attestation.PooledAttestationWithData;
import tech.pegasys.teku.statetransition.attestation.utils.AggregatingAttestationPoolProfiler;
import tech.pegasys.teku.statetransition.attestation.utils.RewardBasedAttestationSorter;
import tech.pegasys.teku.storage.client.RecentChainData;

@Warmup(iterations = 5, time = 2000, timeUnit = TimeUnit.MILLISECONDS)
@Measurement(iterations = 10, time = 2000, timeUnit = TimeUnit.MILLISECONDS)
@Fork(1)
@State(Scope.Thread)
public class AggregatingAttestationPoolBenchmark {
  private static final Spec SPEC = TestSpecFactory.createMainnetElectra();

  // pool dump can be created via something similar to
  // https://github.com/tbenr/teku/commit/08ab0e3ac6e71a9d6340ae30757c63f922f2c968
  // state and actual block can be obtained the usual ways

  // dumps files in https://drive.google.com/drive/folders/11vGCDsZej2nap_3JT6Z6NqyxL3rnJb3y

  // a reference file can be obtained here
  // https://drive.google.com/file/d/139bA7r88riFODZ7S0FpvtO7hmWmdC_XC/view?usp=drive_link
  private static final String STATE_PATH = "parentBlockState_3816770.ssz";

  // a reference file can be obtained here
  // https://drive.google.com/file/d/1I5vXK-x8ZH9wh40wNf1oACXeF_U3to8J/view?usp=drive_link
  private static final String POOL_DUMP_PATH = "attestations_3816773.multi_ssz";
  private static final UInt64 SLOT = UInt64.valueOf(3816773);

  // a reference file can be obtained here
  // https://drive.google.com/file/d/1PN0OToyNOV0SyjeQaS7oF3J4cKbmy1nX/view?usp=drive_link
  private static final Optional<String> ACTUAL_BLOCK_PATH =
      Optional.of(
          "block-3816773-456c9819a4c1e792ba8b1f71119628aed2a4d1e0d2c66199fbc763832937421b.ssz");

  record AttestationDataRootAndCommitteeIndex(Bytes32 attestationDataRoot, UInt64 committeeIndex) {}

  private final List<ValidatableAttestation> attestations = new ArrayList<>();
  private final List<PooledAttestationWithData> pooledAttestations = new ArrayList<>();

  private BeaconState state;
  private BeaconState newBlockState;
  private AggregatingAttestationPool pool;
  private RecentChainData recentChainData;
  private AttestationForkChecker attestationForkChecker;
  private AttestationDataRootAndCommitteeIndex mostFrequentSingleAttestationDataRootAndCI;
  private RewardBasedAttestationSorter sorter;

  @Setup(Level.Trial)
  public void init() throws Exception {

    final Map<AttestationDataRootAndCommitteeIndex, Integer> singleAttCounterByDataAndCommittee =
        new HashMap<>();

    this.recentChainData = mock(RecentChainData.class);
    this.pool =
        new AggregatingAttestationPoolV2(
            SPEC,
            recentChainData,
            new NoOpMetricsSystem(),
            DEFAULT_MAXIMUM_ATTESTATION_COUNT,
            AggregatingAttestationPoolProfiler.NOOP,
            10_000,
            10_000);

    try (final FileInputStream fileInputStream = new FileInputStream(STATE_PATH)) {
      this.state =
          SPEC.getGenesisSpec()
              .getSchemaDefinitions()
              .getBeaconStateSchema()
              .sszDeserialize(Bytes.wrap(fileInputStream.readAllBytes()));
    }

    when(recentChainData.getCurrentEpoch()).thenReturn(Optional.of(SPEC.getCurrentEpoch(state)));

    this.attestationForkChecker = new AttestationForkChecker(SPEC, state);

    final long[] singleAttestationCount = {0};
    final long[] aggregatedAttestationCount = {0};

    var attestationSchema = SPEC.getGenesisSpec().getSchemaDefinitions().getAttestationSchema();
    var singleAttestationSchema =
        SPEC.getGenesisSpec()
            .getSchemaDefinitions()
            .toVersionElectra()
            .map(SchemaDefinitionsElectra::getSingleAttestationSchema);
    var attestationUtil =
        (AttestationUtilElectra) SPEC.forMilestone(SpecMilestone.ELECTRA).getAttestationUtil();

    try (final Stream<String> attestationLinesStream = Files.lines(Paths.get(POOL_DUMP_PATH))) {
      attestationLinesStream
          .map(
              line -> {
                try {
                  aggregatedAttestationCount[0]++;
                  return attestationSchema.sszDeserialize(Bytes.fromHexString(line));
                } catch (Exception e) {
                  aggregatedAttestationCount[0]--;
                  singleAttestationCount[0]++;
                  return singleAttestationSchema
                      .orElseThrow()
                      .sszDeserialize(Bytes.fromHexString(line));
                }
              })
          .map(
              attestation -> {
                var validatableAttestation = ValidatableAttestation.from(SPEC, attestation);

                if (attestation.isSingleAttestation()) {
                  final Attestation convertedAttestation =
                      attestationUtil.convertSingleAttestationToAggregated(
                          state, (SingleAttestation) attestation);

                  validatableAttestation.convertToAggregatedFormatFromSingleAttestation(
                      convertedAttestation);

                  singleAttCounterByDataAndCommittee.compute(
                      new AttestationDataRootAndCommitteeIndex(
                          attestation.getData().hashTreeRoot(),
                          attestation.getFirstCommitteeIndex()),
                      (k, v) -> v == null ? 1 : v + 1);
                }

                validatableAttestation.saveCommitteeShufflingSeedAndCommitteesSize(state);

                return validatableAttestation;
              })
          .forEach(
              attestation -> {
                attestation.saveCommitteeShufflingSeedAndCommitteesSize(state);
                attestation.setIndexedAttestation(
                    attestationUtil.getIndexedAttestation(state, attestation.getAttestation()));

                pool.add(attestation);
                attestations.add(attestation);

                var validatorIndices =
                    attestationUtil.getAttestingIndices(state, attestation.getAttestation());
                pooledAttestations.add(
                    new PooledAttestationWithData(
                        attestation.getData(),
                        PooledAttestation.fromValidatableAttestation(
                            attestation, validatorIndices)));
              });

      mostFrequentSingleAttestationDataRootAndCI =
          singleAttCounterByDataAndCommittee.entrySet().stream()
              .sorted(
                  Entry.<AttestationDataRootAndCommitteeIndex, Integer>comparingByValue()
                      .reversed())
              .findFirst()
              .orElseThrow()
              .getKey();
    }

    this.newBlockState = SPEC.processSlots(state, SLOT);

    sorter = RewardBasedAttestationSorter.create(SPEC, newBlockState);

    System.out.println(
        "init done. Pool size: "
            + pool.getSize()
            + " singleAttestationCount: "
            + singleAttestationCount[0]
            + " aggregatedAttestationCount: "
            + aggregatedAttestationCount[0]);
  }

  @Benchmark
  @BenchmarkMode(Mode.AverageTime)
  public void getAttestationsForBlock(final Blackhole bh) {
    var attestationsForBlock = pool.getAttestationsForBlock(newBlockState, attestationForkChecker);
    bh.consume(attestationsForBlock);
  }

  @Benchmark
  @BenchmarkMode(Mode.AverageTime)
  public void add(final Blackhole bh) {
    var emptyPool =
        new AggregatingAttestationPoolV2(
            SPEC,
            recentChainData,
            new NoOpMetricsSystem(),
            DEFAULT_MAXIMUM_ATTESTATION_COUNT,
            AggregatingAttestationPoolProfiler.NOOP,
            10_000,
            10_000);
    attestations.forEach(emptyPool::add);
  }

  @Benchmark
  @BenchmarkMode(Mode.Throughput)
  public void createAggregateFor(final Blackhole bh) {
    var attestationsForBlock =
        pool.createAggregateFor(
            mostFrequentSingleAttestationDataRootAndCI.attestationDataRoot,
            Optional.of(mostFrequentSingleAttestationDataRootAndCI.committeeIndex));
    bh.consume(attestationsForBlock);
  }

  @Benchmark
  @BenchmarkMode(Mode.Throughput)
  public void getAttestingIndices(final Blackhole bh) {
    var result =
        SPEC.atSlot(SLOT)
            .getAttestationUtil()
            .getAttestingIndices(state, attestations.getFirst().getAttestation());
    bh.consume(result);
  }

  @Benchmark
  @BenchmarkMode(Mode.Throughput)
  public void sortAttestations(final Blackhole bh) {
    var sortedAttestations = sorter.sort(pooledAttestations.subList(0, 50), 8);
    bh.consume(sortedAttestations);
  }

  public void printBlockRewardData() {
    final BlockRewardCalculatorUtil blockRewardCalculatorUtil = new BlockRewardCalculatorUtil(SPEC);
    final DataStructureUtil dataStructureUtil = new DataStructureUtil(SPEC);

    var block =
        dataStructureUtil
            .blockBuilder(SLOT.longValue())
            .slot(SLOT)
            .attestations(pool.getAttestationsForBlock(newBlockState, attestationForkChecker))
            .build()
            .getImmediately();

    // NOTE: the problem with rewards is that we don't feed the pool with the "seen" attestations
    // in theory we should feed the pool with recent blocks' attestations via
    // onAttestationsIncludedInBlock to replicate the actual pool behavior
    final BlockRewardData blockRewardData =
        blockRewardCalculatorUtil.getBlockRewardData(block, state);
    System.out.println(
        "Block attestation rewards: "
            + gweiToEth(UInt64.valueOf(blockRewardData.attestations()))
            + " ETH");

    ACTUAL_BLOCK_PATH.ifPresent(
        actualBlockPath -> {
          final SignedBeaconBlock actualBlock;
          try (final FileInputStream fileInputStream = new FileInputStream(actualBlockPath)) {
            actualBlock =
                SPEC.getGenesisSpec()
                    .getSchemaDefinitions()
                    .getSignedBeaconBlockSchema()
                    .sszDeserialize(Bytes.wrap(fileInputStream.readAllBytes()));
          } catch (IOException e) {
            throw new RuntimeException(e);
          }

          final BlockRewardData actualBlockRewardData =
              blockRewardCalculatorUtil.getBlockRewardData(actualBlock.getMessage(), state);
          System.out.println(
              "Block attestation rewards: "
                  + gweiToEth(UInt64.valueOf(actualBlockRewardData.attestations()))
                  + " ETH (actual block)");
        });
  }

  public static void main(String[] args) throws Exception {
    AggregatingAttestationPoolBenchmark benchmark = new AggregatingAttestationPoolBenchmark();
    benchmark.init();
    benchmark.printBlockRewardData();

    var bh =
        new Blackhole(
            "Today's password is swordfish. I understand instantiating Blackholes directly is dangerous.");

    for (int i = 0; i < 1; i++) {
      benchmark.getAttestationsForBlock(bh);
    }
  }
}
