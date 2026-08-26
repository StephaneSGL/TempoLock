package fr.tempolock.app.platform

import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import android.os.UserManager
import dagger.hilt.android.qualifiers.ApplicationContext
import fr.tempolock.app.domain.LockOperationException
import fr.tempolock.app.domain.LockPolicy
import fr.tempolock.app.receiver.TempoLockDeviceAdminReceiver
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DeviceOwnerPolicy @Inject constructor(
    @ApplicationContext private val context: Context,
) : LockPolicy {
    private val manager = context.getSystemService(DevicePolicyManager::class.java)
    private val admin = ComponentName(context, TempoLockDeviceAdminReceiver::class.java)

    override fun isDeviceOwner(): Boolean = manager.isDeviceOwnerApp(context.packageName)

    override fun prepareTrustedTime() {
        requireOwner()
        enableTrustedTime()
    }

    override fun ensureSelfProtected() {
        if (!isDeviceOwner()) return
        manager.setUninstallBlocked(admin, context.packageName, true)
        check(manager.isUninstallBlocked(admin, context.packageName)) {
            "Android n'a pas confirmé la protection de TempoLock contre la désinstallation."
        }
        manager.setShortSupportMessage(admin, "Verrou temporaire actif dans TempoLock")
        manager.setLongSupportMessage(
            admin,
            "TempoLock protège volontairement une échéance choisie sur cet appareil. " +
                "Les restrictions temporaires sont retirées automatiquement à la fin.",
        )
    }

    override fun engage(targetPackage: String) {
        requireOwner()
        require(targetPackage != context.packageName) { "TempoLock ne peut pas se cibler lui-même." }
        require(isInstalled(targetPackage)) { "L'application cible n'est plus installée." }

        ensureSelfProtected()
        prepareTrustedTime()
        activeRestrictions().forEach { manager.addUserRestriction(admin, it) }
        manager.setUninstallBlocked(admin, targetPackage, true)

        manager.setUserControlDisabledPackages(admin, listOf(context.packageName, targetPackage))

        val failures = manager.setPackagesSuspended(admin, arrayOf(targetPackage), true)
        if (failures.contains(targetPackage)) {
            throw LockOperationException(
                "Android refuse de suspendre ce paquet. Il s'agit probablement d'une application système critique.",
            )
        }
        if (!isSuspended(targetPackage)) {
            throw LockOperationException("La suspension n'a pas été confirmée par Android.")
        }
        check(manager.isUninstallBlocked(admin, targetPackage)) {
            "Android n'a pas confirmé le blocage de la désinstallation de la cible."
        }
        val protectedPackages = manager.getUserControlDisabledPackages(admin)
        check(protectedPackages.containsAll(listOf(context.packageName, targetPackage))) {
            "Android n'a pas confirmé la protection contre l'arrêt forcé."
        }
        val restrictions = manager.getUserRestrictions(admin)
        check(activeRestrictions().all { restrictions.getBoolean(it) }) {
            "Android n'a pas confirmé toutes les restrictions du verrou."
        }
    }

    override fun release(targetPackage: String) {
        requireOwner()

        if (isInstalled(targetPackage)) {
            val failures = manager.setPackagesSuspended(admin, arrayOf(targetPackage), false)
            if (failures.contains(targetPackage)) {
                throw LockOperationException("Android signale un échec pendant la libération de la cible.")
            }
            if (isSuspended(targetPackage)) {
                throw LockOperationException("Android n'a pas encore accepté de libérer l'application cible.")
            }
            manager.setUninstallBlocked(admin, targetPackage, false)
            check(!manager.isUninstallBlocked(admin, targetPackage)) {
                "Android n'a pas confirmé la levée du blocage de désinstallation."
            }
        }

        manager.setUserControlDisabledPackages(admin, emptyList())
        check(
            manager.getUserControlDisabledPackages(admin)
                .none { it == context.packageName || it == targetPackage },
        ) { "Android n'a pas confirmé la levée de la protection contre l'arrêt forcé." }

        activeRestrictions().asReversed().forEach { manager.clearUserRestriction(admin, it) }
        val remainingRestrictions = manager.getUserRestrictions(admin)
        check(activeRestrictions().none { remainingRestrictions.getBoolean(it) }) {
            "Android n'a pas confirmé la levée de toutes les restrictions."
        }
        ensureSelfProtected()
    }

    override fun isSuspended(targetPackage: String): Boolean =
        try {
            manager.isPackageSuspended(admin, targetPackage)
        } catch (_: PackageManager.NameNotFoundException) {
            false
        }

    private fun enableTrustedTime() {
        manager.setAutoTimeEnabled(admin, true)
        manager.setAutoTimeZoneEnabled(admin, true)
    }

    private fun activeRestrictions(): List<String> = buildList {
        add(UserManager.DISALLOW_APPS_CONTROL)
        add(UserManager.DISALLOW_UNINSTALL_APPS)
        add(UserManager.DISALLOW_INSTALL_APPS)
        add(UserManager.DISALLOW_DEBUGGING_FEATURES)
        add(UserManager.DISALLOW_SAFE_BOOT)
        add(UserManager.DISALLOW_FACTORY_RESET)
        add(UserManager.DISALLOW_ADD_USER)
        add(UserManager.DISALLOW_USER_SWITCH)
        add(UserManager.DISALLOW_CONFIG_DATE_TIME)
        add(UserManager.DISALLOW_INSTALL_UNKNOWN_SOURCES_GLOBALLY)
    }

    private fun isInstalled(packageName: String): Boolean =
        try {
            context.packageManager.getPackageInfo(
                packageName,
                PackageManager.PackageInfoFlags.of(
                    (PackageManager.MATCH_DIRECT_BOOT_AWARE or
                        PackageManager.MATCH_DIRECT_BOOT_UNAWARE).toLong(),
                ),
            )
            true
        } catch (_: PackageManager.NameNotFoundException) {
            false
        }

    private fun requireOwner() {
        if (!isDeviceOwner()) {
            throw LockOperationException("TempoLock doit être configuré comme propriétaire de l'appareil.")
        }
    }
}
