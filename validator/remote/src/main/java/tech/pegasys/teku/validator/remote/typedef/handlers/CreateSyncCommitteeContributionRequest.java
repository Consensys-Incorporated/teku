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

import static tech.pegasys.teku.ethereum.json.types.SharedApiTypes.withDataWrapper;
import static tech.pegasys.teku.validator.remote.apiclient.ValidatorApiMethod.GET_SYNC_COMMITTEE_CONTRIBUTION;

import java.util.Collections;
import java.util.Map;
import java.util.Optional;
import okhttp3.HttpUrl;
import okhttp3.OkHttpClient;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.infrastructure.http.RestApiConstants;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.SpecMilestone;
import tech.pegasys.teku.spec.datastructures.operations.versions.altair.SyncCommitteeContribution;
import tech.pegasys.teku.spec.datastructures.operations.versions.altair.SyncCommitteeContributionSchema;
import tech.pegasys.teku.spec.schemas.SchemaDefinitionsAltair;
import tech.pegasys.teku.validator.remote.typedef.ResponseHandler;

public class CreateSyncCommitteeContributionRequest extends AbstractTypeDefRequest {
  private final Spec spec;

  public CreateSyncCommitteeContributionRequest(
      final HttpUrl baseEndpoint, final OkHttpClient okHttpClient, final Spec spec) {
    super(baseEndpoint, okHttpClient);
    this.spec = spec;
  }

  public Optional<SyncCommitteeContribution> submit(
      final UInt64 slot, final int subcommitteeIndex, final Bytes32 beaconBlockRoot) {
    if (spec.atSlot(slot).getMilestone().equals(SpecMilestone.PHASE0)) {
      return Optional.empty();
    }
    final SyncCommitteeContributionSchema syncCommitteeContributionSchema =
        SchemaDefinitionsAltair.required(spec.atSlot(slot).getSchemaDefinitions())
            .getSyncCommitteeContributionSchema();
    final Map<String, String> queryParams =
        Map.of(
            RestApiConstants.SLOT,
            slot.toString(),
            RestApiConstants.SUBCOMMITTEE_INDEX,
            Integer.toString(subcommitteeIndex),
            RestApiConstants.BEACON_BLOCK_ROOT,
            beaconBlockRoot.toHexString());
    return get(
        GET_SYNC_COMMITTEE_CONTRIBUTION,
        Collections.emptyMap(),
        queryParams,
        new ResponseHandler<>(withDataWrapper(syncCommitteeContributionSchema)));
  }
}
