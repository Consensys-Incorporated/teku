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

package tech.pegasys.teku.validator.client.restapi.apis;

import static org.assertj.core.api.AssertionsForClassTypes.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static tech.pegasys.teku.infrastructure.http.HttpStatusCodes.SC_BAD_REQUEST;

import com.fasterxml.jackson.core.JsonProcessingException;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import tech.pegasys.teku.api.exceptions.BadRequestException;
import tech.pegasys.teku.infrastructure.restapi.endpoints.RestApiRequest;
import tech.pegasys.teku.validator.client.OwnedKeyManager;
import tech.pegasys.teku.validator.client.restapi.apis.schema.PostKeysRequest;
import tech.pegasys.teku.validator.client.slashingriskactions.SlashingRiskAction;

public class PostKeysTest {
  private final OwnedKeyManager keyManager = mock(OwnedKeyManager.class);
  private final RestApiRequest request = mock(RestApiRequest.class);
  private final SlashingRiskAction doppelgangerDetectionAction = mock(SlashingRiskAction.class);

  @Test
  void shouldRespondBadRequestIfPasswordsAndKeystoresMisMatch(@TempDir final Path tempDir)
      throws JsonProcessingException {
    final PostKeys endpoint =
        new PostKeys(keyManager, tempDir, Optional.empty(), doppelgangerDetectionAction);
    final PostKeysRequest body = new PostKeysRequest();
    body.setKeystores(List.of("{}"));
    body.setPasswords(List.of());
    when(request.getRequestBody()).thenReturn(body);
    endpoint.handleRequest(request);
    verify(request)
        .respondError(
            SC_BAD_REQUEST, "Keystores count (1) and Passwords count (0) differ, cannot proceed.");
    verify(request, never()).respondOk(any());
  }

  @Test
  void shouldNotImportSlashingProtectionWithoutKeysPresent(@TempDir final Path tempDir)
      throws JsonProcessingException {
    final PostKeys endpoint =
        new PostKeys(keyManager, tempDir, Optional.empty(), doppelgangerDetectionAction);
    final PostKeysRequest body = new PostKeysRequest();
    body.setSlashingProtection(Optional.of("{}"));
    when(request.getRequestBody()).thenReturn(body);

    endpoint.handleRequest(request);
    verify(request).respondOk(List.of());
  }

  @Test
  void emptyRequest_shouldGiveEmptySuccess(@TempDir final Path tempDir)
      throws JsonProcessingException {
    final PostKeys endpoint =
        new PostKeys(keyManager, tempDir, Optional.empty(), doppelgangerDetectionAction);
    final PostKeysRequest body = new PostKeysRequest();
    when(request.getRequestBody()).thenReturn(body);

    endpoint.handleRequest(request);
    verify(request).respondOk(List.of());
  }

  @Test
  void shouldRespondBadRequestIfSlashingProtectionImportFails(@TempDir final Path tempDir)
      throws JsonProcessingException {
    final PostKeys endpoint =
        new PostKeys(keyManager, tempDir, Optional.empty(), doppelgangerDetectionAction);
    final PostKeysRequest body = new PostKeysRequest();
    body.setSlashingProtection(Optional.of("{}"));
    body.setPasswords(List.of("pass"));
    body.setKeystores(List.of("keystore"));
    when(request.getRequestBody()).thenReturn(body);

    assertThatThrownBy(() -> endpoint.handleRequest(request))
        .isInstanceOf(BadRequestException.class)
        .hasMessageStartingWith("Failed to load data. required fields:");
  }
}
