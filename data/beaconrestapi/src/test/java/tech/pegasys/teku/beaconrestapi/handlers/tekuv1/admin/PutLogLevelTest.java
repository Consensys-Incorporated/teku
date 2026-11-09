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

package tech.pegasys.teku.beaconrestapi.handlers.tekuv1.admin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static tech.pegasys.teku.infrastructure.http.HttpStatusCodes.SC_BAD_REQUEST;
import static tech.pegasys.teku.infrastructure.http.HttpStatusCodes.SC_INTERNAL_SERVER_ERROR;
import static tech.pegasys.teku.infrastructure.http.HttpStatusCodes.SC_NO_CONTENT;
import static tech.pegasys.teku.infrastructure.restapi.MetadataTestUtil.getRequestBodyFromMetadata;
import static tech.pegasys.teku.infrastructure.restapi.MetadataTestUtil.verifyMetadataEmptyResponse;
import static tech.pegasys.teku.infrastructure.restapi.MetadataTestUtil.verifyMetadataErrorResponse;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.exc.MismatchedInputException;
import java.io.IOException;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.beaconrestapi.AbstractMigratedBeaconHandlerTest;

public class PutLogLevelTest extends AbstractMigratedBeaconHandlerTest {

  @BeforeEach
  public void setup() {
    setHandler(new PutLogLevel());
  }

  @Test
  public void shouldReturnBadRequestWhenLevelIsInvalid() {
    assertThatThrownBy(() -> request.setRequestBody(new PutLogLevel.LogLevel("I do not exist")))
        .hasMessageContaining("Unknown level constant [I DO NOT EXIST].")
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  public void shouldReturnNoContentWhenLevelIsValid() throws Exception {
    request.setRequestBody(new PutLogLevel.LogLevel("inFO"));
    handler.handleRequest(request);

    assertThat(request.getResponseCode()).isEqualTo(SC_NO_CONTENT);
  }

  @Test
  public void shouldReturnNoContentWhenLevelAndFilterAreValid() throws Exception {
    request.setRequestBody(new PutLogLevel.LogLevel("InfO", List.of("a.class.somewhere")));
    handler.handleRequest(request);

    assertThat(request.getResponseCode()).isEqualTo(SC_NO_CONTENT);
  }

  @Test
  void shouldNotReadInvalidRequestBody() {
    final String data = "{\"level\": \"I do not exist\", \"log_filter\": \"a.class.somewhere\"}";
    assertThatThrownBy(() -> getRequestBodyFromMetadata(handler, data))
        .isInstanceOf(MismatchedInputException.class);
  }

  @Test
  void shouldReadRequestBody() throws IOException {
    final String data = "{\"level\": \"InfO\", \"log_filter\": [\"a.class.somewhere\"]}";
    assertThat(getRequestBodyFromMetadata(handler, data))
        .isEqualTo(new PutLogLevel.LogLevel("INFO", List.of("a.class.somewhere")));
  }

  @Test
  void metadata_shouldHandle204() {
    verifyMetadataEmptyResponse(handler, SC_NO_CONTENT);
  }

  @Test
  void metadata_shouldHandle400() throws JsonProcessingException {
    verifyMetadataErrorResponse(handler, SC_BAD_REQUEST);
  }

  @Test
  void metadata_shouldHandle500() throws JsonProcessingException {
    verifyMetadataErrorResponse(handler, SC_INTERNAL_SERVER_ERROR);
  }
}
