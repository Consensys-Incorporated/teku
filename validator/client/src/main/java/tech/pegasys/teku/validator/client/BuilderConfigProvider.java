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

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;
import org.apache.tuweni.bytes.Bytes;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.SpecMilestone;
import tech.pegasys.teku.spec.config.SpecConfigGloas;
import tech.pegasys.teku.spec.datastructures.builder.versions.gloas.BuilderConfig;
import tech.pegasys.teku.spec.datastructures.builder.versions.gloas.BuilderEntry;
import tech.pegasys.teku.spec.datastructures.builder.versions.gloas.BuilderRequestAuth;
import tech.pegasys.teku.spec.datastructures.builder.versions.gloas.SignedBuilderRequestAuth;
import tech.pegasys.teku.spec.schemas.ApiSchemas;
import tech.pegasys.teku.validator.api.ValidatorConfig;

public class BuilderConfigProvider {

  private final Spec spec;
  private final ValidatorConfig validatorConfig;

  public BuilderConfigProvider(final Spec spec, final ValidatorConfig validatorConfig) {
    this.spec = spec;
    this.validatorConfig = validatorConfig;
  }

  public SafeFuture<Optional<BuilderConfig>> getBuilderConfig(
      final Validator validator, final UInt64 slot) {
    if (!isBuilderConfigRequired(slot)) {
      return SafeFuture.completedFuture(Optional.empty());
    }
    final Stream<SafeFuture<BuilderEntry>> builderEntriesFutures =
        validatorConfig.getBuilderUrls().stream()
            .map(url -> Bytes.of(url.toExternalForm().getBytes(StandardCharsets.UTF_8)))
            .map(
                builderUrl -> {
                  final BuilderRequestAuth auth =
                      ApiSchemas.BUILDER_REQUEST_AUTH_SCHEMA.create(
                          // default to the UTF-8 bytes of the builder's own advertised URL
                          builderUrl, slot);
                  // Authenticates bid requests to the builder
                  return validator
                      .getSigner()
                      .signBuilderRequestAuth(auth)
                      .thenApply(
                          signature -> {
                            final SignedBuilderRequestAuth signedAuth =
                                ApiSchemas.SIGNED_BUILDER_REQUEST_AUTH_SCHEMA.create(
                                    auth, signature);
                            return ApiSchemas.BUILDER_ENTRY_SCHEMA.create(
                                builderUrl,
                                signedAuth,
                                List.of(),
                                SpecConfigGloas.MAX_EXECUTION_PAYMENT,
                                validatorConfig.getBuilderMinBid(),
                                validatorConfig.getBuilderBoostFactor());
                          });
                });
    return SafeFuture.collectAll(builderEntriesFutures)
        .thenApply(
            builderEntries ->
                Optional.of(
                    ApiSchemas.BUILDER_CONFIG_SCHEMA.create(
                        validatorConfig.getBuilderMinBid(),
                        validatorConfig.getBuilderBoostFactor(),
                        builderEntries)));
  }

  private boolean isBuilderConfigRequired(final UInt64 slot) {
    return spec.atSlot(slot).getMilestone().isGreaterThanOrEqualTo(SpecMilestone.GLOAS);
  }
}
