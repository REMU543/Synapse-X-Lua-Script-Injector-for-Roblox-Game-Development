package fm.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.Box
import androidx.compose.ui.Modifier
import fm.app.ui.AppRoot

class MainActivity : ComponentActivity() {
    private val vm: GameViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent { Box(Modifier.fillMaxSize().safeDrawingPadding()) { AppRoot(vm) } }
    }

    override fun onStop() {
        super.onStop()
        // Never lose progress when the app goes to the background.
        if (vm.career != null && vm.engine == null) vm.save(manual = false)
    }
}
