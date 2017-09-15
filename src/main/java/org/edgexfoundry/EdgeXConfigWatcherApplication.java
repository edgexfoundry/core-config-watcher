/*******************************************************************************
 * Copyright 2016-2017 Dell Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 *
 * @microservice: core-config-watcher
 * @author: Cloud Tsai, Dell
 * @version: 1.0.0
 *******************************************************************************/

package org.edgexfoundry;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.List;
import java.util.Properties;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;

import org.apache.http.HttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.nio.client.CloseableHttpAsyncClient;
import org.apache.http.impl.nio.client.HttpAsyncClients;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.base.Optional;
import com.orbitz.consul.Consul;
import com.orbitz.consul.KeyValueClient;
import com.orbitz.consul.model.catalog.CatalogService;

public class EdgeXConfigWatcherApplication {

  private static final String APP_PROPERTIES = "./src/main/resources/application.properties";
  private static final String CONSUL_KEY_SEPARATOR = "/";
  private static final Logger LOGGER = LoggerFactory.getLogger(EdgeXConfigWatcherApplication.class);

  private static String serviceName;
  private static String globalPrefix;
  private static String servicePrefix;
  private static String consulProtocol;
  private static String consulHost;
  private static int consulPort;
  private static String notificationPathKey;
  private static String notificationProtocol;

  public static void main(String[] args) {
    loadProperties();
    LOGGER.info("Connecting to Consul client at {} : {}", consulHost, consulPort);
    try {
      Consul consul = getConsul();
      String notificationPath = getNotificationPath(consul);
      List<CatalogService> services = getApplicationServices(consul);
      registerWatcher(services, notificationPath);
    } catch (MalformedURLException e) {
      LOGGER.error("URI Syntax issue getting to Consul:" + e.getMessage());
    }
  }

  public static Consul getConsul() throws MalformedURLException {
    return Consul.builder().withUrl(new URL(consulProtocol, consulHost, consulPort, "")).build();
  }

  public static String getNotificationPath(Consul consul) {
    KeyValueClient kvClient = consul.keyValueClient();
    Optional<String> notificationPathOptional =
        kvClient.getValueAsString(CONSUL_KEY_SEPARATOR + globalPrefix + CONSUL_KEY_SEPARATOR
            + servicePrefix + CONSUL_KEY_SEPARATOR + notificationPathKey);
    String notificationPath = "";
    if (notificationPathOptional.isPresent()) {
      notificationPath = notificationPathOptional.get();
    } else {
      LOGGER.info("This service doesn't have the notification endpoint");
      System.exit(0);
    }
    return notificationPath;
  }

  public static List<CatalogService> getApplicationServices(Consul consul) {
    List<CatalogService> services = consul.catalogClient().getService(serviceName).getResponse();
    if (services.isEmpty()) {
      LOGGER.info("This service hasn't started up");
      System.exit(0);
    }
    return services;
  }

  public static void registerWatcher(List<CatalogService> services, String notificationPath) {
    try (CloseableHttpAsyncClient httpClient = HttpAsyncClients.createDefault();) {
      httpClient.start();
      for (CatalogService service : services) {
        LOGGER.info("Attempting registration of {} to: {}", service.getServiceName(),
            notificationPath);
        URL serviceNotificationURL = new URL(notificationProtocol, service.getAddress(),
            service.getServicePort(), notificationPath);
        HttpGet request = new HttpGet(serviceNotificationURL.toURI());
        Future<HttpResponse> future = httpClient.execute(request, null);
        future.get();
        LOGGER.info("Registered {} @ {}", service.getServiceName(), notificationPath);
      }
    } catch (URISyntaxException e) {
      LOGGER.error("URI Syntax issue: " + e.getMessage());
    } catch (InterruptedException e) {
      LOGGER.error("Interrupted process: " + e.getMessage());
      Thread.currentThread().interrupt();
    } catch (ExecutionException e) {
      LOGGER.error("Execution problem: " + e.getMessage());
    } catch (IOException e) {
      LOGGER.error("IO problem: " + e.getMessage());
    }
  }

  private static boolean loadProperties() {
    Properties properties = new Properties();
    try (final InputStream inputStream = new FileInputStream(APP_PROPERTIES)) {
      properties.load(inputStream);
      serviceName = properties.getProperty("service.name");
      globalPrefix = properties.getProperty("global.prefix");
      servicePrefix = properties.getProperty("service.prefix");
      consulProtocol = properties.getProperty("consul.protocol");
      consulHost = properties.getProperty("consul.host");
      consulPort = Integer.parseInt(properties.getProperty("consul.port", "8500"));
      notificationPathKey = properties.getProperty("notification.path.key");
      notificationProtocol = properties.getProperty("notification.protocol");
      if (serviceName == null || globalPrefix == null || servicePrefix == null
          || consulProtocol == null || consulHost == null || consulPort == 0
          || notificationPathKey == null || notificationProtocol == null) {
        LOGGER.error(
            "Application configuration did not load properly or is missing elements.  Cannot create watcher.");
        System.exit(1);
        return false;
      }
    } catch (IOException ex) {
      LOGGER.error("IO Exception getting application properties: " + ex.getMessage());
      System.exit(1);
      return false;
    }
    return true;
  }
}
