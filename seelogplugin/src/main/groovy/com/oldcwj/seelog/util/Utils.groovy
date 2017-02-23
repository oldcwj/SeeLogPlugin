package com.oldcwj.seelog.util


class Utils {
    private static final String MAP_SEPARATOR = ":"

    public static boolean notSame(Map map, String name, String hash) {
        def notSame = false
        if (map) {
            def value = map.get(name)
            if (value) {
                if (!value.equals(hash)) {
                    notSame = true
                }
            } else {
                notSame = true
            }
        }
        return notSame
    }

    public static format(String path, String hash) {
        return path + MAP_SEPARATOR + hash + "\n"
    }

    public
    static boolean isExcluded(String path, Set<String> excludePackage, Set<String> excludeClass) {
        for (String exclude:excludeClass){
            if(path.equals(exclude)) {
                return  true;
            }
        }
        for (String exclude:excludePackage){
            if(path.startsWith(exclude)) {
                return  true;
            }
        }

        return false;
    }

    public static boolean isIncluded(String path, Set<String> includePackage) {
        if (includePackage.size() == 0) {
            return true
        }

        for (String include:includePackage){
            if(path.startsWith(include)) {
                return  true;
            }
        }

        return false;
    }

    public static File touchFile(File dir, String path) {
        def file = new File("${dir}/${path}")
        file.getParentFile().mkdirs()
        return file
    }

    public static copyBytesToFile(byte[] bytes, File file) {
        if (!file.exists()) {
            file.createNewFile()
        }
        org.apache.commons.io.FileUtils.writeByteArrayToFile(file, bytes)
    }

}
