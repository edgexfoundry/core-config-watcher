/*******************************************************************************
 * Copyright 2016-2017 Dell Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 * @microservice:  core-config-watcher
 * @author: Cloud Tsai, Dell
 * @version: 1.0.0
 *******************************************************************************/
package org.edgexfoundry;

import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;

import org.apache.http.HttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.nio.client.CloseableHttpAsyncClient;
import org.apache.http.impl.nio.client.HttpAsyncClients;

import com.google.common.base.Optional;
import com.orbitz.consul.Consul;
import com.orbitz.consul.KeyValueClient;
import com.orbitz.consul.model.catalog.CatalogService;

public class EdgeXConfigWatcherApplication {

	private final static String CONSUL_KEY_SEPARATOR = "/";

	private final static String protocol = "http";

	private final static String host = "localhost";

	private final static int port = 8500;

	private final static String globalPrefix = "config";

	private final static String notificationPathKey = "config.notification.path";

	public static void main(String[] args) {

		String appName;

		if (args.length > 0) {
			appName = args[0];
		} else {
			System.err.println("The target application is unset, so there is nothing to do.");
			return;
		}

		if ("".equals(appName)) {
			System.err.println("The target application is unset, so there is nothing to do.");
			return;
		}

		System.out.println("Connecting to Consul client at " + host + ":" + port);

		try {
			Consul consul = Consul.builder().withUrl(new URL(protocol, host, port, "")).build();
			KeyValueClient kvClient = consul.keyValueClient();
			Optional<String> notificationPathOptional = kvClient.getValueAsString(CONSUL_KEY_SEPARATOR + globalPrefix
					+ CONSUL_KEY_SEPARATOR + appName + CONSUL_KEY_SEPARATOR + notificationPathKey);
			String notificationPath = "";
			if (notificationPathOptional.isPresent()) {
				notificationPath = notificationPathOptional.get();
			} else {
				System.out.println("This application doesn't have the notification endpoint");
				return;
			}

			List<CatalogService> services = consul.catalogClient().getService(appName).getResponse();

			if (services.isEmpty()) {
				System.out.println("This application hasn't started up");
				return;
			}

			try (CloseableHttpAsyncClient httpClient = HttpAsyncClients.createDefault();) {

				httpClient.start();

				for (CatalogService service : services) {
					URL serviceNotificationURL = new URL(protocol, service.getAddress(), service.getServicePort(),
							notificationPath);
					HttpGet request = new HttpGet(serviceNotificationURL.toURI());
					Future<HttpResponse> future = httpClient.execute(request, null);
					future.get();
				}

			}
		} catch (MalformedURLException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (URISyntaxException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (InterruptedException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (ExecutionException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}

	}
}
