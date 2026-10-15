// Copyright (C) 2026 White Ravens. AGPL-3.0-only with an additional term; see LICENSE and NOTICE.

package net.whiteravens.dietapp.data.config

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.Inet6Address
import java.net.InetAddress
import java.net.UnknownHostException
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Android 17 lets an app reach the phone's local network only with the permission for nearby
 * devices (`ACCESS_LOCAL_NETWORK`): without it a connection there waits until it times out. A
 * server on the local network is asked for it before anything is sent to it.
 */
interface LocalNetworkAccess {
    /** Whether [host] is on the local network: a private or link-local address, a name that resolves to one, or a `.local` name. */
    suspend fun isLocal(host: String): Boolean

    /** Whether the app may reach the local network: always before Android 17. */
    fun allowed(): Boolean
}

@Singleton
class PhoneLocalNetworkAccess @Inject constructor(@ApplicationContext private val context: Context) : LocalNetworkAccess {
    override suspend fun isLocal(host: String): Boolean = withContext(Dispatchers.IO) { isLocalHost(host) }

    override fun allowed(): Boolean =
        Build.VERSION.SDK_INT < LOCAL_NETWORK_PERMISSION_SINCE ||
            ContextCompat.checkSelfPermission(context, LOCAL_NETWORK_PERMISSION) == PackageManager.PERMISSION_GRANTED
}

/** The permission, and the Android version that asks for it: the name is asked for on that version and later alone. */
@SuppressLint("InlinedApi")
const val LOCAL_NETWORK_PERMISSION = Manifest.permission.ACCESS_LOCAL_NETWORK
const val LOCAL_NETWORK_PERMISSION_SINCE = 37

/** [host] as [LocalNetworkAccess.isLocal] reads it; a name that resolves to nothing is not local. */
fun isLocalHost(host: String): Boolean {
    if (host.endsWith(".local", ignoreCase = true)) return true
    val addresses = try {
        InetAddress.getAllByName(host)
    } catch (e: UnknownHostException) {
        return false
    }
    return addresses.any { it.isSiteLocalAddress || it.isLinkLocalAddress || it.isUniqueLocal() }
}

/** An IPv6 unique local address (`fc00::/7`), the private addresses of IPv6. */
private fun InetAddress.isUniqueLocal() = this is Inet6Address && (address[0].toInt() and 0xfe) == 0xfc
