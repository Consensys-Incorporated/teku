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

package tech.pegasys.teku.dataproviders.lookup;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.ssz.collections.impl.SszByteListImpl;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.TestSpecFactory;
import tech.pegasys.teku.spec.datastructures.epbs.versions.gloas.SignedBlindedExecutionPayloadEnvelope;
import tech.pegasys.teku.spec.datastructures.epbs.versions.gloas.SignedExecutionPayloadEnvelope;
import tech.pegasys.teku.spec.datastructures.execution.ExecutionPayload;
import tech.pegasys.teku.spec.datastructures.execution.ExecutionPayloadBody;
import tech.pegasys.teku.spec.datastructures.execution.versions.capella.ExecutionPayloadCapella;
import tech.pegasys.teku.spec.datastructures.execution.versions.gloas.ExecutionPayloadGloas;
import tech.pegasys.teku.spec.schemas.SchemaDefinitionsGloas;
import tech.pegasys.teku.spec.util.DataStructureUtil;

class UnblindingExecutionPayloadProviderTest {

  private final Spec spec = TestSpecFactory.createMinimalGloas();
  private final DataStructureUtil dataStructureUtil = new DataStructureUtil(spec);

  @Test
  void shouldUnblindExecutionPayloadEnvelope() {
    final SignedExecutionPayloadEnvelope originalExecutionPayloadEnvelope =
        dataStructureUtil.randomSignedExecutionPayloadEnvelope(1);
    final Bytes32 blockRoot = originalExecutionPayloadEnvelope.getBeaconBlockRoot();

    final SignedBlindedExecutionPayloadEnvelope blindedExecutionPayloadEnvelope =
        originalExecutionPayloadEnvelope.blind(spec);

    final ExecutionPayload fullExecutionPayload =
        originalExecutionPayloadEnvelope.getMessage().getPayload();
    final ExecutionPayloadBody executionPayloadBody = toExecutionPayloadBody(fullExecutionPayload);

    final Bytes32 blockHash = fullExecutionPayload.getBlockHash();

    final UnblindingExecutionPayloadProvider provider =
        new UnblindingExecutionPayloadProvider(
            spec,
            roots -> SafeFuture.completedFuture(Map.of(blockRoot, blindedExecutionPayloadEnvelope)),
            blockHashes -> {
              assertThat(blockHashes).containsExactly(blockHash);
              return SafeFuture.completedFuture(List.of(executionPayloadBody));
            });

    assertThat(provider.getExecutionPayloads(Set.of(blockRoot)))
        .isCompletedWithValueMatching(
            result ->
                result.containsKey(blockRoot)
                    && result
                        .get(blockRoot)
                        .hashTreeRoot()
                        .equals(originalExecutionPayloadEnvelope.hashTreeRoot()));
  }

  @Test
  void shouldReturnEmptyWhenNoBlindedEnvelopes() {
    final UnblindingExecutionPayloadProvider provider =
        new UnblindingExecutionPayloadProvider(
            spec,
            roots -> SafeFuture.completedFuture(Map.of()),
            blockHashes -> {
              throw new AssertionError("Should not call EL when no envelopes");
            });

    assertThat(provider.getExecutionPayloads(Set.of(dataStructureUtil.randomBytes32())))
        .isCompletedWithValue(Map.of());
  }

  @Test
  void shouldSkipWhenElReturnsNullBody() {
    final SignedExecutionPayloadEnvelope originalEnvelope =
        dataStructureUtil.randomSignedExecutionPayloadEnvelope(1);
    final Bytes32 blockRoot = originalEnvelope.getBeaconBlockRoot();

    final SignedBlindedExecutionPayloadEnvelope blindedEnvelope = originalEnvelope.blind(spec);

    final UnblindingExecutionPayloadProvider provider =
        new UnblindingExecutionPayloadProvider(
            spec,
            roots -> SafeFuture.completedFuture(Map.of(blockRoot, blindedEnvelope)),
            blockHashes -> {
              final List<ExecutionPayloadBody> bodies = new ArrayList<>();
              bodies.add(null);
              return SafeFuture.completedFuture(bodies);
            });

    assertThat(provider.getExecutionPayloads(Set.of(blockRoot))).isCompletedWithValue(Map.of());
  }

  @Test
  void shouldSkipWhenElReturnsNullBlockAccessList() {
    final UInt64 slot = UInt64.ONE;
    final ExecutionPayload executionPayload =
        dataStructureUtil.randomExecutionPayload(
            slot, builder -> builder.blockAccessList(() -> Bytes.EMPTY));
    final SchemaDefinitionsGloas schemaDefinitions =
        SchemaDefinitionsGloas.required(spec.atSlot(slot).getSchemaDefinitions());
    final SignedExecutionPayloadEnvelope originalEnvelope =
        schemaDefinitions
            .getSignedExecutionPayloadEnvelopeSchema()
            .create(
                schemaDefinitions
                    .getExecutionPayloadEnvelopeSchema()
                    .create(
                        executionPayload,
                        dataStructureUtil.randomExecutionRequests(slot),
                        dataStructureUtil.randomBuilderIndex(),
                        dataStructureUtil.randomBytes32(),
                        dataStructureUtil.randomBytes32()),
                dataStructureUtil.randomSignature());
    final Bytes32 blockRoot = originalEnvelope.getBeaconBlockRoot();
    final SignedBlindedExecutionPayloadEnvelope blindedEnvelope = originalEnvelope.blind(spec);
    final ExecutionPayloadBody completeBody = toExecutionPayloadBody(executionPayload);
    final ExecutionPayloadBody bodyWithNullBlockAccessList =
        new ExecutionPayloadBody(completeBody.transactions(), completeBody.withdrawals(), null);

    final UnblindingExecutionPayloadProvider provider =
        new UnblindingExecutionPayloadProvider(
            spec,
            roots -> SafeFuture.completedFuture(Map.of(blockRoot, blindedEnvelope)),
            blockHashes -> SafeFuture.completedFuture(List.of(bodyWithNullBlockAccessList)));

    assertThat(provider.getExecutionPayloads(Set.of(blockRoot))).isCompletedWithValue(Map.of());
  }

  @Test
  void shouldUnblindEquivocatingEnvelopes() {
    final SignedExecutionPayloadEnvelope originalEnvelope =
        dataStructureUtil.randomSignedExecutionPayloadEnvelope(1);

    final SignedBlindedExecutionPayloadEnvelope blindedEnvelope = originalEnvelope.blind(spec);

    // Two different beacon block roots referencing the same execution payload (equivocation)
    final Bytes32 blockRootA = dataStructureUtil.randomBytes32();
    final Bytes32 blockRootB = dataStructureUtil.randomBytes32();

    final ExecutionPayloadBody body =
        toExecutionPayloadBody(originalEnvelope.getMessage().getPayload());

    final UnblindingExecutionPayloadProvider provider =
        new UnblindingExecutionPayloadProvider(
            spec,
            roots ->
                SafeFuture.completedFuture(
                    Map.of(blockRootA, blindedEnvelope, blockRootB, blindedEnvelope)),
            blockHashes -> {
              // Same execution block hash. The EL should only be asked for one unique hash
              assertThat(blockHashes).hasSize(1);
              return SafeFuture.completedFuture(List.of(body));
            });

    assertThat(provider.getExecutionPayloads(Set.of(blockRootA, blockRootB)))
        .isCompletedWithValueMatching(
            result ->
                result.size() == 2
                    && result.containsKey(blockRootA)
                    && result.containsKey(blockRootB));
  }

  @Test
  void shouldCacheUnblindedEnvelopesAndOnlyFetchMisses() {
    final SignedExecutionPayloadEnvelope executionPayloadEnvelopeA =
        dataStructureUtil.randomSignedExecutionPayloadEnvelope(1);
    final SignedExecutionPayloadEnvelope executionPayloadEnvelopeB =
        dataStructureUtil.randomSignedExecutionPayloadEnvelope(2);
    final Bytes32 blockRootA = executionPayloadEnvelopeA.getBeaconBlockRoot();
    final Bytes32 blockRootB = executionPayloadEnvelopeB.getBeaconBlockRoot();

    final SignedBlindedExecutionPayloadEnvelope blindedExecutionPayloadEnvelopeA =
        executionPayloadEnvelopeA.blind(spec);
    final SignedBlindedExecutionPayloadEnvelope blindedExecutionPayloadEnvelopeB =
        executionPayloadEnvelopeB.blind(spec);

    final Map<Bytes32, SignedBlindedExecutionPayloadEnvelope> allBlinded =
        Map.of(
            blockRootA,
            blindedExecutionPayloadEnvelopeA,
            blockRootB,
            blindedExecutionPayloadEnvelopeB);
    final Map<Bytes32, ExecutionPayloadBody> allBodies =
        Map.of(
            executionPayloadEnvelopeA.getMessage().getPayload().getBlockHash(),
            toExecutionPayloadBody(executionPayloadEnvelopeA.getMessage().getPayload()),
            executionPayloadEnvelopeB.getMessage().getPayload().getBlockHash(),
            toExecutionPayloadBody(executionPayloadEnvelopeB.getMessage().getPayload()));

    final AtomicInteger blindedProviderCallCount = new AtomicInteger(0);
    final AtomicInteger elCallCount = new AtomicInteger(0);

    final UnblindingExecutionPayloadProvider provider =
        new UnblindingExecutionPayloadProvider(
            spec,
            roots -> {
              blindedProviderCallCount.incrementAndGet();
              final Map<Bytes32, SignedBlindedExecutionPayloadEnvelope> result = new HashMap<>();
              for (final Bytes32 root : roots) {
                if (allBlinded.containsKey(root)) {
                  result.put(root, allBlinded.get(root));
                }
              }
              return SafeFuture.completedFuture(result);
            },
            blockHashes -> {
              elCallCount.incrementAndGet();
              final List<ExecutionPayloadBody> bodies =
                  blockHashes.stream().map(allBodies::get).toList();
              return SafeFuture.completedFuture(bodies);
            });

    // First call: fetch A, cache miss, should hit DB + EL
    assertThat(provider.getExecutionPayloads(Set.of(blockRootA)))
        .isCompletedWithValue(Map.of(blockRootA, executionPayloadEnvelopeA));
    assertThat(blindedProviderCallCount.getAndSet(0)).isEqualTo(1);
    assertThat(elCallCount.getAndSet(0)).isEqualTo(1);

    // Second call: fetch A again, cache hit, no DB or EL call
    assertThat(provider.getExecutionPayloads(Set.of(blockRootA)))
        .isCompletedWithValue(Map.of(blockRootA, executionPayloadEnvelopeA));
    assertThat(blindedProviderCallCount.getAndSet(0)).isEqualTo(0);
    assertThat(elCallCount.getAndSet(0)).isEqualTo(0);

    // Third call: fetch A (cached) + B (uncached), only B hits DB + EL
    assertThat(provider.getExecutionPayloads(Set.of(blockRootA, blockRootB)))
        .isCompletedWithValue(
            Map.of(
                blockRootA, executionPayloadEnvelopeA,
                blockRootB, executionPayloadEnvelopeB));
    assertThat(blindedProviderCallCount.getAndSet(0)).isEqualTo(1);
    assertThat(elCallCount.getAndSet(0)).isEqualTo(1);
  }

  private ExecutionPayloadBody toExecutionPayloadBody(final ExecutionPayload executionPayload) {
    final List<Bytes> transactions =
        executionPayload.getTransactions().stream().map(SszByteListImpl::getBytes).toList();
    final List<ExecutionPayloadBody.EncodedWithdrawal> withdrawals =
        ExecutionPayloadCapella.required(executionPayload).getWithdrawals().stream()
            .map(
                w ->
                    new ExecutionPayloadBody.EncodedWithdrawal(
                        w.getIndex(), w.getValidatorIndex(), w.getAddress(), w.getAmount()))
            .toList();
    return new ExecutionPayloadBody(
        transactions,
        withdrawals,
        ExecutionPayloadGloas.required(executionPayload).getBlockAccessList().getBytes());
  }
}
