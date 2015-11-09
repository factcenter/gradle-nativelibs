package org.factcenter.gradle.nativelibs


import org.gradle.api.*
import org.gradle.api.artifacts.Configuration
import org.gradle.api.tasks.*

import org.slf4j.Logger
import org.slf4j.LoggerFactory


//========== Native Dependencies Plugin
class NativeLibsPlugin implements Plugin<Project> {
    Logger logger = LoggerFactory.getLogger('gradle-nativelibs')

    
    void apply(Project project) {
        project.extensions.create('nativelibs', NativeLibsExtension)
        project.nativelibs.project = project
        project.nativelibs.libsDir = "${project.buildDir}/nativeLibs"

        Closure addLibPath = {
            def oldJavaLibPaths = System.getProperty("java.library.path")
            if (oldJavaLibPaths != null)
                oldJavaLibPaths = ":${oldJavaLibPaths}"
            else
                oldJavaLibPaths = ""
            systemProperty ("java.library.path", project.nativelibs.getPlatformDir() + oldJavaLibPaths)
            // Really only needed in unix, but shouldn't hurt anywhere else
            def oldLibPaths = System.getenv("LD_LIBRARY_PATH")
            if (oldLibPaths != null)
                oldLibPaths = ":${oldLibPaths}"
            else
                oldLibPaths = ""
            environment ("LD_LIBRARY_PATH", project.nativelibs.getPlatformDir() + oldLibPaths)
        }

        def unpackTask = project.task('unpackNativeLibs')

        project.afterEvaluate {
            project.nativelibs.supportedPlatforms.each { plat ->
                def unpackPlat = project.task("unpackNativeLibs-${plat}", type: Sync) {
                    from {
                        into { project.nativelibs.getPlatformDir(plat) }

                        def fileList = []
                        project.nativelibs.getNativeDeps(project.configurations, [plat]) { art ->
                            switch (art.type) {
                                case 'zip':
                                case 'jar':
                                    fileList.add(project.zipTree(art.file))
                                    break
                                case 'tar':
                                    fileList.add(project.tarTree(art.file))
                                    break
                                default:
                                    // Assume it's a single native lib
                                    fileList.add(art.file)
                            }
                        }

                        return fileList
                    }
                }
                unpackTask.dependsOn(unpackPlat)
            }
        }


        project.afterEvaluate {
            // Support java and application plugins by adding library path
            // and dependencies to "run" and "test" tasks.
            // We do this in afterEvaluate to make sure all plugins are applied
            ['test', 'run'].each { taskName ->
                if (project.tasks.hasProperty(taskName)) {
                    def task = project.tasks.getByName(taskName)
                    task.configure(addLibPath)
                    task.dependsOn(unpackTask)
                }
            }

            if (project.extensions.findByName('capsule')) {
                logger.info("capsule plugin detected")
                project.tasks.withType(project.FatCapsule) { capTask ->
                    capTask.dependsOn(unpackTask)
                    capTask.configure {

                        logger.debug("configuring fatcapsule task ${capTask}")
                        // Exclude the native library bundles; we include them unpacked
                        def excludeFiles = [:]
                        project.nativelibs.getNativeDeps(project.configurations) { art ->
                            excludeFiles[art.file] = true
                        }

                        project.nativelibs.supportedPlatforms.each { plat ->
                            from(project.nativelibs.getPlatformDir(plat)) {
                                into(plat)
                            }
                        }

                        eachFile { details ->
                            if (excludeFiles[details.file])
                                details.exclude()
                        }

                        // TODO: we need support for platform-specific section in the
                        // gradle capsule plugin to enable a platform-independent
                        // capsule.
                        capsuleManifest {
//                            project.nativelibs.supportedPlatforms.each { platName ->
//                                platform(platName) {
//                                    libraryPathP = [ platName ]
//                                }
//                            }

                            libraryPathPrepended = [ project.nativelibs.currentPlatform ]
                        }
                    }
                }
            } else {
                logger.info("capsule plugin was not detected")
            }
        }
        
    }
}

