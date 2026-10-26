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

package tech.pegasys.teku.beaconrestapi.handlers.v1.beacon;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;
import static tech.pegasys.teku.infrastructure.http.HttpStatusCodes.SC_OK;
import static tech.pegasys.teku.infrastructure.restapi.MetadataTestUtil.getResponseStringFromMetadata;

import com.google.common.io.Resources;
import java.io.IOException;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.api.ChainDataProvider;
import tech.pegasys.teku.infrastructure.ssz.collections.SszUInt64Vector;
import tech.pegasys.teku.spec.SpecMilestone;
import tech.pegasys.teku.spec.datastructures.metadata.ObjectAndMetaData;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.BeaconState;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.versions.fulu.BeaconStateFulu;
import tech.pegasys.teku.spec.schemas.SchemaDefinitionCache;
import tech.pegasys.teku.spec.util.DataStructureUtil;

public class GetStateProposerLookaheadTest extends AbstractGetStateFieldTest<SszUInt64Vector> {

  @Override
  protected SpecMilestone getMinimumMilestone() {
    return SpecMilestone.FULU;
  }

  @Override
  protected String getFieldLabel() {
    return "proposer lookahead";
  }

  @Override
  protected AbstractGetStateField<SszUInt64Vector> createHandler(
      final ChainDataProvider provider, final SchemaDefinitionCache schemaDefinitionCache) {
    return new GetStateProposerLookahead(provider, schemaDefinitionCache);
  }

  @Override
  protected SszUInt64Vector getField(final BeaconState state) {
    return BeaconStateFulu.required(state).getProposerLookahead();
  }

  @Override
  protected BeaconState withField(final BeaconState state, final SszUInt64Vector field) {
    return BeaconStateFulu.required(state)
        .updatedFulu(mutable -> mutable.setProposerLookahead(field));
  }

  @Override
  protected SszUInt64Vector createField() {
    return getField(dataStructureUtil.randomBeaconState(32));
  }

  @Test
  void metadata_shouldHandle200MatchingFixture() throws IOException {
    // The fixture holds the lookahead of the first random state a fresh DataStructureUtil makes
    final ObjectAndMetaData<SszUInt64Vector> responseData =
        new ObjectAndMetaData<>(
            getField(new DataStructureUtil(spec).randomBeaconState(32)),
            SpecMilestone.FULU,
            false,
            true,
            false);
    final String resource =
        Resources.toString(
            Resources.getResource(
                GetStateProposerLookaheadTest.class, "stateProposerLookahead.json"),
            UTF_8);
    final String data = getResponseStringFromMetadata(handler, SC_OK, responseData);
    assertThat(data).isEqualTo(resource);
  }
}
