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

package tech.pegasys.teku.validator.client;

import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Stream;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.tuweni.bytes.Bytes;
import tech.pegasys.teku.bls.BLSPublicKey;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.SpecMilestone;
import tech.pegasys.teku.spec.datastructures.builder.versions.gloas.BuilderConfig;
import tech.pegasys.teku.spec.datastructures.builder.versions.gloas.BuilderEntry;
import tech.pegasys.teku.spec.datastructures.builder.versions.gloas.BuilderRequestAuth;
import tech.pegasys.teku.spec.datastructures.builder.versions.gloas.SignedBuilderRequestAuth;
import tech.pegasys.teku.spec.schemas.ApiSchemas;
import tech.pegasys.teku.validator.api.ValidatorTimingChannel;
import tech.pegasys.teku.validator.client.ResolvedBuilderConfig.ResolvedBuilderEntry;

public class DefaultBuilderConfigProvider implements BuilderConfigProvider, ValidatorTimingChannel {

  private static final Logger LOG = LogManager.getLogger();

  // Builder preferences are sent one epoch before the proposal slot, so the BuilderConfig is
  // computed ahead of time. Pruned on each slot; entries older than 2 epochs are discarded.
  private final Map<ProposerPubkeyAndSlot, BuilderConfig> cachedBuilderConfig =
      new ConcurrentHashMap<>();

  private record ProposerPubkeyAndSlot(BLSPublicKey proposerPubkey, UInt64 slot) {}

  private final Spec spec;
  private final ProposerConfigPropertiesProvider proposerConfigPropertiesProvider;

  public DefaultBuilderConfigProvider(
      final Spec spec, final ProposerConfigPropertiesProvider proposerConfigPropertiesProvider) {
    this.spec = spec;
    this.proposerConfigPropertiesProvider = proposerConfigPropertiesProvider;
  }

  @Override
  public void onSlot(final UInt64 slot) {
    final long slotsPerTwoEpochs = 2L * spec.atSlot(slot).getSlotsPerEpoch();
    cachedBuilderConfig
        .entrySet()
        .removeIf(
            entry ->
                slot.minusMinZero(entry.getKey().slot).isGreaterThanOrEqualTo(slotsPerTwoEpochs));
  }

  @Override
  public SafeFuture<Optional<BuilderConfig>> getBuilderConfig(
      final Validator validator, final UInt64 slot) {
    if (!isBuilderConfigRequired(slot)) {
      return SafeFuture.completedFuture(Optional.empty());
    }
    // just in case initialization of ProposerConfigManager is not yet complete, we return
    // non-biased builder config, but in practice this should never happen
    if (!proposerConfigPropertiesProvider.isReadyToProvideProperties()) {
      return SafeFuture.completedFuture(Optional.of(BuilderConfig.NO_OP));
    }
    final ProposerPubkeyAndSlot cacheKey =
        new ProposerPubkeyAndSlot(validator.getPublicKey(), slot);
    if (cachedBuilderConfig.containsKey(cacheKey)) {
      return SafeFuture.completedFuture(Optional.of(cachedBuilderConfig.get(cacheKey)));
    }
    return createBuilderConfig(validator, slot, cacheKey);
  }

  private SafeFuture<Optional<BuilderConfig>> createBuilderConfig(
      final Validator validator, final UInt64 slot, final ProposerPubkeyAndSlot cacheKey) {
    final ResolvedBuilderConfig resolvedBuilderConfig =
        proposerConfigPropertiesProvider.resolveBuilderConfig(validator.getPublicKey());
    final Stream<SafeFuture<BuilderEntry>> builderEntriesFutures =
        resolvedBuilderConfig.builders().stream()
            .map(
                builder ->
                    getSignedAuthData(builder, validator, slot)
                        .thenApply(
                            signedAuth ->
                                ApiSchemas.BUILDER_ENTRY_SCHEMA.create(
                                    Bytes.of(
                                        builder.url().toString().getBytes(StandardCharsets.UTF_8)),
                                    signedAuth,
                                    builder.builderPubkeys(),
                                    builder.maxExecutionPayment(),
                                    builder.minBid(),
                                    builder.builderBoostFactor()))
                        // In case of unexpected exceptions, we filter them out and log it instead
                        .whenException(
                            ex ->
                                LOG.warn(
                                    "Exception occurred while creating builder entry for {}",
                                    builder.url().toString(),
                                    ex)));
    return SafeFuture.collectAllSuccessful(builderEntriesFutures)
        .thenApply(
            builderEntries -> {
              final BuilderConfig builderConfig =
                  ApiSchemas.BUILDER_CONFIG_SCHEMA.create(
                      resolvedBuilderConfig.minBid(),
                      resolvedBuilderConfig.builderBoostFactor(),
                      builderEntries);
              cachedBuilderConfig.put(cacheKey, builderConfig);
              return Optional.of(builderConfig);
            });
  }

  private boolean isBuilderConfigRequired(final UInt64 slot) {
    return spec.atSlot(slot).getMilestone().isGreaterThanOrEqualTo(SpecMilestone.GLOAS);
  }

  // Authenticates bid requests to the builder
  private SafeFuture<SignedBuilderRequestAuth> getSignedAuthData(
      final ResolvedBuilderEntry builder, final Validator validator, final UInt64 slot) {
    return SafeFuture.of(
        () -> {
          final BuilderRequestAuth auth =
              ApiSchemas.BUILDER_REQUEST_AUTH_SCHEMA.create(builder.authData(), slot);
          return validator
              .getSigner()
              .signBuilderRequestAuth(auth)
              .thenApply(
                  signature ->
                      ApiSchemas.SIGNED_BUILDER_REQUEST_AUTH_SCHEMA.create(auth, signature));
        });
  }
}
