package com.oldcwj.seelog.util

import javassist.*
import org.apache.commons.codec.digest.DigestUtils
import org.apache.commons.io.IOUtils
import org.gradle.api.InvalidUserDataException

import java.util.jar.JarEntry
import java.util.jar.JarFile
import java.util.jar.JarOutputStream
import java.util.zip.ZipEntry

class Processor {
    public static processJar(File hashFile, File jarFile, File patchDir, Map map,
                             HashSet<String> includePackage, HashSet<String> excludePackage, HashSet<String> excludeClass, def project) {
        if (jarFile) {
            println("processJar:" + jarFile.absolutePath)
            def optJar = new File(jarFile.getParent(), jarFile.name + ".opt")

            def file = new JarFile(jarFile);
            Enumeration enumeration = file.entries();
            JarOutputStream jarOutputStream = new JarOutputStream(new FileOutputStream(optJar));

            while (enumeration.hasMoreElements()) {
                JarEntry jarEntry = (JarEntry) enumeration.nextElement();
                String entryName = jarEntry.getName();
                ZipEntry zipEntry = new ZipEntry(entryName);
                InputStream inputStream = file.getInputStream(jarEntry);
                jarOutputStream.putNextEntry(zipEntry);
                if (shouldProcessClassInJar(entryName, includePackage, excludePackage, excludeClass)) {
                    //def bytes = referHackWhenInit(inputStream);
                    def bytes = referHackByJavassistWhenInit(getClassPool(jarFile.absolutePath, project), inputStream);
                    jarOutputStream.write(bytes);

                    def hash = DigestUtils.shaHex(bytes)
                    hashFile.append(Utils.format(entryName, hash))

                    if (Utils.notSame(map, entryName, hash)) {
                        //println("patch class:" + entryName)
                        Utils.copyBytesToFile(bytes, Utils.touchFile(patchDir, entryName))
                    }
                } else {
                    jarOutputStream.write(IOUtils.toByteArray(inputStream));
                }
                jarOutputStream.closeEntry();
            }
            jarOutputStream.close();
            file.close();

            if (jarFile.exists()) {
                jarFile.delete()
            }
            optJar.renameTo(jarFile)
        }

    }

    private static byte[] referHackByJavassistWhenInit(ClassPool classPool, InputStream inputStream) {
        CtClass clazz = classPool.makeClass(inputStream)
        //System.out.println("super class======" + clazz.getSuperclass().getName().endsWith("Activity"));
        String filePaht = clazz.getName();
        int end = filePaht.length();// - 6 // .class = 6
        String className = filePaht.substring(0, end).replace('\\', '.').replace('/', '.')

        CtClass[] param = new CtClass[1] ;
        param[0] = classPool.get("android.os.Bundle") ;
        CtMethod method = null
        try {
            method = clazz.getDeclaredMethod("onCreate", param)
        } catch (Exception e) {
            method = null;
        }


        if (!className.contains("\$") && method != null) {
            String logString = "com.oldcwj.seelog.Logger.saveMotionEvent(\$1, this);";
            CtMethod touchEventMethod = null;
            CtClass[] touchEventParams = new CtClass[1] ;
            touchEventParams[0] = classPool.get("android.view.MotionEvent") ;
            try {
                touchEventMethod = clazz.getDeclaredMethod("dispatchTouchEvent", touchEventParams)
            } catch (Exception e) {
                touchEventMethod = null;
            }

            if (touchEventMethod != null) {
                touchEventMethod.insertBefore(logString);
            } else {
                CtMethod ctMethod = CtNewMethod.make("public boolean dispatchTouchEvent(android.view.MotionEvent dx) { " + logString + " return true; }", clazz);
                clazz.addMethod(ctMethod);
            }
        }

        def bytes = clazz.toBytecode()
        clazz.defrost()
        return bytes
    }

    public static ClassPool getClassPool(String path, def project) {
        //ClassPool classPool = ClassPool.getDefault();
        ClassPool classPool = new ClassPool(true);
        classPool.appendClassPath(path);
        classPool.insertClassPath(getAndroidPath(project));
        classPool.insertClassPath(new ClassClassPath(com.oldcwj.seelog.Logger.class));

        return classPool;
    }

    public static String getAndroidPath(def project) {
        def sdkDir;
        Properties properties = new Properties()
        File localProps = project.rootProject.file("local.properties")
        if (localProps.exists()) {
            properties.load(localProps.newDataInputStream())
            sdkDir = properties.getProperty("sdk.dir")
        } else {
            sdkDir = System.getenv("ANDROID_HOME")
        }
        if (sdkDir) {

        } else {
            throw new InvalidUserDataException('$ANDROID_HOME is not defined')
        }
        return  "${sdkDir}/platforms/android-23/android.jar";
    }

    private static boolean shouldProcessClassInJar(String entryName, HashSet<String> includePackage, HashSet<String> excludePackage, HashSet<String> excludeClass) {
        if (!entryName.endsWith(".class")) {
            return false;
        }
        if (entryName.contains("/R\$") || entryName.endsWith("/R.class") || entryName.endsWith("/BuildConfig.class") || entryName.contains("android/support/"))
            return false;
        return Utils.isIncluded(entryName, includePackage) && !Utils.isExcluded(entryName, excludePackage, excludeClass)
    }

    public static byte[] processClass(String path, File file, def project) {
        def optClass = new File(file.getParent(), file.name + ".opt")

        FileInputStream inputStream = new FileInputStream(file);
        FileOutputStream outputStream = new FileOutputStream(optClass)

        //def bytes = referHackWhenInit(inputStream);
        def bytes = referHackByJavassistWhenInit(getClassPool(path, project), inputStream);
        outputStream.write(bytes)
        inputStream.close()
        outputStream.close()
        if (file.exists()) {
            file.delete()
        }
        optClass.renameTo(file)
        return bytes
    }

    public static void processClassPath(String myPath, String dirName, File hashFile, File classPath, File patchDir, Map map, HashSet<String> includePackage,
                                        HashSet<String> excludePackage, HashSet<String> excludeClass, def project) {
        File[] classfiles = classPath.listFiles()
        classfiles.each { inputFile ->
            def path = inputFile.absolutePath
            path = path.split("${dirName}/")[1]
            if (inputFile.isDirectory()) {
                processClassPath(myPath, dirName, hashFile, inputFile, patchDir, map, includePackage, excludePackage, excludeClass, project)
            } else if (path.endsWith(".jar")) {
                Processor.processJar(hashFile, inputFile, patchDir, map, includePackage, excludePackage, excludeClass, project)
            } else if (path.endsWith(".class") && !path.contains("/R\$") && !path.endsWith("/R.class") && !path.endsWith("/BuildConfig.class")) {
                if (Utils.isIncluded(path, includePackage)) {
                    if (!Utils.isExcluded(path, excludePackage, excludeClass)) {
                        //System.out.println("path=====" + path);
                        def bytes = Processor.processClass(myPath, inputFile, project)
                        def hash = DigestUtils.shaHex(bytes)
                        hashFile.append(Utils.format(path, hash))
                        if (Utils.notSame(map, path, hash)) {
                            //println("patch class:" + path)
                            Utils.copyBytesToFile(inputFile.bytes, Utils.touchFile(patchDir, path))
                        }
                    }
                }
            }

        }
    }
}
