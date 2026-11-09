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

package tech.pegasys.teku.ethereum.executionlayer;

import com.google.common.base.Suppliers;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import tech.pegasys.teku.ethereum.events.SlotEventsChannel;
import tech.pegasys.teku.ethereum.executionclient.ExecutionEngineClient;
import tech.pegasys.teku.ethereum.executionclient.response.ResponseUnwrapper;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.logging.EventLogger;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.Spec;

public class EngineCapabilitiesMonitor implements SlotEventsChannel {

  private static final Logger LOG = LogManager.getLogger();

  private static final int EPOCHS_PER_MONITORING = 10;
  static final UInt64 SLOT_IN_THE_EPOCH_TO_RUN_MONITORING = UInt64.valueOf(3);

  private final AtomicReference<UInt64> lastRunEpoch = new AtomicReference<>();

  private final Spec spec;
  private final EventLogger eventLogger;
  private final Supplier<List<String>> capabilitiesSupplier;
  private final ExecutionEngineClient executionEngineClient;

  public EngineCapabilitiesMonitor(
      final Spec spec,
      final EventLogger eventLogger,
      final EngineJsonRpcMethodsResolver engineMethodsResolver,
      final ExecutionEngineClient executionEngineClient) {
    this.spec = spec;
    this.eventLogger = eventLogger;
    this.capabilitiesSupplier =
        Suppliers.memoize(() -> new ArrayList<>(engineMethodsResolver.getCapabilities()));
    this.executionEngineClient = executionEngineClient;
  }

  @Override
  public void onSlot(final UInt64 slot) {
    final UInt64 currentEpoch = spec.computeEpochAtSlot(slot);
    if (hasNeverRun() || (epochIsApplicable(currentEpoch) && slotIsApplicable(slot))) {
      monitor()
          .handleException(ex -> LOG.error("Exception exchanging Engine API capabilities", ex))
          .always(() -> lastRunEpoch.set(currentEpoch));
    }
  }

  private boolean hasNeverRun() {
    return lastRunEpoch.get() == null;
  }

  private boolean epochIsApplicable(final UInt64 epoch) {
    return epoch.minusMinZero(lastRunEpoch.get()).isGreaterThanOrEqualTo(EPOCHS_PER_MONITORING);
  }

  private boolean slotIsApplicable(final UInt64 slot) {
    return slot.mod(spec.getSlotsPerEpoch(slot))
        .equals(SLOT_IN_THE_EPOCH_TO_RUN_MONITORING.minusMinZero(1));
  }

  private SafeFuture<Void> monitor() {
    final List<String> capabilities = capabilitiesSupplier.get();
    return executionEngineClient
        .exchangeCapabilities(capabilities)
        .thenApply(ResponseUnwrapper::unwrapExecutionClientResponseOrThrow)
        .thenAccept(
            engineCapabilities -> {
              LOG.debug("Engine API capabilities response: {}", engineCapabilities);

              final List<String> missingEngineCapabilities =
                  capabilities.stream()
                      .filter(capability -> !engineCapabilities.contains(capability))
                      .toList();

              if (!missingEngineCapabilities.isEmpty()) {
                eventLogger.missingEngineApiCapabilities(missingEngineCapabilities);
              }
            });
  }
}
