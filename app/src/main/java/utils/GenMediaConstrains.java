package utils;

import org.webrtc.MediaConstraints;

public class GenMediaConstrains extends MediaConstraints {

    private MediaConstraints sdpConstraints;
    private MediaConstraints.KeyValuePair offerReceiveVideo;
    private MediaConstraints.KeyValuePair offerReceiveAudio;
    private KeyValuePair forbiddenVideo;
    private KeyValuePair forbiddenAudio;

    public GenMediaConstrains() {
        initSdpCons();
    }

    private void initSdpCons() {
        sdpConstraints = new MediaConstraints();
        offerReceiveVideo = new MediaConstraints.KeyValuePair("OfferToReceiveAudio", "true");
        offerReceiveAudio = new MediaConstraints.KeyValuePair("OfferToReceiveVideo", "true");
        forbiddenVideo = new MediaConstraints.KeyValuePair("OfferToReceiveVideo", "false");
        forbiddenAudio = new MediaConstraints.KeyValuePair("OfferToReceiveAudio", "false");
        sdpConstraints.mandatory.add(offerReceiveVideo);
        sdpConstraints.mandatory.add(offerReceiveAudio);
    }

    public void setAudio(boolean b) {
        if (b) {
            sdpConstraints.mandatory.remove(forbiddenAudio);
            sdpConstraints.mandatory.add(offerReceiveAudio);
        } else {
            sdpConstraints.mandatory.remove(offerReceiveAudio);
            sdpConstraints.mandatory.add(forbiddenAudio);
        }
    }

    public void setVideo(boolean b) {
        if (b) {
            sdpConstraints.mandatory.remove(forbiddenVideo);
            sdpConstraints.mandatory.add(offerReceiveVideo);
        } else {
            sdpConstraints.mandatory.remove(forbiddenVideo);
            sdpConstraints.mandatory.add(offerReceiveVideo);
        }
    }
}
