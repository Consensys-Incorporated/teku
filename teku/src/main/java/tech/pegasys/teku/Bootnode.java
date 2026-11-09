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

package tech.pegasys.teku;

import tech.pegasys.teku.config.TekuConfiguration;
import tech.pegasys.teku.services.BootnodeServiceController;
import tech.pegasys.teku.services.ServiceController;

public class Bootnode extends AbstractNode implements BootnodeFacade {

  private final BootnodeServiceController serviceController;

  public Bootnode(final TekuConfiguration tekuConfig) {
    super(tekuConfig);

    this.serviceController = new BootnodeServiceController(tekuConfig, serviceConfig);
  }

  @Override
  public ServiceController getServiceController() {
    return serviceController;
  }
}
