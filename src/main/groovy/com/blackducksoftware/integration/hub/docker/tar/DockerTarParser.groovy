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
package com.blackducksoftware.integration.hub.docker.tar


import org.apache.commons.compress.archivers.tar.TarArchiveInputStream
import org.apache.commons.io.IOUtils
import org.apache.commons.lang3.StringUtils
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component

import com.blackducksoftware.integration.hub.docker.OperatingSystemEnum
import com.blackducksoftware.integration.hub.docker.PackageManagerEnum
import com.blackducksoftware.integration.hub.docker.linux.Dirs
import com.blackducksoftware.integration.hub.docker.linux.EtcDir
import com.blackducksoftware.integration.hub.docker.linux.FileSys
import com.blackducksoftware.integration.hub.docker.tar.manifest.Manifest
import com.blackducksoftware.integration.hub.exception.HubIntegrationException

@Component
class DockerTarParser {
    private final Logger logger = LoggerFactory.getLogger(DockerTarParser.class)

    public static final String TAR_EXTRACTION_DIRECTORY = 'tarExtraction'

    // TODO make this private; add getter/setter (it gets set in HubDockerManager, plus tests)
    private File workingDirectory

    File extractDockerLayers(List<File> layerTars, List<LayerMapping> layerMappings){
        File tarExtractionDirectory = getTarExtractionDirectory()
        File imageFilesDir = new File(tarExtractionDirectory, 'imageFiles')

        layerMappings.each { mapping ->
            mapping.layers.each { layer ->
                logger.trace("layer: ${layer}")
                // TODO: move this to LayerTar class?
                File layerTar = layerTars.find{
                    StringUtils.compare(layer, it.getParentFile().getName()) == 0
                }
                if(layerTar != null){
                    def imageOutputDir = new File(imageFilesDir, mapping.getImageDirectory())
                    logger.trace("Processing layer: ${layerTar.getAbsolutePath()}")
                    DockerLayerTar dockerLayerTar = new DockerLayerTar(layerTar)
                    dockerLayerTar.extractToDir(imageOutputDir)
                } else {
                    logger.warn("Could not find the tar for layer ${layer}")
                }
            }
        }
        imageFilesDir
    }

    private File getTarExtractionDirectory() {
        return new File(workingDirectory, TAR_EXTRACTION_DIRECTORY)
    }

    OperatingSystemEnum detectOperatingSystem(String operatingSystem, File extractedFilesDir) {
        OperatingSystemEnum osEnum
        if(StringUtils.isNotBlank(operatingSystem)){
            osEnum = OperatingSystemEnum.determineOperatingSystem(operatingSystem)
        } else {
            logger.trace("Image directory ${extractedFilesDir.getName()}, looking for etc")
            List<File> etcFiles = Dirs.findFileWithName(extractedFilesDir, 'etc')
            if (etcFiles == null) {
                String msg = "Unable to find the files that specify the Linux distro of this image."
                throw new HubIntegrationException(msg)
            }
            for (File etcFile : etcFiles) {
                try{
                    EtcDir etcDir = new EtcDir(etcFile)
                    osEnum = etcDir.getOperatingSystem()
                    if(osEnum != null){
                        break
                    }
                } catch (HubIntegrationException e){
                    logger.debug("Error detecing OS from etc dir: ${e.toString()}")
                }
            }
        }
        FileSys extractedFileSys = new FileSys(extractedFilesDir)
        Set<PackageManagerEnum> packageManagers = extractedFileSys.getPackageManagers()
        if (packageManagers.size() == 1) {
            PackageManagerEnum packageManager = packageManagers.iterator().next()
            osEnum = packageManager.operatingSystem
            logger.debug("Package manager ${packageManager.name()} returns Operating System ${osEnum.name()}")
        }
        if (osEnum == null) {
            String msg = "Unable to identify the Linux distro of this image. You'll need to run with the --linux.distro option"
            throw new HubIntegrationException(msg)
        }
        osEnum
    }

    public ImagePkgMgrInfo collectPkgMgrInfo(File extractedImageFilesDir, OperatingSystemEnum osEnum) {
        ImagePkgMgrInfo imagePkgMgrInfo = new ImagePkgMgrInfo()
        imagePkgMgrInfo.operatingSystemEnum = osEnum
        // There will only be one imageDirectory; the each just gets its name.
        // It has the entire target image file system
        extractedImageFilesDir.listFiles().each { imageDirectory ->
            logger.info("**************9 Extracting data from Image directory ${imageDirectory.getName()}")
            PackageManagerEnum.values().each { packageManagerEnum ->
                File packageManagerDirectory = new File(imageDirectory, packageManagerEnum.directory)
                if (packageManagerDirectory.exists()){
                    logger.trace("Package Manager Dir: ${packageManagerDirectory.getAbsolutePath()}")
                    ImagePkgMgr result = new ImagePkgMgr()
                    result.imageDirectoryName = imageDirectory.getName()
                    result.packageManager = packageManagerEnum
                    result.extractedPackageManagerDirectory = packageManagerDirectory
                    imagePkgMgrInfo.pkgMgrs.add(result)
                } else {
                    logger.info("Package manager dir ${packageManagerDirectory.getAbsolutePath()} does not exist")
                }
            }
        }
        imagePkgMgrInfo
    }

    List<File> extractLayerTars(File dockerTar){
        File tarExtractionDirectory = getTarExtractionDirectory()
        List<File> untaredFiles = new ArrayList<>()
        final File outputDir = new File(tarExtractionDirectory, dockerTar.getName())
        def tarArchiveInputStream = new TarArchiveInputStream(new FileInputStream(dockerTar))
        try {
            def tarArchiveEntry
            while (null != (tarArchiveEntry = tarArchiveInputStream.getNextTarEntry())) {
                final File outputFile = new File(outputDir, tarArchiveEntry.getName())
                if (tarArchiveEntry.isFile()) {
                    if(!outputFile.getParentFile().exists()){
                        outputFile.getParentFile().mkdirs()
                    }
                    final OutputStream outputFileStream = new FileOutputStream(outputFile)
                    try{
                        IOUtils.copy(tarArchiveInputStream, outputFileStream)
                        if(tarArchiveEntry.name.contains('layer.tar')){
                            untaredFiles.add(outputFile)
                        }
                    } finally{
                        outputFileStream.close()
                    }
                }
            }
        } finally {
            IOUtils.closeQuietly(tarArchiveInputStream)
        }
        untaredFiles
    }

    public List<LayerMapping> getLayerMappings(String tarFileName, String dockerImageName, String dockerTagName){
        logger.debug("getLayerMappings()")
        Manifest manifest = new Manifest( dockerImageName, dockerTagName, getTarExtractionDirectory(), tarFileName)
        List<LayerMapping> mappings;
        try {
            mappings = manifest.getLayerMappings();
        } catch (Exception e) {
            logger.error("Could not parse the image manifest file : ${e.toString()}")
            throw e
        }
        mappings
    }
}