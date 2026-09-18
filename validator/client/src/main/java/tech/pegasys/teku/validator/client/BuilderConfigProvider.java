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

import com.google.common.annotations.VisibleForTesting;
import com.google.common.net.InetAddresses;
import java.net.IDN;
import java.net.MalformedURLException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.stream.Stream;
import org.apache.tuweni.bytes.Bytes;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.collections.cache.LRUCache;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.SpecMilestone;
import tech.pegasys.teku.spec.datastructures.builder.versions.gloas.BuilderConfig;
import tech.pegasys.teku.spec.datastructures.builder.versions.gloas.BuilderConfigSchema;
import tech.pegasys.teku.spec.datastructures.builder.versions.gloas.BuilderEntry;
import tech.pegasys.teku.spec.datastructures.builder.versions.gloas.BuilderRequestAuth;
import tech.pegasys.teku.spec.datastructures.builder.versions.gloas.SignedBuilderRequestAuth;
import tech.pegasys.teku.spec.schemas.ApiSchemas;
import tech.pegasys.teku.validator.api.ValidatorConfig;

public class BuilderConfigProvider {

  private final LRUCache<URI, Bytes> cachedDefaultAuthData =
      LRUCache.create((int) BuilderConfigSchema.MAX_BUILDER_ENTRIES);

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
            .map(
                builderUrl -> {
                  final BuilderRequestAuth auth =
                      ApiSchemas.BUILDER_REQUEST_AUTH_SCHEMA.create(getAuthData(builderUrl), slot);
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
                                Bytes.of(builderUrl.toString().getBytes(StandardCharsets.UTF_8)),
                                signedAuth,
                                List.of(),
                                validatorConfig.getBuilderMaxExecutionPayment(),
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

  private Bytes getAuthData(final URI builderUrl) {
    return cachedDefaultAuthData.get(
        builderUrl,
        __ -> Bytes.of(getDefaultAuthData(builderUrl).getBytes(StandardCharsets.US_ASCII)));
  }

  // Spec: hostname lowercased, IPv6 in compressed form (RFC 5952) inside brackets
  @VisibleForTesting
  String getDefaultAuthData(final URI builderUrl) {
    try {
      // java.net.URL handles raw Unicode hostnames without leaving getHost() null
      final String host = builderUrl.toURL().getHost();
      // Handle IPv6 inside brackets
      if (host.startsWith("[") && host.endsWith("]")) {
        final String ipv6 = host.substring(1, host.length() - 1);
        return "[" + InetAddresses.toAddrString(InetAddresses.forString(ipv6)) + "]";
      }
      // IDN.toASCII automatically converts Unicode -> Punycode (and lowercases)
      return IDN.toASCII(host).toLowerCase(Locale.ROOT);
    } catch (MalformedURLException ex) {
      throw new IllegalArgumentException("Invalid URL: " + builderUrl, ex);
    }
  }
}
