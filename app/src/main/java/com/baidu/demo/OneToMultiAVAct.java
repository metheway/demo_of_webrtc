package com.baidu.demo;

import android.Manifest;
import android.content.Context;
import android.media.AudioManager;
import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;


import org.json.JSONException;
import org.json.JSONObject;
import org.webrtc.AudioSource;
import org.webrtc.AudioTrack;
import org.webrtc.Camera1Enumerator;
import org.webrtc.Camera2Enumerator;
import org.webrtc.CameraEnumerator;
import org.webrtc.CameraVideoCapturer;
import org.webrtc.DataChannel;
import org.webrtc.EglBase;
import org.webrtc.IceCandidate;
import org.webrtc.MediaConstraints;
import org.webrtc.MediaStream;
import org.webrtc.PeerConnection;
import org.webrtc.PeerConnectionFactory;
import org.webrtc.RendererCommon;
import org.webrtc.RtpReceiver;
import org.webrtc.SdpObserver;
import org.webrtc.SessionDescription;
import org.webrtc.SurfaceViewRenderer;
import org.webrtc.VideoRenderer;
import org.webrtc.VideoSource;
import org.webrtc.VideoTrack;

import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;

import io.socket.client.IO;
import io.socket.client.Socket;
import io.socket.emitter.Emitter;
import me.weyye.hipermission.HiPermission;
import me.weyye.hipermission.PermissionCallback;
import me.weyye.hipermission.PermissionItem;
import utils.GenMediaConstrains;

public class OneToMultiAVAct extends AppCompatActivity implements View.OnClickListener {

    private String TAG = OneToMultiAVAct.this.getClass().getCanonicalName();
    private LinearLayout chartTools;
    private TextView switcCamera;
    private TextView loundSperaker;
    private String roomId;
    private Button leaveRoom;
    private Button onLine;

    private SurfaceViewRenderer localView;
    private SurfaceViewRenderer remoteView;
    private PeerConnectionFactory mPeerConnectionFactory;
    private CameraVideoCapturer mVideoCapturer;
    private VideoTrack mVideoTrack;
    private AudioTrack mAudioTrack;
    private EglBase mEglBase;
    private MediaStream mMediaStream;

    private Socket mSocket;
    private MediaConstraints pcConstraints;
    private GenMediaConstrains sdpConstraints;
    private LinkedList<PeerConnection.IceServer> iceServers;
    private HashMap<String, Peer> mUserIds = new HashMap<>();
    private HashMap<String, Boolean> mIsOfferedUser = new HashMap<>();
    private Peer mPeer;
    private AudioManager mAudioManager;
    private VideoTrack remoteVideoTrack;

    private VideoRenderer mRemoteVideoRenderer;
    private VideoRenderer mlocalVideoRenderer;
    private boolean isLiver = false;
    private AudioTrack remoteAudioTrack;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN, WindowManager.LayoutParams.FLAG_FULLSCREEN);
        setContentView(R.layout.activity_one_to_multi_av);
        initview();
        AskPermission();
        mSocket.emit("join", roomId);
    }

    private void AskPermission() {
        List<PermissionItem> permissionItems = new ArrayList<PermissionItem>();

        permissionItems.add(new PermissionItem(Manifest.permission.CAMERA, "相机", R.drawable.permission_ic_camera));
        permissionItems.add(new PermissionItem(Manifest.permission.WRITE_EXTERNAL_STORAGE, "存储卡", R.drawable.permission_ic_storage));
        permissionItems.add(new PermissionItem(Manifest.permission.RECORD_AUDIO, "录音", R.drawable.permission_ic_micro_phone));
        permissionItems.add(new PermissionItem(Manifest.permission.READ_PHONE_STATE, "手机", R.drawable.permission_ic_phone));

        HiPermission.create(this).permissions(permissionItems)
                .checkMutiPermission(new PermissionCallback() {
                    @Override
                    public void onClose() {

                    }

                    @Override
                    public void onFinish() {
                        init();
                    }

                    @Override
                    public void onDeny(String permission, int position) {

                    }

                    @Override
                    public void onGuarantee(String permission, int position) {

                    }
                });
    }

    private void init() {
        roomId = getIntent().getStringExtra("roomId");

        //初始化PeerConnectionFactory
        PeerConnectionFactory.initialize(
                PeerConnectionFactory.InitializationOptions.builder(getApplicationContext())
                        .setEnableVideoHwAcceleration(true)
                        .createInitializationOptions());

        //创建PeerConnectionFactory
        PeerConnectionFactory.Options options = new PeerConnectionFactory.Options();
        mPeerConnectionFactory = new PeerConnectionFactory(options);
        //设置视频Hw加速,否则视频播放闪屏
        mPeerConnectionFactory.setVideoHwAccelerationOptions(mEglBase.getEglBaseContext(), mEglBase.getEglBaseContext());

        initConstraints();

        mVideoCapturer = createVideoCapture(this);

        VideoSource videoSource = mPeerConnectionFactory.createVideoSource(mVideoCapturer);
        mVideoTrack = mPeerConnectionFactory.createVideoTrack("videtrack", videoSource);

        //设置视频画质 i:width i1 :height i2:fps

        mVideoCapturer.startCapture(720, 1280, 30);

        AudioSource audioSource = mPeerConnectionFactory.createAudioSource(new MediaConstraints());
        mAudioTrack = mPeerConnectionFactory.createAudioTrack("audiotrack", audioSource);
        //播放本地视频
        mlocalVideoRenderer = new VideoRenderer(localView);
        mVideoTrack.addRenderer(mlocalVideoRenderer);

        //创建媒体流并加入本地音视频
        mMediaStream = mPeerConnectionFactory.createLocalMediaStream("localstream");
        mMediaStream.addTrack(mVideoTrack);
        mMediaStream.addTrack(mAudioTrack);

        //连接服务器
        try {
            mSocket = IO.socket("http://139.199.3.44:8080");
        } catch (URISyntaxException e) {
            e.printStackTrace();
        }

        mSocket.on("joined", new Emitter.Listener() {
            @Override
            public void call(Object... args) {
                String roomName = (String) args[0];
                String userId = (String) args[1];
                Log.i(TAG, "room name is:" + roomName + ", and user id is:" + userId);
            }
        }).on("full", new Emitter.Listener() {
            @Override
            public void call(Object... args) {
            Toast.makeText(OneToMultiAVAct.this, "房间已经满人", Toast.LENGTH_SHORT).show();
            }
        });

        // 如果接收直播，那么开始建立PeerConnection，addStream, 交换SDP createOffer，
        mSocket.on("lineup", new Emitter.Listener() {
            @Override
            public void call(Object... args) {
                Log.i(TAG, "lineup");
                String roomId = (String) args[0];
                String hostId = (String) args[1];
                // 创建Peer并且，发出自己的SDP
                if (!mUserIds.containsKey(hostId)) {
                    mPeer = new Peer();
                    mUserIds.put(hostId, mPeer);
                    // 直播主不用看直播客的音频
                    sdpConstraints.setAudio(false);
                    sdpConstraints.setVideo(false);
                    // setlocaldescription,但是没有setRemoteDesc,标记isOffer
                    mIsOfferedUser.put(hostId, true);
                    Log.i(TAG, "create Offer");
                    mPeer.peerConnection.createOffer(mPeer, sdpConstraints);
                }
            }
        }).on("SdpInfo", new Emitter.Listener() {
            @Override
            public void call(Object... args) {
                Log.i(TAG, "SdpInfo received");
                // 播主收到播客的sdp，设置完后发回去
                JSONObject jsonObject = (JSONObject) args[0];
                String otherUserId = (String) args[1];
                String remoteRoomId = null;
                String type = null;
                String remoteSdpDescription = null;
                try {
                    remoteRoomId = (String) jsonObject.get("roomId");
                    type = (String) jsonObject.get("type");
                    remoteSdpDescription = (String) jsonObject.get("description");
                } catch (JSONException e) {
                    e.printStackTrace();
                }
                // 直播主收到sdp信息，将peer与userid关联,如果没有该user的sdp信息，则保存
                if (!mUserIds.containsKey(otherUserId)) {
                    Log.i(TAG, "create PC");
                    mPeer = new Peer();
                    mUserIds.put(otherUserId, mPeer);
                }
                mPeer = mUserIds.get(otherUserId);

                // setRemotedescrip，发送给对面，对面收到得设置远程desc
                mPeer.peerConnection.setRemoteDescription(mPeer, new SessionDescription(
                        SessionDescription.Type.fromCanonicalForm(type), remoteSdpDescription
                ));

                sdpConstraints.setVideo(true);
                sdpConstraints.setAudio(true);
                // 如果已经和这个user发过offer，按么就不用再发,createAns()就可以了
                if (mIsOfferedUser.containsKey(otherUserId)) {
                    // 如果没有发过createOffer那么就需要再发一次,主要是播主，要为每一个播客都发一次
                } else {
                    // 对于播客，再次收到createAns，也不用继续发了
                    mIsOfferedUser.put(otherUserId, true);
                    mPeer.peerConnection.createAnswer(mPeer, sdpConstraints);
                    Log.i(TAG, "createAns");
                }
                Log.i(TAG, "sdp finished");
            }
        });

        mSocket.on("IceInfo", new Emitter.Listener() {
            @Override
            public void call(Object... args) {
                Log.i(TAG, "receive ice info");
                // 收到对方发过来的ice信息，加入pc
                try {
                    JSONObject jsonObject = new JSONObject(args[0].toString());
                    String otherUserId = (String) args[1];
                    IceCandidate candidate = null;
                    candidate = new IceCandidate(
                            jsonObject.getString("id"),
                            jsonObject.getInt("label"),
                            jsonObject.getString("candidate")
                    );
                    mPeer = mUserIds.get(otherUserId);
                    // mPeer得是匹配的
                    mPeer.peerConnection.addIceCandidate(candidate);
                } catch (JSONException e) {
                    e.printStackTrace();
                }
            }
        }).on("full", new Emitter.Listener() {
            @Override
            public void call(Object... args) {
                Toast.makeText(OneToMultiAVAct.this, "房间满人了", Toast.LENGTH_SHORT).show();
            }
        }).on("leaved", new Emitter.Listener() {
            @Override
            public void call(Object... args) {
                // 已经离开了,应该把视频流关掉
                isLiver = false;
                if (remoteVideoTrack != null) {
                    remoteVideoTrack.dispose();
                }
                remoteView.clearImage();
                mSocket.disconnect();
                Log.i(TAG, "receive leaved");
            }
        });
//        }).on("bye", new Emitter.Listener() {
//            @Override
//            public void call(Object... args) {
//                Log.i(TAG, "bye received");
//            }
//        }).
        mSocket.connect();
        Toast.makeText(OneToMultiAVAct.this, "connected:" + mSocket.connected(), Toast.LENGTH_SHORT).show();

        // 加入房间，则发送join命令给signaling server
        mSocket.emit("join", roomId);
    }

    private void initConstraints() {
        iceServers = new LinkedList<>();
        iceServers.add(new PeerConnection.IceServer("turn:139.199.3.44:3478", "hjm", "123456"));
        iceServers.add(new PeerConnection.IceServer("stun:139.199.3.44:3478", "hjm", "123456"));
//        PeerConnection.IceServer.Builder turnBuilder = PeerConnection.IceServer.builder("turn:139.199.3.44:3478").setHostname("hjm").setPassword("123456");
//        PeerConnection.IceServer.Builder stunBuilder = PeerConnection.IceServer.builder("stun:139.199.3.44:3478").setHostname("hjm").setPassword("123456");
        iceServers.add(PeerConnection.IceServer.builder("stun:stun.l.google.com:19302").createIceServer());

        pcConstraints = new MediaConstraints();
        pcConstraints.optional.add(new MediaConstraints.KeyValuePair("DtlsSrtpKeyAgreement", "true"));
        pcConstraints.optional.add(new MediaConstraints.KeyValuePair("RtpDataChannels", "true"));

        sdpConstraints = new GenMediaConstrains();
    }

    private CameraVideoCapturer createVideoCapture(Context context) {
        CameraEnumerator enumerator;
        if (Camera2Enumerator.isSupported(context)) {
            enumerator = new Camera2Enumerator(context);
        } else {
            enumerator = new Camera1Enumerator(true);
        }
        final String[] deviceNames = enumerator.getDeviceNames();

        for (String deviceName : deviceNames) {
            if (enumerator.isFrontFacing(deviceName)) {
                CameraVideoCapturer videoCapturer = enumerator.createCapturer(deviceName, null);
                if (videoCapturer != null) {
                    return videoCapturer;
                }
            }
        }
        return null;
    }

    private void initview() {

        chartTools = findViewById(R.id.charttools_layout);
        switcCamera = findViewById(R.id.switch_camera_tv);
        loundSperaker = findViewById(R.id.loundspeaker_tv);
        leaveRoom = findViewById(R.id.leave_room_bt);
        onLine = findViewById(R.id.on_line_bt);

        leaveRoom.setOnClickListener(this);
        switcCamera.setOnClickListener(this);
        loundSperaker.setOnClickListener(this);
        onLine.setOnClickListener(this);

        localView = findViewById(R.id.localVideoView);
        remoteView = findViewById(R.id.remoteVideoView);

        //创建EglBase对象
        mEglBase = EglBase.create();

        //初始化localView
        localView.init(mEglBase.getEglBaseContext(), null);
        localView.setKeepScreenOn(true);
        localView.setMirror(true);
        localView.setZOrderMediaOverlay(true);
        localView.setZOrderOnTop(true);
        localView.setScalingType(RendererCommon.ScalingType.SCALE_ASPECT_FIT);
        localView.setEnableHardwareScaler(false);

        // 初始化remoteView
        remoteView.init(mEglBase.getEglBaseContext(), null);
        remoteView.setMirror(false);
        remoteView.setZOrderMediaOverlay(true);
        remoteView.setScalingType(RendererCommon.ScalingType.SCALE_ASPECT_FIT);
        remoteView.setEnableHardwareScaler(false);

        //关闭扬声器
        mAudioManager = (AudioManager) getSystemService(AUDIO_SERVICE);
        assert mAudioManager != null;
        mAudioManager.setMode(AudioManager.MODE_IN_CALL);
        mAudioManager.setSpeakerphoneOn(false);
    }

    @Override
    protected void onDestroy() {
        if (mSocket != null) {
            mSocket.disconnect();
        }
        if (mVideoCapturer != null) {
            try {
                mVideoCapturer.stopCapture();
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
        if (mPeer != null) {
            mPeer.peerConnection.close();
            mPeer = null;
        }
        if (mVideoTrack != null) {
            mVideoTrack.dispose();
        }
        if (mAudioTrack != null) {
            mAudioTrack.dispose();
        }
        super.onDestroy();
    }

    class Peer implements PeerConnection.Observer, SdpObserver {

        PeerConnection peerConnection;

        Peer() {
            peerConnection = mPeerConnectionFactory.createPeerConnection(iceServers, pcConstraints, this);
            peerConnection.addStream(mMediaStream);
        }

        // PeerConnection.Observer

        @Override
        public void onSignalingChange(PeerConnection.SignalingState signalingState) {

        }

        @Override
        public void onIceConnectionChange(PeerConnection.IceConnectionState iceConnectionState) {
            if (iceConnectionState == PeerConnection.IceConnectionState.DISCONNECTED) {
                remoteVideoTrack.dispose();
                remoteView.clearImage();
                mPeer = null;
                for (String id : mIsOfferedUser.keySet()
                     ) {
                    mIsOfferedUser.put(id, false);
                }
//                isOffer = false;
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        Toast.makeText(OneToMultiAVAct.this, "已断开连接!", Toast.LENGTH_SHORT).show();
                    }
                });
            }
        }

        @Override
        public void onIceConnectionReceivingChange(boolean b) {

        }

        @Override
        public void onIceGatheringChange(PeerConnection.IceGatheringState iceGatheringState) {

        }

        @Override
        public void onIceCandidate(IceCandidate iceCandidate) {
            try {
                JSONObject jsonObject = new JSONObject();
                jsonObject.put("roomId", roomId);
                jsonObject.put("label", iceCandidate.sdpMLineIndex);
                jsonObject.put("id", iceCandidate.sdpMid);
                jsonObject.put("candidate", iceCandidate.sdp);
                Log.i(TAG, "candidate:" + jsonObject);
                mSocket.emit("IceInfo", jsonObject);
            } catch (JSONException e) {
                e.printStackTrace();
            }
        }

        @Override
        public void onIceCandidatesRemoved(IceCandidate[] iceCandidates) {

        }

        @Override
        public void onAddStream(MediaStream mediaStream) {
            // 对于直播客，得上屏
            if (!isLiver) {
                remoteVideoTrack = mediaStream.videoTracks.get(0);
                mRemoteVideoRenderer = new VideoRenderer(remoteView);
                remoteVideoTrack.addRenderer(mRemoteVideoRenderer);
                // 音频流
            } else {
                // 对于直播主，就放自己的，可以不接受这部分
            }
            // 对于直播主，就放自己的就可以
            Log.i(TAG, "remoted view added" + mediaStream.toString());
        }

        @Override
        public void onRemoveStream(MediaStream mediaStream) {

        }

        @Override
        public void onDataChannel(DataChannel dataChannel) {

        }

        @Override
        public void onRenegotiationNeeded() {
            // ice连接后，重新协商
            Log.i(TAG, "renegotiated");
//            // 方便起见
//            if (isLiver) {
//                mSocket.emit("online", roomId.getText().toString());
//            }
        }

        @Override
        public void onAddTrack(RtpReceiver rtpReceiver, MediaStream[] mediaStreams) {

        }

        // 如果成功创建Sdp观察者offer数据流，那么setLocalDescription && send SDP info
        // 如果是createAns, setLoc, SDP
        // SdpObserver
        @Override
        public void onCreateSuccess(SessionDescription sessionDescription) {
            Log.i(TAG, "create offer / ans succeed:" + "desctiption");
            peerConnection.setLocalDescription(this, sessionDescription);
            JSONObject jsonObject = new JSONObject();
            try {
                jsonObject.put("roomId", roomId);
                jsonObject.put("type", sessionDescription.type.canonicalForm());
                jsonObject.put("description", sessionDescription.description);
            } catch (JSONException e) {
                e.printStackTrace();
            }
            mSocket.emit("SdpInfo", jsonObject);
        }

        @Override
        public void onSetSuccess() {

        }

        @Override
        public void onCreateFailure(String s) {

        }

        @Override
        public void onSetFailure(String s) {

        }
    }

    //监听音量键控制视频通话音量
    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        switch (keyCode) {
            case KeyEvent.KEYCODE_VOLUME_UP:
                mAudioManager.adjustStreamVolume(AudioManager.STREAM_VOICE_CALL, AudioManager.ADJUST_RAISE, AudioManager.FLAG_SHOW_UI);
                return true;
            case KeyEvent.KEYCODE_VOLUME_DOWN:
                mAudioManager.adjustStreamVolume(AudioManager.STREAM_VOICE_CALL, AudioManager.ADJUST_LOWER, AudioManager.FLAG_SHOW_UI);
                return true;
            default:
                break;
        }
        return super.onKeyDown(keyCode, event);
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        switch (event.getAction()) {
            case MotionEvent.ACTION_UP:
                toggleChartTools();
                break;
        }
        return super.onTouchEvent(event);
    }

    @Override
    public void onClick(View v) {
        switch (v.getId()) {
            case R.id.switch_camera_tv:
                mVideoCapturer.switchCamera(new CameraVideoCapturer.CameraSwitchHandler() {
                    @Override
                    public void onCameraSwitchDone(boolean b) {
                        //切换摄像头完成
                    }

                    @Override
                    public void onCameraSwitchError(String s) {
                        //切换摄像头错误
                    }
                });
                break;

            case R.id.loundspeaker_tv:
                if (mAudioManager.isSpeakerphoneOn()) {
                    mAudioManager.setSpeakerphoneOn(false);
                    Toast.makeText(this, "扬声器已关闭", Toast.LENGTH_SHORT).show();
                } else {
                    mAudioManager.setSpeakerphoneOn(true);
                    Toast.makeText(this, "扬声器已打开", Toast.LENGTH_SHORT).show();
                }
                break;
            case R.id.leave_room_bt:
                // 离开房间
                mSocket.emit("leave", roomId);
                break;
            case R.id.on_line_bt:
                // 申请直播
                // 主动和房间内的所有人交换SDP
                if (!isLiver) {
                    isLiver = true;
                    mVideoTrack.dispose();
                    localView.clearImage();
//                    localView.setZOrderMediaOverlay(false);
//                    remoteView.setZOrderOnTop(true);
                    remoteVideoTrack = mVideoTrack;
                    mRemoteVideoRenderer = new VideoRenderer(remoteView);
                    remoteVideoTrack.addRenderer(mRemoteVideoRenderer);
                    mSocket.emit("online", roomId);
                } else {
                    // 误点了怎么办
                }

                break;
            default:break;
        }

    }

    private void toggleChartTools() {
        if (chartTools.isShown()) {
            chartTools.setVisibility(View.INVISIBLE);
        } else {
            chartTools.setVisibility(View.VISIBLE);
        }
    }

    // 返回也要发一下离开
    @Override
    public void onBackPressed() {
        mSocket.emit("leave",roomId);
        super.onBackPressed();
    }

}
