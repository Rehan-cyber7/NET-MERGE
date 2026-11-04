package app.plexo.engine;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.os.IBinder;

/** Keeps the process (and its network requests) alive while the screen is off. The Engine updates the notification. */
public class DownloadService extends Service {
    static final int ID = 7;
    private static final String CHANNEL = "plexo-downloads";

    static Notification build(Context c, String text) {
        c.getSystemService(NotificationManager.class)
            .createNotificationChannel(new NotificationChannel(CHANNEL, "Downloads", NotificationManager.IMPORTANCE_LOW));
        Notification.Builder b = new Notification.Builder(c, CHANNEL)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setContentTitle("Plexo")
            .setContentText(text)
            .setOngoing(true)
            .setOnlyAlertOnce(true);
        Intent launch = c.getPackageManager().getLaunchIntentForPackage(c.getPackageName());
        if (launch != null) b.setContentIntent(PendingIntent.getActivity(c, 0, launch, PendingIntent.FLAG_IMMUTABLE));
        return b.build();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        startForeground(ID, build(this, "Downloading"), ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC);
        return START_NOT_STICKY;
    }

    @Override
    public IBinder onBind(Intent intent) { return null; }
}
