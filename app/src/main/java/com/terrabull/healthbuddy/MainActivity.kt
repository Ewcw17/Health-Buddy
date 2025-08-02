package com.terrabull.healthbuddy

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.terrabull.healthbuddy.ui.theme.HealthBuddyTheme
import kotlinx.coroutines.launch
import java.io.File

import androidx.compose.foundation.background

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            HealthBuddyTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    RecordingScreen(modifier = Modifier.padding(innerPadding))
                }
            }
        }
    }
}

@Composable
fun RecordingScreen(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    var isRecording by remember { mutableStateOf(false) }
    var transcription by remember { mutableStateOf("") }
    val scope = rememberCoroutineScope()
    val cacheFile = remember { File(context.cacheDir, "recording.wav") }
    val recorder = remember { PcmWavRecorder(cacheFile) }


    // Permission state and launcher
    var permissionGranted by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.RECORD_AUDIO
            ) == PackageManager.PERMISSION_GRANTED
        )
    }
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        permissionGranted = granted
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surface)  // Uses theme color
            .padding(16.dp),
        verticalArrangement = Arrangement.Top
    ) {
        Button(onClick = {
            if (!isRecording) {
                // Ensure permission before starting
                if (!permissionGranted) {
                    permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                    return@Button
                }
                recorder.start()
                isRecording = true
                transcription = ""
            } else {
                recorder.stop()
                isRecording = false
            }
        }) {
            Text(if (isRecording) "Stop Recording" else "Start Recording")
        }
        Spacer(modifier = Modifier.height(16.dp))
        if (transcription.isNotEmpty()) {
            Text("Transcription:", style = MaterialTheme.typography.titleMedium)
            Text(transcription)
        }
    }
}

@Preview(showBackground = true)
@Composable
fun RecordingScreenPreview() {
    HealthBuddyTheme {
        RecordingScreen()
    }
}
