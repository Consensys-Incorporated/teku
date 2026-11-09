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

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static tech.pegasys.teku.infrastructure.http.HttpStatusCodes.SC_UNAUTHORIZED;

import io.javalin.http.Context;
import io.javalin.http.HandlerType;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import tech.pegasys.teku.infrastructure.exceptions.InvalidConfigurationException;

public class AuthorizationHandlerTest {
  private static final String PASS = "secure";
  private final Context context = mock(Context.class);

  @Test
  void shouldNotRequireAuthorizationForSwagger(@TempDir final Path tempDir) throws Exception {
    setupPasswordFile(tempDir);
    when(context.method()).thenReturn(HandlerType.GET);
    when(context.path()).thenReturn("/swagger-docs");
    final AuthorizationHandler handler = new AuthorizationHandler(tempDir.resolve("passwd"));
    handler.handle(context);
  }

  @Test
  void shouldNotRequireAuthorizationForWebjars(@TempDir final Path tempDir) throws Exception {
    setupPasswordFile(tempDir);
    when(context.method()).thenReturn(HandlerType.GET);
    when(context.path()).thenReturn("/webjars");
    final AuthorizationHandler handler = new AuthorizationHandler(tempDir.resolve("passwd"));
    handler.handle(context);
  }

  @Test
  void shouldGrantAccessIfHeaderSet(@TempDir final Path tempDir) throws Exception {
    setupPasswordFile(tempDir);
    when(context.method()).thenReturn(HandlerType.DELETE);
    when(context.path()).thenReturn("/aPath");
    when(context.header("Authorization")).thenReturn("Bearer " + PASS);
    final AuthorizationHandler handler = new AuthorizationHandler(tempDir.resolve("passwd"));
    handler.handle(context);
  }

  @Test
  void shouldGrantAccessIfHeaderSetEncoded(@TempDir final Path tempDir) throws Exception {
    // url encode will rewrite the ' ' to a '+'
    Files.writeString(tempDir.resolve("passwd"), PASS + " " + PASS);
    when(context.method()).thenReturn(HandlerType.POST);
    when(context.path()).thenReturn("/aPath");
    when(context.header("Authorization")).thenReturn("Bearer " + PASS + "+" + PASS);
    final AuthorizationHandler handler = new AuthorizationHandler(tempDir.resolve("passwd"));
    handler.handle(context);
  }

  @Test
  void shouldDenyAccessIfHeaderNotSet(@TempDir final Path tempDir) throws Exception {
    setupPasswordFile(tempDir);
    when(context.method()).thenReturn(HandlerType.POST);
    when(context.path()).thenReturn("/aPath");
    when(context.header("Authorization")).thenReturn(null);
    final AuthorizationHandler handler = new AuthorizationHandler(tempDir.resolve("passwd"));
    handler.handle(context);
    verifyUnauthorizedResponse();
  }

  @Test
  void shouldDenyAccessIfHeaderIsNotBearer(@TempDir final Path tempDir) throws Exception {
    setupPasswordFile(tempDir);
    when(context.method()).thenReturn(HandlerType.GET);
    when(context.path()).thenReturn("/aPath");
    when(context.header("Authorization")).thenReturn(PASS);
    final AuthorizationHandler handler = new AuthorizationHandler(tempDir.resolve("passwd"));
    handler.handle(context);
    verifyUnauthorizedResponse();
  }

  @Test
  void shouldDenyAccessIfBearerIsIncorrect(@TempDir final Path tempDir) throws Exception {
    setupPasswordFile(tempDir);
    when(context.method()).thenReturn(HandlerType.GET);
    when(context.path()).thenReturn("/aPath");
    when(context.header("Authorization")).thenReturn("Bearer no");
    final AuthorizationHandler handler = new AuthorizationHandler(tempDir.resolve("passwd"));
    handler.handle(context);
    verifyUnauthorizedResponse();
  }

  @Test
  public void createAuthorizationManagerShouldFailWhenPasswordFileDoesNotExist() {
    final Path directory = Path.of("/foo/bar");

    assertThatThrownBy(() -> new AuthorizationHandler(directory))
        .isInstanceOf(InvalidConfigurationException.class)
        .hasMessageContaining(
            "password file %s does not exist", directory.toFile().getAbsolutePath());
  }

  @Test
  public void createAuthorizationManagerShouldFailWhenCannotReadPasswordFile(
      @TempDir final Path tempDir) throws IOException {
    final Path unreadableFilePath = Files.createFile(tempDir.resolve("unreadable_file"));

    if (!unreadableFilePath.toFile().setReadable(false)) {
      // If the underlying OS does not support setting file permissions we ignore the check on the
      // error message
      assertThatThrownBy(() -> new AuthorizationHandler(unreadableFilePath))
          .isInstanceOf(InvalidConfigurationException.class);
    } else {
      assertThatThrownBy(() -> new AuthorizationHandler(unreadableFilePath))
          .isInstanceOf(InvalidConfigurationException.class)
          .hasMessageContaining(
              "cannot read password file %s", unreadableFilePath.toFile().getAbsolutePath());
    }
  }

  @Test
  public void createAuthorizationManagerShouldFailWhenPasswordFileIsADirectory(
      @TempDir final Path tempDir) throws IOException {
    final Path directory = Files.createDirectories(tempDir);

    assertThatThrownBy(() -> new AuthorizationHandler(directory))
        .isInstanceOf(InvalidConfigurationException.class)
        .hasMessageContaining(
            "password file %s is a directory", directory.toFile().getAbsolutePath());
  }

  @Test
  public void createAuthorizationManagerShouldFailWhenPasswordFileIsEmpty(
      @TempDir final Path tempDir) throws IOException {
    final Path directory = Files.writeString(tempDir.resolve("passwd"), "");

    assertThatThrownBy(() -> new AuthorizationHandler(directory))
        .isInstanceOf(InvalidConfigurationException.class)
        .hasMessageContaining("password file %s is empty", directory.toFile().getAbsolutePath());
  }

  private void verifyUnauthorizedResponse() {
    verify(context).status(SC_UNAUTHORIZED);
    verify(context).json(any());
    verify(context).skipRemainingHandlers();
  }

  private void setupPasswordFile(final Path tempDir) throws IOException {
    Files.writeString(tempDir.resolve("passwd"), PASS);
  }
}
