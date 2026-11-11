package com.hometalk.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.hometalk.app.presentation.navigation.HomeTalkApp
import com.hometalk.app.presentation.theme.HomeTalkTheme
import com.hometalk.app.service.ServiceController
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    @Inject
    lateinit var serviceController: ServiceController

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        // Start the FGS from a visible Activity so Android 12+/14+ allow typed services.
        serviceController.startService()
        setContent {
            HomeTalkTheme {
                HomeTalkApp()
            }
        }
    }

    override fun onStart() {
        super.onStart()
        serviceController.startService()
    }
}
