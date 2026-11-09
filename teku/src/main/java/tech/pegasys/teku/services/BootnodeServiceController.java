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

import tech.pegasys.teku.config.TekuConfiguration;
import tech.pegasys.teku.service.serviceutils.ServiceConfig;
import tech.pegasys.teku.services.bootnode.BootnodeService;

public class BootnodeServiceController extends ServiceController {

  public BootnodeServiceController(
      final TekuConfiguration tekuConfig, final ServiceConfig serviceConfig) {
    this.services.add(
        new BootnodeService(
            serviceConfig,
            tekuConfig.network(),
            tekuConfig.p2p(),
            tekuConfig.eth2NetworkConfiguration()));
  }
}
