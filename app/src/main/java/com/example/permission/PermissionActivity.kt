package com.example.permission

import android.Manifest
import android.os.Build
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import com.example.permission.ui.theme.PermissionTheme

class PermissionActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            PermissionTheme {
                // A surface container using the 'background' color from the theme
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    val coroutineScope = rememberCoroutineScope()
                    Column(
                        verticalArrangement = Arrangement.Center
                    ) {
                        Button(onClick = {
                            PermissionUtils.requestReadMediaStoragePermission(
                                this@PermissionActivity,
                                MediaPermissionType.Images,
                                object : PermissionsCallback {
                                    override fun onGranted() {
                                        Log.d("Leon", "request Images granted")
                                    }

                                    override fun onDenied(permissions: Set<String>) {
                                        Log.d("Leon", "request Images denied $permissions")
                                    }
                                }
                            )
                        }) {
                            Text(text = "Request Images permission")
                        }

                        Button(onClick = {
                            PermissionUtils.requestReadMediaStoragePermission(
                                this@PermissionActivity,
                                MediaPermissionType.Video,
                                object : PermissionsCallback {
                                    override fun onGranted() {
                                        Log.d("Leon", "request Video granted")
                                    }

                                    override fun onDenied(permissions: Set<String>) {
                                        Log.d("Leon", "request Video denied $permissions")
                                    }
                                }
                            )
                        }) {
                            Text(text = "Request Video permission")
                        }

                        Button(onClick = {
                            PermissionUtils.requestReadMediaStoragePermission(
                                this@PermissionActivity,
                                MediaPermissionType.Audio,
                                object : PermissionsCallback {
                                    override fun onGranted() {
                                        Log.d("Leon", "request Audio granted")
                                    }

                                    override fun onDenied(permissions: Set<String>) {
                                        Log.d("Leon", "request Audio denied $permissions")
                                    }
                                }
                            )
                        }) {
                            Text(text = "Request Audio permission")
                        }

                        Button(onClick = {
                            PermissionUtils.requestReadMediaStoragePermission(
                                this@PermissionActivity,
                                MediaPermissionType.Video + MediaPermissionType.Images,
                                object : PermissionsCallback {
                                    override fun onGranted() {
                                        Log.d("Leon", "request Image And Video granted")
                                    }

                                    override fun onDenied(permissions: Set<String>) {
                                        Log.d("Leon", "request Image And Video denied $permissions")
                                    }
                                }
                            )
                        }) {
                            Text(text = "Request Image And Video permission")
                        }

                        Button(onClick = {
                            PermissionUtils.requestReadMediaStoragePermission(
                                this@PermissionActivity,
                                MediaPermissionType.Images + MediaPermissionType.Audio,
                                object : PermissionsCallback {
                                    override fun onGranted() {
                                        Log.d("Leon", "request Image And Audio granted")
                                    }

                                    override fun onDenied(permissions: Set<String>) {
                                        Log.d("Leon", "request Image And Audio denied $permissions")
                                    }
                                }
                            )
                        }) {
                            Text(text = "Request Image And Audio permission")
                        }

                        Button(onClick = {
                            PermissionUtils.requestReadMediaStoragePermission(
                                this@PermissionActivity,
                                MediaPermissionType.Video + MediaPermissionType.Audio,
                                object : PermissionsCallback {
                                    override fun onGranted() {
                                        Log.d("Leon", "request Video And Audio granted")
                                    }

                                    override fun onDenied(permissions: Set<String>) {
                                        Log.d("Leon", "request Video And Audio denied $permissions")
                                    }
                                }
                            )
                        }) {
                            Text(text = "Request Video And Audio permission")
                        }

                        Button(onClick = {
                            PermissionUtils.requestReadMediaStoragePermission(
                                this@PermissionActivity,
                                MediaPermissionType.Video +
                                        MediaPermissionType.Audio +
                                        MediaPermissionType.Images,
                                object : PermissionsCallback {
                                    override fun onGranted() {
                                        Log.d("Leon", "request Images And Video And Audio granted")
                                    }

                                    override fun onDenied(permissions: Set<String>) {
                                        Log.d("Leon", "request Images And Video And Audio denied $permissions")
                                    }
                                }
                            )
                        }) {
                            Text(text = "Request Images And Video And Audio permission")
                        }

                        Button(onClick = {
                            PermissionUtils.requestWriteMediaStorage(
                                this@PermissionActivity,
                                object : PermissionsCallback {
                                    override fun onGranted() {
                                        Log.d("Leon", "request write media granted")
                                    }

                                    override fun onDenied(permissions: Set<String>) {
                                        Log.d("Leon", "request write media denied $permissions")
                                    }
                                }
                            )
                        }) {
                            Text(text = "Request Write Media permission")
                        }

                        Button(onClick = {
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                withPermission(
                                    Manifest.permission.POST_NOTIFICATIONS,
                                    onGranted = {
                                        Log.d(
                                            "Leon",
                                            "Request POST_NOTIFICATIONS permission granted"
                                        )
                                    },
                                    onDenied = {
                                        Log.d(
                                            "Leon",
                                            "Request POST_NOTIFICATIONS permission denied"
                                        )
                                    }
                                )
                            }
                        }) {
                            Text(text = "Request POST_NOTIFICATIONS permission")
                        }
                    }
                }
            }
        }
    }
}
