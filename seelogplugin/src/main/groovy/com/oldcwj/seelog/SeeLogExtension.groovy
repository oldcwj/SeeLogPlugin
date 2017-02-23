package com.oldcwj.seelog

import org.gradle.api.Project

class SeeLogExtension {
    HashSet<String> includePackage = []
    HashSet<String> excludeClass = []
    HashSet<String> excludePackage = []
    boolean debugOn = false

    SeeLogExtension(Project project) {
    }
}
