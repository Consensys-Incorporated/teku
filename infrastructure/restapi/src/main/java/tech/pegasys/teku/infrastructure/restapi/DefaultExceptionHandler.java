/*
 * Copyright Consensys Software Inc., 2026
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

import static tech.pegasys.teku.infrastructure.http.HttpStatusCodes.SC_INTERNAL_SERVER_ERROR;
import static tech.pegasys.teku.infrastructure.json.types.CoreTypes.HTTP_ERROR_RESPONSE_TYPE;

import io.javalin.http.Context;
import io.javalin.http.ExceptionHandler;
import java.io.EOFException;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import tech.pegasys.teku.infrastructure.exceptions.ExceptionUtil;
import tech.pegasys.teku.infrastructure.exceptions.FatalErrorHandler;
import tech.pegasys.teku.infrastructure.http.HttpErrorResponse;
import tech.pegasys.teku.infrastructure.json.JsonUtil;

public class DefaultExceptionHandler<T extends Exception> implements ExceptionHandler<T> {
  private static final Logger LOG = LogManager.getLogger();

  @Override
  public void handle(final T throwable, final Context context) {
    // Requests must not be able to swallow a fatal error and leave the node running
    FatalErrorHandler.shutdownIfFatalError(throwable, "REST API request");

    if (ExceptionUtil.hasCause(throwable, EOFException.class)) {
      LOG.trace("Connection closed before response could be completed.", throwable);
      return;
    }

    context.status(SC_INTERNAL_SERVER_ERROR);
    try {
      LOG.error("Failed to process request to URL {}", context.url(), throwable);
      context.json(
          JsonUtil.serialize(
              new HttpErrorResponse(SC_INTERNAL_SERVER_ERROR, "An unexpected error occurred"),
              HTTP_ERROR_RESPONSE_TYPE));
    } catch (final Throwable t) {
      LOG.error("Default exception handler threw an exception", t);
    }
  }
}
