package com.terrabull.healthbuddy

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import java.time.ZonedDateTime

class AlarmReceiver : BroadcastReceiver() {
    override fun onReceive(ctx: Context, intent: Intent) {

    }
}

fun scheduleExactAlarm(ctx: Context, at: ZonedDateTime) {
    val alarmMgr = ctx.getSystemService(Context.ALARM_SERVICE) as AlarmManager
    val intent   = Intent(ctx, AlarmReceiver::class.java).apply {
        action = "PRE_WORKOUT_ALARM"
    }
    val pending  = PendingIntent.getBroadcast(
        ctx, 0, intent,
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)

    alarmMgr.setExactAndAllowWhileIdle(
        AlarmManager.RTC_WAKEUP,
        at.toInstant().toEpochMilli(),
        pending)
}
