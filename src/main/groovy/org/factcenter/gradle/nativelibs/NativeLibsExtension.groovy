package org.factcenter.gradle.nativelibs

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.slf4j.Logger
import org.slf4j.LoggerFactory

class NativeLibsExtension {
    Logger logger = LoggerFactory.getLogger('gradle-nativelibs')

    /**
     * Reference to the project we're in
     */
    def project

    /**
     * The current operating system (taken from Java system properties and canonicalized)
     */
    def currentOS

    /**
     * Current architecture (taken from Java system properties)
     */
    def currentArch

    /**
     * Version of the OS.
     */
    def currentOSVersion

    /**
     * Platform string 'os-arch'
     */
    def currentPlatform

    /**
     * Shared-library suffix for current platform
     */
    def currentLibSuffix

    /**
     * Native platforms to copy/unpack into lib directory
     * Default is currentPlatform, but
     * can add additional platforms if cross-compiling
     */
    def supportedPlatforms = []

    /**
     * extensions for native libraries and bundles
     */
    def nativeExtensions = [ zip: true, tar: true, so: true, dll: true, dylib: true ]

    /**
     *  directory into which native libs are unpacked
     * Native libs will be unpacked into libsDir/platform/*
     * unless platformLibsDir is set
     */
    def libsDir

    /**
     * Override per-platform library dir
     * If the key <i>k</i> is set then its value is used
     * as the target directory for platform k.
     */
    def platformLibsDir = [:]

    NativeLibsExtension() {
        currentOS = System.properties['os.name'].toLowerCase()
        switch (currentOS) {
            case ~/.*os x.*/:
                currentOS = "macos"
                currentLibSuffix = "dylib"
                break
            case ~/.*windows.*/:
                currentOS = "windows"
                currentLibSuffix = "dll"
                break
            case 'linux':
            default:
                currentLibSuffix = "so"
        }
        currentArch = System.properties['os.arch'].toLowerCase()
        currentOSVersion = System.properties['os.version'].toLowerCase()
        currentPlatform = "${currentOS}-${currentArch}".toString()
        supportedPlatforms.add(currentPlatform)
    }

    /**
     * Method to compute platform-specific shared library name
     */
    String getShLibName (String basename, String version="", targetOS=currentOS,
                         targetArch=currentArch) {
        def dotVersion = ".${version}"
        if (version == "") {
            dotVersion = ""
        }

        switch (targetOS) {
            case 'osx':
                return "lib${basename}${dotVersion}.dylib"
            case 'windows':
                return "${basename}.dll"
            case 'linux':
            default:
                return "lib${basename}.so${dotVersion}"
        }
    }

    /**
     * Get current platform directory
     * @param target
     * @return
     */
    def getPlatformDir(target = currentPlatform) { platformLibsDir[target] ?: "${libsDir}/${target}" }

    /**
     * "wraps" dependencyNotation by adding a platform-specific classifier and extension.
     * @param dependencyNotation
     * @param platform
     * @param ext
     * @return
     */
    def dep(dependencyNotation, platform = currentPlatform, ext = 'zip') {
        if (dependencyNotation instanceof String || dependencyNotation instanceof groovy.lang.GString) {
            def m = dependencyNotation =~ /^([^:]*):([^:]+):([^:]*)/
            if (m) {
                def group = m.group(1)
                def name = m.group(2)
                def version = m.group(3)

                dependencyNotation = [
                        group: group,
                        name: name,
                        version: version
                ]
            } else {
                throw new org.gradle.api.InvalidUserDataException("dependencyNotation must include start with a group:name:version (not ${dependencyNotation})")
            }
        }
        if (dependencyNotation instanceof Map) {
            dependencyNotation['classifier'] = platform
            dependencyNotation['ext'] = ext
            return dependencyNotation
        } else {
            throw new org.gradle.api.InvalidUserDataException("dependencyNotation must be a String or a Map (not a ${dependencyNotation.getClass()})")
        }
    }

    /**
     * Return a list of all the native dependencies for the given configuration and
     * target platform
     * @param config
     * @param target
     */
    def getNativeDeps(configs, targets, Closure action=null) {
        def artList = []
        def seen = [:]
        configs.each { config ->
            config.resolvedConfiguration.getResolvedArtifacts().each { art ->
                if (art.classifier && targets.contains(art.classifier.toString())) {
                    if (!seen[art.file] && project.nativelibs.nativeExtensions[art.type]) {
                        artList.add(art)
                        if (action != null)
                            action.call(art)
                        seen[art.file] = true
                    }
                }
            }
        }
        return artList
    }

    /**
     * Get the resolved (unpacked) files for the native artifacts.
     * @param configs
     * @param targets
     */
    def getNativeDepFiles(configs, targets = supportedPlatforms) {
        def fileList = []
        getNativeDeps(configs, targets) { art ->
            switch (art.type) {
                case 'zip':
                case 'jar':
                    fileList.add(project.zipTree(art.file).getFiles())
                    break
                case 'tar':
                    fileList.add(project.tarTree(art.file).getFiles())
                    break
                default:
                    // Assume it's a single native lib
                    fileList.add(art.file)
            }
        }
        return fileList
    }

    /**
     * Return a list of all the native dependencies for the given configuration and
     * target platform
     * @param config
     * @param target
     */
    def getNativeDeps(configs, Closure action=null) {  getNativeDeps(configs, supportedPlatforms, action) }
}
