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

package tech.pegasys.teku.validator.client;

import static tech.pegasys.teku.infrastructure.logging.ValidatorLogger.VALIDATOR_LOGGER;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.bls.BLSPublicKey;
import tech.pegasys.teku.ethereum.json.types.validator.ProposerDuty;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.ssz.SszList;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.datastructures.builder.versions.gloas.BuilderConfig;
import tech.pegasys.teku.spec.datastructures.builder.versions.gloas.BuilderPreferencesEntry;
import tech.pegasys.teku.spec.schemas.ApiSchemas;
import tech.pegasys.teku.validator.api.SubmitDataError;
import tech.pegasys.teku.validator.api.ValidatorApiChannel;
import tech.pegasys.teku.validator.client.loader.OwnedValidators;

public class BuilderPreferencesPublisher extends AbstractPreferencesPublisher {

  private static final Logger LOG = LogManager.getLogger();

  private final ValidatorApiChannel validatorApiChannel;
  private final BuilderConfigProvider builderConfigProvider;

  public BuilderPreferencesPublisher(
      final OwnedValidators ownedValidators,
      final Spec spec,
      final ValidatorApiChannel validatorApiChannel,
      final BuilderConfigProvider builderConfigProvider) {
    super(ownedValidators, spec);
    this.validatorApiChannel = validatorApiChannel;
    this.builderConfigProvider = builderConfigProvider;
  }

  @Override
  void publishPreferences(
      final UInt64 epoch,
      final List<ProposerDuty> ownedProposerDuties,
      final Bytes32 dependentRoot) {
    final Stream<SafeFuture<List<BuilderPreferencesEntry>>> builderPreferencesFutures =
        ownedProposerDuties.stream()
            .map(
                duty -> {
                  final BLSPublicKey proposerPubkey = duty.getPublicKey();
                  final Optional<Validator> validator =
                      ownedValidators.getValidator(proposerPubkey);
                  if (validator.isEmpty()) {
                    return SafeFuture.completedFuture(List.of());
                  }
                  return builderConfigProvider
                      .getBuilderConfig(validator.get(), duty.getSlot())
                      .thenApply(
                          maybeBuilderConfig -> {
                            if (maybeBuilderConfig.isEmpty()) {
                              return List.<BuilderPreferencesEntry>of();
                            }
                            final BuilderConfig builderConfig = maybeBuilderConfig.get();
                            return createBuilderPreferences(builderConfig, proposerPubkey);
                          })
                      .exceptionally(
                          ex -> {
                            LOG.warn(
                                "Could not create builder preferences entry for proposerPubkey {} and slot {}",
                                proposerPubkey,
                                duty.getSlot(),
                                ex);
                            return List.of();
                          });
                });
    SafeFuture.collectAll(builderPreferencesFutures)
        .thenCompose(
            unflattenedBuilderPreferences -> {
              final SszList<BuilderPreferencesEntry> builderPreferences =
                  unflattenedBuilderPreferences.stream()
                      .flatMap(List::stream)
                      .collect(ApiSchemas.BUILDER_PREFERENCES_ENTRIES_SCHEMA.collector());
              return sendBuilderPreferences(builderPreferences);
            })
        .finish(error -> VALIDATOR_LOGGER.builderPreferencesPublicationFailed(epoch, error));
  }

  private SafeFuture<Void> sendBuilderPreferences(
      final SszList<BuilderPreferencesEntry> builderPreferences) {
    if (builderPreferences.isEmpty()) {
      return SafeFuture.COMPLETE;
    }
    return validatorApiChannel
        .sendBuilderPreferences(builderPreferences)
        .thenAccept(
            errors -> {
              if (!errors.isEmpty()) {
                throw new IllegalArgumentException(
                    errors.stream()
                        .map(SubmitDataError::message)
                        .collect(Collectors.joining("; ")));
              }
              LOG.debug("{} builder preferences published successfully", builderPreferences.size());
            });
  }

  private List<BuilderPreferencesEntry> createBuilderPreferences(
      final BuilderConfig builderConfig, final BLSPublicKey proposerPubkey) {
    return builderConfig.getBuilders().stream()
        .map(
            builder ->
                ApiSchemas.BUILDER_PREFERENCES_ENTRY_SCHEMA.create(
                    proposerPubkey,
                    builder.getUrlBytes(),
                    builder.getAuth(),
                    builder.getMaxExecutionPayment()))
        .toList();
  }
}
