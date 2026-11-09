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

package tech.pegasys.teku.spec.propertytest.suppliers.type;

import java.util.List;
import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.ArbitrarySupplier;
import org.apache.tuweni.bytes.Bytes;
import tech.pegasys.teku.spec.propertytest.suppliers.blobs.versions.deneb.BlobBytesSupplier;

/**
 * An implementation of {@link ArbitrarySupplier<Bytes>} which chooses randomly between {@link
 * BlobBytesSupplier} and {@link BytesSupplier}
 */
public class DiverseBlobBytesSupplier extends BlobBytesSupplier {

  @Override
  public Arbitrary<Bytes> get() {
    final Arbitrary<Bytes> randomSizeBlob = new BytesSupplier().get();
    return Arbitraries.oneOf(List.of(super.get(), randomSizeBlob));
  }
}
