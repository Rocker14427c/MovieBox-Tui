package app.moviebox

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.viewmodel.compose.viewModel
import app.moviebox.ui.MovieBoxApp
import app.moviebox.ui.theme.MovieBoxTheme
import app.moviebox.ui.viewmodel.MovieBoxViewModel

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MovieBoxTheme {
                MovieBoxApp(vm = viewModel<MovieBoxViewModel>())
            }
        }
    }
}
