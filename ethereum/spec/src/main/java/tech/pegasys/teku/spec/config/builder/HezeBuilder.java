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

package tech.pegasys.teku.spec.config.builder;

import static com.google.common.base.Preconditions.checkNotNull;

import java.util.HashMap;
import java.util.Map;
import java.util.function.BiConsumer;
import tech.pegasys.teku.spec.config.SpecConfigAndParent;
import tech.pegasys.teku.spec.config.SpecConfigGloas;
import tech.pegasys.teku.spec.config.SpecConfigHeze;
import tech.pegasys.teku.spec.config.SpecConfigHezeImpl;

public class HezeBuilder extends BaseForkBuilder
    implements ForkConfigBuilder<SpecConfigGloas, SpecConfigHeze> {

  private Integer inclusionListDueBps;
  private Integer maxRequestInclusionList;
  private Integer maxTransactionsBytesPerInclusionList;

  // heze preset
  private Integer inclusionListCommitteeSize;

  HezeBuilder() {}

  @Override
  public SpecConfigAndParent<SpecConfigHeze> build(
      final SpecConfigAndParent<SpecConfigGloas> specConfigAndParent) {
    return SpecConfigAndParent.of(
        new SpecConfigHezeImpl(
            specConfigAndParent.specConfig(),
            inclusionListDueBps,
            maxRequestInclusionList,
            maxTransactionsBytesPerInclusionList,
            inclusionListCommitteeSize),
        specConfigAndParent);
  }

  public HezeBuilder inclusionListDueBps(final Integer inclusionListDueBps) {
    checkNotNull(inclusionListDueBps);
    this.inclusionListDueBps = inclusionListDueBps;
    return this;
  }

  public HezeBuilder maxRequestInclusionList(final Integer maxRequestInclusionList) {
    checkNotNull(maxRequestInclusionList);
    this.maxRequestInclusionList = maxRequestInclusionList;
    return this;
  }

  public HezeBuilder maxTransactionsBytesPerInclusionList(
      final Integer maxTransactionsBytesPerInclusionList) {
    checkNotNull(maxTransactionsBytesPerInclusionList);
    this.maxTransactionsBytesPerInclusionList = maxTransactionsBytesPerInclusionList;
    return this;
  }

  public HezeBuilder inclusionListCommitteeSize(final Integer inclusionListCommitteeSize) {
    checkNotNull(inclusionListCommitteeSize);
    this.inclusionListCommitteeSize = inclusionListCommitteeSize;
    return this;
  }

  @Override
  public void validate() {
    defaultValuesIfRequired(this);
    validateConstants();
  }

  @Override
  public Map<String, Object> getValidationMap() {
    final Map<String, Object> constants = new HashMap<>();
    constants.put("inclusionListDueBps", inclusionListDueBps);
    constants.put("maxRequestInclusionList", maxRequestInclusionList);
    constants.put("maxTransactionsBytesPerInclusionList", maxTransactionsBytesPerInclusionList);
    constants.put("inclusionListCommitteeSize", inclusionListCommitteeSize);
    return constants;
  }

  @Override
  public void addOverridableItemsToRawConfig(final BiConsumer<String, Object> rawConfig) {}
}
