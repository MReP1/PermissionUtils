package com.example.permission

import android.Manifest
import android.content.Intent
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
import androidx.compose.ui.Modifier
import com.example.permission.ui.theme.PermissionTheme
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import java.util.UUID

class MainActivity : ComponentActivity() {

    private val randomKey = UUID.randomUUID().toString()


    @OptIn(ExperimentalPermissionsApi::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            PermissionTheme {
                // A surface container using the 'background' color from the theme
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    Column(
                        verticalArrangement = Arrangement.Center
                    ) {
                        Button(onClick = {
                            val intent = Intent(this@MainActivity, PermissionActivity::class.java)
                            startActivity(intent)
                        }) {
                            Text(text = "Launch permission activity")
                        }
                    }
                }
            }
        }
    }

}