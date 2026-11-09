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

package tech.pegasys.teku.services;

import java.util.Optional;
import tech.pegasys.teku.config.TekuConfiguration;
import tech.pegasys.teku.networking.nat.NatService;
import tech.pegasys.teku.networking.p2p.network.config.NetworkConfig;
import tech.pegasys.teku.service.serviceutils.ServiceConfig;
import tech.pegasys.teku.services.beaconchain.BeaconChainService;
import tech.pegasys.teku.services.chainstorage.StorageService;
import tech.pegasys.teku.services.executionlayer.ExecutionLayerService;
import tech.pegasys.teku.validator.client.ValidatorClientService;
import tech.pegasys.teku.validator.client.slashingriskactions.DoppelgangerDetectionShutDown;
import tech.pegasys.teku.validator.client.slashingriskactions.SlashedValidatorShutDown;
import tech.pegasys.teku.validator.client.slashingriskactions.SlashingRiskAction;

public class BeaconNodeServiceController extends ServiceController {

  public BeaconNodeServiceController(
      final TekuConfiguration tekuConfig, final ServiceConfig serviceConfig) {
    // Note services will be started in the order they are added here.
    services.add(
        new StorageService(
            serviceConfig,
            tekuConfig.storageConfiguration(),
            tekuConfig.metricsConfig().isBlobSidecarsStorageCountersEnabled(),
            tekuConfig.metricsConfig().isDataColumnSidecarsStorageCountersEnabled(),
            tekuConfig.beaconChain().eth2NetworkConfig().getEth2Network()));
    if (tekuConfig.executionLayer().isEnabled()) {
      // Need to make sure the execution engine is listening before starting the beacon chain
      final ExecutionLayerService executionLayerService =
          ExecutionLayerService.create(
              serviceConfig,
              tekuConfig.executionLayer(),
              tekuConfig.eth2NetworkConfiguration().getSpec());
      services.add(executionLayerService);
    }
    final BeaconChainService beaconChainService =
        new BeaconChainService(serviceConfig, tekuConfig.beaconChain());
    services.add(beaconChainService);
    final NetworkConfig networkConfig = tekuConfig.network();
    services.add(
        new NatService(
            tekuConfig.natConfiguration(),
            networkConfig.getListenPort(),
            networkConfig.getNetworkInterfaces().size() == 2
                // IPv4 and IPv6 (dual-stack)
                ? Optional.of(networkConfig.getListenPortIpv6())
                : Optional.empty(),
            tekuConfig.discovery().isDiscoveryEnabled()));

    final Optional<SlashingRiskAction> maybeValidatorSlashedAction =
        tekuConfig.validatorClient().getValidatorConfig().isShutdownWhenValidatorSlashedEnabled()
            ? Optional.of(new SlashedValidatorShutDown())
            : Optional.empty();

    services.add(
        ValidatorClientService.create(
            serviceConfig,
            tekuConfig.validatorClient(),
            new DoppelgangerDetectionShutDown(),
            maybeValidatorSlashedAction));
  }
}
