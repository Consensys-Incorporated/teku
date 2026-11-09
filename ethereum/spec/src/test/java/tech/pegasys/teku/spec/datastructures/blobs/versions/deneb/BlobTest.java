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

package tech.pegasys.teku.spec.datastructures.blobs.versions.deneb;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.apache.tuweni.bytes.Bytes;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.spec.SpecMilestone;
import tech.pegasys.teku.spec.TestSpecFactory;
import tech.pegasys.teku.spec.schemas.SchemaDefinitions;
import tech.pegasys.teku.spec.util.DataStructureUtil;

class BlobTest {

  private final DataStructureUtil dataStructureUtil =
      new DataStructureUtil(TestSpecFactory.createMinimal(SpecMilestone.DENEB));
  final SchemaDefinitions schemaDefinitions =
      dataStructureUtil.getSpec().getGenesisSchemaDefinitions();
  private final BlobSchema blobSchema =
      schemaDefinitions.toVersionDeneb().orElseThrow().getBlobSchema();

  private final Bytes correctData = dataStructureUtil.randomValidBlob().getBytes();

  @Test
  public void objectEquality() {
    final Blob blob1 = blobSchema.create(correctData);
    final Blob blob2 = blobSchema.create(correctData);

    assertThat(blob1).isEqualTo(blob2);
  }

  @Test
  public void sizeCheck() {
    // Checking is lazy, it is not called when we create an instance
    assertThatThrownBy(
            () ->
                blobSchema
                    .create(Bytes.wrap(correctData, Bytes.fromHexString("a0")))
                    .sszSerialize())
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> blobSchema.create(correctData.slice(1)).sszSerialize())
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  public void roundTripSSZ() {
    final Blob blob = blobSchema.create(correctData);

    final Bytes sszBlobBytes = blob.sszSerialize();
    final Blob deserializedObject = blobSchema.sszDeserialize(sszBlobBytes);

    assertThat(blob).isEqualTo(deserializedObject);
  }
}
