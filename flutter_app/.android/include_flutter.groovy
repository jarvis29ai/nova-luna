def gradle = null
def flutterProjectRoot = null

// Keep the root Nova/Luna Android build wired to the local Flutter source tree.
// This mirrors the Flutter module include template, but reads the checked-in
// android/local.properties file from flutter_app/ instead of a generated .android
// local.properties file.
if (!getBinding().getVariables().containsKey("gradle")) {
    gradle = this
    flutterProjectRoot = gradle.buildscript.getSourceFile().getParentFile().getParentFile().absolutePath
} else {
    gradle = getBinding().getVariables().get("gradle")
    def scriptFile = getClass().protectionDomain.codeSource.location.toURI()
    flutterProjectRoot = new File(scriptFile).parentFile.parentFile.absolutePath
}

gradle.include ":flutter"
gradle.project(":flutter").projectDir = new File(flutterProjectRoot, ".android/Flutter")

def flutterSdkPath = System.getenv("FLUTTER_ROOT")
if (flutterSdkPath == null || flutterSdkPath.trim().isEmpty()) {
    def localPropertiesFile = new File(flutterProjectRoot, ".android/local.properties")
    def properties = new Properties()
    if (localPropertiesFile.exists()) {
        localPropertiesFile.withReader("UTF-8") { reader -> properties.load(reader) }
        flutterSdkPath = properties.getProperty("flutter.sdk")
    }
}

assert flutterSdkPath != null, "flutter.sdk not set in local.properties"
gradle.apply from: "$flutterSdkPath/packages/flutter_tools/gradle/module_plugin_loader.gradle"

gradle.pluginManagement {
    includeBuild("$flutterSdkPath/packages/flutter_tools/gradle")
}
