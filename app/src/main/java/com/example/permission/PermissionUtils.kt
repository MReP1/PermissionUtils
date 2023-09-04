package com.example.permission

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.ComponentActivity
import androidx.activity.result.ActivityResultCallback
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.ActivityResultRegistry
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.Lifecycle
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

    // 权限申请队列，确保同一时间只能进行一次权限申请。
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
    ) = requestPermissions(ActivityPermissionsRequest(activity, permissions, callback))

    @JvmStatic
    fun requestPermissions(
        fragment: Fragment,
        permissions: Set<String>,
        callback: PermissionsCallback
    ) = requestPermissions(FragmentPermissionsRequest(fragment, permissions, callback))

    @JvmStatic
    private fun requestPermissions(request: PermissionsRequest) {
        if (checkPermissions(context = (request.context ?: return), request.permissions)) {
            mainCoroutineScope.launch(Dispatchers.Main.immediate) { request.callback.onGranted() }
        } else {
            requestPermissionChannel.sendPermissionsRequest(request)
        }
    }

    /**
     * 申请写入媒体权限
     *
     * Android 10 及以上无需该权限，默认授权。但是需要通过 MediaStore 写入媒体。
     */
    @JvmStatic
    fun requestWriteMediaStorage(
        activity: ComponentActivity,
        callback: PermissionsCallback
    ) = requestWriteMediaStorage(activity as Any, callback)

    @JvmStatic
    fun requestWriteMediaStorage(
        fragment: Fragment,
        callback: PermissionsCallback
    ) = requestWriteMediaStorage(fragment as Any, callback)

    @JvmStatic
    private fun requestWriteMediaStorage(any: Any, callback: PermissionsCallback) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            // target api 29 及以上启用分区存储，使用 MediaStore 存媒体不需要权限。
            // target api 30 及以上不再授予写入外部权限，强制执行分区存储。
            callback.onGranted()
        } else {
            // target 27 及以下申请写入外部存取权限
            val permission = setOf(Manifest.permission.WRITE_EXTERNAL_STORAGE)
            val permissionsCallback = object : PermissionsCallback {
                override fun onGranted() = callback.onGranted()
                override fun onDenied(permissions: Set<String>) {
                    callback.onDenied(permissions)

                    // Todo show guide to settings dialog.
                }
            }
            when (any) {
                is ComponentActivity -> requestPermissions(any, permission, permissionsCallback)
                is Fragment -> requestPermissions(any, permission, permissionsCallback)
            }
        }
    }

    /**
     * 申请读取媒体权限
     *
     * API 33 细分为 Images, Video, Audio 三个权限，API 33 以下申请读外部存储权限。
     */
    @JvmStatic
    fun requestReadMediaStoragePermission(
        activity: ComponentActivity,
        type: MediaPermissionType,
        callback: PermissionsCallback
    ) = requestReadMediaStoragePermission(activity as Any, type, callback)

    @JvmStatic
    fun requestReadMediaStoragePermission(
        fragment: Fragment,
        type: MediaPermissionType,
        callback: PermissionsCallback
    ) = requestReadMediaStoragePermission(fragment as Any, type, callback)

    @JvmStatic
    private fun requestReadMediaStoragePermission(
        any: Any, type: MediaPermissionType, callback: PermissionsCallback
    ) {
        val permissions = type.toPermissions()
        val permissionsCallback = object : PermissionsCallback {
            override fun onGranted() = callback.onGranted()
            override fun onDenied(permissions: Set<String>) {
                callback.onDenied(permissions)

                // Todo show guide to settings dialog.
            }
        }
        when (any) {
            is ComponentActivity -> requestPermissions(any, permissions, permissionsCallback)
            is Fragment -> requestPermissions(any, permissions, permissionsCallback)
        }
    }

}

/* ___ Kt inline common ___ */

inline fun Fragment.withPermission(
    permission: String,
    crossinline onDenied: () -> Unit = {},
    crossinline onGranted: () -> Unit
) = withPermissions(setOf(permission), { onDenied() }, onGranted)

inline fun Fragment.withPermissions(
    permissions: Set<String>,
    crossinline onDenied: (Set<String>) -> Unit = {},
    crossinline onAllGranted: () -> Unit
) {
    PermissionUtils.requestPermissions(this, permissions, object : PermissionsCallback {
        override fun onGranted() = onAllGranted()
        override fun onDenied(permissions: Set<String>) = onDenied(permissions)
    })
}

inline fun ComponentActivity.withPermission(
    permission: String,
    crossinline onDenied: () -> Unit = {},
    crossinline onGranted: () -> Unit
) = withPermissions(setOf(permission), { onDenied() }, onGranted)

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
 * 读媒体权限类型
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
            // API 33 细分为 Images, Video, Audio 三个权限，API 33 以下申请读外部存储权限
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

sealed class PermissionsRequest(
    val permissions: Set<String>,
    val callback: PermissionsCallback
) {
    abstract val context: Context?
}

private class ActivityPermissionsRequest(
    val activity: ComponentActivity,
    permissions: Set<String>,
    callback: PermissionsCallback
) : PermissionsRequest(permissions, callback) {
    override val context: Context get() = activity
}

private class FragmentPermissionsRequest(
    val fragment: Fragment,
    permissions: Set<String>,
    callback: PermissionsCallback
) : PermissionsRequest(permissions, callback) {
    override val context: Context? get() = fragment.context
}

// 一次性最多请求的权限数量
private const val MAX_CHANNEL_CAPACITY = 32

private class PermissionRequestChannelHolder(mainScope: CoroutineScope) {

    private val channel by lazy {
        Channel<PermissionsRequest>(
            capacity = MAX_CHANNEL_CAPACITY,
            onBufferOverflow = BufferOverflow.DROP_LATEST
        ).apply {
            consumeAsFlow()
                .onEach { request ->
                    when (request) {
                        is ActivityPermissionsRequest -> {
                            if (request.activity.isDestroyed) return@onEach
                            requestPermissions(
                                permissions = request.permissions,
                                callback = request.callback,
                                activityResultRegistry = request.activity.activityResultRegistry,
                                lifecycle = request.activity.lifecycle
                            )
                        }

                        is FragmentPermissionsRequest -> {
                            if (request.fragment.isDetached) return@onEach
                            val activity = request.fragment.activity
                            if (activity == null || activity.isDestroyed) return@onEach
                            requestPermissions(
                                permissions = request.permissions,
                                callback = request.callback,
                                activityResultRegistry = activity.activityResultRegistry,
                                lifecycle = request.fragment.viewLifecycleOwner.lifecycle
                            )
                        }
                    }
                }
                .launchIn(mainScope)
        }
    }

    fun sendPermissionsRequest(request: PermissionsRequest) {
        // this function never block.
        channel.trySendBlocking(request)
    }

    private suspend fun requestPermissions(
        permissions: Set<String>,
        callback: PermissionsCallback,
        activityResultRegistry: ActivityResultRegistry,
        lifecycle: Lifecycle
    ) {
        if (!lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) {
            // The lifecycle is destroyed
            // which means that requesting this permission is useless.
            return
        }

        // Only one permission can be requested at a time, so blocking here.
        suspendCancellableCoroutine { cont ->
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
            val activityResultCallback = ActivityResultCallback<Map<String, Boolean>> { map ->
                launcher?.apply {
                    unregister()
                    launcher = null
                    lifecycle.removeObserver(observer)
                }
                val isAllPermissionsGranted = map.values.all { it }
                if (isAllPermissionsGranted) {
                    callback.onGranted()
                } else {
                    callback.onDenied(map.filterValues { !it }.keys)
                }
                if (cont.isActive) {
                    cont.resume(Unit)
                }
            }

            // real request permissions.
            launcher = activityResultRegistry.register(
                UUID.randomUUID().toString(),
                ActivityResultContracts.RequestMultiplePermissions(),
                activityResultCallback
            ).apply {
                launch(permissions.toTypedArray())
            }
        }
    }
}