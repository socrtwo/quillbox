package info.socrtwo.quillbox

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import dagger.hilt.android.AndroidEntryPoint
import info.socrtwo.quillbox.ui.navigation.QuillboxNavHost
import info.socrtwo.quillbox.ui.root.RootViewModel
import info.socrtwo.quillbox.ui.theme.QuillboxTheme

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            QuillboxTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    val rootViewModel: RootViewModel = hiltViewModel()
                    QuillboxNavHost(rootViewModel)
                }
            }
        }
    }
}
