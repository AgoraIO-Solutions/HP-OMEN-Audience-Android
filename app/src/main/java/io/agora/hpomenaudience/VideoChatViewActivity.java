package io.agora.hpomenaudience;

import android.Manifest;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AppCompatActivity;
import android.text.TextUtils;
import android.util.Log;
import android.view.SurfaceView;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.RelativeLayout;
import android.widget.Toast;

import io.agora.rtc.Constants;
import io.agora.uikit.logger.LoggerRecyclerView;
import io.agora.rtc.IRtcEngineEventHandler;
import io.agora.rtc.RtcEngine;
import io.agora.rtc.video.VideoCanvas;
import io.agora.rtc.video.VideoEncoderConfiguration;

public class VideoChatViewActivity extends AppCompatActivity {
    private static final String TAG = VideoChatViewActivity.class.getSimpleName();

    private static final int PERMISSION_REQ_ID = 22;

    private static final int WINDOW_SHARE_UID = 10000;

    // Permission WRITE_EXTERNAL_STORAGE is not mandatory
    // for Agora RTC SDK, just in case if you wanna save
    // logs to external sdcard.
    private static final String[] REQUESTED_PERMISSIONS = {
            Manifest.permission.RECORD_AUDIO,
            Manifest.permission.CAMERA,
            Manifest.permission.WRITE_EXTERNAL_STORAGE
    };

    private RtcEngine mRtcEngine;
    private boolean mMuted;
    private boolean mEnableDebug = false;
    private int mRemoteCameraUid = 0;
    private UIState mUIState;

    private FrameLayout mRemoteCameraContainer;
    private RelativeLayout mRemoteShareContainerSplit;
    private RelativeLayout mRemoteShareContainerFull;
    private SurfaceView mRemoteCameraView;
    private SurfaceView mRemoteShareView;

    private ImageView mCallBtn;
    private ImageView mMuteBtn;
    private ImageView mShowCameraBtn;
    private EditText mChannelText;

    // Customized logger view
    private LoggerRecyclerView mLogView;

    enum UIState {
        DISCONNECTED, CONNECTED_SPLITSCREEN, CONNECTED_FULLSCREEN;
    }

    //////////////////////////////////// Life cycle events /////////////////////////////////////////

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_video_chat_view);

        // Initialize the UI elements.
        mRemoteCameraContainer = findViewById(R.id.remote_camera_view_container);
        mRemoteShareContainerSplit = findViewById(R.id.remote_share_view_container_split);
        mRemoteShareContainerFull = findViewById(R.id.remote_share_view_container_full);
        mChannelText = findViewById(R.id.channel_text);

        mCallBtn  = findViewById(R.id.btn_call);
        mMuteBtn  = findViewById(R.id.btn_mute);
        mShowCameraBtn = findViewById(R.id.btn_show_camera);
        mLogView = findViewById(R.id.log_recycler_view);

        // Sample logs are optional.
        mLogView.logI("HP OMEN Audience debug log");
        //mLogView.logW("You will see custom logs here");
        //mLogView.logE("You can also use this to show errors");

        // Initialize the UI state.
        setUIState(UIState.DISCONNECTED);

        // Ask for permissions at runtime.
        // This is just an example set of permissions. Other permissions
        // may be needed, and please refer to our online documents.
        if (checkSelfPermission(REQUESTED_PERMISSIONS[0], PERMISSION_REQ_ID) &&
                checkSelfPermission(REQUESTED_PERMISSIONS[1], PERMISSION_REQ_ID) &&
                checkSelfPermission(REQUESTED_PERMISSIONS[2], PERMISSION_REQ_ID)) {
            initEngine();
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (mUIState != UIState.DISCONNECTED) {
            mRtcEngine.leaveChannel();
        }
        RtcEngine.destroy();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode,
                                           @NonNull String[] permissions, @NonNull int[] grantResults) {
        if (requestCode == PERMISSION_REQ_ID) {
            if (grantResults[0] != PackageManager.PERMISSION_GRANTED ||
                    grantResults[1] != PackageManager.PERMISSION_GRANTED ||
                    grantResults[2] != PackageManager.PERMISSION_GRANTED) {
                showLongToast("Need permissions " + Manifest.permission.RECORD_AUDIO +
                        "/" + Manifest.permission.CAMERA + "/" + Manifest.permission.WRITE_EXTERNAL_STORAGE);
                finish();
                return;
            }

            // Here we continue only if all permissions are granted.
            // The permissions can also be granted in the system settings manually.
            initEngine();
        }
    }

    //////////////////////// Implementation of IRtcEngineEventHandler //////////////////////////////

    /**
     * Event handler registered into RTC engine for RTC callbacks.
     * Note that UI operations needs to be in UI thread because RTC
     * engine deals with the events in a separate thread.
     */
    private final IRtcEngineEventHandler mRtcEventHandler = new IRtcEngineEventHandler() {
        @Override
        public void onJoinChannelSuccess(String channel, final int uid, int elapsed) {
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    mLogView.logI("Join channel success, uid: " + (uid & 0xFFFFFFFFL));
                }
            });
        }

        @Override
        public void onRemoteSubscribeFallbackToAudioOnly(int uid, boolean isFallbackOrRecover) {
            mLogView.logI("onRemoteSubscribeFallbackToAudioOnly, isFallbackOrRecover: " + isFallbackOrRecover);
        }

        @Override
        public void onFirstRemoteVideoDecoded(final int uid, int width, int height, int elapsed) {
            Log.w("OMEN","onFirstRemoteVideoDecoded, uid: " + (uid & 0xFFFFFFFFL));

            // Can only render one remote camera in this app, so capture its uid.
            if (uid != WINDOW_SHARE_UID && mRemoteCameraUid == 0) {
                mRemoteCameraUid = uid;
            }

            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    // Render into either the main view or the smaller PIP view depending on the uid.
                    // Uid = 10000 is reserved for the window share, which renders in the main view.
                    mLogView.logI("Remote video starting, uid: " + (uid & 0xFFFFFFFFL));
                    setupRemoteVideo(uid, uid == WINDOW_SHARE_UID ? mRemoteShareContainerSplit : mRemoteCameraContainer);
                }
            });
        }

        @Override
        public void onUserOffline(final int uid, int reason) {
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    mLogView.logI("User offline, uid: " + (uid & 0xFFFFFFFFL));
                    if (uid == WINDOW_SHARE_UID) {
                        removeRemoteShare();
                    } else {
                        mRemoteCameraUid = 0;
                        removeRemoteCamera();
                    }
                }
            });
        }
    };

    ////////////////////////////// UI triggered events /////////////////////////////////////////////

    // Mute/unmute the microphone
    public void onLocalAudioMuteClicked(View view) {
        // Toggle audio mute.
        setAudioState(!mMuted);
    }

    // Hide/show the remote camera.
    public void onShowCameraClicked(View view) {
        if (mUIState == UIState.CONNECTED_FULLSCREEN) {
            // Toggle to split screen while showing remote camera
            if (mRemoteCameraUid != 0)
                setupRemoteVideo(mRemoteCameraUid, mRemoteCameraContainer);
            removeRemoteShare();
            setupRemoteVideo(WINDOW_SHARE_UID, mRemoteShareContainerSplit);
            setUIState(UIState.CONNECTED_SPLITSCREEN);
        } else if (mUIState == UIState.CONNECTED_SPLITSCREEN) {
            // Toggle to full screen while hiding remote camera
            removeRemoteCamera();
            removeRemoteShare();
            setupRemoteVideo(WINDOW_SHARE_UID, mRemoteShareContainerFull);
            setUIState(UIState.CONNECTED_FULLSCREEN);
        }
    }

    // Either join or leave the channel.
    public void onCallClicked(View view) {
        if (mUIState == UIState.DISCONNECTED) {
            // Prior to joining the channel, ensure audio is muted.
           setAudioState(true);

            // Join the channel.
            String token = getString(R.string.agora_access_token);
            if (TextUtils.isEmpty(token) || TextUtils.equals(token, "#YOUR ACCESS TOKEN#")) {
                token = null; // default, no token
            }
            mRtcEngine.joinChannel(token, mChannelText.getEditableText().toString(), "Extra Optional Data", 0);
            setUIState(UIState.CONNECTED_SPLITSCREEN);
        } else {
            // Leave the channel.
            mRemoteCameraUid = 0;
            removeRemoteCamera();
            removeRemoteShare();
            mRtcEngine.leaveChannel();
            mLogView.logI("Left channel");
            setUIState(UIState.DISCONNECTED);
        }
    }

    // Show/hide the debug output.
    public void onDebugButtonClicked(View view) {
        mEnableDebug = !mEnableDebug;
        mLogView.setVisibility(mEnableDebug ? View.VISIBLE : View.GONE);
    }

    /////////////////////////// Private member methods /////////////////////////////////////////////

    private void initEngine() {
        // This is our usual steps for joining a channel and starting a call.
        try {
            // Initialize the Agora RtcEngine.
            mRtcEngine = RtcEngine.create(getBaseContext(), getString(R.string.agora_app_id), mRtcEventHandler);

            // Set the channel profile to Live Broadcast and role to Broadcaster (in order to be heard by others).
            mRtcEngine.setChannelProfile(Constants.CHANNEL_PROFILE_LIVE_BROADCASTING);
            mRtcEngine.setClientRole(Constants.CLIENT_ROLE_BROADCASTER);

            // Enable video capturing and rendering once at the initialization step.
            // Note: audio recording and playing is enabled by default.
            mRtcEngine.enableVideo();

            // Disable the local video capturer.
            mRtcEngine.enableLocalVideo(false);

            /*
            // This is only needed if capturing local camera, which we currently are not.
            // Please go to this page for detailed explanation
            // https://docs.agora.io/en/Video/API%20Reference/java/classio_1_1agora_1_1rtc_1_1_rtc_engine.html#af5f4de754e2c1f493096641c5c5c1d8f
            mRtcEngine.setVideoEncoderConfiguration(new VideoEncoderConfiguration(
                    VideoEncoderConfiguration.VD_640x360,
                    VideoEncoderConfiguration.FRAME_RATE.FRAME_RATE_FPS_15,
                    VideoEncoderConfiguration.STANDARD_BITRATE,
                    VideoEncoderConfiguration.ORIENTATION_MODE.ORIENTATION_MODE_FIXED_PORTRAIT));
            // This is used to set a local preview.
            // The steps setting local and remote view are very similar.
            // But note that if the local user do not have a uid or do
            // not care what the uid is, he can set his uid as ZERO.
            // The Agora server will assign one and return the uid via the event
            // handler callback function (onJoinChannelSuccess) after
            // joining the channel successfully.
            mLocalView = RtcEngine.CreateRendererView(getBaseContext());
            mLocalView.setZOrderMediaOverlay(true);
            mRemoteCameraContainer.addView(mLocalView);
            mRtcEngine.setupLocalVideo(new VideoCanvas(mLocalView, VideoCanvas.RENDER_MODE_HIDDEN, 0));
            */
        } catch (Exception e) {
            Log.e(TAG, Log.getStackTraceString(e));
            throw new RuntimeException("FATAL ERROR! Check rtc sdk init\n" + Log.getStackTraceString(e));
        }
    }

    private boolean checkSelfPermission(String permission, int requestCode) {
        if (ContextCompat.checkSelfPermission(this, permission) !=
                PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, REQUESTED_PERMISSIONS, requestCode);
            return false;
        }

        return true;
    }

    // Set the audio state to either muted or unmuted and make UI consistent to state.
    private void setAudioState(boolean muted) {
        mMuted = muted;
        mRtcEngine.muteLocalAudioStream(mMuted);
        if (mMuted) {
            mMuteBtn.setImageResource(R.drawable.btn_mic_muted);
        } else {
            mMuteBtn.setImageResource(R.drawable.btn_mic_unmuted);
        }
    }

    private void setupRemoteVideo(int uid, ViewGroup remoteContainer) {
        // Only one remote video view is available for this app.
        // Here we check if there exists a surface view tagged as this uid.
        int count = remoteContainer.getChildCount();
        View view = null;
        for (int i = 0; i < count; i++) {
            View v = remoteContainer.getChildAt(i);
            if (v.getTag() instanceof Integer && ((int) v.getTag()) == uid) {
                view = v;
            }
        }
        if (view != null) {
            return;
        }

        // Checks have passed so set up the remote video.
        int renderMode;
        SurfaceView remoteView = RtcEngine.CreateRendererView(getBaseContext());
        if (uid == WINDOW_SHARE_UID) {
            mRemoteShareView = remoteView;
            renderMode = VideoCanvas.RENDER_MODE_FIT;
        } else {
            mRemoteCameraView = remoteView;
            renderMode = VideoCanvas.RENDER_MODE_HIDDEN;
        }

        remoteContainer.addView(remoteView);
        mRtcEngine.setupRemoteVideo(new VideoCanvas(remoteView, renderMode, uid));
        remoteView.setTag(uid);
    }

    private void removeRemoteCamera() {
        if (mRemoteCameraView != null) {
            mRemoteCameraContainer.removeView(mRemoteCameraView);
            mRemoteCameraView = null;
        }
    }

    private void removeRemoteShare() {
        if (mRemoteShareView != null) {
            // Try to remove from both share containers but in reality only one will contain the view.
            mRemoteShareContainerSplit.removeView(mRemoteShareView);
            mRemoteShareContainerFull.removeView(mRemoteShareView);
            mRemoteShareView = null;
        }
    }

    private void showLongToast(final String msg) {
        this.runOnUiThread(new Runnable() {
            @Override
            public void run() {
                Toast.makeText(getApplicationContext(), msg, Toast.LENGTH_LONG).show();
            }
        });
    }

    // The UI is in one of 3 states:
    // (1) DISCONNECTED: only join button is displayed
    // (2) CONNECTED_FULLSCREEN: connected with share in full screen
    // (3) CONNECTED_SPLITSCREEN: connected with share and camera in split screen
    private void setUIState(UIState state) {
        mUIState = state;

        if (mUIState == UIState.DISCONNECTED) {
            // Not connected to a channel.
            mCallBtn.setImageResource(R.drawable.btn_startcall);
            mRemoteShareContainerSplit.setVisibility(View.GONE);
            mRemoteCameraContainer.setVisibility(View.GONE);
            mChannelText.setEnabled(true);

            // Hide the buttons
            mMuteBtn.setVisibility(View.GONE);
            mShowCameraBtn.setVisibility(View.GONE);
        } else {
            // Connected to a channel.
            mCallBtn.setImageResource(R.drawable.btn_endcall);
            mShowCameraBtn.setImageResource(R.drawable.btn_camera_show);
            mChannelText.setEnabled(false);

            // Show the buttons
            mMuteBtn.setVisibility(View.VISIBLE);
            mShowCameraBtn.setVisibility(View.VISIBLE);

            if (mUIState == UIState.CONNECTED_SPLITSCREEN) {
                mRemoteShareContainerSplit.setVisibility(View.VISIBLE);
                mRemoteCameraContainer.setVisibility(View.VISIBLE);
                mShowCameraBtn.setImageResource(R.drawable.btn_camera_show);
            } else {
                mRemoteShareContainerSplit.setVisibility(View.GONE);
                mRemoteCameraContainer.setVisibility(View.GONE);
                mShowCameraBtn.setImageResource(R.drawable.btn_camera_hide);
            }
        }
    }
}
