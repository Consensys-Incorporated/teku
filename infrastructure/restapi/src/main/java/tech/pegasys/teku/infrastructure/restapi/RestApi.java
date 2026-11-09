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

import io.javalin.Javalin;
import io.javalin.util.JavalinBindException;
import java.util.Optional;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.exceptions.InvalidConfigurationException;
import tech.pegasys.teku.service.serviceutils.Service;

public class RestApi extends Service {
  private static final Logger LOG = LogManager.getLogger();
  private final Javalin app;
  private final Optional<String> restApiDocs;

  public RestApi(final Javalin app, final Optional<String> restApiDocs) {
    this.app = app;
    this.restApiDocs = restApiDocs;
  }

  public Optional<String> getRestApiDocs() {
    return restApiDocs;
  }

  @Override
  protected SafeFuture<?> doStart() {
    try {
      app.start();
      LOG.info("Listening on port {}", app.port());
    } catch (final JavalinBindException e) {
      throw new InvalidConfigurationException(e.getMessage());
    } catch (final RuntimeException e) {
      throw new IllegalStateException("Rest API failed to start", e);
    }
    return SafeFuture.COMPLETE;
  }

  public int getListenPort() {
    return app.port();
  }

  @Override
  protected SafeFuture<?> doStop() {
    app.stop();
    return SafeFuture.COMPLETE;
  }
}
