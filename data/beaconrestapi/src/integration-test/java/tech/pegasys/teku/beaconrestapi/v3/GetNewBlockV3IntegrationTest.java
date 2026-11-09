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

package tech.pegasys.teku.beaconrestapi.v3;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static tech.pegasys.teku.infrastructure.http.HttpStatusCodes.SC_INTERNAL_SERVER_ERROR;
import static tech.pegasys.teku.infrastructure.unsigned.UInt64.ONE;

import java.io.IOException;
import java.util.Optional;
import okhttp3.Response;
import org.junit.jupiter.api.TestTemplate;
import tech.pegasys.teku.bls.BLSSignature;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.http.ContentTypes;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.SpecMilestone;
import tech.pegasys.teku.spec.TestSpecContext;
import tech.pegasys.teku.spec.datastructures.blocks.BeaconBlock;

@TestSpecContext(milestone = {SpecMilestone.DENEB, SpecMilestone.ELECTRA, SpecMilestone.FULU})
public class GetNewBlockV3IntegrationTest extends AbstractGetNewBlockV3IntegrationTest {

  @TestTemplate
  void shouldFailWhenNoBlockProduced() throws IOException {
    final BeaconBlock beaconBlock = dataStructureUtil.randomBeaconBlock(ONE);
    final BLSSignature signature = beaconBlock.getBlock().getBody().getRandaoReveal();
    when(validatorApiChannel.createUnsignedBlock(eq(UInt64.ONE), eq(signature), any(), any()))
        .thenReturn(SafeFuture.completedFuture(Optional.empty()));
    Response response = get(signature, ContentTypes.JSON);
    assertThat(response.code()).isEqualTo(SC_INTERNAL_SERVER_ERROR);
    final String body = response.body().string();
    assertThat(body).contains("Unable to produce a block");
  }
}
