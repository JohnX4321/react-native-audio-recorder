package com.react_native_audio_recorder;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaRecorder;
import android.os.Build;
import android.os.Environment;
import android.os.Process;
import android.util.Base64;
import android.util.Log;

import androidx.core.content.ContextCompat;

import com.facebook.react.bridge.Arguments;
import com.facebook.react.bridge.Promise;
import com.facebook.react.bridge.ReactApplicationContext;
import com.facebook.react.bridge.ReactContextBaseJavaModule;
import com.facebook.react.bridge.ReactMethod;
import com.facebook.react.bridge.ReadableMap;
import com.facebook.react.bridge.WritableMap;
import com.facebook.react.modules.core.DeviceEventManagerModule;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Timer;
import java.util.TimerTask;

public class AudioRecorderModule extends ReactContextBaseJavaModule {

    private static final String TAG = "ReactNativeAudio";

    private static final String DocumentDirectoryPath = "DocumentDirectoryPath";
    private static final String PicturesDirectoryPath = "PicturesDirectoryPath";
    private static final String MainBundlePath = "MainBundlePath";
    private static final String CachesDirectoryPath = "CachesDirectoryPath";
    private static final String LibraryDirectoryPath = "LibraryDirectoryPath";
    private static final String MusicDirectoryPath = "MusicDirectoryPath";
    private static final String DownloadsDirectoryPath = "DownloadsDirectoryPath";

    private Context context;
    private MediaRecorder recorder;
    private String currentOutputFile;
    private boolean isRecording = false;
    private boolean isPaused = false;
    private boolean includeBase64 = false;
    private Timer timer;
    private StopWatch stopWatch;

    private boolean isPauseResumeCapable = false;
    private Method pauseMethod = null;
    private Method resumeMethod = null;

    public AudioRecorderModule(ReactApplicationContext reactContext) {

        super(reactContext);
        this.context=reactContext;
        stopWatch=new StopWatch();
        isPauseResumeCapable= Build.VERSION.SDK_INT>Build.VERSION_CODES.M;
        if (isPauseResumeCapable) {
            try {
                pauseMethod=MediaRecorder.class.getMethod("pause");
                resumeMethod=MediaRecorder.class.getMethod("resume");
            } catch (NoSuchMethodException e) {
                Log.d(TAG,"error method");
            }
        }

    }


    @Override
    public Map<String, Object> getConstants() {

        Map<String, Object> constants=new HashMap<>();
        constants.put(DocumentDirectoryPath,this.getReactApplicationContext().getFilesDir().getAbsolutePath());
        constants.put(PicturesDirectoryPath, Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES).getAbsolutePath());
        constants.put(MainBundlePath,"");
        constants.put(CachesDirectoryPath,this.getReactApplicationContext().getCacheDir().getAbsolutePath());
        constants.put(LibraryDirectoryPath,"");
        constants.put(MusicDirectoryPath,Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MUSIC).getAbsolutePath());
        constants.put(DownloadsDirectoryPath,Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS).getAbsolutePath());
        return constants;

    }


    @Override
    public String getName() {
        return "AudioRecorderManager";
    }


    @ReactMethod
    public void checkAuthorizationStatus(Promise promise) {
        int permCheck= ContextCompat.checkSelfPermission(getCurrentActivity(), Manifest.permission.RECORD_AUDIO);
        boolean pg=permCheck== PackageManager.PERMISSION_GRANTED;
        promise.resolve(pg);
    }


    @ReactMethod
    public void prepareRecordingAtPath(String recPath,ReadableMap recSettings,Promise promise) {

        if (isRecording) {
            logAndRejectPromise(promise,"INVALID_STATE","Please stop recording first");
        }

        File destFile = new File(recPath);
        if (destFile.getParentFile()!=null)
            destFile.getParentFile().mkdirs();

        recorder=new MediaRecorder();
        try {
            recorder.setAudioSource(recSettings.getInt("audioSource"));
            int outForm=getOutputFormatFromString(recSettings.getString("outputFormat"));
            recorder.setOutputFormat(outForm);
            int audioEncoder = getAudioEncoderFromString(recSettings.getString("audioEncoding"));
            recorder.setAudioEncoder(audioEncoder);
            recorder.setAudioSamplingRate(recSettings.getInt("sampleRate"));
            recorder.setAudioChannels(recSettings.getInt("channels"));
            recorder.setAudioEncodingBitRate(recSettings.getInt("audioEncodingBitRate"));
            recorder.setOutputFile(destFile.getPath());
            includeBase64 = recSettings.getBoolean("includeBase64");

        } catch (Exception e) {
            logAndRejectPromise(promise, "COULDNT_CONFIGURE_MEDIA_RECORDER" , "Make sure you've added RECORD_AUDIO permission to your AndroidManifest.xml file "+e.getMessage());

            return;
        }

        currentOutputFile=recPath;
        try {
            recorder.prepare();
            promise.resolve(currentOutputFile);
        } catch (Exception e){
            logAndRejectPromise(promise, "COULDNT_PREPARE_RECORDING_AT_PATH "+recPath, e.getMessage());

        }

    }


    private int getAudioEncoderFromString(String enc) {
        switch (enc) {
            case "aac":
                return MediaRecorder.AudioEncoder.AAC;
            case "aac_eld":
                return MediaRecorder.AudioEncoder.AAC_ELD;
            case "amr_nb":
                return MediaRecorder.AudioEncoder.AMR_NB;
            case "amr_wb":
                return MediaRecorder.AudioEncoder.AMR_WB;
            case "he_aac":
                return MediaRecorder.AudioEncoder.HE_AAC;
            case "vorbis":
                return MediaRecorder.AudioEncoder.VORBIS;
            default:
                return MediaRecorder.AudioEncoder.DEFAULT;
        }
    }


    private int getOutputFormatFromString(String outputFormat) {
        switch (outputFormat) {
            case "mpeg_4":
                return MediaRecorder.OutputFormat.MPEG_4;
            case "aac_adts":
                return MediaRecorder.OutputFormat.AAC_ADTS;
            case "amr_nb":
                return MediaRecorder.OutputFormat.AMR_NB;
            case "amr_wb":
                return MediaRecorder.OutputFormat.AMR_WB;
            case "three_gpp":
                return MediaRecorder.OutputFormat.THREE_GPP;
            case "webm":
                return MediaRecorder.OutputFormat.WEBM;
            default:
                Log.d("INVALID_OUPUT_FORMAT", "USING MediaRecorder.OutputFormat.DEFAULT : " + MediaRecorder.OutputFormat.DEFAULT);
                return MediaRecorder.OutputFormat.DEFAULT;

        }
    }


    @ReactMethod
    public void start(Promise promise){

        if (recorder==null){
            logAndRejectPromise(promise, "RECORDING_NOT_PREPARED", "Please call prepareRecordingAtPath before starting recording");
            return;
        }
        if (isRecording){
            logAndRejectPromise(promise, "INVALID_STATE", "Please call stopRecording before starting recording");
            return;
        }

        recorder.start();
        stopWatch.reset();
        stopWatch.start();
        isRecording=true;
        isPaused=false;
        startTimer();
        promise.resolve(currentOutputFile);

    }


    @ReactMethod
    public void stop(Promise promise){

        if (!isRecording){
            logAndRejectPromise(promise, "INVALID_STATE", "Please call startRecording before stopping recording");
            return;
        }

        stopTimer();
        isRecording=false;
        isPaused=false;

        try {
            recorder.stop();
            recorder.release();
            stopWatch.stop();
        } catch (final RuntimeException e) {
            // https://developer.android.com/reference/android/media/MediaRecorder.html#stop()
            logAndRejectPromise(promise, "RUNTIME_EXCEPTION", "No valid audio data received. You may be using a device that can't record audio.");
            return;
        } finally {
            recorder=null;
        }

        promise.resolve(currentOutputFile);

        WritableMap result = Arguments.createMap();
        result.putString("status","OK");
        result.putString("audioFileURL","file://"+currentOutputFile);

        String base64="";
        if (includeBase64){

            try {

                InputStream inputStream=new FileInputStream(currentOutputFile);
                byte[] bytes;
                byte[] buffer=new byte[8192];
                int bytesRead;
                ByteArrayOutputStream output=new ByteArrayOutputStream();
                try {
                    while ((bytesRead=inputStream.read(buffer))!=-1)
                        output.write(buffer,0,bytesRead);
                } catch (IOException e){
                    Log.e(TAG,"Failed to parse");
                }
                bytes=output.toByteArray();
                base64=Base64.encodeToString(bytes,Base64.DEFAULT);

            } catch (FileNotFoundException e){
                Log.e(TAG,"Failed to find");
            }

        }
        result.putString("base64",base64);

        sendEvent("recordingFinished",result);

    }


    @ReactMethod
    public void pauseRecording(Promise promise) {
        if (!isPauseResumeCapable || pauseMethod==null) {
            logAndRejectPromise(promise, "RUNTIME_EXCEPTION", "Method not available on this version of Android.");
            return;
        }

        if (!isPaused) {
            try {
                pauseMethod.invoke(recorder);
                stopWatch.stop();
            } catch (InvocationTargetException | RuntimeException | IllegalAccessException e) {
                e.printStackTrace();
                logAndRejectPromise(promise, "RUNTIME_EXCEPTION", "Method not available on this version of Android.");
                return;
            }
        }

        isPaused = true;
        promise.resolve(null);
    }

    @ReactMethod
    public void resumeRecording(Promise promise) {
        if (!isPauseResumeCapable || resumeMethod == null) {
            logAndRejectPromise(promise, "RUNTIME_EXCEPTION", "Method not available on this version of Android.");
            return;
        }

        if (isPaused) {
            try {
                resumeMethod.invoke(recorder);
                stopWatch.start();
            } catch (InvocationTargetException | RuntimeException | IllegalAccessException e) {
                e.printStackTrace();
                logAndRejectPromise(promise, "RUNTIME_EXCEPTION", "Method not available on this version of Android.");
                return;
            }
        }

        isPaused = false;
        promise.resolve(null);
    }

    private void startTimer(){
        timer = new Timer();
        timer.scheduleAtFixedRate(new TimerTask() {
            @Override
            public void run() {
                if (!isPaused) {
                    WritableMap body = Arguments.createMap();
                    body.putDouble("currentTime", stopWatch.getTimeSeconds());
                    sendEvent("recordingProgress", body);
                }
            }
        }, 0, 1000);
    }

    private void stopTimer(){
        if (timer != null) {
            timer.cancel();
            timer.purge();
            timer = null;
        }
    }



    private void sendEvent(String eventName, Object params) {
        getReactApplicationContext()
                .getJSModule(DeviceEventManagerModule.RCTDeviceEventEmitter.class)
                .emit(eventName, params);
    }

    private void logAndRejectPromise(Promise promise, String errorCode, String errorMessage) {
        Log.e(TAG, errorMessage);
        promise.reject(errorCode, errorMessage);
    }





}
