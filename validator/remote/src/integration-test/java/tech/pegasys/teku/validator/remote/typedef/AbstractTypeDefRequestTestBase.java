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

package tech.pegasys.teku.validator.remote.typedef;

import static tech.pegasys.teku.ethereum.json.types.SharedApiTypes.withDataWrapper;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.io.Resources;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import okhttp3.OkHttpClient;
import okhttp3.mockwebserver.MockWebServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import tech.pegasys.teku.infrastructure.json.JsonUtil;
import tech.pegasys.teku.infrastructure.ssz.SszData;
import tech.pegasys.teku.infrastructure.ssz.schema.SszSchema;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.SpecMilestone;
import tech.pegasys.teku.spec.TestSpecInvocationContextProvider.SpecContext;
import tech.pegasys.teku.spec.schemas.SchemaDefinitions;
import tech.pegasys.teku.spec.util.DataStructureUtil;

/** Use {@link #serializeSszObjectToJsonWithDataWrapper(SszData)} for serializing object to JSON */
public class AbstractTypeDefRequestTestBase {

  protected static final String JSON_CONTENT_TYPE = "application/json; charset=utf-8";
  protected static final String OCTET_STREAM_CONTENT_TYPE = "application/octet-stream";

  protected static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

  protected DataStructureUtil dataStructureUtil;
  protected SchemaDefinitions schemaDefinitions;
  protected Spec spec;
  protected SpecMilestone specMilestone;

  protected final MockWebServer mockWebServer = new MockWebServer();
  protected final OkHttpClient okHttpClient = new OkHttpClient.Builder().build();

  @BeforeEach
  public void beforeEach(final SpecContext specContext) throws Exception {
    mockWebServer.start();
    dataStructureUtil = specContext.getDataStructureUtil();
    schemaDefinitions = specContext.getSchemaDefinitions();
    spec = specContext.getSpec();
    specMilestone = specContext.getSpecMilestone();
  }

  @AfterEach
  public void afterEach() throws Exception {
    mockWebServer.shutdown();
    okHttpClient.dispatcher().executorService().shutdown();
    okHttpClient.connectionPool().evictAll();
  }

  @SuppressWarnings("unchecked")
  protected String serializeSszObjectToJsonWithDataWrapper(final SszData value) throws Exception {
    return JsonUtil.serialize(value, withDataWrapper((SszSchema<SszData>) value.getSchema()));
  }

  protected String readResource(final String resource) {
    try {
      return Resources.toString(Resources.getResource(resource), StandardCharsets.UTF_8);
    } catch (IOException ex) {
      throw new UncheckedIOException(ex);
    }
  }
}
