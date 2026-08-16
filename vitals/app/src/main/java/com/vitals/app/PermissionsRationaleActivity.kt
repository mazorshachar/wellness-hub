package com.vitals.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.vitals.app.ui.VitalsTheme

/**
 * Opened when the user taps the privacy link inside the Health Connect
 * permission sheet.
 *
 * Play's health-apps policy requires this screen to state the same policy shown
 * on the store listing. Pointing the intent filter at the dashboard — which is
 * what most apps accidentally do — fails review.
 */
class PermissionsRationaleActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            VitalsTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background,
                ) {
                    PrivacyPolicy()
                }
            }
        }
    }
}

@Composable
private fun PrivacyPolicy() {
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp, vertical = 48.dp)
    ) {
        Heading("How Vitals uses your health data")

        Body(
            "Vitals reads steps, calories burned, heart rate, sleep, workouts, " +
                "weight, body fat and blood pressure from Health Connect. It shows " +
                "them on one screen so you can see your trends in one place."
        )

        Heading("What it reads, and why")
        Body(
            "• Steps, workouts and calories burned — to show daily activity and " +
                "your energy balance.\n" +
                "• Heart rate and sleep — to show recovery alongside that activity.\n" +
                "• Weight and body fat — to track body composition over time.\n" +
                "• Blood pressure — to show your readings and their trend."
        )

        Heading("What leaves your device")
        Body(
            "Health Connect data never leaves your phone. It is read, displayed, " +
                "and never uploaded anywhere.\n\n" +
                "Voice notes are the exception, and only if you turn on voice " +
                "food logging. A note you record is sent to a speech-to-text " +
                "service to be transcribed, and the resulting text is sent to an " +
                "AI service to identify the food. Audio is not stored by Vitals " +
                "after transcription. If you do not use voice logging, no data " +
                "leaves your device at all."
        )

        Heading("What it never does")
        Body(
            "Vitals does not write to Health Connect, does not share your data " +
                "with anyone, does not sell it, and shows no advertising. There " +
                "are no analytics or tracking libraries in the app."
        )

        Heading("Deleting your data")
        Body(
            "Food entries are stored only on this device, and are removed when " +
                "you uninstall the app. You can revoke Vitals' access to health " +
                "data at any time in Health Connect settings."
        )
    }
}

@Composable
private fun Heading(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.onBackground,
        modifier = Modifier.padding(top = 20.dp, bottom = 6.dp),
    )
}

@Composable
private fun Body(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Spacer(Modifier.height(2.dp))
}
