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

package tech.pegasys.teku.statetransition.util;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.SpecMilestone;
import tech.pegasys.teku.spec.TestSpecFactory;
import tech.pegasys.teku.spec.logic.versions.fulu.helpers.MiscHelpersFulu;
import tech.pegasys.teku.statetransition.datacolumns.CustodyGroupCountManager;
import tech.pegasys.teku.statetransition.datacolumns.util.SuperNodeSupplier;

public class SuperNodeSupplierTest {

  Spec spec;
  SuperNodeSupplier isSuperNodeSupplier;
  final CustodyGroupCountManager custodyGroupCountManager = mock(CustodyGroupCountManager.class);

  @BeforeEach
  public void setup() {
    this.spec = TestSpecFactory.createMinimalFulu();
  }

  @Test
  public void shouldReturnTrue_whenCustodyCountQualifiesAsSuperNode() {
    // But custody count is at maximum (128), which qualifies as super node
    when(custodyGroupCountManager.getCustodyGroupCount()).thenReturn(128);

    isSuperNodeSupplier = new SuperNodeSupplier(spec, () -> custodyGroupCountManager);

    // Verify the custody count qualifies as super node
    final MiscHelpersFulu miscHelpersFulu =
        MiscHelpersFulu.required(spec.forMilestone(SpecMilestone.FULU).miscHelpers());
    assertThat(miscHelpersFulu.isSuperNode(128))
        .as("Custody count 128 should qualify as super node")
        .isTrue();

    assertThat(isSuperNodeSupplier.get())
        .as(
            "Should return true when custody count qualifies as super node (128 custody groups = max)")
        .isTrue();
  }

  @Test
  public void shouldReturnFalse_whenCustodyCountDoesNotQualifyAsSuperNode() {
    // Custody count is low (4 = default minimum)
    when(custodyGroupCountManager.getCustodyGroupCount()).thenReturn(4);

    isSuperNodeSupplier = new SuperNodeSupplier(spec, () -> custodyGroupCountManager);

    // Verify the custody count does NOT qualify as super node
    final MiscHelpersFulu miscHelpersFulu =
        MiscHelpersFulu.required(spec.forMilestone(SpecMilestone.FULU).miscHelpers());
    assertThat(miscHelpersFulu.isSuperNode(4))
        .as("Custody count 4 should NOT qualify as super node")
        .isFalse();

    assertThat(isSuperNodeSupplier.get())
        .as(
            "Should return false when custody count does not qualify as super node (only 4 custody groups)")
        .isFalse();
  }

  @Test
  public void shouldReturnFalse_beforeFuluMilestone() {
    final Spec specElectra = TestSpecFactory.createMinimalElectra();

    isSuperNodeSupplier = new SuperNodeSupplier(specElectra, () -> custodyGroupCountManager);

    assertThat(isSuperNodeSupplier.get())
        .as("Should return false when Fulu milestone is not supported")
        .isFalse();
  }

  @Test
  public void shouldDynamicallyUpdate_whenCustodyCountChanges() {

    // Initially low custody count
    when(custodyGroupCountManager.getCustodyGroupCount()).thenReturn(4);

    isSuperNodeSupplier = new SuperNodeSupplier(spec, () -> custodyGroupCountManager);

    assertThat(isSuperNodeSupplier.get())
        .as("Should return false with low custody count")
        .isFalse();

    // Custody count increases after validators load (simulating delayed validator loading)
    when(custodyGroupCountManager.getCustodyGroupCount()).thenReturn(128);
    assertThat(isSuperNodeSupplier.get())
        .as("Should return true after custody count increases to super node level")
        .isTrue();
  }
}
