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

package tech.pegasys.teku.statetransition.forkchoice.fastconfirmation;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Random;
import java.util.concurrent.TimeUnit;
import org.apache.tuweni.bytes.Bytes32;
import org.apache.tuweni.bytes.Bytes48;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Level;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.Warmup;
import org.openjdk.jmh.infra.Blackhole;
import tech.pegasys.teku.bls.BLSPublicKey;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.TestSpecFactory;
import tech.pegasys.teku.spec.datastructures.blocks.BlockCheckpoints;
import tech.pegasys.teku.spec.datastructures.blocks.SlotAndBlockRoot;
import tech.pegasys.teku.spec.datastructures.forkchoice.FastConfirmationStore;
import tech.pegasys.teku.spec.datastructures.forkchoice.ForkChoiceNode;
import tech.pegasys.teku.spec.datastructures.forkchoice.ForkChoicePayloadStatus;
import tech.pegasys.teku.spec.datastructures.forkchoice.ProtoNodeData;
import tech.pegasys.teku.spec.datastructures.forkchoice.ProtoNodeValidationStatus;
import tech.pegasys.teku.spec.datastructures.forkchoice.ReadOnlyForkChoiceStrategy;
import tech.pegasys.teku.spec.datastructures.forkchoice.ReadOnlyStore;
import tech.pegasys.teku.spec.datastructures.forkchoice.VoteSnapshot;
import tech.pegasys.teku.spec.datastructures.forkchoice.VoteTracker;
import tech.pegasys.teku.spec.datastructures.state.Checkpoint;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.BeaconState;
import tech.pegasys.teku.spec.util.DataStructureUtil;

/**
 * Benchmarks the Fast Confirmation Rule catch-up cost (issue #10994 acceptance criterion): the
 * ~2-epoch catch-up slot that ends the warm-up — {@code get_latest_confirmed} restarting the
 * confirmed root from the observed justified checkpoint and advancing it across a whole epoch of
 * blocks to the head — must complete comfortably inside one slot at mainnet scale.
 *
 * <p>Fixture: mainnet preset, a linear chain with one block at every slot of epochs 0 and 1, the
 * current slot starting epoch 2, and every validator voting (90% for the head, 10% spread across
 * the previous epoch to exercise the prefix binary search). Fork choice is a lightweight in-memory
 * linear chain whose ancestor lookups walk the parent chain the way protoarray does, so per-check
 * costs are comparable to production. The head state is built directly in the current epoch, so the
 * benchmark excludes the epoch-transition (pull-up) cost, which the scoring optimization does not
 * affect.
 *
 * <p>{@code perBlockScoreTwoEpochChain} reproduces the pre-optimization cost model (one full
 * validator pass per block) for direct comparison with {@code batchScoreTwoEpochChain} (one pass
 * for the whole segment).
 *
 * <p>Quick run: {@code ./gradlew :ethereum:statetransition:jmh --args="FastConfirmationBenchmark -p
 * validatorCount=1000000 -wi 1 -i 2 -f 0"}
 */
@Warmup(iterations = 2, time = 2, timeUnit = TimeUnit.SECONDS)
@Measurement(iterations = 5, time = 2, timeUnit = TimeUnit.SECONDS)
@Fork(
    value = 1,
    jvmArgsAppend = {"-Xmx8g"})
@State(Scope.Thread)
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MILLISECONDS)
public class FastConfirmationBenchmark {

  private static final Spec SPEC = TestSpecFactory.createMainnetPhase0();
  private static final int SLOTS_PER_EPOCH = SPEC.getGenesisSpecConfig().getSlotsPerEpoch();

  /** One block at every slot of epochs 0 and 1; the current slot is the first of epoch 2. */
  private static final int CHAIN_LENGTH = SLOTS_PER_EPOCH * 2;

  private static final UInt64 CURRENT_SLOT = UInt64.valueOf(CHAIN_LENGTH);

  @Param({"100000", "1000000"})
  private int validatorCount;

  private BeaconState balanceSource;
  private FastConfirmationStore fcrStore;
  private FastConfirmationStates states;
  private Bytes32 head;
  private List<Bytes32> scoredChain;

  @Setup(Level.Trial)
  public void init() {
    final Random random = new Random(2718281828L);
    // Pubkeys are never used by the FCR, so generate cheap (lazily parsed) random keys instead of
    // paying real BLS key generation for a mainnet-sized validator set.
    final DataStructureUtil dataStructureUtil =
        new DataStructureUtil(SPEC)
            .withPubKeyGenerator(
                () -> {
                  final byte[] keyBytes = new byte[48];
                  random.nextBytes(keyBytes);
                  return BLSPublicKey.fromBytesCompressed(Bytes48.wrap(keyBytes));
                });
    balanceSource =
        dataStructureUtil.randomBeaconStateWithActiveValidators(validatorCount, CURRENT_SLOT);

    final List<Bytes32> chain = new ArrayList<>(CHAIN_LENGTH);
    for (int slot = 0; slot < CHAIN_LENGTH; slot++) {
      chain.add(Bytes32.random(random));
    }
    head = chain.get(CHAIN_LENGTH - 1);
    final Bytes32 previousSlotHead = chain.get(CHAIN_LENGTH - 2);

    final Checkpoint finalized = new Checkpoint(UInt64.ZERO, chain.get(0));
    final Checkpoint observedJustified = new Checkpoint(UInt64.ONE, chain.get(SLOTS_PER_EPOCH));
    // Every block reports the epoch-1 checkpoint as its unrealized justification, matching the
    // healthy-network shape the restart branch expects.
    final BlockCheckpoints blockCheckpoints =
        new BlockCheckpoints(finalized, finalized, observedJustified, finalized);
    final LinearChainForkChoiceStrategy forkChoice =
        new LinearChainForkChoiceStrategy(chain, blockCheckpoints);

    final VoteTracker[] votes = new VoteTracker[validatorCount];
    for (int index = 0; index < validatorCount; index++) {
      final Bytes32 votedRoot =
          index % 10 == 0
              ? chain.get(SLOTS_PER_EPOCH + 1 + ((index / 10) % (SLOTS_PER_EPOCH - 2)))
              : head;
      votes[index] = new VoteTracker(Bytes32.ZERO, votedRoot);
    }
    final VoteSnapshot voteSnapshot =
        VoteSnapshot.create(UInt64.valueOf(validatorCount - 1), votes);

    final ReadOnlyStore store = mock(ReadOnlyStore.class);
    when(store.getForkChoiceStrategy()).thenReturn(forkChoice);
    when(store.getVoteSnapshot()).thenReturn(voteSnapshot);
    when(store.getFinalizedCheckpoint()).thenReturn(finalized);

    // Warm-up exit shape: the confirmed root is still the (2-epoch-old) finalized block, so
    // get_latest_confirmed reverts it, restarts it from the observed justified checkpoint at the
    // epoch-1 start, and advances it across the whole previous epoch to the head.
    fcrStore =
        new FastConfirmationStore(
            store,
            chain.get(0),
            finalized,
            observedJustified,
            observedJustified,
            previousSlotHead,
            head);
    states = new FastConfirmationStates(Optional.of(balanceSource), balanceSource, balanceSource);
    // The full 2-epoch segment above the finalized block, for the scoring benchmarks.
    scoredChain = List.copyOf(chain.subList(1, CHAIN_LENGTH));

    // Fail fast if the fixture stops exercising the full catch-up path.
    final long startNanos = System.nanoTime();
    final Bytes32 confirmed = newCalculator().getLatestConfirmed();
    if (!confirmed.equals(head)) {
      throw new IllegalStateException(
          "Benchmark fixture did not advance the confirmed root to the head: " + confirmed);
    }
    System.out.printf(
        "init done: %d validators, cold 2-epoch catch-up took %d ms%n",
        validatorCount, TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startNanos));
  }

  /** The acceptance-criterion scenario: must average comfortably below one slot (12s). */
  @Benchmark
  public void twoEpochCatchUpGetLatestConfirmed(final Blackhole bh) {
    bh.consume(newCalculator().getLatestConfirmed());
  }

  /** The optimized core: the whole 2-epoch segment scored in one validator pass. */
  @Benchmark
  public void batchScoreTwoEpochChain(final Blackhole bh) {
    bh.consume(newCalculator().computeChainAttestationScores(scoredChain, balanceSource));
  }

  /** Pre-optimization cost model: one full validator pass per block of the segment. */
  @Benchmark
  public void perBlockScoreTwoEpochChain(final Blackhole bh) {
    final FastConfirmationCalculator calculator = newCalculator();
    for (final Bytes32 blockRoot : scoredChain) {
      bh.consume(calculator.getAttestationScore(blockRoot, balanceSource));
    }
  }

  private FastConfirmationCalculator newCalculator() {
    return new FastConfirmationCalculator(SPEC, fcrStore, states, CURRENT_SLOT);
  }

  public static void main(final String[] args) {
    final FastConfirmationBenchmark benchmark = new FastConfirmationBenchmark();
    benchmark.validatorCount = args.length > 0 ? Integer.parseInt(args[0]) : 1_000_000;
    benchmark.init();
    for (int i = 0; i < 5; i++) {
      final long catchUpStart = System.nanoTime();
      benchmark.newCalculator().getLatestConfirmed();
      final long batchStart = System.nanoTime();
      benchmark
          .newCalculator()
          .computeChainAttestationScores(benchmark.scoredChain, benchmark.balanceSource);
      final long perBlockStart = System.nanoTime();
      final FastConfirmationCalculator calculator = benchmark.newCalculator();
      for (final Bytes32 blockRoot : benchmark.scoredChain) {
        calculator.getAttestationScore(blockRoot, benchmark.balanceSource);
      }
      final long endNanos = System.nanoTime();
      System.out.printf(
          "catch-up %d ms, batch score %d ms, per-block score (old model) %d ms%n",
          TimeUnit.NANOSECONDS.toMillis(batchStart - catchUpStart),
          TimeUnit.NANOSECONDS.toMillis(perBlockStart - batchStart),
          TimeUnit.NANOSECONDS.toMillis(endNanos - perBlockStart));
    }
  }

  /**
   * Minimal linear-chain fork-choice view: one block per slot, parent = previous slot. Ancestor
   * lookups walk the chain slot by slot the way protoarray walks parent links, so per-check cost is
   * comparable to production. Only the methods the fast confirmation calculator touches are
   * implemented.
   */
  private static final class LinearChainForkChoiceStrategy implements ReadOnlyForkChoiceStrategy {
    private final List<Bytes32> rootBySlot;
    private final Map<Bytes32, Integer> slotByRoot = new HashMap<>();
    private final BlockCheckpoints blockCheckpoints;

    private LinearChainForkChoiceStrategy(
        final List<Bytes32> rootBySlot, final BlockCheckpoints blockCheckpoints) {
      this.rootBySlot = rootBySlot;
      this.blockCheckpoints = blockCheckpoints;
      for (int slot = 0; slot < rootBySlot.size(); slot++) {
        slotByRoot.put(rootBySlot.get(slot), slot);
      }
    }

    @Override
    public Optional<UInt64> blockSlot(final Bytes32 blockRoot) {
      return Optional.ofNullable(slotByRoot.get(blockRoot)).map(UInt64::valueOf);
    }

    @Override
    public Optional<Bytes32> blockParentRoot(final Bytes32 blockRoot) {
      final Integer slot = slotByRoot.get(blockRoot);
      if (slot == null) {
        return Optional.empty();
      }
      return Optional.of(slot == 0 ? Bytes32.ZERO : rootBySlot.get(slot - 1));
    }

    @Override
    public Optional<Bytes32> getAncestor(final Bytes32 blockRoot, final UInt64 slot) {
      final Integer blockSlot = slotByRoot.get(blockRoot);
      if (blockSlot == null) {
        return Optional.empty();
      }
      // Walk down one slot at a time like protoarray's parent-pointer walk.
      int currentSlot = blockSlot;
      while (UInt64.valueOf(currentSlot).isGreaterThan(slot)) {
        currentSlot--;
      }
      return Optional.of(rootBySlot.get(currentSlot));
    }

    @Override
    public Optional<ForkChoiceNode> getAncestorNode(final ForkChoiceNode node, final UInt64 slot) {
      return getAncestor(node.blockRoot(), slot).map(ForkChoiceNode::createBase);
    }

    @Override
    public boolean contains(final Bytes32 blockRoot) {
      return slotByRoot.containsKey(blockRoot);
    }

    @Override
    public boolean isFullyValidated(final Bytes32 blockRoot) {
      return contains(blockRoot);
    }

    @Override
    public Optional<Boolean> isOptimistic(final Bytes32 blockRoot) {
      return Optional.of(false);
    }

    @Override
    public Optional<ProtoNodeData> getBlockData(final Bytes32 blockRoot) {
      return blockSlot(blockRoot)
          .map(
              slot ->
                  new ProtoNodeData(
                      slot,
                      blockRoot,
                      blockParentRoot(blockRoot).orElse(Bytes32.ZERO),
                      Bytes32.ZERO,
                      UInt64.ZERO,
                      Bytes32.ZERO,
                      UInt64.ZERO,
                      ProtoNodeValidationStatus.VALID,
                      blockCheckpoints,
                      UInt64.ZERO,
                      ForkChoicePayloadStatus.PAYLOAD_STATUS_PENDING));
    }

    @Override
    public List<ProtoNodeData> getBlockData() {
      return rootBySlot.stream().map(root -> getBlockData(root).orElseThrow()).toList();
    }

    @Override
    public Optional<UInt64> executionBlockNumber(final Bytes32 blockRoot) {
      throw notUsed();
    }

    @Override
    public Optional<Bytes32> executionBlockHash(final Bytes32 blockRoot) {
      throw notUsed();
    }

    @Override
    public Optional<ForkChoiceNode> getParentBeaconBlockNode(final ForkChoiceNode node) {
      throw notUsed();
    }

    @Override
    public Optional<SlotAndBlockRoot> findCommonAncestor(
        final Bytes32 blockRoot1, final Bytes32 blockRoot2) {
      throw notUsed();
    }

    @Override
    public List<Bytes32> getBlockRootsAtSlot(final UInt64 slot) {
      throw notUsed();
    }

    @Override
    public List<ProtoNodeData> getChainHeads(final boolean includeNonViableHeads) {
      throw notUsed();
    }

    @Override
    public Optional<Bytes32> getOptimisticallySyncedTransitionBlockRoot(final Bytes32 head) {
      throw notUsed();
    }

    @Override
    public boolean shouldExtendPayload(
        final ReadOnlyStore store, final SlotAndBlockRoot slotAndBlockRoot) {
      throw notUsed();
    }

    @Override
    public boolean shouldBuildOnFull(
        final ReadOnlyStore store, final UInt64 slot, final ForkChoiceNode head) {
      throw notUsed();
    }

    @Override
    public Optional<UInt64> getWeight(final Bytes32 blockRoot) {
      throw notUsed();
    }

    private static UnsupportedOperationException notUsed() {
      return new UnsupportedOperationException("Not used by the fast confirmation calculator");
    }
  }
}
