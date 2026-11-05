package co.apexmoto.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import co.apexmoto.app.ui.AppViewModel
import co.apexmoto.app.ui.ApexRoot
import co.apexmoto.app.ui.theme.ApexTheme

class MainActivity : ComponentActivity() {
    private val vm: AppViewModel by viewModels { AppViewModel.Factory((application as ApexApp).container) }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent { ApexTheme { ApexRoot(vm) } }
    }
}
