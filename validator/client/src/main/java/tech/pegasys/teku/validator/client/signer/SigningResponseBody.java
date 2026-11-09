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

package tech.pegasys.teku.validator.client.signer;

import static tech.pegasys.teku.ethereum.json.types.EthereumTypes.SIGNATURE_TYPE;

import tech.pegasys.teku.bls.BLSSignature;
import tech.pegasys.teku.infrastructure.json.types.DeserializableTypeDefinition;

public record SigningResponseBody(BLSSignature signature) {

  static DeserializableTypeDefinition<SigningResponseBody> getJsonTypeDefinition() {
    return DeserializableTypeDefinition.object(
            SigningResponseBody.class, SigningResponseBodyBuilder.class)
        .initializer(SigningResponseBodyBuilder::new)
        .finisher(SigningResponseBodyBuilder::build)
        .withField(
            "signature",
            SIGNATURE_TYPE,
            SigningResponseBody::signature,
            SigningResponseBodyBuilder::signature)
        .build();
  }

  static class SigningResponseBodyBuilder {
    private BLSSignature signature;

    SigningResponseBodyBuilder signature(final BLSSignature signature) {
      this.signature = signature;
      return this;
    }

    SigningResponseBody build() {
      return new SigningResponseBody(signature);
    }
  }
}
