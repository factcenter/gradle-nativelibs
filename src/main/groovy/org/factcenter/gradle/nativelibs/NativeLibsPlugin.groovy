package org.factcenter.gradle.nativelibs


import org.gradle.api.*
import org.gradle.api.artifacts.Configuration
import org.gradle.api.tasks.*

//========== Native Dependencies Plugin
class NativeLibsPlugin implements Plugin<Project> {
    
    
    void apply(Project project) {
        project.extensions.create('nativelibs', NativeLibsExtension)
        project.nativelibs.project = project
        project.nativelibs.libsDir = "${project.buildDir}/nativeLibs"

        Closure addLibPath = {
            systemProperty "java.library.path", project.nativelibs.getPlatformDir()
            environment "LD_LIBRARY_PATH", project.nativelibs.getPlatformDir()
        }


        def unpackTask = project.task('unpackNativeLibs', type: Sync) {
			
			// Go over dependencies for all configurations
			// Unpack anything that has classifier matching one of the supported platforms
			from {
				def fileList = []
				def seen = [:]
				project.configurations.each { config ->
                    config.resolvedConfiguration.getResolvedArtifacts().each { art ->
                        if (project.nativelibs.supportedPlatforms.containsKey(art.classifier)) {
                            if (!seen[art.file] && project.nativelibs.nativeExtensions[art.type]) {
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
                                seen[art.file] = true
                            }
                        }
                    }
                }
                return fileList
			}
			
			into { project.nativelibs.getPlatformDir() }
        }


        ['test', 'run'].each { taskName ->
            if (project.tasks.hasProperty(taskName)) {
                def task = project.tasks.getByName(taskName)
                task.configure(addLibPath)
                task.dependsOn(unpackTask)
            }
        }
        
    }
}

