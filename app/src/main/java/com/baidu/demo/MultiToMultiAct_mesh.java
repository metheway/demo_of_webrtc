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
import java.util.Map;

import io.socket.client.IO;
import io.socket.client.Socket;
import io.socket.emitter.Emitter;
import me.weyye.hipermission.HiPermission;
import me.weyye.hipermission.PermissionCallback;
import me.weyye.hipermission.PermissionItem;
import utils.GenMediaConstrains;


public class MultiToMultiAct_mesh extends AppCompatActivity implements View.OnClickListener {

    private String TAG = MultiToMultiAct_mesh.this.getClass().getCanonicalName();
    private VideoRenderer mlocalVideoRenderer;
    private int numsConnected = 4;

    private EglBase mEglBase;

    // 可以用recyclerview改进
    int[] surfaceViewIds = new int[]{
            R.id.local_view, R.id.first_remote_view, R.id.sec_remote_view, R.id.third_remote_view
    };

    private int mOnLiverNum = 1;

    private Peer mPeer;
    private PeerConnectionFactory mPeerConnectionFactory;
    private AudioSource mAudioSouce;
    private GenMediaConstrains mMediaConstraints;
    private VideoSource mVideoSource;
    private VideoTrack mVideoTrack;
    private AudioTrack mAudioTrack;
    private LinkedList<PeerConnection.IceServer> iceServers;
    private MediaConstraints pcConstraints;
    private GenMediaConstrains sdpConstraints;
    private MediaStream mMediaStream;
    private AudioManager mAudioManager;
    private CameraVideoCapturer mVideoCapturer;
    private Socket mSocket;
    private String roomId;

    private Map<Integer, SurfaceViewRenderer> mSurfaceViewMap = new HashMap<>();
    private Map<Integer, VideoRenderer> mVideoRendererMap = new HashMap<>();
    private String currentUserId;
    // 设置Bean和notEmpty类主要是为了确定空屏幕的位置，从而为新加入的人员分配
    private HashMap<String, SurfaceViewIdBean> mViewsMap = new HashMap<>();
    private int[] mSurfaceViewsNotEmpty = new int[numsConnected];
    private LinearLayout chartTools;
    private TextView switcCamera;
    private TextView loundSperaker;
    private String mToUserId;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN, WindowManager.LayoutParams.FLAG_FULLSCREEN);
        setContentView(R.layout.activity_multi_to_multi);
        initView();
        AskPermission();
        // 其实转入这个页面就可以直接发送join请求了
        roomId = getIntent().getStringExtra("roomId");
        mSocket.emit("join", roomId);
    }


    private void initView() {
        chartTools = findViewById(R.id.charttools_layout);
        switcCamera = findViewById(R.id.switch_camera_tv);
        loundSperaker = findViewById(R.id.loundspeaker_tv);
        switcCamera.setOnClickListener(this);
        loundSperaker.setOnClickListener(this);

        //创建EglBase对象
        mEglBase = EglBase.create();

        for (int i = 0; i < numsConnected; i++) {
            Log.i(TAG, "surface id:" + String.valueOf(surfaceViewIds[i]));
            SurfaceViewRenderer surfaceViewRenderer = findViewById(surfaceViewIds[i]);
            mSurfaceViewMap.put(i, surfaceViewRenderer);
            surfaceViewRenderer.init(mEglBase.getEglBaseContext(), null);
            surfaceViewRenderer.setEnableHardwareScaler(false);
            surfaceViewRenderer.setMirror(false);
            surfaceViewRenderer.setKeepScreenOn(true);
            surfaceViewRenderer.setScalingType(RendererCommon.ScalingType.SCALE_ASPECT_FIT);
            Log.i(TAG, String.valueOf("surf:" + surfaceViewRenderer));
        }

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
    private void init() {

        PeerConnectionFactory.initialize(PeerConnectionFactory.InitializationOptions.builder(
                getApplicationContext()).setEnableVideoHwAcceleration(true).createInitializationOptions());
        mPeerConnectionFactory = new PeerConnectionFactory(new PeerConnectionFactory.Options());
        mPeerConnectionFactory.setVideoHwAccelerationOptions(mEglBase.getEglBaseContext(),
                mEglBase.getEglBaseContext());

        initConstraints();
        mVideoCapturer = createVideoCapture(this);

        mVideoSource = mPeerConnectionFactory.createVideoSource(mVideoCapturer);
        mVideoTrack = mPeerConnectionFactory.createVideoTrack("videoTrack", mVideoSource);
        mVideoCapturer.startCapture(72 * 4, 128 * 4, 30);

        mAudioSouce = mPeerConnectionFactory.createAudioSource(new MediaConstraints());
        mAudioTrack = mPeerConnectionFactory.createAudioTrack("audioTrack", mAudioSouce);

        // 播放本地视频，经过测试，视频渲染实例化必须得在PeerConnectionFactory创建之后
        for (int i = 0; i < numsConnected; i++) {
            mVideoRendererMap.put(i, new VideoRenderer(mSurfaceViewMap.get(i)));
        }

        mlocalVideoRenderer = mVideoRendererMap.get(0);
        mVideoTrack.addRenderer(mlocalVideoRenderer);

        //创建媒体流并加入本地音视频
        mMediaStream = mPeerConnectionFactory.createLocalMediaStream("localstream");
        mMediaStream.addTrack(mVideoTrack);
        mMediaStream.addTrack(mAudioTrack);

        mMediaConstraints = new GenMediaConstrains();

        try {
//            mSocket = IO.socket("http://localhost:8081");
//            mSocket = IO.socket("http://959d79ac.ngrok.io");
            mSocket = IO.socket("http://139.199.3.44:8081");
        } catch (URISyntaxException e) {
            e.printStackTrace();
        }

        mSocket.on("joined", new Emitter.Listener() {
            @Override
            public void call(Object... args) {
                // 收到joined的信息，则录入自己的id号
                String roomId = (String) args[0];
                String userId = (String) args[1];
                instantiatePeerIfNotHave(userId);
                mViewsMap.get(userId).setViewIndex(0);
                currentUserId = userId;
                // 把localView放在0的位置
            }
        }).on("otherjoin", new Emitter.Listener() {
            @Override
            public void call(Object... args) {
                // 收到其他人加入的信息的时候，发送sdp
                Log.i(TAG, "otherjoin");
                String roomId = (String) args[0];
                String fromUserId = (String) args[1];
                if (!instantiatePeerIfNotHave(fromUserId)) {
                    // 为这个mPeer分配视频位置
                    setSurfViewForBeanIfNotEmpty(fromUserId);
                    mPeer = mViewsMap.get(fromUserId).getPeer();
                }
                // createOffer并且标记已经传给这个id,发出SdpInfo
                createOfferAndMark(fromUserId);
            }
        }).on("SdpInfo", new Emitter.Listener() {
            @Override
            public void call(Object... args) {
                Log.i(TAG, "sdpInfo");
                // 收到对方sdp，创建相应的peer，设置远程sdp
                JSONObject jsonObject = (JSONObject) args[0];
                String type = null;
                String remoteSdpDescription = null;
                String roomId = null;
                String fromUserId = null;
                try {
                    roomId = jsonObject.getString("roomId");
                    remoteSdpDescription = jsonObject.getString("description");
                    type = jsonObject.getString("type");
//                    fromUserId = (String) jsonObject.get("to");
                    // to 实际上是表示自己这个socket
                    fromUserId = (String) args[1];
                } catch (JSONException e) {
                    e.printStackTrace();
                }
                if (!instantiatePeerIfNotHave(fromUserId)) {
                    // 为这个user设置视频的位置，如果已经设置了，那么就跳过
                    setSurfViewForBeanIfNotEmpty(fromUserId);
                }
                mPeer = mViewsMap.get(fromUserId).getPeer();
                mPeer.peerConnection.setRemoteDescription(mPeer, new SessionDescription(
                        SessionDescription.Type.fromCanonicalForm(type), remoteSdpDescription
                ));
                // 如果已经对这个用户回复过sdp，则可以跳过，如果没有，则createAns()
                if (!mViewsMap.get(fromUserId).isOffered) {
                    createAnsAndMark(fromUserId);
                }
            }
        }).on("IceInfo", new Emitter.Listener() {
            // iceServers也可以指向发送，这里没有用指向的
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
                    // 如果这个是空的，表示实例化没有成功,说明ice是在sdp前接收的？还是说两者的id不一样
//                    if (mViewsMap.get(otherUserId) == null) {
                        instantiatePeerIfNotHave(otherUserId);
                        setSurfViewForBeanIfNotEmpty(otherUserId);
//                    }
                    mPeer = mViewsMap.get(otherUserId).getPeer();
                    mPeer.peerConnection.addIceCandidate(candidate);
                } catch (JSONException e) {
                    e.printStackTrace();
                }
            }
        }).on("left", new Emitter.Listener() {
            @Override
            public void call(Object... args) {
            }
        }).on("bye", new Emitter.Listener() {
            @Override
            public void call(Object... args) {
                // 对于离开的用户，直接删除相应的pc连接及sdp记录
                String roomId = (String) args[0];
                String otherUserId = (String) args[1];
                // 将视图清空
                // 这个暂时没做
                // 得找到这个surfaceview，将索引置空
                resetIndexForLeftSurf(otherUserId);
                mViewsMap.remove(otherUserId);
            }
        });

        mSocket.connect();
    }

    private void createOfferAndMark(String fromUserId) {
        mToUserId = fromUserId;
        mViewsMap.get(fromUserId).setOffered(true);
        // 用当前同级连接创建Offer
        mPeer.peerConnection.createOffer(mPeer, mMediaConstraints);
    }
    private void createAnsAndMark(String fromUserId) {
        mToUserId = fromUserId;
        mViewsMap.get(fromUserId).setOffered(true);
        // 用当前同级连接创建Ans
        mPeer.peerConnection.createAnswer(mPeer, mMediaConstraints);
    }

    private void resetIndexForLeftSurf(String userId) {
        // 当一个user离开的时候，将其surf位置0
        if (mViewsMap.containsKey(userId)) {
            mSurfaceViewsNotEmpty[mViewsMap.get(userId).getViewIndex()] = 0;
        }
    }

    private void setSurfViewForBeanIfNotEmpty(String userId) {
        // localView不用管（根本进不来），对于其他的view，如果已经有位置那就不设置了
        if (mViewsMap.get(userId).getViewIndex() <= 0) {
            mViewsMap.get(userId).setViewIndex(transferToEmptyViewIndex());
        }
    }


    private boolean instantiatePeerIfNotHave(String userId) {
        if (!mViewsMap.containsKey(userId)) {
            // 第三个人加入的时候没有id，创建一个bean，实例化Peer
            SurfaceViewIdBean surfaceViewIdBean = new SurfaceViewIdBean();
            surfaceViewIdBean.setPeer(new Peer());
            mViewsMap.put(userId, surfaceViewIdBean);
            return false;
        }
        return true;
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

    @Override
    public void onClick(View view) {
        switch (view.getId()) {
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

                default:break;
        }

    }

    private class Peer implements SdpObserver, PeerConnection.Observer {

        public PeerConnection peerConnection;
        Peer() {
            peerConnection = mPeerConnectionFactory.createPeerConnection(iceServers, pcConstraints, this);
            peerConnection.addStream(mMediaStream);
        }

        @Override
        public void onSignalingChange(PeerConnection.SignalingState signalingState) {

        }

        @Override
        public void onIceConnectionChange(PeerConnection.IceConnectionState iceConnectionState) {
            if (iceConnectionState == PeerConnection.IceConnectionState.DISCONNECTED) {
//                remoteVideoTrack.dispose();
//                remoteView.clearImage();
                // 清除远程的视频
//                for (SurfaceViewIdBean surfaceBean : mViewsMap.values()
//                     ) {
//                    surfaceBean.setOffered(false);
//                }
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        Toast.makeText(MultiToMultiAct_mesh.this, "已断开连接!", Toast.LENGTH_SHORT).show();
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
            // 注意，这里如果只这样发的话，新加入的成员不一定有原成员的candidate信息
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
            // 这是这个Peer里面peerConnection的回调，所以实际上放上surfaceview里面就可以了
            // 找到相应的屏幕，放上相应的渲染
            // 获取媒体流里面的视频轨
            final VideoTrack remoteVideoTrack = mediaStream.videoTracks.get(0);
//                remoteVideoTrack.addRenderer(mVideoRendererMap.get(transferToEmptyViewIndex()));
//            if (transferToEmptyViewIndex() == -1) {
//                Log.i(TAG, "the screen is full");
//            }
            // 找到这个Peer的surfViewIndex，然后加入渲染

            for (final SurfaceViewIdBean surfBean : mViewsMap.values()
                 ) {
                if (surfBean.getPeer().equals(this)) {
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            remoteVideoTrack.addSink(mSurfaceViewMap.get(surfBean.getViewIndex()));
                        }
                    });
                }
            }
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

        }

        @Override
        public void onAddTrack(RtpReceiver rtpReceiver, MediaStream[] mediaStreams) {

        }

        @Override
        public void onCreateSuccess(SessionDescription sessionDescription) {
            Log.i(TAG, "create offer / ans succeed:" + "desctiption");
            // 这个当前的id是指收到用户的socket.id
            peerConnection.setLocalDescription(this, sessionDescription);
            JSONObject jsonObject = new JSONObject();
            try {
                jsonObject.put("roomId", roomId);
                jsonObject.put("type", sessionDescription.type.canonicalForm());
                jsonObject.put("description", sessionDescription.description);
                jsonObject.put("to", mToUserId);
            } catch (JSONException e) {
                e.printStackTrace();
            }
            // 要把自己的向谁发告诉SigServer
//            mSocket.emit("SdpInfo", jsonObject, mToUserId);
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

    private void toggleChartTools() {
        if (chartTools.isShown()) {
            chartTools.setVisibility(View.INVISIBLE);
        } else {
            chartTools.setVisibility(View.VISIBLE);
        }
    }

    private Integer transferToEmptyViewIndex() {
        // 遍历SurfaceViewRenderer，找到第一个是空的视频容器
        // 这里是建立了一个数组，如果数是0，那么表示没有放置，如果不是，那么放置了
        for (int i = 1; i < mSurfaceViewsNotEmpty.length; i++) {
            if (mSurfaceViewsNotEmpty[i] == 0) {
                mSurfaceViewsNotEmpty[i] = 1;
                return i;
            }
        }
        return -1;
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
    public void onBackPressed() {
        // 如果后退，那么直接退出这个视频聊天，让别人的offer请求重置
        mSocket.emit("leave", roomId);
        super.onBackPressed();

    }

    class SurfaceViewIdBean {
        private Peer peer;
        private boolean isOffered;
        private int viewIndex;

        public SurfaceViewIdBean() {
            this.isOffered = false;
        }

        public Peer getPeer() {
            return peer;
        }

        public void setPeer(Peer peer) {
            this.peer = peer;
        }

        public boolean isOffered() {
            return isOffered;
        }

        public void setOffered(boolean offered) {
            isOffered = offered;
        }

        public int getViewIndex() {
            return viewIndex;
        }

        public void setViewIndex(int viewIndex) {
            this.viewIndex = viewIndex;
        }
    }
}
