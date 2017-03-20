/*
 * Copyright (C) 2017 Black Duck Software Inc.
 * http://www.blackducksoftware.com/
 * All rights reserved.
 *
 * This software is the confidential and proprietary information of
 * Black Duck Software ("Confidential Information"). You shall not
 * disclose such Confidential Information and shall use it only in
 * accordance with the terms of the license agreement you entered into
 * with Black Duck Software.
 */
package com.blackducksoftware.integration.hub.docker.extractor

import javax.annotation.PostConstruct

import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component

import com.blackducksoftware.integration.hub.bdio.simple.BdioWriter
import com.blackducksoftware.integration.hub.docker.OperatingSystemEnum
import com.blackducksoftware.integration.hub.docker.PackageManagerEnum

@Component
class YumExtractor extends Extractor {
    private final Logger logger = LoggerFactory.getLogger(YumExtractor.class)

    @PostConstruct
    void init() {
        initValues(PackageManagerEnum.YUM)
    }

    void extractComponents(BdioWriter bdioWriter, OperatingSystemEnum operatingSystem,  File yumOutput) {
        boolean startOfComponents = false

        def componentColumns = []
        yumOutput.eachLine { line ->
            if (line != null) {
                if ('Installed Packages' == line) {
                    startOfComponents = true
                } else if (startOfComponents) {
                    componentColumns.addAll(line.tokenize(' '))
                    if ((componentColumns.size() == 3) && (!line.startsWith("Loaded plugins:"))) {
                        String nameArch = componentColumns.get(0)
                        String version = componentColumns.get(1)
                        String name =nameArch.substring(0, nameArch.lastIndexOf("."))
                        String architecture = nameArch.substring(nameArch.lastIndexOf(".") + 1)

                        String externalId = "$name/$version/$architecture"
                        bdioWriter.writeBdioNode(bdioNodeFactory.createComponent(name, version, null, operatingSystem.forge, externalId))
                        componentColumns = []
                    } else  if (componentColumns.size() > 3) {
                        logger.error("Parsing multi-line components has failed. $line")
                        componentColumns = []
                    }
                }
            }
        }
    }
}
