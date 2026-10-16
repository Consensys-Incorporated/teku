/*
 * Copyright Consensys Software Inc., 2026
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

package tech.pegasys.teku.validator.client.restapi.apis.schema;

import java.util.List;
import java.util.Optional;
import org.apache.tuweni.bytes.Bytes;
import tech.pegasys.teku.bls.BLSPublicKey;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;

/**
 * Unlike {@link tech.pegasys.teku.spec.datastructures.builder.versions.gloas.BuilderEntry}, all
 * fields except {@code url} are optional and are resolved by the validator client.
 */
public record BuilderEntry(
    String url,
    Optional<Bytes> authData,
    Optional<List<BLSPublicKey>> builderPubkeys,
    Optional<UInt64> maxExecutionPayment,
    Optional<UInt64> minBid,
    Optional<UInt64> builderBoostFactor) {

  public static class Builder {
    private String url;
    private Optional<Bytes> authData = Optional.empty();
    private Optional<List<BLSPublicKey>> builderPubkeys = Optional.empty();
    private Optional<UInt64> maxExecutionPayment = Optional.empty();
    private Optional<UInt64> minBid = Optional.empty();
    private Optional<UInt64> builderBoostFactor = Optional.empty();

    public Builder url(final String url) {
      this.url = url;
      return this;
    }

    public Builder authData(final Optional<Bytes> authData) {
      this.authData = authData;
      return this;
    }

    public Builder builderPubkeys(final Optional<List<BLSPublicKey>> builderPubkeys) {
      this.builderPubkeys = builderPubkeys;
      return this;
    }

    public Builder maxExecutionPayment(final Optional<UInt64> maxExecutionPayment) {
      this.maxExecutionPayment = maxExecutionPayment;
      return this;
    }

    public Builder minBid(final Optional<UInt64> minBid) {
      this.minBid = minBid;
      return this;
    }

    public Builder builderBoostFactor(final Optional<UInt64> builderBoostFactor) {
      this.builderBoostFactor = builderBoostFactor;
      return this;
    }

    public BuilderEntry build() {
      return new BuilderEntry(
          url, authData, builderPubkeys, maxExecutionPayment, minBid, builderBoostFactor);
    }
  }
}
