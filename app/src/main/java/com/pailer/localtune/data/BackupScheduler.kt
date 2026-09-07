package com.pailer.localtune.data

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import java.util.Calendar

// So agenda o PROXIMO disparo (nao um alarme repetido de verdade) - BackupAlarmReceiver reagenda
// o dia seguinte toda vez que roda, incluindo depois de um reboot (AlarmManager perde alarmes
// agendados quando o aparelho reinicia, so RECEIVE_BOOT_COMPLETED garante que a cadeia continua).
object BackupScheduler {
    fun scheduleNextMidnightIfConfigured(context: Context) {
        if (!BackupRepository(context).hasDestination()) return
        scheduleNextMidnight(context)
    }

    fun scheduleNextMidnight(context: Context) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
        val triggerAt = Calendar.getInstance().apply {
            add(Calendar.DAY_OF_YEAR, 1)
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis
        runCatching {
            // Inexata de proposito (setAndAllowWhileIdle, nao setExactAndAllowWhileIdle) - backup
            // diario nao precisa cair EXATAMENTE na virada do dia, so perto dela, e assim evita
            // pedir a permissao especial de alarme exato do Android 12+ (SCHEDULE_EXACT_ALARM),
            // que exigiria o usuario ir nas configuracoes do sistema liberar manualmente.
            alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pendingIntent(context))
        }
    }

    fun cancel(context: Context) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
        runCatching { alarmManager.cancel(pendingIntent(context)) }
    }

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
