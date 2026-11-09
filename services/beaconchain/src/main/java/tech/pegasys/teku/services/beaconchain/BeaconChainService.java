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

package tech.pegasys.teku.services.beaconchain;

import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.service.serviceutils.Service;
import tech.pegasys.teku.service.serviceutils.ServiceConfig;

public class BeaconChainService extends Service implements BeaconChainServiceFacade {

  private final BeaconChainController controller;

  public BeaconChainService(
      final ServiceConfig serviceConfig, final BeaconChainConfiguration beaconConfig) {
    this.controller =
        beaconConfig.getBeaconChainControllerFactory().create(serviceConfig, beaconConfig);
  }

  @Override
  protected SafeFuture<?> doStart() {
    return this.controller.start();
  }

  @Override
  protected SafeFuture<?> doStop() {
    return this.controller.stop();
  }

  @Override
  public BeaconChainController getBeaconChainController() {
    return controller;
  }
}
