package com.oldcwj.seelog

import com.oldcwj.seelog.util.Processor
import org.gradle.api.Plugin
import org.gradle.api.Project

class SeeLogPlugin implements Plugin<Project> {
    HashSet<String> includePackage = []
    HashSet<String> excludePackage = []
    HashSet<String> excludeClass = []
    def debugOn

    private static final String HASH_TXT = "hash.txt"

    private static final String RELEASE = "Release"


    @Override
    void apply(Project project) {

        project.extensions.create("seelog", SeeLogExtension, project)

        project.afterEvaluate {
            def extension = project.extensions.findByName("seelog") as SeeLogExtension
            debugOn = extension.debugOn
//            patchFilePrefixName = extension.patchFilePrefixName

            ////initial include classes and exclude classes
            extension.includePackage.each {
                includeName ->
                    includePackage.add(includeName.replace(".", "/"))
            }

            extension.excludePackage.add("android.support")
            extension.excludePackage.each {
                excludeName ->
                    excludePackage.add(excludeName.replace(".", "/"))
            }
            extension.excludeClass.each {
                excludeName ->
                    excludeClass.add(excludeName.replace(".", "/") + ".class")
            }

            println("include:" + includePackage)
            println("exclude:" + excludePackage)
            println("excludeClass:" + excludeClass)

            ////
            project.android.applicationVariants.each { variant ->
                println("variant:" + variant.name)
                println("variant:" + variant.getDirName())

                if (variant.name.endsWith(RELEASE) || debugOn) {

                    ///////
                    def prepareTaskName = "check${variant.name.capitalize()}Manifest";
                    def prepareTask = project.tasks.findByName(prepareTaskName)
                    if (prepareTask) {
                        prepareTask.doFirst({
                            prepareBuild(project, variant);
                        })
                    } else {
                        println("not found task ${prepareTaskName}")
                    }

                    ///////
                    def dexTaskName = "transformClassesWithDexFor${variant.name.capitalize()}"
                    def dexTask = project.tasks.findByName(dexTaskName)
                    if (dexTask) {
                        def patchProcessBeforeDex = "patchProcessBeforeDex${variant.name.capitalize()}"
                        project.task(patchProcessBeforeDex) << {
                            patchProcess(project, variant, dexTask);
                        }
                        //insert task
                        def patchProcessBeforeDexTask = project.tasks[patchProcessBeforeDex]
                        patchProcessBeforeDexTask.dependsOn dexTask.taskDependencies.getDependencies(dexTask)
                        dexTask.dependsOn patchProcessBeforeDexTask
                    } else {
                        println("not found task:${dexTaskName}")
                    }

                }
            }

        }
    }

    void prepareBuild(def project, def variant) {
        //proguard map
        println("prepareBuild")

        //apply mapping file
        File mappingProguardFile = new File(project.projectDir, "proguard-mapping.pro")
        mappingProguardFile.delete()
        mappingProguardFile.createNewFile()

    }

    void patchProcess(def project, def variant, def dexTask) {
        println("seelogJarBeforeDex")

        def dirName = variant.dirName
        def seelogDir = new File("${project.buildDir}/outputs/seelog")
        def outputDir = new File("${seelogDir}/${dirName}")

        outputDir.mkdirs()


        //load last build class hash file
        Map hashMap
        def patchDir

        ////process jar or class file, generate class hash and find modified class
        def hashFile = new File(outputDir, HASH_TXT)
        hashFile.delete()
        hashFile.createNewFile()
        Set<File> inputFiles = dexTask.inputs.files.files
        inputFiles.each { inputFile ->
            def path = inputFile.absolutePath
            if (inputFile.isDirectory()) {
                Processor.processClassPath(path, dirName, hashFile, inputFile, patchDir, hashMap, includePackage, excludePackage, excludeClass, project)
            } else if (path.endsWith(".jar")) {
                Processor.processJar(hashFile, inputFile, patchDir, hashMap, includePackage, excludePackage, excludeClass, project)
            }

        }

    }
}


