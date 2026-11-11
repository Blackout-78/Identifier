package com.blackout.identifier.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings

/**
 * Android does not let one app take another app's permissions away. These open the exact screen
 * where you do it yourself, in one tap, and fall back to the app's main settings page.
 */
object AppActions {
    private fun go(context: Context, vararg intents: Intent) {
        for (i in intents) if (runCatching { context.startActivity(i) }.isSuccess) return
    }

    private fun details(pkg: String) = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", pkg, null))

    fun openPermissions(context: Context, pkg: String) = go(
        context,
        Intent("android.intent.action.MANAGE_APP_PERMISSIONS").putExtra(Intent.EXTRA_PACKAGE_NAME, pkg),
        details(pkg),
    )

    /** The screen with the "Remove permissions if app isn't used" switch. */
    fun openAutoRevoke(context: Context, pkg: String) = go(
        context,
        Intent("android.intent.action.AUTO_REVOKE_PERMISSIONS").putExtra(Intent.EXTRA_PACKAGE_NAME, pkg).setData(Uri.fromParts("package", pkg, null)),
        details(pkg),
    )

    fun uninstall(context: Context, pkg: String) = go(context, Intent(Intent.ACTION_DELETE, Uri.fromParts("package", pkg, null)))

    fun openDetails(context: Context, pkg: String) = go(context, details(pkg))
}
