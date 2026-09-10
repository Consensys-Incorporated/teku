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

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static tech.pegasys.teku.infrastructure.async.SafeFuture.completedFuture;
import static tech.pegasys.teku.spec.config.SpecConfigGloas.MAX_EXECUTION_PAYMENT;

import java.net.MalformedURLException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Optional;
import org.apache.tuweni.bytes.Bytes;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.bls.BLSSignature;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.TestSpecFactory;
import tech.pegasys.teku.spec.datastructures.builder.versions.gloas.BuilderConfig;
import tech.pegasys.teku.spec.datastructures.builder.versions.gloas.BuilderEntry;
import tech.pegasys.teku.spec.datastructures.builder.versions.gloas.BuilderRequestAuth;
import tech.pegasys.teku.spec.schemas.ApiSchemas;
import tech.pegasys.teku.spec.signatures.Signer;
import tech.pegasys.teku.spec.util.DataStructureUtil;
import tech.pegasys.teku.validator.api.FileBackedGraffitiProvider;
import tech.pegasys.teku.validator.api.ValidatorConfig;

class BuilderConfigProviderTest {

  private final Spec spec = TestSpecFactory.createMinimalGloas();
  private final DataStructureUtil dataStructureUtil = new DataStructureUtil(spec);
  private final Signer signer = mock(Signer.class);
  private final Validator validator =
      new Validator(
          dataStructureUtil.randomPublicKey(),
          signer,
          new FileBackedGraffitiProvider(Optional.empty(), Optional.empty()));

  @Test
  void shouldReturnEmptyPreGloas() {
    final Spec spec = TestSpecFactory.createMinimalFulu();
    final BuilderConfigProvider provider =
        new BuilderConfigProvider(spec, ValidatorConfig.builder().build());

    assertThat(provider.getBuilderConfig(validator, UInt64.ZERO))
        .isCompletedWithValue(Optional.empty());
  }

  @Test
  void shouldReturnBuilderConfigForGloas() throws MalformedURLException {
    final String builderUrl = "https://builder.example.com";
    final UInt64 slot = UInt64.valueOf(42);
    final UInt64 minBid = UInt64.valueOf(100);
    final UInt64 boostFactor = UInt64.valueOf(80);
    final BLSSignature signature = dataStructureUtil.randomSignature();

    final ValidatorConfig config =
        ValidatorConfig.builder()
            .builderUrls(List.of(URI.create(builderUrl).toURL()))
            .builderMinBid(minBid)
            .builderBoostFactor(boostFactor)
            .build();

    final BuilderRequestAuth expectedAuth =
        ApiSchemas.BUILDER_REQUEST_AUTH_SCHEMA.create(
            Bytes.of(builderUrl.getBytes(StandardCharsets.UTF_8)), slot);
    when(signer.signBuilderRequestAuth(expectedAuth)).thenReturn(completedFuture(signature));

    final BuilderConfigProvider provider = new BuilderConfigProvider(spec, config);
    final Optional<BuilderConfig> result = provider.getBuilderConfig(validator, slot).join();

    assertThat(result).isPresent();
    final BuilderConfig builderConfig = result.get();
    assertThat(builderConfig.getMinBid()).isEqualTo(minBid);
    assertThat(builderConfig.getBuilderBoostFactor()).isEqualTo(boostFactor);
    assertThat(builderConfig.getBuilders()).hasSize(1);

    final BuilderEntry entry = builderConfig.getBuilders().get(0);
    assertThat(entry.getUrl()).isEqualTo(builderUrl);
    assertThat(entry.getAuth().getMessage()).isEqualTo(expectedAuth);
    assertThat(entry.getAuth().getSignature()).isEqualTo(signature);
    assertThat(entry.getMinBid()).isEqualTo(minBid);
    assertThat(entry.getBuilderBoostFactor()).isEqualTo(boostFactor);
    assertThat(entry.getMaxExecutionPayment()).isEqualTo(MAX_EXECUTION_PAYMENT);
  }
}
