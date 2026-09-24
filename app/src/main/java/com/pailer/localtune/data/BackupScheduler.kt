package com.pailer.localtune.data

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import java.util.Calendar

// Backup automatico 2x por dia, 00h e 12h (pedido do usuario 24/09/2026 depois de perder
// progresso com o backup so a meia-noite). So agenda o PROXIMO disparo (nao um alarme repetido de
// verdade) - BackupAlarmReceiver reagenda o seguinte toda vez que roda, incluindo depois de um
// reboot (AlarmManager perde alarmes agendados quando o aparelho reinicia, so
// RECEIVE_BOOT_COMPLETED garante que a cadeia continua).
//
// Alarme inexato pode atrasar muito ou ate nao disparar (Doze, economia de bateria agressiva da
// Motorola) - por isso isBackupStale(): quem abre o app ou reinicia o aparelho com o ultimo backup
// mais velho que MAX_BACKUP_AGE_MS faz um backup de recuperacao na hora, sem esperar o alarme.
object BackupScheduler {
    private val BACKUP_HOURS = listOf(0, 12)
    private const val MAX_BACKUP_AGE_MS = 12 * 60 * 60 * 1000L

    fun scheduleNextIfConfigured(context: Context) {
        if (!BackupRepository(context).hasDestination()) return
        scheduleNext(context)
    }

    fun scheduleNext(context: Context) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
        runCatching {
            // Inexata de proposito (setAndAllowWhileIdle, nao setExactAndAllowWhileIdle) - o
            // backup nao precisa cair EXATAMENTE as 00h/12h, so perto disso, e assim evita pedir a
            // permissao especial de alarme exato do Android 12+ (SCHEDULE_EXACT_ALARM), que
            // exigiria o usuario ir nas configuracoes do sistema liberar manualmente.
            alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, nextTriggerAt(), pendingIntent(context))
        }
    }

    fun isBackupStale(context: Context): Boolean {
        val repository = BackupRepository(context)
        if (!repository.hasDestination()) return false
        return System.currentTimeMillis() - repository.lastBackupAt() >= MAX_BACKUP_AGE_MS
    }

    fun cancel(context: Context) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
        runCatching { alarmManager.cancel(pendingIntent(context)) }
    }

    private fun nextTriggerAt(): Long {
        val now = System.currentTimeMillis()
        val candidate = Calendar.getInstance().apply {
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        BACKUP_HOURS.forEach { hour ->
            candidate.set(Calendar.HOUR_OF_DAY, hour)
            if (candidate.timeInMillis > now) return candidate.timeInMillis
        }
        candidate.add(Calendar.DAY_OF_YEAR, 1)
        candidate.set(Calendar.HOUR_OF_DAY, BACKUP_HOURS.first())
        return candidate.timeInMillis
    }

    // Action/requestCode mantidos iguais aos do agendamento antigo (so meia-noite) - assim o
    // FLAG_UPDATE_CURRENT substitui o alarme que ja estava agendado em quem atualizou o app, sem
    // deixar um alarme duplicado pra tras.
    private fun pendingIntent(context: Context): PendingIntent {
        val intent = Intent(context, BackupAlarmReceiver::class.java).apply {
            action = BackupAlarmReceiver.ACTION_DAILY_BACKUP
        }
        return PendingIntent.getBroadcast(
            context,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }
}
