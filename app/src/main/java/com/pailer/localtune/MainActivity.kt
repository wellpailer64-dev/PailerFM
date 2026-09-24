package com.pailer.localtune

import android.os.Bundle
import android.os.Handler
import android.os.Looper
import androidx.activity.compose.setContent
import androidx.core.view.WindowCompat
import androidx.fragment.app.FragmentActivity
import com.google.android.gms.cast.framework.CastContext
import com.pailer.localtune.data.BackupScheduler
import com.pailer.localtune.data.ListenerHeartbeat
import com.pailer.localtune.ui.LocalTuneApp

// FragmentActivity (nao ComponentActivity puro) porque o dialogo de selecao de dispositivo do
// Google Cast (MediaRouteButton/CastButtonFactory) precisa de suporte a Fragment do androidx.
class MainActivity : FragmentActivity() {
    // Sinal de "app aberto" pro painel de ouvintes (pedido do usuario 24/09/2026: ver no painel
    // quem abriu o app mesmo sem tocar nada). Manda toda vez que o app volta pra frente (onStart,
    // nao so quando o processo nasce) e repete a cada HEARTBEAT_INTERVAL_MS enquanto a tela
    // estiver visivel - o painel considera offline depois de 5 min sem sinal. Nao espera o
    // onboarding: chega "Sem nome" e o nome entra no proximo sinal (finishOnboarding manda um).
    private val heartbeatHandler = Handler(Looper.getMainLooper())
    private val heartbeatTick = object : Runnable {
        override fun run() {
            ListenerHeartbeat.send(this@MainActivity)
            heartbeatHandler.postDelayed(this, ListenerHeartbeat.HEARTBEAT_INTERVAL_MS)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        // Reagenda a cadeia de backup das 00h/12h (ver BackupScheduler) toda vez que o app abre -
        // e um no-op se nenhum destino de backup foi configurado ainda, e cobre o caso de o
        // AlarmManager ter perdido o alarme por algum motivo fora de um reboot (BOOT_COMPLETED
        // ja cobre o caso do reboot, ver BackupAlarmReceiver).
        BackupScheduler.scheduleNextIfConfigured(this)
        // Inicializa o singleton do Cast cedo, antes de qualquer MediaRouteButton ser composto.
        runCatching { CastContext.getSharedInstance(this) }
        setContent {
            LocalTuneApp()
        }
    }

    override fun onStart() {
        super.onStart()
        heartbeatHandler.removeCallbacks(heartbeatTick)
        heartbeatTick.run()
    }

    override fun onStop() {
        heartbeatHandler.removeCallbacks(heartbeatTick)
        super.onStop()
    }
}
