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

package tech.pegasys.teku.ethereum.executionclient.rest;

import java.time.Duration;
import java.util.Collections;
import java.util.Map;
import tech.pegasys.teku.ethereum.executionclient.schema.BuilderApiResponse;
import tech.pegasys.teku.ethereum.executionclient.schema.Response;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.json.types.DeserializableTypeDefinition;
import tech.pegasys.teku.infrastructure.ssz.SszData;
import tech.pegasys.teku.infrastructure.ssz.schema.SszSchema;

public interface RestClient {
  Map<String, String> NO_HEADERS = Collections.emptyMap();

  SafeFuture<Response<Void>> getAsync(
      String apiPath, Map<String, String> headers, Duration timeout);

  <TResp extends SszData> SafeFuture<Response<BuilderApiResponse<TResp>>> getAsync(
      String apiPath,
      Map<String, String> headers,
      ResponseSchemaAndDeserializableTypeDefinition<TResp> responseSchema,
      Duration timeout);

  <TReq extends SszData> SafeFuture<Response<Void>> postAsync(
      String apiPath, TReq requestBodyObject, boolean postAsSsz, Duration timeout);

  <TReq extends SszData> SafeFuture<Response<Void>> postAsync(
      String apiPath,
      Map<String, String> headers,
      TReq requestBodyObject,
      boolean postAsSsz,
      Duration timeout);

  <TResp extends SszData, TReq extends SszData>
      SafeFuture<Response<BuilderApiResponse<TResp>>> postAsync(
          String apiPath,
          Map<String, String> headers,
          TReq requestBodyObject,
          boolean postAsSsz,
          ResponseSchemaAndDeserializableTypeDefinition<TResp> responseSchema,
          Duration timeout);

  record ResponseSchemaAndDeserializableTypeDefinition<TResp extends SszData>(
      SszSchema<TResp> responseSchema,
      DeserializableTypeDefinition<BuilderApiResponse<TResp>> responseTypeDefinition) {}
}
