package com.pailer.localtune

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.core.view.WindowCompat
import androidx.fragment.app.FragmentActivity
import com.google.android.gms.cast.framework.CastContext
import com.pailer.localtune.data.BackupScheduler
import com.pailer.localtune.ui.LocalTuneApp

// FragmentActivity (nao ComponentActivity puro) porque o dialogo de selecao de dispositivo do
// Google Cast (MediaRouteButton/CastButtonFactory) precisa de suporte a Fragment do androidx.
class MainActivity : FragmentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        // Reagenda a cadeia de backup diario (ver BackupScheduler) toda vez que o app abre - e
        // um no-op se nenhum destino de backup foi configurado ainda, e cobre o caso de o
        // AlarmManager ter perdido o alarme por algum motivo fora de um reboot (BOOT_COMPLETED
        // ja cobre o caso do reboot, ver BackupAlarmReceiver).
        BackupScheduler.scheduleNextMidnightIfConfigured(this)
        // Inicializa o singleton do Cast cedo, antes de qualquer MediaRouteButton ser composto.
        runCatching { CastContext.getSharedInstance(this) }
        setContent {
            LocalTuneApp()
        }
    }
}
