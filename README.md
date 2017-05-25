Main Author:  Cloud Tsai

Copyright 2016-17, Dell, Inc.

If the client micro service requires reloading the configuration data dynamically at runtime and it is not based on Spring Cloud Consul (if yes, see the next section), it needs to implement a callback API to do the reloading action.  Configuration Management sends a HTTP GET request to the callback API, when it detects any change which is related to the client micro service on the Key/Value Store.
This function leverages Consul Watches and a simple tool called core-config-watcher.  Consul Watches are a way of specifying a view of data (e.g. Key/Value pairs or health checks) which is monitored for updates.  When an update is detected, an external handler is invoked. A handler can be any executable.  In our case, the external handler is edgex-core-config-watcher which executes the following tasks:
1) Accept an argument as the micro service id.
2) Use this micro service id to query a specific property named "config.notification.path" from the Key/Value Store, and the URL might be like "/ping?config_changed=true"
Sending GET request to 
http://localhost:8500/v1/kv/config/edgex-core-data/config.notification.path
to retrieve the relative path.
3) Use this micro service id to discover the address and service port number of the microservice.
Sending GET request to
http://localhost:8500/v1/catalog/service/edgex-core-data
to retrieve the address and service port variable.
4) Send http request to the completed notification URL from 2) and 3).  Maybe, it looks like 
https://edgex-core-data:48080/ping?config_changed=true
The path of callback API is stored in a configuration property in the Key/Value Store, and its naming convention is "config.notification.path".  If there is no "config.notification.path" configuration for the micro service, edgex-core-config-watcher will exit on step 2).