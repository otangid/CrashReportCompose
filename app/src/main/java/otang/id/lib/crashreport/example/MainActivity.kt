package otang.id.lib.crashreport.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import otang.id.lib.crashreport.activity.CrashActivity
import otang.id.lib.crashreport.config.CrashConfig
import otang.id.lib.crashreport.example.ui.theme.CrashReportSampleTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        CrashActivity.setConfig(
            CrashConfig(
                isEnabled = true,
                isShowRestartButton = true,
                isTrackActivities = true,
                restartActivityClass = MainActivity::class.java
            )
        )
        enableEdgeToEdge()
        setContent {
            CrashReportSampleTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(innerPadding),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Button({ throw RuntimeException("Snap !") }) { Text("Snap !") }
                    }
                }
            }
        }
    }
}