package com.pailer.localtune.data

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

// Recebe tanto o alarme diario (ACTION_DAILY_BACKUP, disparado por BackupScheduler) quanto
// BOOT_COMPLETED (o sistema apaga alarmes agendados no reboot - sem reagendar aqui, o backup
// automatico parava de rodar silenciosamente no primeiro reboot do aparelho).
class BackupAlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val appContext = context.applicationContext
        BackupScheduler.scheduleNextMidnightIfConfigured(appContext)
        if (intent.action != ACTION_DAILY_BACKUP) return

        // BroadcastReceiver.onReceive roda na main thread e o processo pode ser morto assim que
        // ele retornar - goAsync() segura o processo vivo o suficiente pra I/O (escrever o
        // arquivo de backup) terminar antes do sistema reciclar.
        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            runCatching { BackupRepository(appContext).performBackup() }
            pendingResult.finish()
        }
    }

    companion object {
        const val ACTION_DAILY_BACKUP = "com.pailer.localtune.ACTION_DAILY_BACKUP"
    }
}
