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
        if (!tryCheckPermissionsAndCallback(activity, permissions, callback)) {
            requestPermissionChannel.sendPermissionsRequest(
                ActivityPermissionsRequest(activity, permissions, callback)
            )
        }
    }

    @JvmStatic
    fun requestPermissions(
        fragment: Fragment,
        permissions: Set<String>,
        callback: PermissionsCallback
    ) {
        val context = fragment.context ?: return
        if (!tryCheckPermissionsAndCallback(context, permissions, callback)) {
            requestPermissionChannel.sendPermissionsRequest(
                FragmentPermissionsRequest(fragment, permissions, callback)
            )
        }
    }

    @JvmStatic
    private fun tryCheckPermissionsAndCallback(
        context: Context,
        permissions: Set<String>,
        callback: PermissionsCallback
    ): Boolean = checkPermissions(context, permissions).also { isGranted ->
        if (isGranted) {
            // invoke callback immediate in main thread.
            mainCoroutineScope.launch(Dispatchers.Main.immediate) {
                callback.onGranted()
            }
        }
    }

    @JvmStatic
    fun requestWriteMediaStorage(
        activity: ComponentActivity, callback: PermissionsCallback
    ) = requestWriteMediaStorage(activity as Any, callback)

    @JvmStatic
    fun requestWriteMediaStorage(
        fragment: Fragment, callback: PermissionsCallback
    ) = requestWriteMediaStorage(fragment as Any, callback)

    @JvmStatic
    private fun requestWriteMediaStorage(any: Any, callback: PermissionsCallback) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            // target api 28 及以上启用分区存储，使用 MediaStore 存媒体不需要权限。
            // target api 29 可以声明 requestLegacyExternalStorage 停用分区存储。
            // target api 30 及以上不再授予写入外部权限，强制执行分区存储。
            callback.onGranted()
        } else {
            // target 27 及以下申请写入外部存取权限
            val permissionsCallback = object : PermissionsCallback {
                override fun onGranted() = callback.onGranted()
                override fun onDenied(permissions: Set<String>) {
                    callback.onDenied(permissions)

                    // Todo show guide to settings dialog.
                }
            }
            when (any) {
                is ComponentActivity -> requestPermissions(
                    activity = any,
                    permissions = setOf(Manifest.permission.WRITE_EXTERNAL_STORAGE),
                    callback = permissionsCallback
                )

                is Fragment -> requestPermissions(
                    fragment = any,
                    permissions = setOf(Manifest.permission.WRITE_EXTERNAL_STORAGE),
                    callback = permissionsCallback
                )
            }
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

    @JvmStatic
    fun requestReadMediaStoragePermission(
        fragment: Fragment,
        type: MediaPermissionType,
        callback: PermissionsCallback
    ) {
        requestPermissions(
            fragment = fragment,
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

inline fun Fragment.withPermission(
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

sealed class PermissionsRequest

private class ActivityPermissionsRequest(
    val activity: ComponentActivity,
    val permissions: Set<String>,
    val callback: PermissionsCallback
) : PermissionsRequest()

private class FragmentPermissionsRequest(
    val fragment: Fragment,
    val permissions: Set<String>,
    val callback: PermissionsCallback
) : PermissionsRequest()

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