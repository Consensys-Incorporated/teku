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

package tech.pegasys.teku.services.zkchain;

import java.time.Duration;

public record ZkChainConfiguration(
    boolean statelessValidationEnabled,
    boolean generateExecutionProofsEnabled,
    int statelessMinProofsRequired,
    Duration proofDelayDurationInMs) {

  public static final boolean DEFAULT_STATELESS_VALIDATION_ENABLED = false;
  public static final boolean DEFAULT_GENERATE_EXECUTION_PROOFS_ENABLED = false;
  public static final int DEFAULT_STATELESS_MIN_PROOFS_REQUIRED = 1;
  public static final Duration DEFAULT_PROOF_GENERATION_DELAY = Duration.ofSeconds(2);

  public static Builder builder() {
    return new Builder();
  }

  public static class Builder {

    private boolean statelessValidationEnabled = DEFAULT_STATELESS_VALIDATION_ENABLED;
    private boolean generateExecutionProofsEnabled = DEFAULT_GENERATE_EXECUTION_PROOFS_ENABLED;
    private int statelessMinProofsRequired = DEFAULT_STATELESS_MIN_PROOFS_REQUIRED;
    private Duration proofDelayDurationInMs = DEFAULT_PROOF_GENERATION_DELAY;

    public Builder() {}

    public Builder statelessValidationEnabled(final boolean statelessValidationEnabled) {
      this.statelessValidationEnabled = statelessValidationEnabled;
      return this;
    }

    public Builder generateExecutionProofsEnabled(final boolean generateExecutionProofsEnabled) {
      this.generateExecutionProofsEnabled = generateExecutionProofsEnabled;
      return this;
    }

    public Builder statelessMinProofsRequired(final int statelessMinProofsRequired) {
      if (statelessMinProofsRequired < 1) {
        throw new IllegalArgumentException("statelessMinProofsRequired must be at least 1");
      }
      this.statelessMinProofsRequired = statelessMinProofsRequired;
      return this;
    }

    public Builder proofDelayDurationInMs(final Duration proofDelayDurationInMs) {
      this.proofDelayDurationInMs = proofDelayDurationInMs;
      return this;
    }

    public ZkChainConfiguration build() {
      if (generateExecutionProofsEnabled && !statelessValidationEnabled) {
        throw new IllegalStateException(
            "Can't generate execution proofs when stateless validation isn't enabled");
      }
      return new ZkChainConfiguration(
          statelessValidationEnabled,
          generateExecutionProofsEnabled,
          statelessMinProofsRequired,
          proofDelayDurationInMs);
    }
  }
}
