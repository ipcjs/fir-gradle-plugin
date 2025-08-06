package im.fir.gradle

import com.android.build.api.artifact.SingleArtifact
import com.android.build.api.variant.ApplicationAndroidComponentsExtension
import com.android.build.api.variant.Variant
import com.android.build.api.variant.VariantOutputConfiguration
import org.gradle.api.Plugin
import org.gradle.api.Project

class FirPublisherPlugin implements Plugin<Project> {

    public static final String FIR_IM_GROUP = "fir.im"

    @Override
    void apply(Project project) {
        def log = project.logger

        def androidComponents = project.extensions.findByType(ApplicationAndroidComponentsExtension)
        if (androidComponents == null) {
            throw new IllegalStateException("The 'com.android.application' plugin is required.")
        }

        def firExtension = project.extensions.create('fir', FirPublisherPluginExtension)
        def bugHdExtension = project.extensions.create('bughd', BugHdPublisherPluginExtension)

        androidComponents.with {
            onVariants(selector().withBuildType('release')) { Variant variant ->
                if (firExtension == null) {
                    log.error("Please config your fir.im apiToken in your build.gradle.")
                    return
                }
                def publishFirApkTaskName = "publishFirApk${variant.name.capitalize()}"
                log.info("publishFirApkTaskName === " + publishFirApkTaskName)
                project.tasks.register(publishFirApkTaskName, FirPublishApkTask) { task ->
                    task.firExtension = firExtension
                    if (bugHdExtension.apiToken != null && bugHdExtension.projectId != null) {
                        log.info("bugHdExtension === bugHdExtension not null")
                        task.bugHdExtension = bugHdExtension
                    }
                    task.apkFolder.set(variant.artifacts.get(SingleArtifact.APK.INSTANCE))
                    task.builtArtifactsLoader.set(variant.artifacts.builtArtifactsLoader)
                    task.mappingFile.set(variant.artifacts.get(SingleArtifact.OBFUSCATION_MAPPING_FILE.INSTANCE))
                    task.manifestPlaceholders.set(variant.manifestPlaceholders)

                    task.description = "Uploads the APK for the ${variant.name} build"
                    task.group = FIR_IM_GROUP
                }
            }
        }
    }
}
