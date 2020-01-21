/**
 * @format
 */

import React from 'react';
import ReactNative,{NativeModules,NativeAppEventEmitter,DeviceEventEmitter,PermissionsAndroid,Platform,NativeEventEmitter} from 'react-native';

const {WAVRecorderModule}=NativeModules;

var AudioRecorderManager=NativeModules.AudioRecorderModule;


var AudioRecorder={
    prepareRecordingAtPath: function (path,options) {
if (this.progressSubscription) this.progressSubscription.remove();
this.progressSubscription=NativeAppEventEmitter.addListener('recordingProgress',(data)=>{
    if (this.onProgress)
        this.onProgress(data);
});


    if (this.finishedSubscription) this.finishedSubscription.remove();
    this.finishedSubscription=NativeAppEventEmitter.addListener('recordingFinished',(data)=>{
        if (this.onFinished) this.onFinished(data);
    });


        var defaultOptions = {
            SampleRate: 44100.0,
            Channels: 2,
            AudioQuality: 'High',
            AudioEncoding: 'ima4',
            OutputFormat: 'mpeg_4',
            MeteringEnabled: false,
            MeasurementMode: false,
            AudioEncodingBitRate: 32000,
            IncludeBase64: false,
            AudioSource: 0
        };

        var recordingOptions = {...defaultOptions, ...options};

        if (Platform.OS==='ios') {
            AudioRecorderManager.prepareRecordingAtPath(path,
                recordingOptions.SampleRate,
                recordingOptions.Channels,
                recordingOptions.AudioQuality,
                recordingOptions.AudioEncoding,
                recordingOptions.MeteringEnabled,
                recordingOptions.MeasurementMode,
                recordingOptions.IncludeBase64
            );
        } else{
            return AudioRecorderManager.prepareRecordingAtPath(path,recordingOptions);
        }

    },
    startRecording: function () {
        return AudioRecorderManager.startRecording();
    },
    pauseRecording:function () {
        return AudioRecorderManager.pauseRecording();
    },
    resumeRecording: function() {
        return AudioRecorderManager.resumeRecording();
    },
    stopRecording: function() {
        return AudioRecorderManager.stopRecording();
    },
    checkAuthorizationStatus: AudioRecorderManager.checkAuthorizationStatus,
    requestAuthroization: ()=>{
        if (Platform.OS==='ios')
            return AudioRecorderManager.requestAuthroization();
        else
            return new Promise((res,rej)=>{
                PermissionsAndroid.request(PermissionsAndroid.PERMISSIONS.RECORD_AUDIO)
                    .then(result=>{
                        if (result==PermissionsAndroid.RESULTS.GRANTED||result==true)
                            res(true);
                        else
                            res(false);
                    })
            });
    },
    removeListeners: function () {
        if (this.progressSubscription) this.progressSubscription.remove();
        if (this.finishedSubscription) this.finishedSubscription.remove();

    },
};

let AudioUtils={},AudioSource={};

if (Platform.OS==='android') {
    AudioUtils={
        MainBundlePath: AudioRecorderManager.MainBundlePath,
        CachesDirectoryPath: AudioRecorderManager.CachesDirectoryPath,
        DocumentDirectoryPath: AudioRecorderManager.DocumentDirectoryPath,
        LibraryDirectoryPath: AudioRecorderManager.LibraryDirectoryPath,
        PicturesDirectoryPath: AudioRecorderManager.PicturesDirectoryPath,
        MusicDirectoryPath: AudioRecorderManager.MusicDirectoryPath,
        DownloadsDirectoryPath: AudioRecorderManager.DownloadsDirectoryPath
    };

    AudioSource={
        DEFAULT: 0, MIC: 1,
        VOICE_UPLINK: 2, VOICE_DOWNLINK:3,
        VOICE_CALL: 4,CAMCORDER: 5,
        VOICE_RECOGNITION: 6,
        VOICE_COMMUNICATION:7, REMOTE_SUBMIX: 8,
        UNPROCESSED: 9,
    };
}


const EventEmitter=new NativeEventEmitter(WAVRecorderModule);

const WAVAudioRecord={};

WAVAudioRecord.init=options=>WAVRecorderModule.init(options);
WAVAudioRecord.stop=()=>WAVRecorderModule.stop();
WAVAudioRecord.start=()=>WAVRecorderModule.start();

const eventsMap={
    data: 'data'
};

WAVAudioRecord.on=(event,callback)=>{
    const natEvent=eventsMap[event];
    if (!natEvent)
        throw new Error('Invalid Event');
    EventEmitter.removeAllListeners(natEvent);
    return EventEmitter.addListener(natEvent,callback);
};






module.exports={AudioRecorder,AudioUtils,AudioSource,WAVAudioRecord};
