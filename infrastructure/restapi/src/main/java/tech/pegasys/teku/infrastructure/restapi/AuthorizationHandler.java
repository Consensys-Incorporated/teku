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

import static tech.pegasys.teku.infrastructure.http.HttpStatusCodes.SC_UNAUTHORIZED;

import io.javalin.http.Context;
import io.javalin.http.Handler;
import java.io.File;
import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Stream;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import tech.pegasys.teku.infrastructure.exceptions.InvalidConfigurationException;

public class AuthorizationHandler implements Handler {
  private static final Logger LOG = LogManager.getLogger();
  private static final String BEARER_PREFIX = "Bearer ";
  private final String bearer;

  public AuthorizationHandler(final Path path) {
    bearer = readPasswordFile(path);
  }

  private String readPasswordFile(final Path path) {
    final File passwordFile = path.toFile();
    final StringBuilder errorMessage =
        new StringBuilder("Failed to initialize authorization bearer token - ");

    if (!passwordFile.exists()) {
      errorMessage.append(
          String.format("password file %s does not exist", passwordFile.getAbsolutePath()));
      LOG.error(errorMessage);
      throw new InvalidConfigurationException(errorMessage.toString());
    }

    if (!passwordFile.canRead()) {
      errorMessage.append(
          String.format("cannot read password file %s", passwordFile.getAbsolutePath()));
      LOG.error(errorMessage);
      throw new InvalidConfigurationException(errorMessage.toString());
    }

    if (passwordFile.isDirectory()) {
      errorMessage.append(
          String.format("password file %s is a directory", passwordFile.getAbsolutePath()));
      LOG.error(errorMessage);
      throw new InvalidConfigurationException(errorMessage.toString());
    }

    try (Stream<String> lines = Files.lines(path)) {
      return lines
          .findFirst()
          .map(val -> URLEncoder.encode(val, StandardCharsets.UTF_8))
          .orElseThrow(
              () -> {
                errorMessage.append(
                    String.format("password file %s is empty", passwordFile.getAbsolutePath()));
                LOG.error(errorMessage);
                return new InvalidConfigurationException(errorMessage.toString());
              });
    } catch (IOException e) {
      errorMessage.append(String.format(e.getMessage(), passwordFile.getAbsolutePath()));
      LOG.error(errorMessage);
      throw new InvalidConfigurationException(errorMessage.toString());
    }
  }

  private void unauthorized(final Context ctx, final String message) {
    LOG.debug(message);
    ctx.json("{\n  \"status\": 401, \n \"message\":\"Unauthorized\"\n}");
    ctx.status(SC_UNAUTHORIZED);
    ctx.skipRemainingHandlers();
  }

  @Override
  public void handle(final Context ctx) throws Exception {
    if (ctx.path().startsWith("/swagger-") || ctx.path().startsWith("/webjars")) {
      return;
    }

    if (bearer.isEmpty()) {
      unauthorized(ctx, "API Reject - no bearer loaded by server.");
      return;
    }

    final String auth = ctx.header("Authorization");
    if (auth == null || !auth.startsWith(BEARER_PREFIX)) {
      unauthorized(ctx, "API Reject - authorization bearer missing from request header");
      return;
    }

    if (!auth.substring(BEARER_PREFIX.length()).equals(bearer)) {
      unauthorized(ctx, "API Reject - Unauthorized");
    }
  }
}
