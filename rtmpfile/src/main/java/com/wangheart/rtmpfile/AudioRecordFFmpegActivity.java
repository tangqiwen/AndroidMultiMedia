package com.wangheart.rtmpfile;

import android.app.Activity;
import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaRecorder;
import android.os.Bundle;
import android.view.View;

import com.wangheart.rtmpfile.audio.AudioBuffer;
import com.wangheart.rtmpfile.audio.FFmpegAudioHandle;
import com.wangheart.rtmpfile.utils.ADTSUtils;
import com.wangheart.rtmpfile.utils.FileUtil;
import com.wangheart.rtmpfile.utils.IOUtils;
import com.wangheart.rtmpfile.utils.LogUtils;

import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Date;

/**
 * Author : eric
 * CreateDate : 2018/1/5  14:01
 * Email : ericli_wang@163.com
 * Version : 2.0
 * Desc :
 * Modified :
 */

public class AudioRecordFFmpegActivity extends Activity {
    private AudioRecord mAudioRecord;
    private int mAudioSampleRate;
    private int mAudioChanelCount;
    private byte[] mAudioBuffer;
    private long presentationTimeUs;
    private Thread mRecordThread;
    private Thread mEncodeThread;
    private int mSampleRateType;
    private boolean isRecord = false;
    private int MAX_BUFFER_SIZE = 10240;
    int ret = 0;
    OutputStream out;
    private AudioBuffer audioBuffer;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_audio_record_ffmpeg);
    }

    public void btnStart(View view) {
        out = IOUtils.open(FileUtil.getMainDir() + "/AudioRecordFFmpegActivity.pcm", false);
        ret = FFmpegAudioHandle.getInstance().initAudio(FileUtil.getMainDir() + "/record_ffmpeg.aac");
        if (ret < 0) {
            LogUtils.e("initAudio error " + ret);
            return;
        }
        LogUtils.d("readSize " + ret);
        audioBuffer = new AudioBuffer(ret);
        initAudioDevice();
        presentationTimeUs = new Date().getTime() * 1000;
        isRecord = true;
        //开启录音
        mRecordThread = new Thread(fetchAudioRunnable());
        mEncodeThread = new Thread(new EncodeRunnable());
        mAudioRecord.startRecording();
        mEncodeThread.start();
        mRecordThread.start();
    }

    public void btnStop(View view) {
        isRecord = false;
    }

    private Runnable fetchAudioRunnable() {
        return new Runnable() {
            @Override
            public void run() {
                fetchPcmFromDevice();
            }
        };
    }

    /**
     * 初始化AudioRecord
     */
    private void initAudioDevice() {
        int[] sampleRates = {44100, 22050, 16000, 11025};
        for (int sampleRate : sampleRates) {
            //编码制式
            int audioFormat = AudioFormat.ENCODING_PCM_16BIT;
            // stereo 立体声，
            int channelConfig = AudioFormat.CHANNEL_CONFIGURATION_STEREO;
            int buffsize = AudioRecord.getMinBufferSize(sampleRate, channelConfig, audioFormat);
            mAudioRecord = new AudioRecord(MediaRecorder.AudioSource.MIC, sampleRate, channelConfig,
                    audioFormat, buffsize);
            if (mAudioRecord.getState() == AudioRecord.STATE_INITIALIZED && buffsize <= MAX_BUFFER_SIZE) {
                mAudioSampleRate = sampleRate;
                mAudioChanelCount = channelConfig == AudioFormat.CHANNEL_CONFIGURATION_STEREO ? 2 : 1;
                mAudioBuffer = new byte[buffsize];
                mSampleRateType = ADTSUtils.getSampleRateType(sampleRate);
                LogUtils.w("编码器参数:" + mAudioSampleRate + " " + mSampleRateType + " " + mAudioChanelCount + " " + buffsize);
                break;
            }
        }
    }

    /**
     * 采集音频数据
     */
    private void fetchPcmFromDevice() {
        LogUtils.w("录音线程开始");
        while (isRecord && mAudioRecord != null && !Thread.interrupted()) {
            int size = mAudioRecord.read(mAudioBuffer, 0, mAudioBuffer.length);
            if (size < 0) {
                LogUtils.w("audio ignore ,no data to read");
                break;
            }
            if (isRecord) {
                byte[] audio = new byte[size];
                System.arraycopy(mAudioBuffer, 0, audio, 0, size);
                LogUtils.v("采集到数据:" + audio.length);
//                IOUtils.write(out, audio, 0, audio.length);
                putPCMData(audio);
            }
        }
    }

    /**
     * 将PCM数据存入队列
     *
     * @param pcmChunk PCM数据块
     */
    private void putPCMData(byte[] pcmChunk) {
        try {
            audioBuffer.put(pcmChunk, 0, pcmChunk.length);
        } catch (Exception e) {
            e.printStackTrace();
            LogUtils.e("queue put error");
        }
    }

    /**
     * 在Container中队列取出PCM数据
     *
     * @return PCM数据块
     */
    private byte[] getPCMData() {
        try {
            return audioBuffer.getFrameBuf();
        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }

    public void btnEncodePcmFile(View view) {
        new Thread(new Runnable() {
            @Override
            public void run() {
//                FFmpegAudioHandle.getInstance().encodePcmFile(FileUtil.getMainDir() + "/tdjm.pcm",
                FFmpegAudioHandle.getInstance().encodePcmFile(FileUtil.getMainDir() + "/AudioRecordFFmpegActivity.pcm",
                        FileUtil.getMainDir() + "/tdjm.aac");
            }
        }).start();
    }

    public void btnTest(View view) {
        new Thread(new Runnable() {
            @Override
            public void run() {
                int count = 0;
                InputStream in = null;
                ret = FFmpegAudioHandle.getInstance().initAudio(FileUtil.getMainDir() + "/record_ffmpeg.aac");
                if (ret < 0) {
                    LogUtils.e("init audio error ");
                    return;
                }
                int len = 0;
                LogUtils.d("read size " + ret);
                audioBuffer = new AudioBuffer(ret);
                try {
                    int readSize = ret * 2;
                    byte[] buff = new byte[readSize];
                    in = new FileInputStream(new File(FileUtil.getMainDir(), "AudioRecordFFmpegActivity.pcm"));
                    LogUtils.w("avaliable " + in.available());
                    while ((len = in.read(buff)) >= ret) {
                        count++;
                        LogUtils.w("read " + len + "  " + count);
                        audioBuffer.put(buff, 0, len);
                    }
                } catch (FileNotFoundException e) {
                    e.printStackTrace();
                } catch (IOException e) {
                    e.printStackTrace();
                } finally {
                    IOUtils.close(in);
                }
            }
        }).start();
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    Thread.sleep(200);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
                while (!audioBuffer.isEmpty()) {
                    byte[] buf = audioBuffer.getFrameBuf();
                    if (buf != null) {
                        FFmpegAudioHandle.getInstance().encodeAudio(buf);
                    }
                }
                FFmpegAudioHandle.getInstance().close();
            }
        }).start();
    }

    private class EncodeRunnable implements Runnable {
        @Override
        public void run() {
            LogUtils.w("编码线程开始");
            while (isRecord || !audioBuffer.isEmpty())
                encodePCM();
            release();
        }
    }

    private void encodePCM() {
        byte[] chunkPCM = getPCMData();//获取解码器所在线程输出的数据 代码后边会贴上
        if (chunkPCM == null) {
            return;
        }
        FFmpegAudioHandle.getInstance().encodeAudio(chunkPCM);
    }

    /**
     * 释放资源
     */
    public void release() {
        if (mAudioRecord != null) {
            mAudioRecord.stop();
            mAudioRecord.release();
        }
        LogUtils.w("release");
        IOUtils.close(out);
    }
}
