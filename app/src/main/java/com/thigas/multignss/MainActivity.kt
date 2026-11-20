package com.thigas.multignss

import android.Manifest
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.viewmodel.compose.viewModel
import com.thigas.multignss.ui.MapScreen
import com.thigas.multignss.ui.NavScreen
import com.thigas.multignss.ui.RadarScreen

val RadarColors = darkColorScheme(
    primary = Color(0xFFFFC400),
    secondary = Color(0xFFFF9100),
    background = Color(0xFF0A0906),
    surface = Color(0xFF14120C),
    surfaceVariant = Color(0xFF221E12),
    onBackground = Color(0xFFF5F0DC),
    onSurface = Color(0xFFF5F0DC),
)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { MaterialTheme(colorScheme = RadarColors) { App() } }
    }
}

@Composable
private fun App(vm: GnssViewModel = viewModel()) {
    var granted by remember { mutableStateOf(vm.hasPermission()) }
    val ask = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        granted = vm.hasPermission()
        if (granted) vm.startGnss()
    }

    val owner = LocalLifecycleOwner.current
    DisposableEffect(owner) {
        val obs = LifecycleEventObserver { _, e ->
            when (e) {
                Lifecycle.Event.ON_START -> { granted = vm.hasPermission(); if (granted) vm.startGnss() }
                Lifecycle.Event.ON_STOP -> vm.stopGnss()
                else -> {}
            }
        }
        owner.lifecycle.addObserver(obs)
        onDispose { owner.lifecycle.removeObserver(obs) }
    }

    if (!granted) {
        PermissionScreen {
            ask.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION))
        }
        return
    }

    var tab by rememberSaveable { mutableIntStateOf(0) }
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        bottomBar = {
            NavigationBar(containerColor = MaterialTheme.colorScheme.surface) {
                NavigationBarItem(tab == 0, { tab = 0 }, { Icon(Icons.Filled.Star, null) }, label = { Text("Satélites & Radar") })
                NavigationBarItem(tab == 1, { tab = 1 }, { Icon(Icons.Filled.Place, null) }, label = { Text("Mapa Offline") })
                NavigationBarItem(tab == 2, { tab = 2 }, { Icon(Icons.Filled.LocationOn, null) }, label = { Text("Navegar") })
            }
        },
    ) { pad ->
        val m = Modifier.padding(pad)
        when (tab) {
            0 -> RadarScreen(vm, m)
            1 -> MapScreen(vm, m)
            else -> NavScreen(vm, m)
        }
    }
}

@Composable
private fun PermissionScreen(onAsk: () -> Unit) {
    Column(
        Modifier.fillMaxSize().padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        val night = isNight(java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY))
        com.thigas.multignss.ui.Orbi(if (night) com.thigas.multignss.ui.OrbiMood.SLEEP else com.thigas.multignss.ui.OrbiMood.HAPPY, 140.dp)
        Text(if (night) "Oi, eu sou o Orbi… (bocejo) 🌙" else "Oi, eu sou o Orbi!", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onBackground)
        Spacer(Modifier.height(8.dp))
        Text("MultiGNSS Tracker", style = MaterialTheme.typography.headlineMedium, color = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.height(16.dp))
        Text(
            "Para ler os satélites GLONASS, Galileo e BeiDou direto do chip do celular, o app precisa da permissão de localização precisa. Nada sai do aparelho.",
            textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.onBackground,
        )
        Spacer(Modifier.height(24.dp))
        Button(onClick = onAsk) { Text("Permitir localização") }
    }
}
