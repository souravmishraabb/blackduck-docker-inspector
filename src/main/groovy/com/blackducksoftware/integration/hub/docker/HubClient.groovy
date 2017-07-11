/**
 * Hub Docker Inspector
 *
 * Copyright (C) 2017 Black Duck Software, Inc.
 * http://www.blackducksoftware.com/
 *
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements. See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership. The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package com.blackducksoftware.integration.hub.docker

import org.apache.commons.lang3.StringUtils
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component

import com.blackducksoftware.integration.hub.api.bom.BomImportRequestService
import com.blackducksoftware.integration.hub.builder.HubServerConfigBuilder
import com.blackducksoftware.integration.hub.buildtool.BuildToolConstants
import com.blackducksoftware.integration.hub.exception.HubIntegrationException
import com.blackducksoftware.integration.hub.global.HubServerConfig
import com.blackducksoftware.integration.hub.rest.CredentialsRestConnection
import com.blackducksoftware.integration.hub.service.HubServicesFactory
import com.blackducksoftware.integration.log.Slf4jIntLogger

@Component
class HubClient {
	private final Logger logger = LoggerFactory.getLogger(HubClient.class)

	@Value('${hub.url}')
	String hubUrl

	@Value('${hub.timeout}')
	String hubTimeout

	@Value('${hub.username}')
	String hubUsername

	@Value('${hub.password}')
	String hubPasswordProperty

	@Value('${BD_HUB_PASSWORD:}')
	String hubPasswordEnvVar

	@Value('${SCAN_CLI_OPTS:}')
	String scanCliOptsEnvVar

	@Value('${hub.proxy.host}')
	String hubProxyHostProperty

	@Value('${hub.proxy.port}')
	String hubProxyPortProperty

	@Value('${hub.proxy.username}')
	String hubProxyUsernameProperty

	@Value('${hub.proxy.password}')
	String hubProxyPasswordProperty

	@Value('${command.timeout}')
	long commandTimeout

	@Value('${hub.auto.import.cert}')
	Boolean autoImportCert

	boolean isValid() {
		createBuilder().isValid()
	}

	void assertValid() throws IllegalStateException {
		createBuilder().build()
	}

	void testHubConnection() throws HubIntegrationException {
		HubServerConfig hubServerConfig = createBuilder().build()
		CredentialsRestConnection credentialsRestConnection = hubServerConfig.createCredentialsRestConnection(new Slf4jIntLogger(logger))
		credentialsRestConnection.connect()
		logger.info('Successful connection to the Hub.')
	}

	void uploadBdioToHub(File bdioFile) {
		HubServerConfig hubServerConfig = createBuilder().build()

		CredentialsRestConnection credentialsRestConnection = hubServerConfig.createCredentialsRestConnection(new Slf4jIntLogger(logger))
		HubServicesFactory hubServicesFactory = new HubServicesFactory(credentialsRestConnection)
		BomImportRequestService bomImportRequestService = hubServicesFactory.createBomImportRequestService()
		bomImportRequestService.importBomFile(bdioFile, BuildToolConstants.BDIO_FILE_MEDIA_TYPE)
		logger.info("Uploaded bdio file ${bdioFile.getName()} to ${hubServerConfig.hubUrl}")
	}

	private HubServerConfigBuilder createBuilder() {

		String hubPassword = hubPasswordEnvVar
		if (!StringUtils.isBlank(hubPasswordProperty)) {
			hubPassword = hubPasswordProperty
		}

		String hubProxyHost = hubProxyHostProperty
		String hubProxyPort = hubProxyPortProperty
		String hubProxyUsername = hubProxyUsernameProperty
		String hubProxyPassword = hubProxyPasswordProperty
		if ((StringUtils.isBlank(hubProxyHostProperty)) && (!StringUtils.isBlank(scanCliOptsEnvVar))) {
			List<String> scanCliOpts = scanCliOptsEnvVar.split("\\s")
			for (String opt : scanCliOpts) {
				opt = opt.trim()
				if ((opt.startsWith("-Dhttp.proxy.host=")) || (opt.startsWith("-Dhttps.proxy.host="))) {
					hubProxyHost = getValue(opt)
				} else if ((opt.startsWith("-Dhttp.proxy.port=")) || (opt.startsWith("-Dhttps.proxy.port="))) {
					hubProxyPort = getValue(opt)
				} else if ((opt.startsWith("-Dhttp.proxy.username=")) || (opt.startsWith("-Dhttps.proxy.username="))) {
					hubProxyUsername = getValue(opt)
				} else if ((opt.startsWith("-Dhttp.proxy.password=")) || (opt.startsWith("-Dhttps.proxy.password="))) {
					hubProxyPassword = getValue(opt)
				}
			}
		}

		HubServerConfigBuilder hubServerConfigBuilder = new HubServerConfigBuilder()
		hubServerConfigBuilder.hubUrl = hubUrl
		hubServerConfigBuilder.username = hubUsername
		hubServerConfigBuilder.password = hubPassword

		hubServerConfigBuilder.timeout = hubTimeout
		hubServerConfigBuilder.proxyHost = hubProxyHost
		hubServerConfigBuilder.proxyPort = hubProxyPort
		hubServerConfigBuilder.proxyUsername = hubProxyUsername
		hubServerConfigBuilder.proxyPassword = hubProxyPassword

		if(autoImportCert == null){
			autoImportCert = true
		}
		hubServerConfigBuilder.autoImportHttpsCertificates = autoImportCert

		hubServerConfigBuilder
	}

	private String getValue(String nameEqualsValue) {
		List<String> nameValue = nameEqualsValue.split("=")
		String value = null
		if (nameValue.size() == 2) {
			value = nameValue.get(1)
		}
		return value
	}
}
