package fr.tempolock.app.platform

import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import dagger.hilt.android.qualifiers.ApplicationContext
import fr.tempolock.app.domain.InstalledApp
import java.text.Collator
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class InstalledAppsRepository @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    fun launchableUserApps(): List<InstalledApp> {
        val packageManager = context.packageManager
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        val resolved = packageManager.queryIntentActivities(
            intent,
            PackageManager.ResolveInfoFlags.of(PackageManager.MATCH_DISABLED_COMPONENTS.toLong()),
        )
        val collator = Collator.getInstance(Locale.FRENCH)

        return resolved
            .asSequence()
            .mapNotNull { info ->
                val applicationInfo = info.activityInfo?.applicationInfo ?: return@mapNotNull null
                val packageName = applicationInfo.packageName
                val isSystem = applicationInfo.flags and ApplicationInfo.FLAG_SYSTEM != 0
                val isUpdatedSystem = applicationInfo.flags and ApplicationInfo.FLAG_UPDATED_SYSTEM_APP != 0
                if (
                    packageName == context.packageName ||
                    !applicationInfo.enabled ||
                    isSystem ||
                    isUpdatedSystem
                ) {
                    return@mapNotNull null
                }
                InstalledApp(
                    packageName = packageName,
                    label = packageManager.getApplicationLabel(applicationInfo).toString(),
                )
            }
            .distinctBy(InstalledApp::packageName)
            .sortedWith { left, right -> collator.compare(left.label, right.label) }
            .toList()
    }
}
