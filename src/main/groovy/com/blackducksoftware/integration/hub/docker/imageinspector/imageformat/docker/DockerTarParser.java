/**
 * hub-docker-inspector
 *
 * Copyright (C) 2018 Black Duck Software, Inc.
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
package com.blackducksoftware.integration.hub.docker.imageinspector.imageformat.docker;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import org.apache.commons.compress.archivers.tar.TarArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.blackducksoftware.integration.hub.docker.imageinspector.Names;
import com.blackducksoftware.integration.hub.docker.imageinspector.OperatingSystemEnum;
import com.blackducksoftware.integration.hub.docker.imageinspector.PackageManagerEnum;
import com.blackducksoftware.integration.hub.docker.imageinspector.imageformat.docker.manifest.Manifest;
import com.blackducksoftware.integration.hub.docker.imageinspector.imageformat.docker.manifest.ManifestFactory;
import com.blackducksoftware.integration.hub.docker.imageinspector.imageformat.docker.manifest.ManifestLayerMapping;
import com.blackducksoftware.integration.hub.docker.imageinspector.linux.FileSys;
import com.blackducksoftware.integration.hub.exception.HubIntegrationException;

@Component
public class DockerTarParser {
    private static final String TAR_EXTRACTION_DIRECTORY = "tarExtraction";
    private static final String TARGET_IMAGE_FILESYSTEM_PARENT_DIR = "imageFiles";
    private static final String DOCKER_LAYER_TAR_FILENAME = "layer.tar";
    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    private File workingDirectory;
    private File tarExtractionDirectory;

    @Autowired
    private ManifestFactory manifestFactory;

    public void setWorkingDirectory(final File workingDirectory) {
        logger.debug(String.format("working dir: %s", workingDirectory));
        this.workingDirectory = workingDirectory;
    }

    public File extractDockerLayers(final String imageName, final String imageTag, final List<File> layerTars, final List<ManifestLayerMapping> manifestLayerMappings) throws IOException {
        logger.debug(String.format("working dir: %s", workingDirectory));
        final File tarExtractionDirectory = getTarExtractionDirectory();
        final File targetImageFileSystemParentDir = new File(tarExtractionDirectory, TARGET_IMAGE_FILESYSTEM_PARENT_DIR);
        File targetImageFileSystemRootDir = null;
        for (final ManifestLayerMapping manifestLayerMapping : manifestLayerMappings) {
            for (final String layer : manifestLayerMapping.getLayers()) {
                logger.trace(String.format("Looking for tar for layer: %s", layer));
                final File layerTar = getLayerTar(layerTars, layer);
                if (layerTar != null) {
                    targetImageFileSystemRootDir = extractLayerTarToDir(imageName, imageTag, targetImageFileSystemParentDir, layerTar, manifestLayerMapping);
                } else {
                    logger.error(String.format("Could not find the tar for layer %s", layer));
                }
            }
        }
        return targetImageFileSystemRootDir;
    }

    public OperatingSystemEnum detectOperatingSystem(final String operatingSystem) {
        OperatingSystemEnum osEnum = null;
        if (StringUtils.isNotBlank(operatingSystem)) {
            osEnum = OperatingSystemEnum.determineOperatingSystem(operatingSystem);
        }
        return osEnum;
    }

    public OperatingSystemEnum detectOperatingSystem(final File targetImageFileSystemRootDir) throws HubIntegrationException, IOException {
        final OperatingSystemEnum osEnum = deriveOsFromPkgMgr(targetImageFileSystemRootDir);
        if (osEnum != null) {
            return osEnum;
        }
        throw new HubIntegrationException("No package manager files were found, and no operating system name was provided.");
    }

    public ImageInfoParsed collectPkgMgrInfo(final File targetImageFileSystemRootDir, final OperatingSystemEnum osEnum) throws HubIntegrationException {
        logger.debug(String.format("Checking image file system at %s for package managers", targetImageFileSystemRootDir.getName()));
        if (osEnum == null) {
            throw new HubIntegrationException("Operating System value is null");
        }
        for (final PackageManagerEnum packageManagerEnum : PackageManagerEnum.values()) {
            final File packageManagerDirectory = new File(targetImageFileSystemRootDir, packageManagerEnum.getDirectory());
            if (packageManagerDirectory.exists()) {
                logger.info(String.format("Found package Manager Dir: %s", packageManagerDirectory.getAbsolutePath()));
                final ImagePkgMgr targetImagePkgMgr = new ImagePkgMgr(packageManagerDirectory, packageManagerEnum);
                final ImageInfoParsed imagePkgMgrInfo = new ImageInfoParsed(targetImageFileSystemRootDir.getName(), osEnum, targetImagePkgMgr);
                return imagePkgMgrInfo;
            } else {
                logger.debug(String.format("Package manager dir %s does not exist", packageManagerDirectory.getAbsolutePath()));
            }
        }
        throw new HubIntegrationException("No package manager files found in this Docker image.");

    }

    public List<File> extractLayerTars(final File dockerTar) throws IOException {
        logger.debug(String.format("working dir: %s", workingDirectory));
        final File tarExtractionDirectory = getTarExtractionDirectory();
        final List<File> untaredFiles = new ArrayList<>();
        final File outputDir = new File(tarExtractionDirectory, dockerTar.getName());
        final TarArchiveInputStream tarArchiveInputStream = new TarArchiveInputStream(new FileInputStream(dockerTar));
        try {
            TarArchiveEntry tarArchiveEntry = null;
            while (null != (tarArchiveEntry = tarArchiveInputStream.getNextTarEntry())) {
                final File outputFile = new File(outputDir, tarArchiveEntry.getName());
                if (tarArchiveEntry.isFile()) {
                    if (!outputFile.getParentFile().exists()) {
                        outputFile.getParentFile().mkdirs();
                    }
                    final OutputStream outputFileStream = new FileOutputStream(outputFile);
                    try {
                        IOUtils.copy(tarArchiveInputStream, outputFileStream);
                        if (tarArchiveEntry.getName().contains(DOCKER_LAYER_TAR_FILENAME)) {
                            untaredFiles.add(outputFile);
                        }
                    } finally {
                        outputFileStream.close();
                    }
                }
            }
        } finally {
            IOUtils.closeQuietly(tarArchiveInputStream);
        }
        return untaredFiles;
    }

    public List<ManifestLayerMapping> getLayerMappings(final String tarFileName, final String dockerImageName, final String dockerTagName) throws Exception {
        logger.debug(String.format("getLayerMappings(): dockerImageName: %s; dockerTagName: %s", dockerImageName, dockerTagName));
        logger.debug(String.format("working dir: %s", workingDirectory));
        final Manifest manifest = manifestFactory.createManifest(getTarExtractionDirectory(), tarFileName);
        List<ManifestLayerMapping> mappings;
        try {
            mappings = manifest.getLayerMappings(dockerImageName, dockerTagName);
        } catch (final Exception e) {
            logger.error(String.format("Could not parse the image manifest file : %s", e.getMessage()));
            throw e;
        }
        if (mappings.size() == 0) {
            final String msg = String.format("Could not find image %s:%s in tar file %s", dockerImageName, dockerTagName, tarFileName);
            throw new HubIntegrationException(msg);
        }
        return mappings;
    }

    private File getTarExtractionDirectory() {
        if (tarExtractionDirectory == null) {
            tarExtractionDirectory = new File(workingDirectory, TAR_EXTRACTION_DIRECTORY);
        }
        return tarExtractionDirectory;
    }

    private File extractLayerTarToDir(final String imageName, final String imageTag, final File imageFilesDir, final File layerTar, final ManifestLayerMapping mapping) throws IOException {
        logger.trace(String.format("Extracting layer: %s into %s", layerTar.getAbsolutePath(), Names.getTargetImageFileSystemRootDirName(imageName, imageTag)));
        final File targetImageFileSystemRoot = new File(imageFilesDir, Names.getTargetImageFileSystemRootDirName(imageName, imageTag));
        final DockerLayerTar dockerLayerTar = new DockerLayerTar(layerTar);
        dockerLayerTar.extractToDir(targetImageFileSystemRoot);
        return targetImageFileSystemRoot;
    }

    private File getLayerTar(final List<File> layerTars, final String layer) {
        File layerTar = null;
        for (final File candidateLayerTar : layerTars) {
            if (layer.equals(candidateLayerTar.getParentFile().getName())) {
                logger.debug(String.format("Found layer tar for layer %s", layer));
                layerTar = candidateLayerTar;
                break;
            }
        }
        return layerTar;
    }

    private OperatingSystemEnum deriveOsFromPkgMgr(final File targetImageFileSystemRootDir) {
        OperatingSystemEnum osEnum = null;

        final FileSys extractedFileSys = new FileSys(targetImageFileSystemRootDir);
        final Set<PackageManagerEnum> packageManagers = extractedFileSys.getPackageManagers();
        if (packageManagers.size() == 1) {
            final PackageManagerEnum packageManager = packageManagers.iterator().next();
            osEnum = packageManager.getOperatingSystem();
            logger.debug(String.format("Package manager %s returns Operating System %s", packageManager.name(), osEnum.name()));
            return osEnum;
        }
        return null;

    }

}