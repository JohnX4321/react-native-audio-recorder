package com.react_native_audio_recorder;

import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaRecorder;
import android.util.Base64;
import android.util.Log;

import com.facebook.react.bridge.Promise;
import com.facebook.react.bridge.ReactApplicationContext;
import com.facebook.react.bridge.ReactContextBaseJavaModule;
import com.facebook.react.bridge.ReactMethod;
import com.facebook.react.bridge.ReadableMap;
import com.facebook.react.modules.core.DeviceEventManagerModule;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class WAVRecorderModule extends ReactContextBaseJavaModule {

    private final String TAG="AudioRecorderModule";
    private final ReactApplicationContext reactContext;

    private DeviceEventManagerModule.RCTDeviceEventEmitter eventEmitter;

    private int sampeRateInHz,channelConfig,audioFormat,audioSource;

    private boolean isRecording;
    private int bufferSize;
    private AudioRecord recorder;

    private String tmpFile,outFile;
    private Promise stopRecordingPromise;

    public WAVRecorderModule(ReactApplicationContext context){
        super(context);
        this.reactContext=context;
    }

    @Override
    public String getName(){
        return "AudiRecord";
    }

    @ReactMethod
    public void initWave(ReadableMap options){

        sampeRateInHz=44100;
        if (options.hasKey("sampleRate"))
            sampeRateInHz=options.getInt("sampleRate");

        channelConfig= AudioFormat.CHANNEL_IN_MONO;
        if (options.hasKey("channels")){
            if (options.getInt("channels")==2)
                channelConfig=AudioFormat.CHANNEL_IN_STEREO;
        }

        audioFormat=AudioFormat.ENCODING_PCM_16BIT;
        if (options.hasKey("bitsPerSample")) {
            if (options.getInt("bitsPerSample")==8)
                audioFormat=AudioFormat.ENCODING_PCM_8BIT;
        }

        audioSource= MediaRecorder.AudioSource.VOICE_RECOGNITION;
        if (options.hasKey("audioSource"))
            audioSource=options.getInt("audioSource");

        String docDirPath=getReactApplicationContext().getFilesDir().getAbsolutePath();
        outFile=docDirPath+"/"+"audio-"+getTimestamp()+".wav";
        tmpFile=docDirPath+"/"+"temp.pcm";

        if (options.hasKey("wavFile")){
            String fileName=options.getString("wavFile");
            outFile=docDirPath+"/"+fileName;
        }


        isRecording=false;
        eventEmitter=reactContext.getJSModule(DeviceEventManagerModule.RCTDeviceEventEmitter.class);

        bufferSize=AudioRecord.getMinBufferSize(sampeRateInHz,channelConfig,audioFormat);
        int recordingBufferSize=bufferSize*3;
        recorder=new AudioRecord(audioSource,sampeRateInHz,channelConfig ,audioFormat,recordingBufferSize );


    }


    private String getTimestamp() {
        return new SimpleDateFormat("yyyy-MM-dd-HH-mm-ss", Locale.US).format(new Date());
    }


    @ReactMethod
    public void start() {

        isRecording=true;
        recorder.startRecording();
        Log.d(TAG,"recording");

        Thread newThread=new Thread(new Runnable() {
            @Override
            public void run() {
                try {

                    int bytesRead,count=0;
                    String base64Data;
                    byte[] buffer=new byte[bufferSize];
                    FileOutputStream os=new FileOutputStream(tmpFile);
                    while (isRecording) {

                        bytesRead=recorder.read(buffer,0,buffer.length);

                        if (bytesRead>0&&++count>2) {
                            base64Data= Base64.encodeToString(buffer,Base64.NO_WRAP);
                            eventEmitter.emit("data",base64Data);
                            os.write(buffer,0,bytesRead);
                        }

                    }
                    recorder.stop(); os.close();
                    saveAsWav();
                    stopRecordingPromise.resolve(outFile);

                } catch (Exception e){
                    e.printStackTrace();
                }
            }
        });

        newThread.start();

    }


    @ReactMethod
    public void stop(Promise promise){
        isRecording=false;
        stopRecordingPromise=promise;
        promise.resolve(outFile);
    }

    private void saveAsWav() {


        try {

            FileInputStream in=new FileInputStream(tmpFile);
            FileOutputStream out=new FileOutputStream(outFile);
            long totalAudioLen = in.getChannel().size(),totalDataLen=totalAudioLen+36;
            addWavHeader(out,totalAudioLen,totalDataLen);

            byte[] data=new byte[bufferSize];
            int bytesRead;
            while ((bytesRead=in.read(data))!=-1)
                out.write(data,0,bytesRead);

            Log.d(TAG,"saved"+outFile);
            in.close(); out.close();
            deleteTempFile();


        } catch (Exception e){
            e.printStackTrace();
        }
    }


    private void addWavHeader(FileOutputStream out,long totalAudioLen,long totalDataLen) throws Exception {

        long sampleRate=sampeRateInHz;
        int channels=channelConfig==AudioFormat.CHANNEL_IN_MONO?1:2;
        int bitsPerSample=audioFormat==AudioFormat.ENCODING_PCM_8BIT?8:16;
        long byteRate=sampleRate*channels*bitsPerSample/8;
        int blockAlign=channels*bitsPerSample/8;

        byte[] header=new byte[44];

        header[0]='R';
        header[1]='I'; header[2]='F'; header[3]='F';
        header[4] = (byte) (totalDataLen & 0xff);           // how big is the rest of this file
        header[5] = (byte) ((totalDataLen >> 8) & 0xff);
        header[6] = (byte) ((totalDataLen >> 16) & 0xff);
        header[7] = (byte) ((totalDataLen >> 24) & 0xff);
        header[8] = 'W';                                    // WAVE chunk
        header[9] = 'A';
        header[10] = 'V';
        header[11] = 'E';
        header[12] = 'f';                                   // 'fmt ' chunk
        header[13] = 'm';
        header[14] = 't';
        header[15] = ' ';
        header[16] = 16;                                    // 4 bytes: size of 'fmt ' chunk
        header[17] = 0;
        header[18] = 0;
        header[19] = 0;
        header[20] = 1;                                     // format = 1 for PCM
        header[21] = 0;
        header[22] = (byte) channels;                       // mono or stereo
        header[23] = 0;
        header[24] = (byte) (sampleRate & 0xff);            // samples per second
        header[25] = (byte) ((sampleRate >> 8) & 0xff);
        header[26] = (byte) ((sampleRate >> 16) & 0xff);
        header[27] = (byte) ((sampleRate >> 24) & 0xff);
        header[28] = (byte) (byteRate & 0xff);              // bytes per second
        header[29] = (byte) ((byteRate >> 8) & 0xff);
        header[30] = (byte) ((byteRate >> 16) & 0xff);
        header[31] = (byte) ((byteRate >> 24) & 0xff);
        header[32] = (byte) blockAlign;                     // bytes in one sample, for all channels
        header[33] = 0;
        header[34] = (byte) bitsPerSample;                  // bits in a sample
        header[35] = 0;
        header[36] = 'd';                                   // beginning of the data chunk
        header[37] = 'a';
        header[38] = 't';
        header[39] = 'a';
        header[40] = (byte) (totalAudioLen & 0xff);         // how big is this data chunk
        header[41] = (byte) ((totalAudioLen >> 8) & 0xff);
        header[42] = (byte) ((totalAudioLen >> 16) & 0xff);
        header[43] = (byte) ((totalAudioLen >> 24) & 0xff);

        out.write(header,0,44);

    }


    private void deleteTempFile() {
        File file=new File(tmpFile);
        file.delete();
    }

}
