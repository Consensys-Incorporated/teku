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

package tech.pegasys.teku.networking.nat;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jupnp.model.action.ActionInvocation;
import org.jupnp.model.message.UpnpResponse;
import org.jupnp.model.meta.Service;
import org.jupnp.support.igd.callback.PortMappingDelete;
import org.jupnp.support.model.PortMapping;
import tech.pegasys.teku.infrastructure.async.SafeFuture;

public class TekuPortMappingDelete extends PortMappingDelete {
  private static final Logger LOG = LogManager.getLogger();
  private final SafeFuture<Void> future = new SafeFuture<>();

  public TekuPortMappingDelete(final Service<?, ?> service, final PortMapping portMapping) {
    super(service, portMapping);
  }

  /**
   * Because the underlying jupnp library omits generics info in this method signature, we must too
   * when we override it.
   */
  @Override
  @SuppressWarnings("rawtypes")
  public void success(final ActionInvocation invocation) {
    LOG.info(
        "Port forward {} {} -> {} removed successfully.",
        portMapping.getProtocol(),
        portMapping.getInternalPort(),
        portMapping.getExternalPort());

    future.complete(null);
  }

  /**
   * Because the underlying jupnp library omits generics info in this method signature, we must too
   * when we override it.
   */
  @Override
  @SuppressWarnings("rawtypes")
  public void failure(
      final ActionInvocation invocation, final UpnpResponse operation, final String msg) {
    LOG.warn(
        "Port forward removal request for {} {} -> {} failed (ignoring): {}",
        portMapping.getProtocol(),
        portMapping.getInternalPort(),
        portMapping.getExternalPort(),
        msg);

    // ignore exceptions; we did our best
    future.complete(null);
  }

  public SafeFuture<Void> getFuture() {
    return future;
  }
}
