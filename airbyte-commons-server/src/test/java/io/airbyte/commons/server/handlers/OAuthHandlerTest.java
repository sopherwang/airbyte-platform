/*
 * Copyright (c) 2023 Airbyte, Inc., all rights reserved.
 */

package io.airbyte.commons.server.handlers;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.JsonNode;
import io.airbyte.analytics.TrackingClient;
import io.airbyte.api.model.generated.CompleteSourceOauthRequest;
import io.airbyte.api.model.generated.SetInstancewideDestinationOauthParamsRequestBody;
import io.airbyte.api.model.generated.SetInstancewideSourceOauthParamsRequestBody;
import io.airbyte.commons.json.Jsons;
import io.airbyte.config.DestinationOAuthParameter;
import io.airbyte.config.SourceOAuthParameter;
import io.airbyte.config.persistence.ActorDefinitionVersionHelper;
import io.airbyte.config.persistence.ConfigNotFoundException;
import io.airbyte.config.persistence.ConfigRepository;
import io.airbyte.config.secrets.SecretsRepositoryReader;
import io.airbyte.config.secrets.SecretsRepositoryWriter;
import io.airbyte.featureflag.TestClient;
import io.airbyte.validation.json.JsonValidationException;
import java.io.IOException;
import java.net.http.HttpClient;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

class OAuthHandlerTest {

  private ConfigRepository configRepository;
  private OAuthHandler handler;
  private TrackingClient trackingClient;
  private HttpClient httpClient;
  private SecretsRepositoryReader secretsRepositoryReader;
  private SecretsRepositoryWriter secretsRepositoryWriter;
  private ActorDefinitionVersionHelper actorDefinitionVersionHelper;
  private static final String CLIENT_ID = "123";
  private static final String CLIENT_ID_KEY = "client_id";
  private static final String CLIENT_SECRET_KEY = "client_secret";
  private static final String CLIENT_SECRET = "hunter2";
  private static TestClient featureFlagClient;

  @BeforeEach
  public void init() {
    configRepository = Mockito.mock(ConfigRepository.class);
    trackingClient = mock(TrackingClient.class);
    httpClient = Mockito.mock(HttpClient.class);
    secretsRepositoryReader = mock(SecretsRepositoryReader.class);
    secretsRepositoryWriter = mock(SecretsRepositoryWriter.class);
    actorDefinitionVersionHelper = mock(ActorDefinitionVersionHelper.class);
    featureFlagClient = mock(TestClient.class);
    handler = new OAuthHandler(configRepository, httpClient, trackingClient, secretsRepositoryReader, secretsRepositoryWriter,
        actorDefinitionVersionHelper, featureFlagClient);
  }

  @Test
  void setSourceInstancewideOauthParams() throws JsonValidationException, IOException {
    final UUID sourceDefId = UUID.randomUUID();
    final Map<String, Object> params = new HashMap<>();
    params.put(CLIENT_ID_KEY, CLIENT_ID);
    params.put(CLIENT_SECRET_KEY, CLIENT_SECRET);

    final SetInstancewideSourceOauthParamsRequestBody actualRequest = new SetInstancewideSourceOauthParamsRequestBody()
        .sourceDefinitionId(sourceDefId)
        .params(params);

    handler.setSourceInstancewideOauthParams(actualRequest);

    final ArgumentCaptor<SourceOAuthParameter> argument = ArgumentCaptor.forClass(SourceOAuthParameter.class);
    Mockito.verify(configRepository).writeSourceOAuthParam(argument.capture());
    assertEquals(Jsons.jsonNode(params), argument.getValue().getConfiguration());
    assertEquals(sourceDefId, argument.getValue().getSourceDefinitionId());
  }

  @Test
  void resetSourceInstancewideOauthParams() throws JsonValidationException, IOException {
    final UUID sourceDefId = UUID.randomUUID();
    final Map<String, Object> firstParams = new HashMap<>();
    firstParams.put(CLIENT_ID_KEY, CLIENT_ID);
    firstParams.put(CLIENT_SECRET_KEY, CLIENT_SECRET);
    final SetInstancewideSourceOauthParamsRequestBody firstRequest = new SetInstancewideSourceOauthParamsRequestBody()
        .sourceDefinitionId(sourceDefId)
        .params(firstParams);
    handler.setSourceInstancewideOauthParams(firstRequest);

    final UUID oauthParameterId = UUID.randomUUID();
    when(configRepository.getSourceOAuthParamByDefinitionIdOptional(null, sourceDefId))
        .thenReturn(Optional.of(new SourceOAuthParameter().withOauthParameterId(oauthParameterId)));

    final Map<String, Object> secondParams = new HashMap<>();
    secondParams.put(CLIENT_ID_KEY, "456");
    secondParams.put(CLIENT_SECRET_KEY, "hunter3");
    final SetInstancewideSourceOauthParamsRequestBody secondRequest = new SetInstancewideSourceOauthParamsRequestBody()
        .sourceDefinitionId(sourceDefId)
        .params(secondParams);
    handler.setSourceInstancewideOauthParams(secondRequest);

    final ArgumentCaptor<SourceOAuthParameter> argument = ArgumentCaptor.forClass(SourceOAuthParameter.class);
    Mockito.verify(configRepository, Mockito.times(2)).writeSourceOAuthParam(argument.capture());
    final List<SourceOAuthParameter> capturedValues = argument.getAllValues();
    assertEquals(Jsons.jsonNode(firstParams), capturedValues.get(0).getConfiguration());
    assertEquals(Jsons.jsonNode(secondParams), capturedValues.get(1).getConfiguration());
    assertEquals(sourceDefId, capturedValues.get(0).getSourceDefinitionId());
    assertEquals(sourceDefId, capturedValues.get(1).getSourceDefinitionId());
    assertEquals(oauthParameterId, capturedValues.get(1).getOauthParameterId());
  }

  @Test
  void setDestinationInstancewideOauthParams() throws JsonValidationException, IOException {
    final UUID destinationDefId = UUID.randomUUID();
    final Map<String, Object> params = new HashMap<>();
    params.put(CLIENT_ID_KEY, CLIENT_ID);
    params.put(CLIENT_SECRET_KEY, CLIENT_SECRET);

    final SetInstancewideDestinationOauthParamsRequestBody actualRequest = new SetInstancewideDestinationOauthParamsRequestBody()
        .destinationDefinitionId(destinationDefId)
        .params(params);

    handler.setDestinationInstancewideOauthParams(actualRequest);

    final ArgumentCaptor<DestinationOAuthParameter> argument = ArgumentCaptor.forClass(DestinationOAuthParameter.class);
    Mockito.verify(configRepository).writeDestinationOAuthParam(argument.capture());
    assertEquals(Jsons.jsonNode(params), argument.getValue().getConfiguration());
    assertEquals(destinationDefId, argument.getValue().getDestinationDefinitionId());
  }

  @Test
  void resetDestinationInstancewideOauthParams() throws JsonValidationException, IOException {
    final UUID destinationDefId = UUID.randomUUID();
    final Map<String, Object> firstParams = new HashMap<>();
    firstParams.put(CLIENT_ID_KEY, CLIENT_ID);
    firstParams.put(CLIENT_SECRET_KEY, CLIENT_SECRET);
    final SetInstancewideDestinationOauthParamsRequestBody firstRequest = new SetInstancewideDestinationOauthParamsRequestBody()
        .destinationDefinitionId(destinationDefId)
        .params(firstParams);
    handler.setDestinationInstancewideOauthParams(firstRequest);

    final UUID oauthParameterId = UUID.randomUUID();
    when(configRepository.getDestinationOAuthParamByDefinitionIdOptional(null, destinationDefId))
        .thenReturn(Optional.of(new DestinationOAuthParameter().withOauthParameterId(oauthParameterId)));

    final Map<String, Object> secondParams = new HashMap<>();
    secondParams.put(CLIENT_ID_KEY, "456");
    secondParams.put(CLIENT_SECRET_KEY, "hunter3");
    final SetInstancewideDestinationOauthParamsRequestBody secondRequest = new SetInstancewideDestinationOauthParamsRequestBody()
        .destinationDefinitionId(destinationDefId)
        .params(secondParams);
    handler.setDestinationInstancewideOauthParams(secondRequest);

    final ArgumentCaptor<DestinationOAuthParameter> argument = ArgumentCaptor.forClass(DestinationOAuthParameter.class);
    Mockito.verify(configRepository, Mockito.times(2)).writeDestinationOAuthParam(argument.capture());
    final List<DestinationOAuthParameter> capturedValues = argument.getAllValues();
    assertEquals(Jsons.jsonNode(firstParams), capturedValues.get(0).getConfiguration());
    assertEquals(Jsons.jsonNode(secondParams), capturedValues.get(1).getConfiguration());
    assertEquals(destinationDefId, capturedValues.get(0).getDestinationDefinitionId());
    assertEquals(destinationDefId, capturedValues.get(1).getDestinationDefinitionId());
    assertEquals(oauthParameterId, capturedValues.get(1).getOauthParameterId());
  }

  @Test
  void testBuildJsonPathFromOAuthFlowInitParameters() {
    final Map<String, List<String>> input = Map.ofEntries(
        Map.entry("field1", List.of("1")),
        Map.entry("field2", List.of("2", "3")));

    final Map<String, String> expected = Map.ofEntries(
        Map.entry("field1", "$.1"),
        Map.entry("field2", "$.2.3"));

    assertEquals(expected, handler.buildJsonPathFromOAuthFlowInitParameters(input));
  }

  @Test
  void testGetOAuthInputConfiguration() {
    final JsonNode hydratedConfig = Jsons.deserialize(
        """
        {
          "field1": "1",
          "field2": "2",
          "field3": {
            "field3_1": "3_1",
            "field3_2": "3_2"
          }
        }
        """);

    final Map<String, String> pathsToGet = Map.ofEntries(
        Map.entry("field1", "$.field1"),
        Map.entry("field3_1", "$.field3.field3_1"),
        Map.entry("field3_2", "$.field3.field3_2"),
        Map.entry("field4", "$.someNonexistentField"));

    final JsonNode expected = Jsons.deserialize(
        """
        {
          "field1": "1",
          "field3_1": "3_1",
          "field3_2": "3_2"
        }
        """);

    assertEquals(expected, handler.getOAuthInputConfiguration(hydratedConfig, pathsToGet));
  }

  @Test
  void testGetOauthFromDBIfNeeded() {
    final JsonNode fromInput = Jsons.deserialize(
        """
        {
          "testMask": "**********",
          "testNotMask": "this",
          "testOtherType": true
        }
        """);

    final JsonNode fromDb = Jsons.deserialize(
        """
        {
          "testMask": "mask",
          "testNotMask": "notThis",
          "testOtherType": true
        }
        """);

    final JsonNode expected = Jsons.deserialize(
        """
        {
          "testMask": "mask",
          "testNotMask": "this",
          "testOtherType": true
        }
        """);

    assertEquals(expected, handler.getOauthFromDBIfNeeded(fromDb, fromInput));
  }

  @Test
  void testCompleteSourceOAuthHandleReturnSecret() throws JsonValidationException, ConfigNotFoundException, IOException {
    final UUID sourceDefinitionId = UUID.randomUUID();
    final UUID workspaceId = UUID.randomUUID();

    // This is being created without returnSecretCoordinate set intentionally
    final CompleteSourceOauthRequest completeSourceOauthRequest = new CompleteSourceOauthRequest()
        .sourceDefinitionId(sourceDefinitionId)
        .workspaceId(workspaceId);

    final OAuthHandler handlerSpy = Mockito.spy(handler);

    doReturn(
        handler.mapToCompleteOAuthResponse(Map.of("access_token", "access", "refresh_token", "refresh"))).when(handlerSpy).completeSourceOAuth(any());
    doReturn(
        handler.mapToCompleteOAuthResponse(Map.of("secret_id", "secret"))).when(handlerSpy).writeOAuthResponseSecret(any(), any());

    handlerSpy.completeSourceOAuthHandleReturnSecret(completeSourceOauthRequest);

    // Tests that with returnSecretCoordinate unset, we DO NOT return secrets.
    verify(handlerSpy).completeSourceOAuth(completeSourceOauthRequest);
    verify(handlerSpy, never()).writeOAuthResponseSecret(any(), any());

    completeSourceOauthRequest.returnSecretCoordinate(true);

    handlerSpy.completeSourceOAuthHandleReturnSecret(completeSourceOauthRequest);

    // Tests that with returnSecretCoordinate set explicitly to true, we DO return secrets.
    verify(handlerSpy, times(2)).completeSourceOAuth(completeSourceOauthRequest);
    verify(handlerSpy).writeOAuthResponseSecret(any(), any());

    completeSourceOauthRequest.returnSecretCoordinate(false);

    handlerSpy.completeSourceOAuthHandleReturnSecret(completeSourceOauthRequest);

    // Tests that with returnSecretCoordinate set explicitly to false, we DO NOT return secrets.
    verify(handlerSpy, times(3)).completeSourceOAuth(completeSourceOauthRequest);
    verify(handlerSpy).writeOAuthResponseSecret(any(), any());
  }

  @Test
  void testGetSourceOAuthParamConfigNoParamFound() throws JsonValidationException, ConfigNotFoundException, IOException {
    final UUID sourceDefinitionId = UUID.randomUUID();
    final UUID workspaceId = UUID.randomUUID();
    when(configRepository.listSourceOAuthParam()).thenReturn(List.of());
    assertThrows(ConfigNotFoundException.class, () -> handler.getSourceOAuthParamConfig(sourceDefinitionId, workspaceId));

  }

  @Test
  void testGetSourceOAuthParamConfigNoFeatureFlag() throws JsonValidationException, ConfigNotFoundException, IOException {
    final UUID sourceDefinitionId = UUID.randomUUID();
    final UUID workspaceId = UUID.randomUUID();
    SourceOAuthParameter sourceOAuthParameter = new SourceOAuthParameter()
        .withOauthParameterId(UUID.randomUUID())
        .withSourceDefinitionId(sourceDefinitionId)
        .withConfiguration(Jsons.deserialize("""
                                             {"credentials": {"client_id": "test", "client_secret": "shhhh" }}
                                             """));
    when(configRepository.listSourceOAuthParam()).thenReturn(List.of(sourceOAuthParameter));
    when(secretsRepositoryReader.hydrateConfig(any())).thenReturn(sourceOAuthParameter.getConfiguration());

    JsonNode expected = Jsons.deserialize("""
                                          {"client_id": "test", "client_secret": "shhhh"}
                                          """);
    assertEquals(expected, handler.getSourceOAuthParamConfig(workspaceId, sourceDefinitionId));
  }

  @Test
  void testGetSourceOAuthParamConfigFeatureFlagNoOverride() throws JsonValidationException, ConfigNotFoundException, IOException {
    final UUID sourceDefinitionId = UUID.randomUUID();
    final UUID workspaceId = UUID.randomUUID();
    SourceOAuthParameter sourceOAuthParameter = new SourceOAuthParameter()
        .withOauthParameterId(UUID.randomUUID())
        .withSourceDefinitionId(sourceDefinitionId)
        .withConfiguration(Jsons.deserialize("""
                                             {"credentials": {"client_id": "test", "client_secret": "shhhh" }}
                                             """));
    when(configRepository.listSourceOAuthParam()).thenReturn(List.of(sourceOAuthParameter));
    when(featureFlagClient.boolVariation(any(), any())).thenReturn(true);
    when(secretsRepositoryReader.hydrateConfig(any())).thenReturn(sourceOAuthParameter.getConfiguration());

    JsonNode expected = Jsons.deserialize("""
                                          {"client_id": "test", "client_secret": "shhhh"}
                                          """);
    assertEquals(expected, handler.getSourceOAuthParamConfig(workspaceId, sourceDefinitionId));
  }

}
