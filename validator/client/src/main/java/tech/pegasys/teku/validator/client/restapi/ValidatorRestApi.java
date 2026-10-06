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

package tech.pegasys.teku.validator.client.restapi;

import static tech.pegasys.teku.infrastructure.http.HttpStatusCodes.SC_BAD_REQUEST;
import static tech.pegasys.teku.infrastructure.http.HttpStatusCodes.SC_SERVICE_UNAVAILABLE;

import com.fasterxml.jackson.core.JsonProcessingException;
import java.nio.file.Path;
import java.util.Optional;
import org.apache.commons.lang3.StringUtils;
import tech.pegasys.teku.api.exceptions.BadRequestException;
import tech.pegasys.teku.api.exceptions.ServiceUnavailableException;
import tech.pegasys.teku.infrastructure.http.HttpErrorResponse;
import tech.pegasys.teku.infrastructure.restapi.RestApi;
import tech.pegasys.teku.infrastructure.restapi.RestApiBuilder;
import tech.pegasys.teku.infrastructure.time.TimeProvider;
import tech.pegasys.teku.infrastructure.version.VersionProvider;
import tech.pegasys.teku.service.serviceutils.layout.DataDirLayout;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.validator.api.GraffitiManager;
import tech.pegasys.teku.validator.api.ValidatorApiChannel;
import tech.pegasys.teku.validator.beaconnode.GenesisDataProvider;
import tech.pegasys.teku.validator.client.KeyManager;
import tech.pegasys.teku.validator.client.ProposerConfigManager;
import tech.pegasys.teku.validator.client.ValidatorClientService;
import tech.pegasys.teku.validator.client.VoluntaryExitDataProvider;
import tech.pegasys.teku.validator.client.doppelganger.DoppelgangerDetector;
import tech.pegasys.teku.validator.client.restapi.apis.DeleteFeeRecipient;
import tech.pegasys.teku.validator.client.restapi.apis.DeleteGasLimit;
import tech.pegasys.teku.validator.client.restapi.apis.DeleteGraffiti;
import tech.pegasys.teku.validator.client.restapi.apis.DeleteKeys;
import tech.pegasys.teku.validator.client.restapi.apis.DeleteRemoteKeys;
import tech.pegasys.teku.validator.client.restapi.apis.GetFeeRecipient;
import tech.pegasys.teku.validator.client.restapi.apis.GetGasLimit;
import tech.pegasys.teku.validator.client.restapi.apis.GetGraffiti;
import tech.pegasys.teku.validator.client.restapi.apis.GetKeys;
import tech.pegasys.teku.validator.client.restapi.apis.GetRemoteKeys;
import tech.pegasys.teku.validator.client.restapi.apis.PostKeys;
import tech.pegasys.teku.validator.client.restapi.apis.PostRemoteKeys;
import tech.pegasys.teku.validator.client.restapi.apis.PostVoluntaryExit;
import tech.pegasys.teku.validator.client.restapi.apis.SetFeeRecipient;
import tech.pegasys.teku.validator.client.restapi.apis.SetGasLimit;
import tech.pegasys.teku.validator.client.restapi.apis.SetGraffiti;
import tech.pegasys.teku.validator.client.slashingriskactions.SlashingRiskAction;

public class ValidatorRestApi {

  public static final String TAG_VOLUNTARY_EXIT = "Voluntary Exit";
  public static final String TAG_KEY_MANAGEMENT = "Key Management";
  public static final String TAG_FEE_RECIPIENT = "Fee Recipient";
  public static final String TAG_GAS_LIMIT = "Gas Limit";
  public static final String TAG_GRAFFITI = "Graffiti";

  public static RestApi create(
      final Spec spec,
      final ValidatorRestApiConfig config,
      final ValidatorApiChannel validatorApiChannel,
      final GenesisDataProvider genesisDataProvider,
      final Optional<ProposerConfigManager> proposerConfigManager,
      final KeyManager keyManager,
      final DataDirLayout dataDirLayout,
      final TimeProvider timeProvider,
      final Optional<DoppelgangerDetector> maybeDoppelgangerDetector,
      final SlashingRiskAction doppelgangerDetectionAction,
      final GraffitiManager graffitiManager) {
    final VoluntaryExitDataProvider voluntaryExitDataProvider =
        new VoluntaryExitDataProvider(
            spec, keyManager, validatorApiChannel, genesisDataProvider, timeProvider);
    final Path slashingProtectionPath =
        ValidatorClientService.getSlashingProtectionPath(dataDirLayout);
    final Path validatorApiBearerFile =
        config
            .getMaybeValidatorApiBearerFile()
            .orElse(
                ValidatorClientService.getKeyManagerPath(dataDirLayout)
                    .resolve("validator-api-bearer"));
    return new RestApiBuilder()
        .openApiInfo(
            openApi ->
                openApi
                    .title(
                        StringUtils.capitalize(
                            VersionProvider.CLIENT_IDENTITY + " Validator Rest API"))
                    .version(VersionProvider.IMPLEMENTATION_VERSION)
                    .bearerAuth(true)
                    .description("An implementation of the key management standard Rest API.")
                    .license("Apache 2.0", "https://www.apache.org/licenses/LICENSE-2.0.html"))
        .openApiDocsEnabled(config.isRestApiDocsEnabled())
        .listenAddress(config.getRestApiInterface())
        .port(config.getRestApiPort())
        .maxUrlLength(config.getMaxUrlLength())
        .corsAllowedOrigins(config.getRestApiCorsAllowedOrigins())
        .hostAllowlist(config.getRestApiHostAllowlist())
        .exceptionHandler(
            ServiceUnavailableException.class,
            (throwable) -> new HttpErrorResponse(SC_SERVICE_UNAVAILABLE, "Service unavailable"))
        .exceptionHandler(
            BadRequestException.class,
            (throwable) -> new HttpErrorResponse(SC_BAD_REQUEST, throwable.getMessage()))
        .exceptionHandler(
            IllegalArgumentException.class,
            (throwable) -> new HttpErrorResponse(SC_BAD_REQUEST, throwable.getMessage()))
        .exceptionHandler(
            JsonProcessingException.class,
            (throwable) -> new HttpErrorResponse(SC_BAD_REQUEST, throwable.getMessage()))
        .endpoint(new GetKeys(keyManager))
        .endpoint(new DeleteKeys(keyManager, slashingProtectionPath))
        .endpoint(
            new PostKeys(
                keyManager,
                slashingProtectionPath,
                maybeDoppelgangerDetector,
                doppelgangerDetectionAction))
        .endpoint(new GetRemoteKeys(keyManager))
        .endpoint(
            new PostRemoteKeys(keyManager, maybeDoppelgangerDetector, doppelgangerDetectionAction))
        .endpoint(new DeleteRemoteKeys(keyManager))
        .endpoint(new GetFeeRecipient(proposerConfigManager))
        .endpoint(new GetGasLimit(proposerConfigManager))
        .endpoint(new SetFeeRecipient(proposerConfigManager))
        .endpoint(new SetGasLimit(proposerConfigManager))
        .endpoint(new DeleteFeeRecipient(proposerConfigManager))
        .endpoint(new DeleteGasLimit(proposerConfigManager))
        .endpoint(new PostVoluntaryExit(voluntaryExitDataProvider, keyManager))
        .endpoint(new GetGraffiti(keyManager))
        .endpoint(new SetGraffiti(keyManager, graffitiManager))
        .endpoint(new DeleteGraffiti(keyManager, graffitiManager))
        .sslCertificate(config.getRestApiKeystoreFile(), config.getRestApiKeystorePasswordFile())
        .passwordFilePath(validatorApiBearerFile)
        .build();
  }
}
