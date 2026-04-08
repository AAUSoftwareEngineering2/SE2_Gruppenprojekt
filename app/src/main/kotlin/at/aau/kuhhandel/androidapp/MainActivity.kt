package at.aau.kuhhandel.androidapp

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import at.aau.kuhhandel.androidapp.ui.menu.MainMenuScreen
import at.aau.kuhhandel.androidapp.ui.theme.AndroidAppTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            AndroidAppTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding -> //
                    // Open the main menu
                    MainMenuScreen(modifier = Modifier.padding(innerPadding))
                }
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
fun MainMenuScreenPreview() {
    AndroidAppTheme {
        MainMenuScreen()
    }
}
