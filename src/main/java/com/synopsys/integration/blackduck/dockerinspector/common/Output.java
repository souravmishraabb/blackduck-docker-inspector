/**
 * blackduck-docker-inspector
 *
 * Copyright (C) 2019 Black Duck Software, Inc.
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
package com.synopsys.integration.blackduck.dockerinspector.common;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;

import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.google.gson.Gson;
import com.synopsys.integration.blackduck.dockerinspector.blackduckclient.BlackDuckClient;
import com.synopsys.integration.blackduck.dockerinspector.config.Config;
import com.synopsys.integration.blackduck.dockerinspector.config.ProgramPaths;
import com.synopsys.integration.blackduck.imageinspector.lib.ImageInspector;
import com.synopsys.integration.blackduck.imageinspector.linux.extractor.BdioGenerator;
import com.synopsys.integration.blackduck.imageinspector.result.ResultFile;
import com.synopsys.integration.exception.IntegrationException;
import com.synopsys.integration.bdio.BdioWriter;
import com.synopsys.integration.bdio.model.SimpleBdioDocument;

@Component
public class Output {
    private final Logger logger = LoggerFactory.getLogger(this.getClass());

    @Autowired
    private Config config;

    @Autowired
    private ProgramPaths programPaths;

    @Autowired
    private Gson gson;

    public void ensureWriteability() {
        if (config.isOnHost()) {
            final File outputDir = new File(programPaths.getDockerInspectorOutputPathHost());
            final boolean dirCreated = outputDir.mkdirs();
            final boolean dirMadeWriteable = outputDir.setWritable(true, false);
            final boolean dirMadeExecutable = outputDir.setExecutable(true, false);
            logger.debug(String.format("Output dir: %s; created: %b; successfully made writeable: %b; make executable: %b", outputDir.getAbsolutePath(), dirCreated, dirMadeWriteable, dirMadeExecutable));
        }
    }

    public void provideOutput() throws IOException {
        if (config.isOnHost()) {
            copyOutputToUserOutputDir();
        }
    }

    public File provideBdioFileOutput(final SimpleBdioDocument bdioDocument) throws IOException, IntegrationException {
        // if user specified an output dir, use that; else use the temp output dir
        File outputDir;
        if (StringUtils.isNotBlank(config.getOutputPath())) {
            outputDir = new File(config.getOutputPath());
            provideOutput();
        } else {
            outputDir = new File(programPaths.getDockerInspectorOutputPathHost());
        }
        final String bdioFilename = String.format("%s_bdio.jsonld", bdioDocument.billOfMaterials.spdxName);
        final File outputBdioFile = new File(outputDir, bdioFilename);
        final FileOutputStream outputBdioStream = new FileOutputStream(outputBdioFile);
        logger.info(String.format("Writing BDIO to %s", outputBdioFile.getAbsolutePath()));
        try (BdioWriter bdioWriter = new BdioWriter(gson, outputBdioStream)) {
            bdioWriter.writeSimpleBdioDocument(bdioDocument);
        }
        return outputBdioFile;
    }

    private void copyOutputToUserOutputDir() throws IOException {
        final String userOutputDirPath = programPaths.getUserOutputDir();
        if (userOutputDirPath == null) {
            logger.debug("User has not specified an output path");
            return;
        }
        final File srcDir = new File(programPaths.getDockerInspectorOutputPathHost());
        if (!srcDir.exists()) {
            logger.info(String.format("Output source dir %s does not exist", srcDir.getAbsolutePath()));
            return;
        }
        logger.info(String.format("Copying output from %s to %s", programPaths.getDockerInspectorOutputPathHost(), userOutputDirPath));
        final File userOutputDir = new File(userOutputDirPath);
        copyDirContentsToDir(programPaths.getDockerInspectorOutputPathHost(), userOutputDir.getAbsolutePath(), true);
    }

    private void copyDirContentsToDir(final String fromDirPath, final String toDirPath, final boolean createIfNecessary) throws IOException {
        final File srcDir = new File(fromDirPath);
        final File destDir = new File(toDirPath);
        if (createIfNecessary && !destDir.exists()) {
            destDir.mkdirs();
        }
        FileUtils.copyDirectory(srcDir, destDir);
    }

}
