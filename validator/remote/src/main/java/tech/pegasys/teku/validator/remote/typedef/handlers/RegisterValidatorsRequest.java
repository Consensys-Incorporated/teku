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

package tech.pegasys.teku.validator.remote.typedef.handlers;

import static tech.pegasys.teku.infrastructure.http.HttpStatusCodes.SC_UNSUPPORTED_MEDIA_TYPE;

import java.util.Collections;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import okhttp3.HttpUrl;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import tech.pegasys.teku.infrastructure.ssz.SszList;
import tech.pegasys.teku.spec.datastructures.builder.SignedValidatorRegistration;
import tech.pegasys.teku.spec.schemas.ApiSchemas;
import tech.pegasys.teku.validator.remote.apiclient.ValidatorApiMethod;
import tech.pegasys.teku.validator.remote.typedef.ResponseHandler;

public class RegisterValidatorsRequest extends AbstractTypeDefRequest {

  private final ResponseHandler<Object> sszResponseHandler =
      new ResponseHandler<>()
          .withHandler(SC_UNSUPPORTED_MEDIA_TYPE, this::handleUnsupportedSszRequest);

  private final AtomicBoolean preferSszEncoding;

  public RegisterValidatorsRequest(
      final HttpUrl baseEndpoint,
      final OkHttpClient okHttpClient,
      final boolean preferSszEncoding) {
    super(baseEndpoint, okHttpClient);
    this.preferSszEncoding = new AtomicBoolean(preferSszEncoding);
  }

  public void submit(final SszList<SignedValidatorRegistration> validatorRegistrations) {
    if (preferSszEncoding.get()) {
      sendValidatorRegistrationsAsSszOrFallback(validatorRegistrations);
    } else {
      sendValidatorRegistrationsAsJson(validatorRegistrations);
    }
  }

  private void sendValidatorRegistrationsAsSszOrFallback(
      final SszList<SignedValidatorRegistration> validatorRegistrations) {
    sendValidatorRegistrationsAsSsz(validatorRegistrations);
    if (!preferSszEncoding.get()) {
      sendValidatorRegistrationsAsJson(validatorRegistrations);
    }
  }

  private void sendValidatorRegistrationsAsSsz(
      final SszList<SignedValidatorRegistration> validatorRegistrations) {
    postOctetStream(
        ValidatorApiMethod.REGISTER_VALIDATOR,
        Collections.emptyMap(),
        Collections.emptyMap(),
        ApiSchemas.SIGNED_VALIDATOR_REGISTRATIONS_SCHEMA
            .sszSerialize(validatorRegistrations)
            .toArray(),
        sszResponseHandler);
  }

  private void sendValidatorRegistrationsAsJson(
      final SszList<SignedValidatorRegistration> validatorRegistrations) {
    postJson(
        ValidatorApiMethod.REGISTER_VALIDATOR,
        Collections.emptyMap(),
        validatorRegistrations,
        ApiSchemas.SIGNED_VALIDATOR_REGISTRATIONS_SCHEMA.getJsonTypeDefinition(),
        new ResponseHandler<>());
  }

  private Optional<Object> handleUnsupportedSszRequest(
      final Request request, final Response response) {
    preferSszEncoding.set(false);
    return Optional.empty();
  }
}
