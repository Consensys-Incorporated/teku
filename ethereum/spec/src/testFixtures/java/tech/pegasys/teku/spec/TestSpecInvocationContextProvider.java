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

package tech.pegasys.teku.spec;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Supplier;
import java.util.stream.Stream;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.extension.Extension;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.api.extension.ParameterContext;
import org.junit.jupiter.api.extension.ParameterResolutionException;
import org.junit.jupiter.api.extension.ParameterResolver;
import org.junit.jupiter.api.extension.TestTemplateInvocationContext;
import org.junit.jupiter.api.extension.TestTemplateInvocationContextProvider;
import tech.pegasys.teku.bls.BLSSignatureVerifier;
import tech.pegasys.teku.spec.logic.common.statetransition.blockvalidator.BatchSignatureVerifier;
import tech.pegasys.teku.spec.logic.common.statetransition.blockvalidator.BatchSignatureVerifierImpl;
import tech.pegasys.teku.spec.networks.Eth2Network;
import tech.pegasys.teku.spec.schemas.SchemaDefinitions;
import tech.pegasys.teku.spec.util.DataStructureUtil;

public class TestSpecInvocationContextProvider implements TestTemplateInvocationContextProvider {

  @Override
  public boolean supportsTestTemplate(final ExtensionContext extensionContext) {
    return true;
  }

  @Override
  public Stream<TestTemplateInvocationContext> provideTestTemplateInvocationContexts(
      final ExtensionContext extensionContext) {

    Class<?> clazz = extensionContext.getRequiredTestClass();
    TestSpecContext testSpecContext = clazz.getAnnotation(TestSpecContext.class);

    final Set<SpecMilestone> milestones;
    final Set<Eth2Network> networks;

    if (testSpecContext.allMilestones()) {
      milestones = new HashSet<>(List.of(SpecMilestone.values()));
    } else {
      milestones = new HashSet<>(List.of(testSpecContext.milestone()));
    }

    for (final SpecMilestone ignoredMilestone : testSpecContext.ignoredMilestones()) {
      milestones.remove(ignoredMilestone);
    }

    if (testSpecContext.allNetworks()) {
      networks = Set.of(Eth2Network.values());
    } else {
      networks = Set.of(testSpecContext.network());
    }

    List<TestTemplateInvocationContext> invocations = new ArrayList<>();

    milestones.forEach(
        specMilestone -> {
          networks.forEach(
              eth2Network -> {
                invocations.add(
                    generateContext(
                        new SpecContext(
                            specMilestone,
                            eth2Network,
                            testSpecContext.doNotGenerateSpec(),
                            testSpecContext.signatureVerifierNoop())));
              });
        });

    return invocations.stream();
  }

  private TestTemplateInvocationContext generateContext(final SpecContext specContext) {
    return new TestTemplateInvocationContext() {
      @Override
      public String getDisplayName(final int invocationIndex) {
        return specContext.getDisplayName();
      }

      @Override
      public List<Extension> getAdditionalExtensions() {
        return List.of(new SpecContextParameterResolver<>(specContext));
      }
    };
  }

  public static class SpecContext {

    private final String displayName;
    private final Spec spec;
    private final DataStructureUtil dataStructureUtil;
    private final SchemaDefinitions schemaDefinitions;

    private final SpecMilestone specMilestone;
    private final Eth2Network network;

    SpecContext(
        final SpecMilestone specMilestone,
        final Eth2Network network,
        final boolean doNotGenerateSpec,
        final boolean signatureVerifierNoop) {
      if (doNotGenerateSpec) {
        spec = null;
        dataStructureUtil = null;
        schemaDefinitions = null;
      } else {
        final BLSSignatureVerifier blsSignatureVerifier =
            signatureVerifierNoop ? BLSSignatureVerifier.NOOP : BLSSignatureVerifier.SIMPLE;
        final Supplier<BatchSignatureVerifier> batchSignatureVerifierSupplier =
            signatureVerifierNoop
                ? () -> BatchSignatureVerifier.NOOP
                : BatchSignatureVerifierImpl::new;
        this.spec =
            TestSpecFactory.create(
                specMilestone,
                network,
                builder ->
                    builder
                        .blsSignatureVerifier(blsSignatureVerifier)
                        .batchSignatureVerifierSupplier(batchSignatureVerifierSupplier));
        this.dataStructureUtil = new DataStructureUtil(spec);
        this.schemaDefinitions = spec.forMilestone(specMilestone).getSchemaDefinitions();
      }
      this.displayName = specMilestone.name() + ' ' + network.name();
      this.specMilestone = specMilestone;
      this.network = network;
    }

    public String getDisplayName() {
      return displayName;
    }

    public Spec getSpec() {
      return spec;
    }

    public DataStructureUtil getDataStructureUtil() {
      return dataStructureUtil;
    }

    public SchemaDefinitions getSchemaDefinitions() {
      return schemaDefinitions;
    }

    public SpecMilestone getSpecMilestone() {
      return specMilestone;
    }

    public Eth2Network getNetwork() {
      return network;
    }

    public void assumeIsOneOf(final SpecMilestone... milestones) {
      Assumptions.assumeTrue(List.of(milestones).contains(specMilestone), "Milestone skipped");
    }

    public void assumeIsOneOf(final Eth2Network... networks) {
      Assumptions.assumeTrue(List.of(networks).contains(network), "Network skipped");
    }

    public void assumeIsNotOneOf(final SpecMilestone... milestones) {
      Assumptions.assumeFalse(List.of(milestones).contains(specMilestone), "Milestone skipped");
    }

    public void assumeIsNotOneOf(final Eth2Network... networks) {
      Assumptions.assumeFalse(List.of(networks).contains(network), "Network skipped");
    }

    public void assumeMilestoneActive(final SpecMilestone milestone) {
      Assumptions.assumeTrue(specMilestone.isGreaterThanOrEqualTo(milestone), "Milestone skipped");
    }

    public void assumeMilestonesActive(final SpecMilestone from, final SpecMilestone to) {
      Assumptions.assumeTrue(specMilestone.isBetween(from, to), "Milestone skipped");
    }

    public void assumeGloasActive() {
      assumeMilestoneActive(SpecMilestone.GLOAS);
    }

    public void assumeFuluActive() {
      assumeMilestoneActive(SpecMilestone.FULU);
    }

    public void assumeElectraActive() {
      assumeMilestoneActive(SpecMilestone.ELECTRA);
    }

    public void assumeDenebActive() {
      assumeMilestoneActive(SpecMilestone.DENEB);
    }

    public void assumeCapellaActive() {
      assumeMilestoneActive(SpecMilestone.CAPELLA);
    }

    public void assumeBellatrixActive() {
      assumeMilestoneActive(SpecMilestone.BELLATRIX);
    }

    public void assumeAltairActive() {
      assumeMilestoneActive(SpecMilestone.ALTAIR);
    }
  }

  public static class SpecContextParameterResolver<T> implements ParameterResolver {

    T data;

    public SpecContextParameterResolver(final T data) {
      this.data = data;
    }

    @Override
    public boolean supportsParameter(
        final ParameterContext parameterContext, final ExtensionContext extensionContext)
        throws ParameterResolutionException {
      return parameterContext.getParameter().getType().isInstance(data);
    }

    @Override
    public Object resolveParameter(
        final ParameterContext parameterContext, final ExtensionContext extensionContext)
        throws ParameterResolutionException {
      return data;
    }
  }
}
