package com.vitals.app

import android.Manifest
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.work.ExistingWorkPolicy
import com.vitals.app.data.voice.NewRecordingWorker
import com.vitals.app.ui.DashboardScreen
import com.vitals.app.ui.VitalsTheme

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            VitalsTheme {
                val vm: DashboardViewModel = viewModel()
                val state by vm.state.collectAsStateWithLifecycle()
                val refreshing by vm.refreshing.collectAsStateWithLifecycle()
                val foodEntries by vm.todayFood.collectAsStateWithLifecycle()
                val scanningVoice by vm.scanningVoice.collectAsStateWithLifecycle()

                // Recomputed on resume rather than held as truth, since the user
                // can revoke it in system settings while we're backgrounded.
                var audioGranted by remember { mutableStateOf(vm.hasAudioPermission()) }

                // Health Connect owns its own permission sheet; this contract hands
                // it the set we want and reports back what the user actually granted.
                val healthPermissionLauncher = rememberLauncherForActivityResult(
                    contract = vm.healthConnect.permissionContract()
                ) { vm.refresh() }

                val audioPermissionLauncher = rememberLauncherForActivityResult(
                    contract = ActivityResultContracts.RequestPermission()
                ) { granted ->
                    audioGranted = granted
                    if (granted) {
                        // The hook isn't armed at startup without this permission,
                        // so turn it on now rather than waiting for the next launch.
                        NewRecordingWorker.arm(applicationContext, ExistingWorkPolicy.REPLACE)
                        vm.scanVoiceNotes()
                    }
                }

                LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
                    vm.refresh()
                    audioGranted = vm.hasAudioPermission()
                    // Catch anything recorded since the app was last open, without
                    // waiting for the 15-minute worker.
                    if (audioGranted) vm.scanVoiceNotes()
                }

                DashboardScreen(
                    state = state,
                    refreshing = refreshing,
                    foodEntries = foodEntries,
                    hasAudioPermission = audioGranted,
                    scanningVoice = scanningVoice,
                    onRequestPermissions = {
                        healthPermissionLauncher.launch(vm.healthConnect.permissions)
                    },
                    onRequestAudioPermission = {
                        audioPermissionLauncher.launch(
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                Manifest.permission.READ_MEDIA_AUDIO
                            } else {
                                Manifest.permission.READ_EXTERNAL_STORAGE
                            }
                        )
                    },
                    onScanVoiceNotes = { vm.scanVoiceNotes() },
                    onConfirmEntry = { id, kcal -> vm.confirmEntry(id, kcal) },
                    onDeleteEntry = { id -> vm.deleteEntry(id) },
                    onOpenSettings = { vm.healthConnect.openHealthConnectSettings() },
                    onInstallProvider = { vm.healthConnect.openProviderInstall() },
                    onRefresh = { vm.refresh() },
                )
            }
        }
    }
}
