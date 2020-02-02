/* CS478 Project 5: Services
 * Name:   Shyam Patel
 * NetID:  spate54
 * Date:   Dec 9, 2019
 */

package com.proj5.services.AudioClient;

import com.proj5.services.MediaPlaybackCommon.Interface;
import androidx.appcompat.app.AppCompatActivity;
import androidx.constraintlayout.widget.ConstraintLayout;
import androidx.constraintlayout.widget.ConstraintSet;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.content.pm.ResolveInfo;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.RemoteException;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.CompoundButton;
import android.widget.ImageButton;
import android.widget.ListView;
import android.widget.ProgressBar;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

public class MainActivity extends AppCompatActivity {
  // variable(s)
  private Interface               mediaPlaybackService;
  private Switch                  serviceSwitch;
  private TextView                nameTextView;
  private TextView                positionTextView;
  private ImageButton             playButton;
  private ImageButton             pauseButton;
  private ImageButton             stopButton;
  private ProgressBar             progressBar;
  private SharedPreferences       sharedPreferences;
  private ConstraintLayout        constraintLayout;
  private View                    barrierView;
  private View                    rootView;
  private String[]                clips;
  private int                     selectedClip;
  private static final String     TAG             = "AudioClient";
  private static final String     CLIP_ID         = "ClipID";
  private static final String     SERVICE_STATE   = "ServiceState";
  private boolean                 serviceIsActive = false;
  private boolean                 serviceIsBound  = false;
  private boolean                 playerIsActive  = false;
  private boolean                 playLock        = false;
  private final ServiceConnection connection      = new ServiceConnection() {
    @Override public void onServiceConnected(ComponentName name, IBinder service) {
      serviceIsBound       = true;
      mediaPlaybackService = Interface.Stub.asInterface(service);
    }//end onServiceConnected

    @Override public void onServiceDisconnected(ComponentName name) {
      serviceIsBound       = false;
      mediaPlaybackService = null;
    }//end onServiceDisconnected
  };

  @Override protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    setContentView(R.layout.activity_main);

    // link layout component(s)
    clips             = getResources().getStringArray(R.array.clips);
    serviceSwitch     = findViewById(R.id.activity_main_sw_service);
    nameTextView      = findViewById(R.id.activity_main_tv_name);
    positionTextView  = findViewById(R.id.activity_main_tv_position);
    playButton        = findViewById(R.id.activity_main_btn_play);
    pauseButton       = findViewById(R.id.activity_main_btn_pause);
    stopButton        = findViewById(R.id.activity_main_btn_stop);
    progressBar       = findViewById(R.id.activity_main_pb_progress);
    constraintLayout  = findViewById(R.id.activity_main_constraint_layout);
    barrierView       = findViewById(R.id.activity_main_iv_barrier);
    rootView          = findViewById(android.R.id.content);

    // access stored service state and selected clip
    sharedPreferences = getSharedPreferences(TAG, MODE_PRIVATE);
    selectedClip      = sharedPreferences.getInt(CLIP_ID, 0);
    serviceIsActive   = sharedPreferences.getBoolean(SERVICE_STATE, false);

    // set list view adapter and on-item-click listener
    ListView clipsListView = findViewById(R.id.activity_main_lv_clips);
    clipsListView.setAdapter(new ArrayAdapter<>(this, R.layout.clip_item, clips));
    clipsListView.setItemChecked(selectedClip, true);
    clipsListView.setOnItemClickListener(this::clipSelector);

    // set service switch and on-checked change listener
    serviceSwitch.setChecked(serviceIsActive);
    serviceSwitch.setOnCheckedChangeListener(this::toggleService);

    if (serviceIsActive) {
      barrierView.setVisibility(View.GONE);
      rootView.setAlpha(0f);

      // ensure foreground service is available before attempting to display playback state
      ensureService();
      displayPlaybackState();
    } else {
      // display toast
      Toast t = Toast.makeText(this, getString(R.string.toast_begin), Toast.LENGTH_SHORT);
      ((TextView) ((ViewGroup) t.getView()).getChildAt(0)).setTextSize(20);
      t.show();
    }
  }//end onCreate

  @Override protected void onDestroy() {
    super.onDestroy();

    // unbind media playback service
    unbindService();
  }//end onDestroy

  // helper method to start ClipServer media playback service in foreground
  private void startService() {
    // start media playback service
    Intent i = new Intent();
    i.setComponent(new ComponentName(getString(R.string.service_pkg),
                                     getString(R.string.service_cls)));
    if (getApplicationContext().startForegroundService(i) != null && !serviceIsActive) {
      // successfully started service
      serviceIsActive = true;
      recordServiceState();
      barrierView.setVisibility(View.GONE);
      Log.i(TAG, getString(R.string.log_start));
    }
  }//end startService

  // helper method to stop ClipServer media playback service
  private void stopService() {
    // stop currently playing media and unbind from service
    stopIfPlaying();

    // stop media playback service
    Intent i = new Intent();
    i.setComponent(new ComponentName(getString(R.string.service_pkg),
                                     getString(R.string.service_cls)));
    if (getApplicationContext().stopService(i) && serviceIsActive) {
      // successfully stopped service
      serviceIsActive = false;
      recordServiceState();
      barrierView.setVisibility(View.VISIBLE);
      Log.i(TAG, getString(R.string.log_stop));
    }
  }//end stopService

  // helper method to bind ClipServer media playback service
  private void bindService() {
    if (!serviceIsBound) {
      Intent      i    = new Intent(Interface.class.getName());
      ResolveInfo info = getPackageManager().resolveService(i, 0);
      i.setComponent(new ComponentName(info.serviceInfo.packageName, info.serviceInfo.name));
      if (getApplicationContext().bindService(i, connection, Context.BIND_AUTO_CREATE))
        Log.i(TAG, getString(R.string.log_bind_success));
      else
        Log.i(TAG, getString(R.string.log_bind_failure));
    }
  }//end bindService

  // helper method to unbind ClipServer media playback service
  private void unbindService() {
    if (serviceIsBound) {
      serviceIsBound = false;
      getApplicationContext().unbindService(connection);
      Log.i(TAG, getString(R.string.log_unbind));
    }
  }//end unbindService

  // helper method to record service state in shared preferences
  private void recordServiceState() {
    SharedPreferences.Editor editor = sharedPreferences.edit();
    editor.putBoolean(SERVICE_STATE, serviceIsActive);
    editor.apply();
  }//end recordServiceState

  // helper method to ensure media playback service is active and bound
  private void ensureService() {
    startService();
    bindService();
  }//end ensureService

  // helper method to toggle service state
  private void toggleService(CompoundButton v, boolean c) {
    // disable toggling for 600ms
    v.setEnabled(false);
    new Handler().postDelayed(() -> serviceSwitch.setEnabled(true), 600);

    if (c)
      startService();
    else
      stopService();
  }//end toggleService

  // helper method to toggle service state
  public void toggleService(View v) {
    if (serviceSwitch.isEnabled())
      serviceSwitch.toggle();
  }//end toggleService

  // helper method to select clip from list view
  private void clipSelector(AdapterView<?> parent, View v, int pos, long id) {
    if (pos != selectedClip) {
      // record selected clip
      SharedPreferences.Editor editor = sharedPreferences.edit();
      selectedClip = pos;
      editor.putInt(CLIP_ID, selectedClip);
      editor.apply();
      play(v);
    } else if (!playerIsActive) {
      play(v);
    } else {
      stop(v);
    }
  }//end clipSelector

  // helper method to play selected clip
  private void play() {
    if (serviceIsActive && serviceIsBound) {
      try {
        if (mediaPlaybackService.play(selectedClip)) {
          if (!playerIsActive)
            activateControls(false);

          playerIsActive = true;
          nameTextView.setText(clips[selectedClip]);
          playButton.animate().rotationBy(360f).start();
          progress(true);
        }
      } catch (RemoteException e) {
        Log.e(TAG, e.toString());
      }
    } else {
      new Handler().postDelayed(this::play, 60);
    }
  }//end play

  // helper method to play selected clip
  public void play(View v) {
    if (playLock)
      return;

    // lock playback for 600ms
    playLock = true;
    new Handler().postDelayed(() -> playLock = false, 600);

    // ensure foreground service is available before attempting to play clip
    ensureService();
    play();
  }//end play

  // helper method to pause clip
  public void pause(View v) {
    if (serviceIsActive && serviceIsBound && playerIsActive) {
      try {
        playerIsActive = false;

        // disable pause button
        if (mediaPlaybackService.pause())
          v.setVisibility(View.GONE);
        else
          replay();
      } catch (RemoteException e) {
        Log.e(TAG, e.toString());
      }
    }
  }//end pause

  // helper method to resume clip
  private void resume() {
    if (serviceIsActive && serviceIsBound && !playerIsActive) {
      try {
        if (mediaPlaybackService.resume()) {
          playerIsActive = true;
          progress(false);

          // enable pause button
          pauseButton.setVisibility(View.VISIBLE);
        } else {
          replay();
        }
      } catch (RemoteException e) {
        Log.e(TAG, e.toString());
      }
    } else {
      new Handler().postDelayed(this::resume, 60);
    }
  }//end resume

  // helper method to resume clip
  public void resume(View v) {
    // ensure foreground service is available before attempting to resume clip
    ensureService();
    resume();
  }//end resume

  // helper method to stop clip
  private void stop() {
    if (serviceIsActive && serviceIsBound) {
      try {
        if (mediaPlaybackService.stop())
          playerIsActive = false;

        // clear clip
        nameTextView.setText("");
        positionTextView.setText("");
        progressBar.setProgress(0);
        progressBar.animate().scaleY(0f).setDuration(600).start();

        // reset playback controls
        resetControls(false);
        // disable stop button
        stopButton.setVisibility(View.GONE);
        // unbind media playback service
        unbindService();
      } catch (RemoteException e) {
        Log.e(TAG, e.toString());
      }
    } else {
      new Handler().postDelayed(this::stop, 60);
    }
  }//end stop

  // helper method to stop clip
  public void stop(View v) {
    // ensure foreground service is available before attempting to stop clip
    ensureService();
    stop();
  }//end stop

  // helper method to stop media playback if clip is playing
  private void stopIfPlaying() {
    try {
      if (serviceIsActive && serviceIsBound && playerIsActive && mediaPlaybackService.stop()) {
        playerIsActive = false;

        // clear clip
        nameTextView.setText("");
        positionTextView.setText("");
        progressBar.setProgress(0);
        progressBar.setScaleY(0f);

        // reset playback controls
        resetControls(false);
        // disable stop button
        stopButton.setVisibility(View.GONE);
        // display toast
        Toast t = Toast.makeText(this, getString(R.string.toast_stop), Toast.LENGTH_SHORT);
        ((TextView) ((ViewGroup) t.getView()).getChildAt(0)).setTextSize(20);
        t.show();
      }

      // unbind media playback service
      unbindService();
    } catch (RemoteException e) {
      Log.e(TAG, e.toString());
    }
  }//end stopIfPlaying

  // helper method to display progress of currently playing clip
  private void progress(boolean anim) {
    // ensure foreground service is available
    ensureService();

    if (serviceIsActive && serviceIsBound && playerIsActive) {
      try {
        if (mediaPlaybackService.isPlaying()) {
          // animate progress bar
          if (anim)
            progressBar.animate().scaleY(2f).setDuration(600).start();
          // update position timestamp
          positionTextView.setText(mediaPlaybackService.getTime());
          // update progress
          progressBar.setProgress(mediaPlaybackService.getProgress());
        } else {
          playerIsActive = false;
          replay();
        }
      } catch (RemoteException e) {
        Log.e(TAG, e.toString());
      }
    }

    // recursive call
    new Handler().postDelayed(() -> { if (playerIsActive) progress(false); }, 60);
  }//end progress

  // helper method to display progress of paused clip
  private void progressPaused() {
    if (serviceIsActive && serviceIsBound && !playerIsActive) {
      try {
        // update time
        positionTextView.setText(mediaPlaybackService.getTime());
        // update progress
        progressBar.setProgress(mediaPlaybackService.getProgress());
        progressBar.setScaleY(2f);
        // check for playback completion
        if (progressBar.getProgress() == 2000) {
          // release media player
          mediaPlaybackService.stop();
          // reset playback controls
          resetControls(true);
          // disable stop button
          stopButton.setVisibility(View.GONE);
          // unbind media playback service
          unbindService();
        }
      } catch (RemoteException e) {
        Log.e(TAG, e.toString());
      }
    }
  }//end progressPaused

  // helper method to display current playback state on activity re-creation
  private void displayPlaybackState() {
    if (serviceIsActive && serviceIsBound) {
      try {
        if (mediaPlaybackService.isPlaying()) {
          nameTextView.setText(clips[selectedClip]);
          activateControls(false);
          progress(true);
        } else if (mediaPlaybackService.isPaused()) {
          nameTextView.setText(clips[selectedClip]);
          activateControls(true);
          progressPaused();
        } else {
          unbindService();
        }
        rootView.setAlpha(1f);
      } catch (RemoteException e) {
        Log.e(TAG, e.toString());
      }
    } else {
      new Handler().postDelayed(this::displayPlaybackState, 60);
    }
  }//end displayPlaybackState

  // helper method display replay button upon media playback completion or service error
  private void replay() {
    if (serviceIsActive && serviceIsBound && !playerIsActive) {
      try {
        // release media player
        mediaPlaybackService.stop();
        // set maximum progress
        if (progressBar.getProgress() > 1990)
          progressBar.setProgress(2000);
        // reset playback controls
        resetControls(true);
        // disable stop button
        stopButton.setVisibility(View.GONE);
        // unbind media playback service
        unbindService();
      } catch (RemoteException e) {
        Log.e(TAG, e.toString());
      }
    }
  }//end replay

  // helper method to rearrange playback controls during active state
  private void activateControls(boolean paused) {
    if (!playerIsActive) {
      // position replay button
      playButton.setImageResource(R.drawable.ic_replay);
      ConstraintSet constraintSet = new ConstraintSet();
      constraintSet.clone(constraintLayout);
      constraintSet.clear(R.id.activity_main_btn_play, ConstraintSet.LEFT);
      constraintSet.connect(R.id.activity_main_btn_play, ConstraintSet.RIGHT,
                            R.id.activity_main_btn_resume, ConstraintSet.LEFT);
      constraintSet.applyTo(constraintLayout);

      // enable/disable pause button
      if (paused) {
        pauseButton.setVisibility(View.GONE);
      } else {
        playerIsActive = true;
        pauseButton.setVisibility(View.VISIBLE);
      }
      // enable stop button
      stopButton.setVisibility(View.VISIBLE);
    }
  }//end activateControls

  // helper method to rearrange playback controls during inactive state
  private void resetControls(boolean replay) {
    if (!playerIsActive) {
      // reposition (re)play button
      if (!replay)
        playButton.setImageResource(R.drawable.ic_play);
      ConstraintSet constraintSet = new ConstraintSet();
      constraintSet.clone(constraintLayout);
      constraintSet.connect(R.id.activity_main_btn_play, ConstraintSet.LEFT,
                            ConstraintSet.PARENT_ID, ConstraintSet.LEFT);
      constraintSet.connect(R.id.activity_main_btn_play, ConstraintSet.RIGHT,
                            ConstraintSet.PARENT_ID, ConstraintSet.RIGHT);
      constraintSet.applyTo(constraintLayout);
    }
  }//end resetControls
}//end MainActivity
