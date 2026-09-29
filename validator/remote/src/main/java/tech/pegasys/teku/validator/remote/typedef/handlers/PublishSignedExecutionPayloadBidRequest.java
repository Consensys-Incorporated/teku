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

package tech.pegasys.teku.validator.remote.typedef.handlers;

import static java.util.Collections.emptyMap;
import static tech.pegasys.teku.infrastructure.http.RestApiConstants.HEADER_CONSENSUS_VERSION;
import static tech.pegasys.teku.validator.remote.apiclient.ValidatorApiMethod.SEND_SIGNED_EXECUTION_PAYLOAD_BID;

import java.util.Map;
import okhttp3.HttpUrl;
import okhttp3.OkHttpClient;
import tech.pegasys.teku.infrastructure.json.types.DeserializableTypeDefinition;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.SpecMilestone;
import tech.pegasys.teku.spec.datastructures.epbs.versions.gloas.SignedExecutionPayloadBid;
import tech.pegasys.teku.spec.schemas.SchemaDefinitionsGloas;
import tech.pegasys.teku.validator.remote.typedef.ResponseHandler;

public class PublishSignedExecutionPayloadBidRequest extends AbstractTypeDefRequest {

  private final ResponseHandler<Void> responseHandler = new ResponseHandler<>();

  private final Spec spec;

  public PublishSignedExecutionPayloadBidRequest(
      final Spec spec, final HttpUrl baseEndpoint, final OkHttpClient okHttpClient) {
    super(baseEndpoint, okHttpClient);
    this.spec = spec;
  }

  public void submit(final SignedExecutionPayloadBid signedExecutionPayloadBid) {
    final UInt64 slot = signedExecutionPayloadBid.getMessage().getSlot();
    final SpecMilestone milestone = spec.atSlot(slot).getMilestone();

    final DeserializableTypeDefinition<SignedExecutionPayloadBid> typeDefinition =
        SchemaDefinitionsGloas.required(spec.atSlot(slot).getSchemaDefinitions())
            .getSignedExecutionPayloadBidSchema()
            .getJsonTypeDefinition();

    postJson(
        SEND_SIGNED_EXECUTION_PAYLOAD_BID,
        emptyMap(),
        emptyMap(),
        Map.of(HEADER_CONSENSUS_VERSION, milestone.lowerCaseName()),
        signedExecutionPayloadBid,
        typeDefinition,
        responseHandler);
  }
}
