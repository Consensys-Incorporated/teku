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

package tech.pegasys.teku.statetransition.validation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Consumer;
import org.apache.tuweni.bytes.Bytes32;
import org.junit.jupiter.api.BeforeEach;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.metrics.StubMetricsSystem;
import tech.pegasys.teku.infrastructure.metrics.TekuMetricCategory;
import tech.pegasys.teku.infrastructure.time.StubTimeProvider;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.config.builder.SpecConfigBuilder;
import tech.pegasys.teku.spec.datastructures.blobs.DataColumnSidecar;
import tech.pegasys.teku.spec.logic.common.statetransition.results.BlockImportResult;
import tech.pegasys.teku.spec.util.DataStructureUtil;
import tech.pegasys.teku.statetransition.datacolumns.BlobKzgCommitmentsProvider;

/**
 * Abstract base class for testing DataColumnSidecarGossipValidator across different forks.
 * Subclasses should implement createSpec() to provide fork-specific Spec instances.
 */
abstract class AbstractDataColumnSidecarGossipValidatorTest {
  protected final Map<Bytes32, BlockImportResult> invalidBlocks = new HashMap<>();
  protected final StubMetricsSystem metricsSystemStub = new StubMetricsSystem();
  protected final StubTimeProvider stubTimeProvider = StubTimeProvider.withTimeInMillis(0);
  protected final BlobKzgCommitmentsProvider blobKzgCommitmentsProvider =
      mock(BlobKzgCommitmentsProvider.class);

  protected DataStructureUtil dataStructureUtil;
  protected DataColumnSidecarGossipValidator dataColumnSidecarGossipValidator;
  protected UInt64 slot;
  protected UInt64 index;
  protected DataColumnSidecar dataColumnSidecar;

  public abstract Spec createSpec(final Consumer<SpecConfigBuilder> configAdapter);

  @BeforeEach
  void setUpBlobKzgCommitmentsProvider() {
    when(blobKzgCommitmentsProvider.getBlobKzgCommitments(any(DataColumnSidecar.class)))
        .thenAnswer(
            invocation -> {
              final DataColumnSidecar sidecar = invocation.getArgument(0);
              return SafeFuture.completedFuture(sidecar.getMaybeKzgCommitments());
            });
  }

  protected void assertValidationMetrics(final Map<ValidationResultCode, Integer> values) {
    assertThat(
            metricsSystemStub.getCounterValue(
                TekuMetricCategory.BEACON, "data_column_sidecar_processing_requests_total"))
        .isEqualTo(values.values().stream().map(Integer::longValue).reduce(0L, Long::sum));

    values.forEach(
        (validationResultCode, count) -> {
          assertThat(
                  metricsSystemStub.getLabelledCounterValue(
                      TekuMetricCategory.BEACON,
                      "data_column_sidecar_processing_validated_total",
                      validationResultCode.name()))
              .isEqualTo(count.longValue());

          if (validationResultCode == ValidationResultCode.ACCEPT) {
            assertThat(
                    metricsSystemStub.getCounterValue(
                        TekuMetricCategory.BEACON,
                        "data_column_sidecar_processing_successes_total"))
                .isEqualTo(count.longValue());
          }
        });
  }
}
