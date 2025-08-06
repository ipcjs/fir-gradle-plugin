package im.fir.gradle


import com.android.build.api.variant.BuiltArtifactsLoader
import im.fir.gradle.http.FirClient
import im.fir.gradle.module.App
import im.fir.gradle.module.Mapping
import net.dongliu.apk.parser.ApkFile
import net.dongliu.apk.parser.bean.Icon
import org.gradle.api.DefaultTask
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.MapProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.*

abstract class FirPublishApkTask extends DefaultTask {
    protected FirPublisherPluginExtension firExtension
    protected BugHdPublisherPluginExtension bugHdExtension
    protected FirClient client
    protected App app

    @InputFiles
    abstract DirectoryProperty getApkFolder()

    @InputFile
    @Optional
    abstract RegularFileProperty getMappingFile()

    @Input
    @Optional
    abstract MapProperty<String, String> getManifestPlaceholders()

    @Internal
    abstract Property<BuiltArtifactsLoader> getBuiltArtifactsLoader()

    @TaskAction
    publishApk() {
        def log = project.logger
        def buildArtifacts = builtArtifactsLoader.get().load(apkFolder.get())
        if (buildArtifacts?.elements?.size() != 1) {
            throw new IllegalStateException("Expected one APK file, but found ${buildArtifacts?.elements?.size()} from APK folder ${apkFolder.get()}")
        }
        def apkArtifact = buildArtifacts.elements.first()

        if (client == null) {
            client = FirPublisherHelper.init(firExtension)
        }

        app = new App()
        app.setBundleId(buildArtifacts.applicationId)
        app.setAppType("android")
        app.setBuild(apkArtifact.versionCode.toString())
        app.setVersion(apkArtifact.versionName)

        def apkPath = apkArtifact.outputFile
        log.info("apkPath ===> " + apkPath)
        parseApk(apkPath, app)
        app.setAppPath(apkPath)

        String changeLog
        if (manifestPlaceholders.get().containsKey("FIR_CHANGE_LOG_VALUE")) {
            changeLog = manifestPlaceholders.get()["FIR_CHANGE_LOG_VALUE"]
        }
        if (changeLog) {
            app.setChangeLog(changeLog)
        } else if (firExtension.changeLog != null) {
            app.setChangeLog(firExtension.changeLog)
        }

        Mapping mapping = null
        if (mappingFile.isPresent() && bugHdExtension != null) {
            String mappingPath = mappingFile.get().asFile.absolutePath
            mapping = new Mapping()
            mapping.setFilePath(mappingPath)
            mapping.setApiToken(bugHdExtension.apiToken)
            mapping.setProjectId(bugHdExtension.projectId)
        }

        def shortCode = client.deployFile(app, mapping, firExtension.apiToken)
        String msg
        try {
            def latestAppInfo = client.getLatestAppInfo(app.getAppType(), app.getBundleId(), firExtension.apiToken)
            msg = "Short URL: $latestAppInfo.update_url"
        } catch (Exception e) {
            msg = "Short Code: $shortCode"
        }
        log.warn("Uploading ${apkPath} to fir.im finish!\n$msg")
    }

    static App parseApk(String apkPath, App app) {
        ApkFile apkParser = null
        try {
            File apkFile = new File(apkPath)
            apkParser = new ApkFile(apkFile)
            Icon icon = apkParser.getIconFile()
            String iconPath = icon.getPath()
            app.setName(apkParser.apkMeta.name)
            String[] strs
            if (iconPath != null) {
                strs = iconPath.split("/")
                createFile(icon.getData(), apkFile.getParent(), strs[(strs.length - 1)], app)
            }
            return app
        } catch (IOException e) {
            e.printStackTrace()
        } finally {
            try {
                apkParser?.close()
            } catch (IOException e) {
                e.printStackTrace()
            }
        }
        return null
    }

    static void createFile(byte[] bytes, String path, String name, App app) {
        try {
            FileOutputStream fos = new FileOutputStream(path + "/" + name)
            fos.write(bytes)
            app.setIconPath(path + "/" + name)
            fos.close()
        } catch (IOException e) {
            e.printStackTrace()
        }
    }
}
