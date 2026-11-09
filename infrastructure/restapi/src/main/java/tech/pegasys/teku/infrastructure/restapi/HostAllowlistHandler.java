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

package tech.pegasys.teku.infrastructure.restapi;

import static tech.pegasys.teku.infrastructure.http.HostAllowlistUtils.isHostAuthorized;

import io.javalin.http.Context;
import io.javalin.http.ForbiddenResponse;
import io.javalin.http.Handler;
import java.util.List;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class HostAllowlistHandler implements Handler {
  private static final Logger LOG = LogManager.getLogger();

  private final List<String> hostAllowlist;

  public HostAllowlistHandler(final List<String> hostAllowlist) {
    this.hostAllowlist = hostAllowlist;
  }

  @Override
  public void handle(final Context ctx) throws Exception {
    String header = ctx.host();
    if (!isHostAuthorized(hostAllowlist, header)) {
      LOG.debug("Host not authorized " + header);
      throw new ForbiddenResponse("Host not authorized");
    }
  }
}
