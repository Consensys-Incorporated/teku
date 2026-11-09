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

package tech.pegasys.teku.kzg;

import static com.google.common.base.Preconditions.checkArgument;
import static tech.pegasys.teku.kzg.KZG.BYTES_PER_G1;
import static tech.pegasys.teku.kzg.KZG.BYTES_PER_G2;

import java.util.List;
import org.apache.tuweni.bytes.Bytes;

record TrustedSetup(List<Bytes> g1Lagrange, List<Bytes> g2Monomial, List<Bytes> g1Monomial) {

  public TrustedSetup(
      final List<Bytes> g1Lagrange, final List<Bytes> g2Monomial, final List<Bytes> g1Monomial) {
    g1Lagrange.forEach(this::validateG1Point);
    this.g1Lagrange = g1Lagrange;
    g2Monomial.forEach(this::validateG2Point);
    this.g2Monomial = g2Monomial;
    g1Monomial.forEach(this::validateG1Point);
    this.g1Monomial = g1Monomial;
  }

  private void validateG1Point(final Bytes g1Point) {
    checkArgument(g1Point.size() == BYTES_PER_G1, "Expected G1 point to be %s bytes", BYTES_PER_G1);
  }

  private void validateG2Point(final Bytes g2Point) {
    checkArgument(g2Point.size() == BYTES_PER_G2, "Expected G2 point to be %s bytes", BYTES_PER_G2);
  }
}
