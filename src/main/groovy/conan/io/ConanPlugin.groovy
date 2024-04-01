package conan.io

import org.gradle.api.tasks.Exec
import org.gradle.api.tasks.Delete

//  From https://gist.github.com/CAMOBAP795/ed9aa6a7549787b5eea4b2048b896747
//  with some changes
//
// Originally discovered here: https://github.com/conan-io/docs/issues/1259

import java.nio.file.Paths
import org.gradle.api.Plugin
import org.gradle.api.Project
import groovy.text.SimpleTemplateEngine

class ConanPluginExtension {
    String conanfile = "src/main/cpp/conanfile.py"
    String profile = 'android-${abi}' // TODO maybe support map abi->filename
    String outputDirPath = '${projectDir}/.conan/${buildType}/${abi}/'
    String cmakePreset = 'conan-android-clang-12-${abi}-${buildType}'
}

class ConanPluginImpl implements Plugin<Project> {
    def android
    def extension
    def conanfilePath
    def conanProfileFileNameTemplate
    def conanOutputDirPathTemplate

    void validate(Project project) {
        android = project.extensions.android
        assert android: "Cannot be applied for non android projects"

        conanfilePath = Paths.get(project.projectDir.absolutePath, extension.conanfile).toString()
        assert project.file(conanfilePath).exists(): "conan file ${conanfilePath} doesn't exists"

        println "ConanPlugin: conan profile template: ${extension.profile}"
        println "ConanPlugin: conan dir path template: ${extension.outputDirPath}"

        conanProfileFileNameTemplate = extension.profile
        conanOutputDirPathTemplate = extension.outputDirPath
    }

    void createTasksForAndroidExternalNativeBuild(Project project) {

        android.applicationVariants.all { variant ->
            def engine = new SimpleTemplateEngine()

            for (def abi in android.defaultConfig.externalNativeBuild.cmake.abiFilters) {
                def flavor = variant.name
                def taskSuffix = "${abi.capitalize()}${flavor.capitalize()}"
                def buildType = flavor.toLowerCase().contains('release') ? 'release' : 'debug'

                def params = ['abi': abi, 'flavor': flavor, 'projectDir': project.projectDir, 'buildType': buildType.capitalize()]

                println """
                ----------------------------
                ConanPlugin(
                  abi: ${params['abi']}
                  flavor: ${params['flavor']}
                  projectDir: ${params['projectDir']}
                  buildType: ${params['buildType']}
                  )
                ----------------------------
                        """.stripMargin()

                def conanProfileFileName = engine.createTemplate(conanProfileFileNameTemplate).make(params).toString()
                def conanOutputDirPath = engine.createTemplate(conanOutputDirPathTemplate).make(params).toString()

                println """
                ----------------------------
                ConanPlugin(
                  conanProfileFileName: ${conanProfileFileName}
                  conanOutputDirPath: ${conanOutputDirPath}
                  )
                ----------------------------
                        """.stripMargin()

                def conanInstallTaskName = "conanInstall${taskSuffix}"
                println "ConanPlugin: Executing conan install task: ${conanInstallTaskName}"

                def conanInstallTask = project.task(conanInstallTaskName, type: Exec) {
                    group 'Conan tasks'
                    description 'Run conan to get and build missing dependencies'

                    workingDir conanOutputDirPath
                    commandLine 'conan', 'install', conanfilePath,
                            '--profile', conanProfileFileName,
                            '--settings', "build_type=${params['buildType']}",
                            '-of', workingDir,
                            '--build', 'missing'

                    inputs.files conanfilePath
                    outputs.dir workingDir

                    doFirst {
                        workingDir.mkdirs()
                    }
                }

                def conanCheckProfileTaskName = "conanCheckProfileFor${taskSuffix}"
                project.task(conanCheckProfileTaskName) {
                    group 'Conan tasks'
                    description 'Check that conan profile file exists'

                    doFirst {
                        if (!project.file(conanProfileFileName).exists()) {
                            def conanProfilePath = "${System.properties['user.home']}/.conan2/profiles/${conanProfileFileName}"
                            assert project.file(conanProfilePath).exists() \
                                : "Conan profile file \"${conanProfilePath}\" missing please check README.md"
                        }
                    }
                }

                def conanCleanTaskName = "conanClean${taskSuffix}"
                project.task(conanCleanTaskName, type: Delete) {
                    group 'Conan tasks'
                    description 'Delete conan generated files'

                    delete conanOutputDirPath
                }

                conanInstallTask.dependsOn(conanCheckProfileTaskName)


                ['externalNativeBuildCleanRegularDebug',
                 'externalNativeBuildCleanRegularRelease',
                 'generateRegularDebugSources',
                 'generateRegularReleaseSources',
                 "externalNativeBuild${flavor.capitalize()}",
                 "externalNativeBuildClean${flavor.capitalize()}"].each {
                    if (project.tasks.findByName("${it}")) {
                        println "ConanPlugin: Adding dependency ${it}->conanInstallTaskName"
                        project.tasks.findByName("${it}").dependsOn(conanInstallTaskName)
                    }
                }
            }
        }


    } //void createTasksForAndroidExternalNativeBuild

    void apply(Project project) {
        extension = project.extensions.create('conan', ConanPluginExtension)

        project.afterEvaluate {
            validate(project)
            createTasksForAndroidExternalNativeBuild(project)
        }

    }
} //class ConanPlugin

apply plugin: ConanPlugin
