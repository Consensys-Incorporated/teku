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

import static com.google.common.base.MoreObjects.firstNonNull;

import com.google.common.collect.Sets;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.collections.LimitedMap;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.datastructures.epbs.versions.gloas.BlindedExecutionPayloadEnvelope;
import tech.pegasys.teku.spec.datastructures.epbs.versions.gloas.SignedBlindedExecutionPayloadEnvelope;
import tech.pegasys.teku.spec.datastructures.epbs.versions.gloas.SignedExecutionPayloadEnvelope;
import tech.pegasys.teku.spec.datastructures.execution.ExecutionPayload;
import tech.pegasys.teku.spec.datastructures.execution.ExecutionPayloadBody;
import tech.pegasys.teku.spec.datastructures.execution.ExecutionPayloadHeader;
import tech.pegasys.teku.spec.datastructures.execution.ExecutionPayloadSchema;
import tech.pegasys.teku.spec.datastructures.execution.versions.capella.Withdrawal;
import tech.pegasys.teku.spec.datastructures.execution.versions.capella.WithdrawalSchema;
import tech.pegasys.teku.spec.datastructures.execution.versions.deneb.ExecutionPayloadHeaderDeneb;
import tech.pegasys.teku.spec.schemas.SchemaDefinitionsBellatrix;
import tech.pegasys.teku.spec.schemas.SchemaDefinitionsCapella;
import tech.pegasys.teku.spec.schemas.SchemaDefinitionsGloas;

/**
 * An {@link ExecutionPayloadProvider} that reads blinded execution payload envelopes from the
 * database and unblinds them by fetching payload bodies from the execution layer.
 */
public class UnblindingExecutionPayloadProvider implements ExecutionPayloadProvider {

  private static final Logger LOG = LogManager.getLogger();
  private static final int DEFAULT_UNBLINDED_ENVELOPE_CACHE_SIZE = 32;

  private final Spec spec;
  private final BlindedExecutionPayloadProvider blindedExecutionPayloadProvider;
  private final ExecutionPayloadBodiesByHashProvider executionPayloadBodiesByHashProvider;
  private final Map<Bytes32, SignedExecutionPayloadEnvelope> recentlyUnblindedEnvelopes;

  public UnblindingExecutionPayloadProvider(
      final Spec spec,
      final BlindedExecutionPayloadProvider blindedExecutionPayloadProvider,
      final ExecutionPayloadBodiesByHashProvider executionPayloadBodiesByHashProvider) {
    this(
        spec,
        blindedExecutionPayloadProvider,
        executionPayloadBodiesByHashProvider,
        DEFAULT_UNBLINDED_ENVELOPE_CACHE_SIZE);
  }

  public UnblindingExecutionPayloadProvider(
      final Spec spec,
      final BlindedExecutionPayloadProvider blindedExecutionPayloadProvider,
      final ExecutionPayloadBodiesByHashProvider executionPayloadBodiesByHashProvider,
      final int cacheSize) {
    this.spec = spec;
    this.blindedExecutionPayloadProvider = blindedExecutionPayloadProvider;
    this.executionPayloadBodiesByHashProvider = executionPayloadBodiesByHashProvider;
    this.recentlyUnblindedEnvelopes = LimitedMap.createSynchronizedLRU(cacheSize);
  }

  @FunctionalInterface
  public interface ExecutionPayloadBodiesByHashProvider {
    SafeFuture<List<ExecutionPayloadBody>> getExecutionPayloadBodiesByHash(
        List<Bytes32> blockHashes);
  }

  @Override
  public SafeFuture<Map<Bytes32, SignedExecutionPayloadEnvelope>> getExecutionPayloads(
      final Set<Bytes32> blockRoots) {
    // Serve recently unblinded envelopes from cache
    final Map<Bytes32, SignedExecutionPayloadEnvelope> result = new HashMap<>();
    for (final Bytes32 root : blockRoots) {
      final SignedExecutionPayloadEnvelope envelope = recentlyUnblindedEnvelopes.get(root);
      if (envelope != null) {
        result.put(root, envelope);
      }
    }

    final Set<Bytes32> cacheMisses = Sets.difference(blockRoots, result.keySet());
    if (cacheMisses.isEmpty()) {
      return SafeFuture.completedFuture(Collections.unmodifiableMap(result));
    }

    // Fetch and unblind remaining from DB and EL
    return blindedExecutionPayloadProvider
        .getBlindedExecutionPayloads(cacheMisses)
        .thenCompose(this::unblindExecutionPayloadEnvelopes)
        .thenApply(
            newlyUnblinded -> {
              recentlyUnblindedEnvelopes.putAll(newlyUnblinded);
              result.putAll(newlyUnblinded);
              return Collections.unmodifiableMap(result);
            });
  }

  private SafeFuture<Map<Bytes32, SignedExecutionPayloadEnvelope>> unblindExecutionPayloadEnvelopes(
      final Map<Bytes32, SignedBlindedExecutionPayloadEnvelope> blindedExecutionPayloadEnvelopes) {
    if (blindedExecutionPayloadEnvelopes.isEmpty()) {
      return SafeFuture.completedFuture(Map.of());
    }

    // Collect unique block hashes to fetch from the EL
    final List<Bytes32> blockHashes =
        blindedExecutionPayloadEnvelopes.values().stream()
            .map(env -> env.getMessage().getPayloadHeader().getBlockHash())
            .distinct()
            .toList();

    return executionPayloadBodiesByHashProvider
        .getExecutionPayloadBodiesByHash(blockHashes)
        .thenApply(
            executionPayloadBodies -> {
              // index EL response by block hash
              final Map<Bytes32, ExecutionPayloadBody> bodiesByHash = new HashMap<>();
              for (int i = 0; i < blockHashes.size(); i++) {
                if (executionPayloadBodies.get(i) != null) {
                  bodiesByHash.put(blockHashes.get(i), executionPayloadBodies.get(i));
                }
              }

              final Map<Bytes32, SignedExecutionPayloadEnvelope> result = new HashMap<>();
              for (final Map.Entry<Bytes32, SignedBlindedExecutionPayloadEnvelope> entry :
                  blindedExecutionPayloadEnvelopes.entrySet()) {
                final Bytes32 blockRoot = entry.getKey();
                final SignedBlindedExecutionPayloadEnvelope blindedEnvelope = entry.getValue();
                final Bytes32 blockHash =
                    blindedEnvelope.getMessage().getPayloadHeader().getBlockHash();
                final ExecutionPayloadBody executionPayloadBody = bodiesByHash.get(blockHash);
                if (executionPayloadBody == null) {
                  LOG.debug(
                      "No execution payload body available for block hash {}, skipping unblinding execution payload for block root {}",
                      blockHash,
                      blockRoot);
                  continue;
                }
                if (executionPayloadBody.blockAccessList() == null) {
                  LOG.debug(
                      "Execution payload body for block hash {} is missing blockAccessList, skipping unblinding execution payload for block root {}",
                      blockHash,
                      blockRoot);
                  continue;
                }
                try {
                  result.put(
                      blockRoot,
                      unblindExecutionPayloadEnvelope(blindedEnvelope, executionPayloadBody));
                } catch (final Exception e) {
                  LOG.warn("Failed to unblind execution payload for block root {}", blockRoot, e);
                }
              }
              return result;
            });
  }

  private SignedExecutionPayloadEnvelope unblindExecutionPayloadEnvelope(
      final SignedBlindedExecutionPayloadEnvelope signedBlindedExecutionPayloadEnvelope,
      final ExecutionPayloadBody executionPayloadBody) {
    final BlindedExecutionPayloadEnvelope blindedExecutionPayloadEnvelope =
        signedBlindedExecutionPayloadEnvelope.getMessage();
    final UInt64 slot = blindedExecutionPayloadEnvelope.getSlot();
    final SchemaDefinitionsGloas schemaDefinitions =
        SchemaDefinitionsGloas.required(spec.atSlot(slot).getSchemaDefinitions());
    final ExecutionPayload fullExecutionPayload =
        buildExecutionPayload(
            blindedExecutionPayloadEnvelope.getPayloadHeader(), executionPayloadBody, slot);
    return signedBlindedExecutionPayloadEnvelope.unblind(schemaDefinitions, fullExecutionPayload);
  }

  private ExecutionPayload buildExecutionPayload(
      final ExecutionPayloadHeader header, final ExecutionPayloadBody body, final UInt64 slot) {
    final ExecutionPayloadSchema<?> payloadSchema =
        SchemaDefinitionsBellatrix.required(spec.atSlot(slot).getSchemaDefinitions())
            .getExecutionPayloadSchema();

    return payloadSchema.createExecutionPayload(
        builder ->
            builder
                .parentHash(header.getParentHash())
                .feeRecipient(header.getFeeRecipient())
                .stateRoot(header.getStateRoot())
                .receiptsRoot(header.getReceiptsRoot())
                .logsBloom(header.getLogsBloom())
                .prevRandao(header.getPrevRandao())
                .blockNumber(header.getBlockNumber())
                .gasLimit(header.getGasLimit())
                .gasUsed(header.getGasUsed())
                .timestamp(header.getTimestamp())
                .extraData(header.getExtraData())
                .baseFeePerGas(header.getBaseFeePerGas())
                .blockHash(header.getBlockHash())
                .transactions(body.transactions())
                .withdrawals(() -> toWithdrawals(body, slot))
                .blobGasUsed(() -> getBlobGasUsed(header))
                .excessBlobGas(() -> getExcessBlobGas(header))
                .blockAccessList(() -> firstNonNull(body.blockAccessList(), Bytes.EMPTY))
                .slotNumber(() -> slot));
  }

  private List<Withdrawal> toWithdrawals(final ExecutionPayloadBody body, final UInt64 slot) {
    if (body.withdrawals() == null) {
      return List.of();
    }
    final WithdrawalSchema withdrawalSchema =
        SchemaDefinitionsCapella.required(spec.atSlot(slot).getSchemaDefinitions())
            .getWithdrawalSchema();
    return body.withdrawals().stream()
        .map(w -> withdrawalSchema.create(w.index(), w.validatorIndex(), w.address(), w.amount()))
        .toList();
  }

  private static UInt64 getBlobGasUsed(final ExecutionPayloadHeader header) {
    return header
        .toVersionDeneb()
        .map(ExecutionPayloadHeaderDeneb::getBlobGasUsed)
        .orElse(UInt64.ZERO);
  }

  private static UInt64 getExcessBlobGas(final ExecutionPayloadHeader header) {
    return header
        .toVersionDeneb()
        .map(ExecutionPayloadHeaderDeneb::getExcessBlobGas)
        .orElse(UInt64.ZERO);
  }
}
