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

package tech.pegasys.teku.statetransition.datacolumns;

import java.util.Optional;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.hyperledger.besu.plugin.services.MetricsSystem;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.metrics.MetricsHistogram;
import tech.pegasys.teku.infrastructure.metrics.MetricsHistogram.Timer;
import tech.pegasys.teku.infrastructure.metrics.TekuMetricCategory;
import tech.pegasys.teku.infrastructure.subscribers.Subscribers;
import tech.pegasys.teku.infrastructure.time.TimeProvider;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.datastructures.blobs.DataColumnSidecar;
import tech.pegasys.teku.statetransition.blobs.RemoteOrigin;
import tech.pegasys.teku.statetransition.datacolumns.log.gossip.DasGossipLogger;
import tech.pegasys.teku.statetransition.validation.DataColumnSidecarGossipValidator;
import tech.pegasys.teku.statetransition.validation.InternalValidationResult;

public class DataColumnSidecarManagerImpl implements DataColumnSidecarManager {
  private static final Logger LOG = LogManager.getLogger();
  private final DataColumnSidecarGossipValidator validator;
  private final Subscribers<ValidDataColumnSidecarsListener> validDataColumnSidecarsSubscribers =
      Subscribers.create(true);
  private final DasGossipLogger dasGossipLogger;
  private final MetricsHistogram histogram;

  public DataColumnSidecarManagerImpl(
      final DataColumnSidecarGossipValidator validator,
      final DasGossipLogger dasGossipLogger,
      final MetricsSystem metricsSystem,
      final TimeProvider timeProvider) {
    this.validator = validator;
    this.dasGossipLogger = dasGossipLogger;
    this.histogram =
        new MetricsHistogram(
            metricsSystem,
            timeProvider,
            TekuMetricCategory.BEACON,
            "data_column_sidecar_gossip_verification_seconds",
            "Full runtime of data column sidecars gossip verification");
  }

  @Override
  public SafeFuture<InternalValidationResult> onDataColumnSidecarGossip(
      final DataColumnSidecar dataColumnSidecar, final Optional<UInt64> arrivalTimestamp) {
    LOG.trace("Received data column sidecar from GOSSIP: {}", dataColumnSidecar);
    final SafeFuture<InternalValidationResult> validation;
    try (Timer ignored = histogram.startTimer()) {
      validation = validator.validate(dataColumnSidecar);
    } catch (final Throwable t) {
      LOG.error(
          "Failed to validate data column sidecar {}", dataColumnSidecar::toLogString, () -> t);
      return SafeFuture.completedFuture(InternalValidationResult.reject("error"));
    }

    return validation.thenPeek(
        res -> {
          dasGossipLogger.onReceive(dataColumnSidecar, res);
          if (res.isAccept()) {
            validDataColumnSidecarsSubscribers.forEach(
                listener -> listener.onNewValidSidecar(dataColumnSidecar, RemoteOrigin.GOSSIP));
          }
        });
  }

  @Override
  public void subscribeToValidDataColumnSidecars(
      final ValidDataColumnSidecarsListener sidecarsListener) {
    validDataColumnSidecarsSubscribers.subscribe(sidecarsListener);
  }
}
