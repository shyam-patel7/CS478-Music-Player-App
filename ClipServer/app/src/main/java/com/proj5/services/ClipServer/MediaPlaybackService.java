/* CS478 Project 5: Services
 * Name:   Shyam Patel
 * NetID:  spate54
 * Date:   Dec 9, 2019
 */

package com.proj5.services.ClipServer;

import com.proj5.services.MediaPlaybackCommon.Interface;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.ComponentName;
import android.content.Intent;
import android.media.MediaPlayer;
import android.os.IBinder;
import java.util.Locale;

public class MediaPlaybackService extends Service {
  // variable(s)
  private static final String CHANNEL_ID      = "MediaPlaybackService";
  private static final int    NOTIFICATION_ID = 1;
  private static final int[]  clips           = { R.raw.anewbeginning, R.raw.creativeminds,
                                                  R.raw.downtown, R.raw.evolution,
                                                  R.raw.retrosoul, R.raw.summer, R.raw.ukulele };
  private MediaPlayer         player          = null;

  // aidl interface stub
  private Interface.Stub binder = new Interface.Stub() {
    @Override public boolean play(int id) {
      // release media player
      stop();

      // create and start media player
      player = MediaPlayer.create(getApplicationContext(), clips[id]);
      player.setLooping(false);
      player.start();
      return true;
    }//end playTrack

    @Override public boolean pause() {
      if (player != null && player.isPlaying()) {
        // pause media player
        player.pause();
        return true;
      } else {
        return false;
      }
    }//end pause

    @Override public boolean resume() {
      if (player != null && !player.isPlaying()) {
        // resume media player
        player.start();
        return true;
      } else {
        return false;
      }
    }//end resume

    @Override public boolean stop() {
      if (player != null) {
        // stop media player
        if (player.isPlaying())
          player.stop();

        // release media player
        player.reset();
        player.release();
        player = null;
        return true;
      } else {
        return false;
      }
    }//end stop

    @Override public boolean isPlaying() {
      if (player != null)
        return player.isPlaying();
      else
        return false;
    }//end isPlaying

    @Override public boolean isPaused() {
      if (player != null)
        return (player.getCurrentPosition() > 0);
      else
        return false;
    }//end isPaused

    @Override public int getProgress() {
      // return progress of media playback as an integer from 0 to 2000
      if (player != null)
        return (int) (((float) player.getCurrentPosition() / player.getDuration()) * 2000);
      else
        return 0;
    }//end getProgress

    @Override public String getTime() {
      // return string of current playback position in minutes and seconds
      //  out of total playback duration in minutes and seconds
      if (player != null) {
        int mins1 = ((player.getCurrentPosition() % (1000 * 60 * 60)) / (1000 * 60));
        int secs1 = (((player.getCurrentPosition() % (1000 * 60 * 60)) % (1000 * 60)) / 1000);
        int mins2 = ((player.getDuration() % (1000 * 60 * 60)) / (1000 * 60));
        int secs2 = (((player.getDuration() % (1000 * 60 * 60)) % (1000 * 60)) / 1000);
        return mins1 + ":" + String.format(Locale.getDefault(), "%02d", secs1) + "/" +
               mins2 + ":" + String.format(Locale.getDefault(), "%02d", secs2);
      }
      else
        return "";
    }//end getTime
  };

  @Override public void onCreate() {
    super.onCreate();

    // create notification channel
    getSystemService(NotificationManager.class)
        .createNotificationChannel(new NotificationChannel(CHANNEL_ID,
            getString(R.string.app_name), NotificationManager.IMPORTANCE_DEFAULT));

    // intent to open client activity
    Intent i = new Intent(Intent.ACTION_MAIN);
    i.setComponent(new ComponentName(getString(R.string.client_pkg),
                                     getString(R.string.client_cls)));

    // start foreground service
    startForeground(NOTIFICATION_ID, new Notification.Builder(this, CHANNEL_ID)
        .setChannelId(CHANNEL_ID)
        .setSmallIcon(android.R.drawable.ic_media_play)
        .setContentTitle(getString(R.string.notif_title))
        .setContentText(getString(R.string.notif_desc))
        .setFullScreenIntent(PendingIntent.getActivity(this, 0,
                             i, 0), false)
        .setOngoing(true)
        .build());
  }//end onCreate

  @Override public void onDestroy() {
    super.onDestroy();

    // release media player
    if (player != null) {
      player.reset();
      player.release();
      player = null;
    }
  }//end onDestroy

  // explicitly start and stop service as needed
  @Override public int onStartCommand(Intent i, int flags, int sId) { return START_STICKY; }

  // return interface stub
  @Override public IBinder onBind(Intent intent) { return binder; }
}//end MediaPlaybackService
