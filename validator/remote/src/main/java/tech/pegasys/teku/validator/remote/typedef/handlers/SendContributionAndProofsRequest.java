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

package tech.pegasys.teku.validator.remote.typedef.handlers;

import static tech.pegasys.teku.infrastructure.json.types.DeserializableTypeDefinition.listOf;
import static tech.pegasys.teku.validator.remote.apiclient.ValidatorApiMethod.SEND_CONTRIBUTION_AND_PROOF;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import okhttp3.HttpUrl;
import okhttp3.OkHttpClient;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import tech.pegasys.teku.spec.datastructures.operations.versions.altair.SignedContributionAndProof;
import tech.pegasys.teku.validator.remote.typedef.ResponseHandler;

public class SendContributionAndProofsRequest extends AbstractTypeDefRequest {
  private static final Logger LOG = LogManager.getLogger();

  public SendContributionAndProofsRequest(
      final HttpUrl baseEndpoint, final OkHttpClient okHttpClient) {
    super(baseEndpoint, okHttpClient);
  }

  public void submit(final Collection<SignedContributionAndProof> signedContributionAndProofs) {
    if (signedContributionAndProofs == null || signedContributionAndProofs.isEmpty()) {
      LOG.warn("Submitted no contribution and proofs");
      return;
    }
    final List<SignedContributionAndProof> requestData =
        new ArrayList<>(signedContributionAndProofs);
    postJson(
        SEND_CONTRIBUTION_AND_PROOF,
        Collections.emptyMap(),
        requestData,
        listOf(requestData.getFirst().getSchema().getJsonTypeDefinition()),
        new ResponseHandler<>());
  }
}
