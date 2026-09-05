package dikiz.app.crashreport.activity

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import dikiz.app.crashreport.theme.CrashReportTheme

class DefaultErrorActivity : ComponentActivity() {
    @OptIn(ExperimentalMaterial3Api::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val config = CrashActivity.getConfigFromIntent(intent)
        if (config == null) {
            finish()
            return
        }
        enableEdgeToEdge()
        setContent {
            var showDetails by remember { mutableStateOf(false) }
            val stackTrace = CrashActivity.getAllErrorDetailsFromIntent(application, intent)

            CrashReportTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(innerPadding)
                            .padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Text(
                            "An error has occurred.",
                            style = MaterialTheme.typography.titleLarge,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(Modifier.height(24.dp))
                        Text(
                            "We apologize for the inconvenience.\nPlease click the restart button to reload. If it continues to crash, click the details button, copy the information, and please send it to me so I can fix the issue.\n\nThank you.",
                            style = MaterialTheme.typography.bodyMedium,
                            textAlign = TextAlign.Center
                        )
                        Button(
                            onClick = {
                                if (config.isShowRestartButton && config.restartActivityClass != null) {
                                    CrashActivity.restartApplication(this@DefaultErrorActivity, config)
                                } else {
                                    CrashActivity.closeApplication(this@DefaultErrorActivity, config)
                                }
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 16.dp)
                        ) {
                            Text("Restart App")
                        }

                        if (config.isShowErrorDetails) {
                            FilledTonalButton(
                                onClick = { showDetails = true },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(top = 8.dp)
                            ) {
                                Text("Crash Details")
                            }
                        }
                    }

                    if (showDetails) {
                        ModalBottomSheet({ showDetails = false }) {
                            Column(
                                modifier = Modifier.padding(horizontal = 16.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text("Crash Details", style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.error)
                                Spacer(Modifier.height(16.dp))
                                Box(
                                    modifier = Modifier
                                        .padding(bottom = 8.dp)
                                        .weight(1f, fill = false)
                                        .clip(MaterialTheme.shapes.large)
                                        .background(MaterialTheme.colorScheme.surface)
                                ) {
                                    Text(
                                        stackTrace,
                                        style = MaterialTheme.typography.bodySmall,
                                        modifier = Modifier
                                            .horizontalScroll(rememberScrollState())
                                            .verticalScroll(rememberScrollState())
                                            .padding(16.dp)
                                    )
                                }
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    modifier = Modifier.padding(bottom = 8.dp)
                                ) {
                                    FilledTonalButton({}, modifier = Modifier.weight(1f)) { Text("Copy") }
                                    Button({ showDetails = false }, modifier = Modifier.weight(1f)) { Text("Close") }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
