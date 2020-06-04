package com.wavefront.spring.autoconfigure;

import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;
import java.util.function.BiFunction;
import java.util.function.Supplier;

import com.wavefront.sdk.common.application.ApplicationTags;
import com.wavefront.spring.account.AccountInfo;
import com.wavefront.spring.account.AccountManagementClient;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.context.event.ApplicationFailedEvent;
import org.springframework.boot.context.event.ApplicationPreparedEvent;
import org.springframework.boot.context.event.ApplicationStartedEvent;
import org.springframework.boot.context.event.SpringApplicationEvent;
import org.springframework.boot.env.EnvironmentPostProcessor;
import org.springframework.boot.logging.DeferredLog;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.context.ApplicationListener;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.io.PathResource;
import org.springframework.core.io.Resource;
import org.springframework.util.StreamUtils;
import org.springframework.util.StringUtils;

/**
 * An {@link EnvironmentPostProcessor} that auto-negotiates an api token for Wavefront if
 * necessary. If an account was already provisioned and the api token is available from
 * disk, retrieves a one time link url to the Wavefront dashboard.
 *
 * @author Stephane Nicoll
 */
class AccountManagementEnvironmentPostProcessor
    implements EnvironmentPostProcessor, ApplicationListener<SpringApplicationEvent> {

  private static final String ENABLED_PROPERTY = "management.metrics.export.wavefront.enabled";

  private static final String API_TOKEN_PROPERTY = "management.metrics.export.wavefront.api-token";

  private static final String URI_PROPERTY = "management.metrics.export.wavefront.uri";

  private static final String FREEMIUM_ACCOUNT_PROPERTY = "wavefront.freemium-account";

  private static final String DEFAULT_CLUSTER_URI = "https://wavefront.surf";

  private final DeferredLog logger = new DeferredLog();

  private Supplier<String> accountConfigurationOutcome;

  @Override
  public void postProcessEnvironment(ConfigurableEnvironment environment, SpringApplication application) {
    if (!shouldRun(environment)) {
      return;
    }
    application.addListeners(this);
    String clusterUri = environment.getProperty(URI_PROPERTY, DEFAULT_CLUSTER_URI);
    if (!isApiTokenRequired(environment)) {
      this.accountConfigurationOutcome = validateExistingConfiguration(environment, clusterUri);
      return;
    }
    Resource localApiTokenResource = getLocalApiTokenResource();
    String existingApiToken = readExistingApiToken(localApiTokenResource);
    if (existingApiToken != null) {
      this.accountConfigurationOutcome = configureExistingAccount(environment, clusterUri,
          existingApiToken, localApiTokenResource);
    }
    else {
      this.accountConfigurationOutcome = configureNewAccount(environment, clusterUri, localApiTokenResource);
    }
  }

  private boolean shouldRun(ConfigurableEnvironment environment) {
    if (environment.getPropertySources().contains("bootstrap")) {
      // Do not run in the bootstrap phase as the user configuration is not available yet
      return false;
    }
    Boolean freemiumAccount = environment.getProperty(FREEMIUM_ACCOUNT_PROPERTY, Boolean.class);
    if (freemiumAccount != null) {
      return freemiumAccount;
    }
    if (!shouldEnableAccountManagement(Thread.currentThread())) {
      return false;
    }
    return environment.getProperty(ENABLED_PROPERTY, Boolean.class, true);
  }

  @Override
  public void onApplicationEvent(SpringApplicationEvent event) {
    if (event instanceof ApplicationPreparedEvent) {
      this.logger.switchTo(AccountManagementEnvironmentPostProcessor.class);
    }
    if (event instanceof ApplicationStartedEvent || event instanceof ApplicationFailedEvent) {
      if (this.accountConfigurationOutcome != null) {
        System.out.println(this.accountConfigurationOutcome.get());
      }
    }
  }

  private boolean isApiTokenRequired(ConfigurableEnvironment environment) {
    String apiToken = environment.getProperty(API_TOKEN_PROPERTY);
    if (StringUtils.hasText(apiToken)) {
      this.logger.debug("Wavefront api token already set, no need to negotiate one");
      return false;
    }
    URI uri = environment.getProperty(URI_PROPERTY, URI.class);
    if (uri != null && "proxy".equals(uri.getScheme())) {
      this.logger.debug("Pushing to a Wavefront proxy does not require an api token.");
      return false;
    }
    return true;
  }

  private Supplier<String> validateExistingConfiguration(ConfigurableEnvironment environment, String clusterUri) {
    Boolean freemiumAccount = environment.getProperty(FREEMIUM_ACCOUNT_PROPERTY, boolean.class, false);
    String apiToken = environment.getProperty(API_TOKEN_PROPERTY);
    if (!freemiumAccount || !StringUtils.hasText(apiToken)) {
      return null;
    }
    try {
      AccountInfo accountInfo = invokeAccountManagementClient(environment,
          (client, applicationTags) -> getExistingAccount(client, clusterUri, applicationTags, apiToken));
      StringBuilder sb = new StringBuilder();
      sb.append(String.format("%nConnect to your Wavefront dashboard using this one-time use link:%n%s%n",
          accountInfo.getLoginUrl()));
      return sb::toString;
    } catch (Exception ex) {
      return accountManagementFailure(
          String.format("Failed to retrieve existing account information from %s.", clusterUri),
          ex.getMessage());
    }

  }

  private Supplier<String> configureExistingAccount(ConfigurableEnvironment environment, String clusterUri,
      String apiToken, Resource localApiTokenResource) {
    try {
      this.logger.debug("Existing Wavefront api token found from " + localApiTokenResource);
      registerApiToken(environment, apiToken);
      AccountInfo accountInfo = invokeAccountManagementClient(environment,
          (client, applicationTags) -> getExistingAccount(client, clusterUri, applicationTags, apiToken));
      return accountManagementSuccess("Your existing Wavefront account information has been restored from disk.",
          clusterUri, accountInfo);
    }
    catch (Exception ex) {
      return accountManagementFailure(
          String.format("Failed to retrieve existing account information from %s.", clusterUri),
          ex.getMessage());
    }
  }

  private Supplier<String> configureNewAccount(ConfigurableEnvironment environment, String clusterUri,
      Resource localApiTokenResource) {
    try {
      AccountInfo accountInfo = invokeAccountManagementClient(environment,
          (client, applicationInfo) -> provisionAccount(client, clusterUri, applicationInfo));
      registerApiToken(environment, accountInfo.getApiToken());
      writeApiTokenToDisk(localApiTokenResource, accountInfo.getApiToken());
      return accountManagementSuccess(
          "A Wavefront account has been provisioned successfully and the API token has been saved to disk.",
          clusterUri, accountInfo);
    }
    catch (Exception ex) {
      return accountManagementFailure(
          String.format("Failed to auto-negotiate a Wavefront api token from %s.", clusterUri),
          ex.getMessage());
    }
  }

  private Supplier<String> accountManagementSuccess(String message, String clusterUri, AccountInfo accountInfo) {
    StringBuilder sb = new StringBuilder(String.format("%n%s%n%n", message));
    sb.append(String.format("To share this account, make sure the following is added to your configuration:%n%n"));
    sb.append(String.format("\t%s=%s%n", API_TOKEN_PROPERTY, accountInfo.getApiToken()));
    sb.append(String.format("\t%s=%s%n%n", URI_PROPERTY, clusterUri));
    sb.append(String.format("Connect to your Wavefront dashboard using this one-time use link:%n%s%n",
        accountInfo.getLoginUrl()));
    return sb::toString;
  }

  private Supplier<String> accountManagementFailure(String reason, String message) {
    StringBuilder sb = new StringBuilder(String.format("%n%s", reason));
    if (StringUtils.hasText(message)) {
      sb.append(String.format(" The error was:%n%n%s%n%n", message));
    }
    return sb::toString;
  }

  private String readExistingApiToken(Resource localApiTokenResource) {
    if (localApiTokenResource.isReadable()) {
      try (InputStream in = localApiTokenResource.getInputStream()) {
        return StreamUtils.copyToString(in, StandardCharsets.UTF_8).trim();
      }
      catch (IOException ex) {
        this.logger.error("Failed to read wavefront token from " + localApiTokenResource, ex);
      }
    }
    return null;
  }

  private void writeApiTokenToDisk(Resource localApiTokenResource, String apiToken) {
    if (localApiTokenResource.isFile()) {
      try (OutputStream out = new FileOutputStream(localApiTokenResource.getFile())) {
        StreamUtils.copy(apiToken, StandardCharsets.UTF_8, out);
      }
      catch (IOException ex) {
        throw new IllegalStateException(ex);
      }
    }
  }

  private AccountInfo invokeAccountManagementClient(ConfigurableEnvironment environment,
      BiFunction<AccountManagementClient, ApplicationTags, AccountInfo> accountProvider) {
    RestTemplateBuilder restTemplateBuilder = new RestTemplateBuilder();
    AccountManagementClient client = new AccountManagementClient(restTemplateBuilder);
    ApplicationTags applicationTags = new ApplicationTagsFactory().createFromEnvironment(environment);
    return accountProvider.apply(client, applicationTags);
  }

  private void registerApiToken(ConfigurableEnvironment environment, String apiToken) {
    Map<String, Object> wavefrontSettings = new HashMap<>();
    wavefrontSettings.put(FREEMIUM_ACCOUNT_PROPERTY, true);
    wavefrontSettings.put(API_TOKEN_PROPERTY, apiToken);
    String configuredClusterUri = environment.getProperty(URI_PROPERTY);
    if (!StringUtils.hasText(configuredClusterUri)) {
      wavefrontSettings.put(URI_PROPERTY, DEFAULT_CLUSTER_URI);
    }
    MapPropertySource wavefrontPropertySource = new MapPropertySource("wavefront", wavefrontSettings);
    environment.getPropertySources().addLast(wavefrontPropertySource);
  }

  protected boolean shouldEnableAccountManagement(Thread thread) {
    return AccountManagementEnablementDeducer.shouldEnable(thread);
  }

  protected Resource getLocalApiTokenResource() {
    return new PathResource(Paths.get(System.getProperty("user.home"), ".wavefront_freemium"));
  }

  protected AccountInfo getExistingAccount(AccountManagementClient client, String clusterUri,
      ApplicationTags applicationTags, String apiToken) {
    this.logger.debug("Retrieving existing account from " + clusterUri);
    return client.getExistingAccount(clusterUri, applicationTags, apiToken);
  }

  protected AccountInfo provisionAccount(AccountManagementClient client, String clusterUri,
      ApplicationTags applicationTags) {
    this.logger.debug("Auto-negotiating Wavefront user account from " + clusterUri);
    return client.provisionAccount(clusterUri, applicationTags);
  }

}
