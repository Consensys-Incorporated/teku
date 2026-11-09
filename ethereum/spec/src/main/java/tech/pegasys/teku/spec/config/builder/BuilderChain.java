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

import java.util.Map;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.config.SpecConfig;
import tech.pegasys.teku.spec.config.SpecConfigAndParent;

/**
 * Hides some serious abuse of Java's type system so that from the outside we have a type safe chain
 * of {@link ForkConfigBuilder} where each one takes the type of config from the previous and
 * modifies it further. Due to limitations in Java's type system, we can't actually track that so
 * this class exposes a type safe public API but uses a lot of unchecked and raw types internally.
 *
 * @param <In> the specific type of SpecConfig taken as input
 * @param <Out> the specific type of SpecConfig returned as output
 */
@SuppressWarnings("rawtypes")
class BuilderChain<In extends SpecConfig, Out extends In> implements ForkConfigBuilder<In, Out> {

  private final ForkConfigBuilder builderToApply;
  private final ForkConfigBuilder tail;

  private BuilderChain(final ForkConfigBuilder builderToApply, final ForkConfigBuilder tail) {
    this.builderToApply = builderToApply;
    this.tail = tail;
  }

  public static <In extends SpecConfig, Out extends In> BuilderChain<In, Out> create(
      final ForkConfigBuilder<In, Out> builder) {
    return new BuilderChain<>(builder, new NoOpForkBuilder());
  }

  @SuppressWarnings("unchecked")
  public <T extends ForkConfigBuilder<?, ?>> void withBuilder(
      final Class<T> type, final Consumer<T> consumer) {
    if (type.isInstance(builderToApply)) {
      consumer.accept((T) builderToApply);
    } else if (tail instanceof BuilderChain) {
      ((BuilderChain<?, ?>) tail).withBuilder(type, consumer);
    } else {
      throw new IllegalArgumentException("Unrecognized config type: " + type.getCanonicalName());
    }
  }

  @SuppressWarnings("unchecked")
  public <NewOut extends Out> BuilderChain<In, NewOut> appendBuilder(
      final ForkConfigBuilder<Out, NewOut> newBuilder) {
    final BuilderChain<Out, NewOut> newTail;
    if (tail instanceof BuilderChain) {
      newTail = ((BuilderChain) tail).appendBuilder(newBuilder);
    } else {
      newTail = new BuilderChain<>(newBuilder, new NoOpForkBuilder<>());
    }
    return new BuilderChain<>(builderToApply, newTail);
  }

  @SuppressWarnings("unchecked")
  @Override
  public void addOverridableItemsToRawConfig(final BiConsumer<String, Object> rawConfig) {
    builderToApply.addOverridableItemsToRawConfig(rawConfig);
    tail.addOverridableItemsToRawConfig(rawConfig);
  }

  @Override
  @SuppressWarnings("unchecked")
  public SpecConfigAndParent<Out> build(final SpecConfigAndParent<In> specConfig) {
    final SpecConfigAndParent<In> config = builderToApply.build(specConfig);
    return tail.build(config);
  }

  @Override
  public void setForkEpoch(final UInt64 epoch) {
    throw new UnsupportedOperationException();
  }

  @Override
  public void validate() {
    builderToApply.validate();
    tail.validate();
  }

  @Override
  public Map<String, Object> getValidationMap() {
    return Map.of();
  }

  private static class NoOpForkBuilder<T extends SpecConfig> implements ForkConfigBuilder<T, T> {

    @Override
    public SpecConfigAndParent<T> build(final SpecConfigAndParent<T> specConfig) {
      return specConfig;
    }

    @Override
    public void setForkEpoch(final UInt64 epoch) {
      throw new UnsupportedOperationException();
    }

    @Override
    public void validate() {}

    @Override
    public Map<String, Object> getValidationMap() {
      return Map.of();
    }

    @Override
    public void addOverridableItemsToRawConfig(final BiConsumer<String, Object> rawConfig) {}
  }
}
