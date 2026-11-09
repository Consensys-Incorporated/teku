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

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;
import static tech.pegasys.teku.infrastructure.async.SafeFuture.completedFuture;

import java.net.MalformedURLException;
import java.net.URI;
import java.util.List;
import java.util.Optional;
import org.apache.tuweni.bytes.Bytes;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.bls.BLSPublicKey;
import tech.pegasys.teku.bls.BLSSignature;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.TestSpecFactory;
import tech.pegasys.teku.spec.datastructures.builder.versions.gloas.BuilderConfig;
import tech.pegasys.teku.spec.datastructures.builder.versions.gloas.BuilderEntry;
import tech.pegasys.teku.spec.datastructures.builder.versions.gloas.BuilderRequestAuth;
import tech.pegasys.teku.spec.datastructures.type.SszPublicKey;
import tech.pegasys.teku.spec.schemas.ApiSchemas;
import tech.pegasys.teku.spec.signatures.Signer;
import tech.pegasys.teku.spec.util.DataStructureUtil;
import tech.pegasys.teku.validator.api.FileBackedGraffitiProvider;
import tech.pegasys.teku.validator.client.ResolvedBuilderConfig.ResolvedBuilderEntry;

class DefaultBuilderConfigProviderTest {

  private static final String BUILDER_URL = "https://builder.example.com";
  private static final UInt64 SLOT = UInt64.valueOf(42);

  private final Spec spec = TestSpecFactory.createMinimalGloas();
  private final DataStructureUtil dataStructureUtil = new DataStructureUtil(spec);
  private final Signer signer = mock(Signer.class);
  private final ProposerConfigPropertiesProvider proposerConfigPropertiesProvider =
      mock(ProposerConfigPropertiesProvider.class);
  private final Validator validator =
      new Validator(
          dataStructureUtil.randomPublicKey(),
          signer,
          new FileBackedGraffitiProvider(Optional.empty(), Optional.empty()));

  private final DefaultBuilderConfigProvider provider =
      new DefaultBuilderConfigProvider(spec, proposerConfigPropertiesProvider);

  @BeforeEach
  void setUp() {
    when(proposerConfigPropertiesProvider.isReadyToProvideProperties()).thenReturn(true);
  }

  @Test
  void shouldReturnEmptyPreGloas() {
    final DefaultBuilderConfigProvider provider =
        new DefaultBuilderConfigProvider(
            TestSpecFactory.createMinimalFulu(), proposerConfigPropertiesProvider);

    assertThat(provider.getBuilderConfig(validator, UInt64.ZERO))
        .isCompletedWithValue(Optional.empty());

    verifyNoInteractions(proposerConfigPropertiesProvider);
  }

  @Test
  void shouldReturnNonBiasedBuilderConfigIfProposerConfigPropertiesProviderIsNotReady() {
    when(proposerConfigPropertiesProvider.isReadyToProvideProperties()).thenReturn(false);
    assertThat(provider.getBuilderConfig(validator, UInt64.ZERO))
        .isCompletedWithValue(Optional.of(BuilderConfig.NO_OP));
  }

  @Test
  void shouldReturnBuilderConfigForGloas() throws MalformedURLException {
    final UInt64 minBid = UInt64.valueOf(100);
    final UInt64 boostFactor = UInt64.valueOf(80);
    final UInt64 builderMinBid = UInt64.valueOf(120);
    final UInt64 builderBoostFactor = UInt64.valueOf(90);
    final UInt64 maxExecutionPayment = UInt64.valueOf(67);
    final Bytes authData = dataStructureUtil.randomBytes(12);
    final List<BLSPublicKey> builderPubkeys = List.of(dataStructureUtil.randomPublicKey());
    final BLSSignature signature = dataStructureUtil.randomSignature();

    when(proposerConfigPropertiesProvider.resolveBuilderConfig(validator.getPublicKey()))
        .thenReturn(
            new ResolvedBuilderConfig(
                minBid,
                boostFactor,
                List.of(
                    new ResolvedBuilderEntry(
                        URI.create(BUILDER_URL).toURL(),
                        authData,
                        builderPubkeys,
                        maxExecutionPayment,
                        builderMinBid,
                        builderBoostFactor))));

    final BuilderRequestAuth expectedAuth =
        ApiSchemas.BUILDER_REQUEST_AUTH_SCHEMA.create(authData, SLOT);
    when(signer.signBuilderRequestAuth(expectedAuth)).thenReturn(completedFuture(signature));

    final Optional<BuilderConfig> result = provider.getBuilderConfig(validator, SLOT).join();

    verify(proposerConfigPropertiesProvider).resolveBuilderConfig(validator.getPublicKey());

    assertThat(result).isPresent();
    final BuilderConfig builderConfig = result.get();
    assertThat(builderConfig.getMinBid()).isEqualTo(minBid);
    assertThat(builderConfig.getBuilderBoostFactor()).isEqualTo(boostFactor);
    assertThat(builderConfig.getBuilders()).hasSize(1);

    final BuilderEntry entry = builderConfig.getBuilders().get(0);
    assertThat(entry.getUrl()).isEqualTo(BUILDER_URL);
    assertThat(entry.getAuth().getMessage()).isEqualTo(expectedAuth);
    assertThat(entry.getAuth().getSignature()).isEqualTo(signature);
    assertThat(entry.getBuilderPubkeys())
        .extracting(SszPublicKey::getBLSPublicKey)
        .containsExactlyElementsOf(builderPubkeys);
    assertThat(entry.getMaxExecutionPayment()).isEqualTo(maxExecutionPayment);
    assertThat(entry.getMinBid()).isEqualTo(builderMinBid);
    assertThat(entry.getBuilderBoostFactor()).isEqualTo(builderBoostFactor);
  }

  @Test
  void shouldUseConfiguredAuthData() throws MalformedURLException {
    final Bytes authData = dataStructureUtil.randomBytes(16);
    final BLSSignature signature = dataStructureUtil.randomSignature();

    when(proposerConfigPropertiesProvider.resolveBuilderConfig(validator.getPublicKey()))
        .thenReturn(
            new ResolvedBuilderConfig(
                UInt64.ZERO,
                UInt64.valueOf(100),
                List.of(
                    new ResolvedBuilderEntry(
                        URI.create(BUILDER_URL).toURL(),
                        authData,
                        List.of(),
                        UInt64.ZERO,
                        UInt64.ZERO,
                        UInt64.valueOf(100)))));

    final BuilderRequestAuth expectedAuth =
        ApiSchemas.BUILDER_REQUEST_AUTH_SCHEMA.create(authData, SLOT);
    when(signer.signBuilderRequestAuth(expectedAuth)).thenReturn(completedFuture(signature));

    final Optional<BuilderConfig> result = provider.getBuilderConfig(validator, SLOT).join();

    assertThat(result).isPresent();
    assertThat(result.get().getBuilders().get(0).getAuth().getMessage()).isEqualTo(expectedAuth);
  }

  @Test
  void shouldDropBuilderWhichFailsAndKeepTheRest() throws MalformedURLException {
    final Bytes failingAuthData = Bytes.fromHexString("0x01");
    final Bytes authData = Bytes.fromHexString("0x02");
    when(proposerConfigPropertiesProvider.resolveBuilderConfig(validator.getPublicKey()))
        .thenReturn(
            new ResolvedBuilderConfig(
                UInt64.ZERO,
                UInt64.valueOf(100),
                List.of(
                    new ResolvedBuilderEntry(
                        URI.create("https://failing-builder.example.com").toURL(),
                        failingAuthData,
                        List.of(),
                        UInt64.ZERO,
                        UInt64.ZERO,
                        UInt64.valueOf(100)),
                    new ResolvedBuilderEntry(
                        URI.create(BUILDER_URL).toURL(),
                        authData,
                        List.of(),
                        UInt64.ZERO,
                        UInt64.ZERO,
                        UInt64.valueOf(100)))));
    when(signer.signBuilderRequestAuth(
            ApiSchemas.BUILDER_REQUEST_AUTH_SCHEMA.create(failingAuthData, SLOT)))
        .thenReturn(SafeFuture.failedFuture(new IllegalStateException("signing failed")));
    when(signer.signBuilderRequestAuth(
            ApiSchemas.BUILDER_REQUEST_AUTH_SCHEMA.create(authData, SLOT)))
        .thenReturn(completedFuture(dataStructureUtil.randomSignature()));

    final Optional<BuilderConfig> result = provider.getBuilderConfig(validator, SLOT).join();

    assertThat(result).isPresent();
    assertThat(result.get().getBuilders())
        .extracting(BuilderEntry::getUrl)
        .containsExactly(BUILDER_URL);
  }

  @Test
  void shouldReturnEmptyBuildersWhenNoneConfigured() {
    when(proposerConfigPropertiesProvider.resolveBuilderConfig(validator.getPublicKey()))
        .thenReturn(new ResolvedBuilderConfig(UInt64.ZERO, UInt64.valueOf(100), List.of()));

    final Optional<BuilderConfig> result = provider.getBuilderConfig(validator, SLOT).join();

    assertThat(result).isPresent();
    assertThat(result.get().getBuilders()).isEmpty();
    verifyNoInteractions(signer);
  }

  @Test
  void shouldReturnCachedBuilderConfigOnSubsequentCalls() throws MalformedURLException {
    final BLSSignature signature = dataStructureUtil.randomSignature();

    when(proposerConfigPropertiesProvider.resolveBuilderConfig(validator.getPublicKey()))
        .thenReturn(
            new ResolvedBuilderConfig(
                UInt64.ZERO,
                UInt64.valueOf(100),
                List.of(
                    new ResolvedBuilderEntry(
                        URI.create(BUILDER_URL).toURL(),
                        Bytes.EMPTY,
                        List.of(),
                        UInt64.ZERO,
                        UInt64.ZERO,
                        UInt64.valueOf(100)))));
    when(signer.signBuilderRequestAuth(any())).thenReturn(completedFuture(signature));

    final Optional<BuilderConfig> first = provider.getBuilderConfig(validator, SLOT).join();

    verify(signer).signBuilderRequestAuth(any());

    final Optional<BuilderConfig> second = provider.getBuilderConfig(validator, SLOT).join();

    assertThat(second).isEqualTo(first);
    verifyNoMoreInteractions(signer);
    // served from the cache, so the proposer config is not read again
    verify(proposerConfigPropertiesProvider, times(2)).isReadyToProvideProperties();
    verify(proposerConfigPropertiesProvider).resolveBuilderConfig(validator.getPublicKey());
    verifyNoMoreInteractions(proposerConfigPropertiesProvider);
  }
}
