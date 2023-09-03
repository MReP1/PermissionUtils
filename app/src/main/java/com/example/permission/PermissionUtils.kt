package com.example.permission

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.ComponentActivity
import androidx.activity.result.ActivityResultCallback
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.trySendBlocking
import kotlinx.coroutines.flow.consumeAsFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import java.util.UUID
import kotlin.coroutines.resume

interface PermissionsCallback {
    fun onGranted()
    fun onDenied(permissions: Set<String>)
}

object PermissionUtils {

    private val mainCoroutineScope = CoroutineScope(Dispatchers.Main)

    private val requestPermissionChannel = PermissionRequestChannelHolder(mainCoroutineScope)

    @JvmStatic
    fun checkPermission(
        context: Context,
        permission: String
    ): Boolean {
        return ContextCompat.checkSelfPermission(
            context, permission
        ) == PackageManager.PERMISSION_GRANTED
    }

    @JvmStatic
    fun checkPermissions(
        context: Context,
        permissions: Set<String>
    ): Boolean {
        return permissions.all { checkPermission(context, it) }
    }

    @JvmStatic
    fun requestPermissions(
        activity: ComponentActivity,
        permissions: Set<String>,
        callback: PermissionsCallback
    ) {
        if (checkPermissions(activity, permissions)) {
            // invoke callback immediate in main thread.
            mainCoroutineScope.launch(Dispatchers.Main.immediate) {
                callback.onGranted()
            }
        } else {
            requestPermissionChannel.sendPermissionsRequest(
                PermissionsRequest(activity, permissions, callback)
            )
        }
    }

    @JvmStatic
    fun requestWriteMediaStorage(
        activity: ComponentActivity,
        callback: PermissionsCallback
    ) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            // target api 28 及以上启用分区存储，使用 MediaStore 存媒体不需要权限。
            // target api 29 可以声明 requestLegacyExternalStorage 停用分区存储。
            // target api 30 及以上不再授予写入外部权限，强制执行分区存储。
            callback.onGranted()
        } else {
            // target 27 及以下申请写入外部存取权限
            requestPermissions(
                activity = activity,
                permissions = setOf(Manifest.permission.WRITE_EXTERNAL_STORAGE),
                callback = object : PermissionsCallback {
                    override fun onGranted() = callback.onGranted()
                    override fun onDenied(permissions: Set<String>) {
                        callback.onDenied(permissions)

                        // Todo show guide to settings dialog.
                    }
                }
            )
        }
    }

    @JvmStatic
    fun requestReadMediaStoragePermission(
        activity: ComponentActivity,
        type: MediaPermissionType,
        callback: PermissionsCallback
    ) {
        requestPermissions(
            activity = activity,
            permissions = type.toPermissions(),
            callback = object : PermissionsCallback {
                override fun onGranted() = callback.onGranted()
                override fun onDenied(permissions: Set<String>) {
                    callback.onDenied(permissions)

                    // Todo show guide to settings dialog.
                }
            }
        )
    }

}

/* ___ Kt inline common ___ */

inline fun ComponentActivity.withPermission(
    permission: String,
    crossinline onDenied: () -> Unit = {},
    crossinline onGranted: () -> Unit
) {
    withPermissions(
        setOf(permission),
        onDenied = { onDenied() },
        onAllGranted = onGranted
    )
}

inline fun ComponentActivity.withPermissions(
    permissions: Set<String>,
    crossinline onDenied: (Set<String>) -> Unit = {},
    crossinline onAllGranted: () -> Unit
) {
    PermissionUtils.requestPermissions(this, permissions, object : PermissionsCallback {
        override fun onGranted() = onAllGranted()
        override fun onDenied(permissions: Set<String>) = onDenied(permissions)
    })
}

/**
 * 读媒体权限类型，API 33 细分为 Images, Video, Audio 三个权限
 */
sealed class MediaPermissionType(private val mask: Int) {

    data object Images : MediaPermissionType(1)

    data object Video : MediaPermissionType(2)

    data object Audio : MediaPermissionType(4)

    private class CombinedMediaPermissionType(mask: Int) : MediaPermissionType(mask)

    operator fun plus(other: MediaPermissionType): MediaPermissionType {
        return CombinedMediaPermissionType(this.mask or other.mask)
    }

    internal fun toPermissions(): Set<String> {
        return buildSet {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                if (mask and 1 > 0) add(Manifest.permission.READ_MEDIA_IMAGES)
                if (mask and 2 > 0) add(Manifest.permission.READ_MEDIA_VIDEO)
                if (mask and 4 > 0) add(Manifest.permission.READ_MEDIA_AUDIO)
            } else if (mask > 0) {
                add(Manifest.permission.READ_EXTERNAL_STORAGE)
            }
        }
    }
}

/* __ Implement __ */

private class PermissionsRequest(
    val activity: ComponentActivity,
    val permissions: Set<String>,
    val callback: PermissionsCallback
)

// 一次性最多请求的权限数量
private const val MAX_CHANNEL_CAPACITY = 32

private class PermissionRequestChannelHolder(mainScope: CoroutineScope) {

    private val channel by lazy {
        Channel<PermissionsRequest>(
            capacity = MAX_CHANNEL_CAPACITY,
            onBufferOverflow = BufferOverflow.DROP_LATEST
        ).apply {
            consumeAsFlow()
                .onEach(::dealWithPermissionRequest)
                .launchIn(mainScope)
        }
    }

    fun sendPermissionsRequest(request: PermissionsRequest) {
        // this function never block.
        channel.trySendBlocking(request)
    }

    private suspend fun dealWithPermissionRequest(request: PermissionsRequest) {
        if (request.activity.isDestroyed) {
            // The activity has been destroyed
            // which means that requesting this permission is useless.
            return
        }

        // Only one permission can be requested at a time, so blocking here.
        suspendCancellableCoroutine { cont ->
            val lifecycle = request.activity.lifecycle
            var launcher: ActivityResultLauncher<Array<String>>? = null

            // Unregister the permission request launcher and stop blocking flow when destroyed.
            val observer = object : DefaultLifecycleObserver {
                override fun onDestroy(owner: LifecycleOwner) {
                    launcher?.apply {
                        unregister()
                        launcher = null
                    }
                    lifecycle.removeObserver(this)
                    // We don't call the callback because we don't know the permissions response.
                    if (cont.isActive) {
                        cont.resume(Unit)
                    }
                }
            }
            lifecycle.addObserver(observer)

            // Unregister the launcher and the lifecycle observer after getting the response.
            val callback = ActivityResultCallback<Map<String, Boolean>> { map ->
                launcher?.apply {
                    unregister()
                    launcher = null
                    lifecycle.removeObserver(observer)
                }
                val isAllPermissionsGranted = map.values.all { it }
                if (isAllPermissionsGranted) {
                    request.callback.onGranted()
                } else {
                    request.callback.onDenied(map.filterValues { !it }.keys)
                }
                if (cont.isActive) {
                    cont.resume(Unit)
                }
            }

            // real request permissions.
            launcher = request.activity.activityResultRegistry.register(
                UUID.randomUUID().toString(),
                ActivityResultContracts.RequestMultiplePermissions(),
                callback
            ).apply {
                launch(request.permissions.toTypedArray())
            }
        }
    }
}