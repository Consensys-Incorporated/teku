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

package tech.pegasys.teku.spec.logic.versions.deneb.helpers;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

import org.junit.jupiter.api.Test;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.TestSpecFactory;
import tech.pegasys.teku.spec.config.SpecConfigDeneb;
import tech.pegasys.teku.spec.logic.common.helpers.Predicates;
import tech.pegasys.teku.spec.logic.versions.altair.helpers.BeaconStateAccessorsAltair;
import tech.pegasys.teku.spec.schemas.SchemaDefinitionsDeneb;

public class BeaconStateAccessorsDenebTest {

  private final Spec spec = TestSpecFactory.createMinimalDeneb();
  private final Predicates predicates = new Predicates(spec.getGenesisSpecConfig());
  private final SchemaDefinitionsDeneb schemaDefinitionsDeneb =
      SchemaDefinitionsDeneb.required(spec.getGenesisSchemaDefinitions());
  private final SpecConfigDeneb specConfigDeneb =
      spec.getGenesisSpecConfig().toVersionDeneb().orElseThrow();
  private final MiscHelpersDeneb miscHelpersDeneb =
      new MiscHelpersDeneb(specConfigDeneb, predicates, schemaDefinitionsDeneb);
  private final BeaconStateAccessorsDeneb beaconStateAccessorsDeneb =
      new BeaconStateAccessorsDeneb(specConfigDeneb, predicates, miscHelpersDeneb);

  @Test
  public void testRequired() {
    assertDoesNotThrow(() -> BeaconStateAccessorsAltair.required(beaconStateAccessorsDeneb));
  }
}
