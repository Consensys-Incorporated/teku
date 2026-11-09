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

package tech.pegasys.teku.validator.beaconnode;

import static tech.pegasys.teku.spec.config.Constants.GENESIS_DATA_RETRY_DELAY;

import com.google.common.base.Suppliers;
import java.util.function.Supplier;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.infrastructure.async.AsyncRunner;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.datastructures.genesis.GenesisData;
import tech.pegasys.teku.validator.api.ValidatorApiChannel;

public class GenesisDataProvider {
  private static final Logger LOG = LogManager.getLogger();
  private final ValidatorApiChannel validatorApiChannel;
  private final AsyncRunner asyncRunner;
  private final Supplier<SafeFuture<GenesisData>> genesisData =
      Suppliers.memoize(this::fetchGenesisData);

  public GenesisDataProvider(
      final AsyncRunner asyncRunner, final ValidatorApiChannel validatorApiChannel) {
    this.validatorApiChannel = validatorApiChannel;
    this.asyncRunner = asyncRunner;
  }

  public SafeFuture<GenesisData> getGenesisData() {
    return genesisData.get();
  }

  public SafeFuture<UInt64> getGenesisTime() {
    return genesisData.get().thenApply(GenesisData::getGenesisTime);
  }

  public SafeFuture<Bytes32> getGenesisValidatorsRoot() {
    return genesisData.get().thenApply(GenesisData::getGenesisValidatorsRoot);
  }

  private SafeFuture<GenesisData> fetchGenesisData() {
    return requestGenesisData()
        .thenApply(
            response ->
                new GenesisData(response.getGenesisTime(), response.getGenesisValidatorsRoot()))
        .exceptionallyCompose(
            error -> {
              LOG.error("Failed to retrieve genesis data. Retrying after delay", error);
              return asyncRunner.runAfterDelay(this::fetchGenesisData, GENESIS_DATA_RETRY_DELAY);
            });
  }

  private SafeFuture<GenesisData> requestGenesisData() {
    return validatorApiChannel
        .getGenesisData()
        .thenCompose(
            maybeGenesisData ->
                maybeGenesisData
                    .map(SafeFuture::completedFuture)
                    .orElseGet(
                        () -> {
                          LOG.info("Waiting for genesis data to be known");
                          return asyncRunner.runAfterDelay(
                              this::requestGenesisData, GENESIS_DATA_RETRY_DELAY);
                        }));
  }
}
