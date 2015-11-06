package org.factcenter.gradle.nativelibs

import org.gradle.api.Plugin
import org.gradle.api.Project

class NativeLibsExtension {
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
     * This is a map, with platforms as keys
     */
    def supportedPlatforms = [:]

    /**
     * extensions for native libraries and bundles
     */
    def nativeExtensions = [ zip: true, tar: true, so: true, dll: true, dylib: true ]

    /**
     *  directory into which native libs are unpacked
     * Native libs will be unpacked into libsDir/platform/*
     * unless nativeLibsPlatformDir is set
     */
    def libsDir

    /**
     * Override per-platform library dir
     * If the key <i>platform</i> is set then its value is used
     * as the target directory for that platform.
     */
    def platformLibsDir = [:]

    NativeLibsExtension() {
        currentOS = System.properties['os.name'].toLowerCase()
        switch (currentOS) {
            case ~/.*os x.*/:
                currentOS = "osx"
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
        currentPlatform = "${currentOS}-${currentArch}"
        supportedPlatforms[currentPlatform] = true
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
}
