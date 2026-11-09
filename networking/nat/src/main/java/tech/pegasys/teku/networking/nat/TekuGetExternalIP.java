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

import java.util.Optional;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jupnp.model.action.ActionInvocation;
import org.jupnp.model.message.UpnpResponse;
import org.jupnp.model.meta.RemoteDevice;
import org.jupnp.model.meta.RemoteDeviceIdentity;
import org.jupnp.model.meta.RemoteService;
import org.jupnp.model.meta.Service;
import org.jupnp.support.igd.callback.GetExternalIP;
import tech.pegasys.teku.infrastructure.async.SafeFuture;

public class TekuGetExternalIP extends GetExternalIP {
  private static final Logger LOG = LogManager.getLogger();

  private Optional<String> discoveredOnLocalAddress = Optional.empty();
  private final SafeFuture<String> future = new SafeFuture<>();

  public TekuGetExternalIP(final Service<?, ?> service) {
    super(service);
  }

  /**
   * Override the success(ActionInvocation) version of success so that we can take a peek at the
   * network interface that we discovered this on.
   *
   * <p>Because the underlying jupnp library omits generics info in this method signature, we must
   * too when we override it.
   */
  @Override
  @SuppressWarnings("rawtypes")
  public void success(final ActionInvocation invocation) {
    RemoteService service = (RemoteService) invocation.getAction().getService();
    RemoteDevice device = service.getDevice();
    RemoteDeviceIdentity identity = device.getIdentity();

    discoveredOnLocalAddress = Optional.of(identity.getDiscoveredOnLocalAddress().getHostAddress());

    super.success(invocation);
  }

  @Override
  protected void success(final String result) {

    LOG.info(
        "External IP address {} detected for internal address {}",
        result,
        discoveredOnLocalAddress.get());

    future.complete(result);
  }

  /**
   * Because the underlying jupnp library omits generics info in this method signature, we must too
   * when we override it.
   */
  @Override
  @SuppressWarnings("rawtypes")
  public void failure(
      final ActionInvocation invocation, final UpnpResponse operation, final String msg) {
    future.completeExceptionally(new Exception(msg));
  }

  public SafeFuture<String> getFuture() {
    return future;
  }

  public Optional<String> getDiscoveredOnLocalAddress() {
    return discoveredOnLocalAddress;
  }
}
