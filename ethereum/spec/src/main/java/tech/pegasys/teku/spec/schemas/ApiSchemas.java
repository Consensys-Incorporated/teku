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

package tech.pegasys.teku.spec.schemas;

import tech.pegasys.teku.spec.config.SpecConfigGloas;
import tech.pegasys.teku.spec.datastructures.builder.SignedValidatorRegistrationSchema;
import tech.pegasys.teku.spec.datastructures.builder.SignedValidatorRegistrationsSchema;
import tech.pegasys.teku.spec.datastructures.builder.ValidatorRegistrationSchema;
import tech.pegasys.teku.spec.datastructures.builder.versions.gloas.BuilderConfigSchema;
import tech.pegasys.teku.spec.datastructures.builder.versions.gloas.BuilderEntrySchema;
import tech.pegasys.teku.spec.datastructures.builder.versions.gloas.BuilderPreferencesRequestSchema;
import tech.pegasys.teku.spec.datastructures.builder.versions.gloas.BuilderPreferencesSchema;
import tech.pegasys.teku.spec.datastructures.builder.versions.gloas.BuilderRequestAuthSchema;
import tech.pegasys.teku.spec.datastructures.builder.versions.gloas.SignedBuilderRequestAuthSchema;

public class ApiSchemas {

  public static final ValidatorRegistrationSchema VALIDATOR_REGISTRATION_SCHEMA =
      new ValidatorRegistrationSchema();

  public static final SignedValidatorRegistrationSchema SIGNED_VALIDATOR_REGISTRATION_SCHEMA =
      new SignedValidatorRegistrationSchema(VALIDATOR_REGISTRATION_SCHEMA);

  // the max size is based on VALIDATOR_REGISTRY_LIMIT spec config
  public static final long MAX_VALIDATOR_REGISTRATIONS_SIZE = 1099511627776L;
  public static final SignedValidatorRegistrationsSchema SIGNED_VALIDATOR_REGISTRATIONS_SCHEMA =
      new SignedValidatorRegistrationsSchema(
          SIGNED_VALIDATOR_REGISTRATION_SCHEMA, MAX_VALIDATOR_REGISTRATIONS_SIZE);

  // https://github.com/ethereum/builder-specs/blob/main/specs/gloas/validator.md#new-containers
  public static final BuilderRequestAuthSchema BUILDER_REQUEST_AUTH_SCHEMA =
      new BuilderRequestAuthSchema(SpecConfigGloas.MAX_DATA_SIZE);

  public static final SignedBuilderRequestAuthSchema SIGNED_BUILDER_REQUEST_AUTH_SCHEMA =
      new SignedBuilderRequestAuthSchema(BUILDER_REQUEST_AUTH_SCHEMA);

  public static final BuilderPreferencesSchema BUILDER_PREFERENCES_SCHEMA =
      new BuilderPreferencesSchema();

  public static final BuilderPreferencesRequestSchema BUILDER_PREFERENCES_REQUEST_SCHEMA =
      new BuilderPreferencesRequestSchema(
          BUILDER_PREFERENCES_SCHEMA, SIGNED_BUILDER_REQUEST_AUTH_SCHEMA);

  // https://github.com/ethereum/beacon-APIs/pull/630/
  public static final BuilderEntrySchema BUILDER_ENTRY_SCHEMA = new BuilderEntrySchema();

  public static final BuilderConfigSchema BUILDER_CONFIG_SCHEMA =
      new BuilderConfigSchema(BUILDER_ENTRY_SCHEMA);
}
