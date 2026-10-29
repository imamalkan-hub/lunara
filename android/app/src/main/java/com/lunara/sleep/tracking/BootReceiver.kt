package com.lunara.sleep.tracking

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/**
 * Cihaz yeniden başlatıldığında alarmları yeniden kurar.
 *
 * Takip servisi kullanıcı eylemiyle başlatıldığı için yeniden başlatma
 * gerekmez; bu alıcı yalnızca "bu gece kayıt" bildiriminin yeniden
 * planlanmasını sağlar.
 */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return
        // Gelecekte: AlarmManager ile gece bildirimini yeniden planla.
    }
}
