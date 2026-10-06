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

package tech.pegasys.teku.builder.rest;

import okhttp3.OkHttpClient;
import okhttp3.mockwebserver.MockWebServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.TestSpecInvocationContextProvider.SpecContext;
import tech.pegasys.teku.spec.util.DataStructureUtil;

public abstract class AbstractBuilderRequestTestBase {

  protected DataStructureUtil dataStructureUtil;
  protected Spec spec;

  protected final MockWebServer mockWebServer = new MockWebServer();
  protected final OkHttpClient okHttpClient = new OkHttpClient.Builder().build();

  @BeforeEach
  public void beforeEach(final SpecContext specContext) throws Exception {
    mockWebServer.start();
    dataStructureUtil = specContext.getDataStructureUtil();
    spec = specContext.getSpec();
  }

  @AfterEach
  public void afterEach() throws Exception {
    mockWebServer.shutdown();
    okHttpClient.dispatcher().executorService().shutdown();
    okHttpClient.connectionPool().evictAll();
  }
}
