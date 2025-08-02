package com.terrabull.healthbuddy;

import android.Manifest;
import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaRecorder;

import androidx.annotation.RequiresPermission;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.RandomAccessFile;

public class PcmWavRecorder {
    private static final int SAMPLE_RATE = 16000;
    private static final int CHANNEL_CONFIG = AudioFormat.CHANNEL_IN_MONO;
    private static final int AUDIO_FORMAT = AudioFormat.ENCODING_PCM_16BIT;

    private AudioRecord recorder;
    private Thread recordingThread;
    private boolean isRecording = false;
    private File outputWav;

    @RequiresPermission(Manifest.permission.RECORD_AUDIO)
    public PcmWavRecorder(File wavFile) {
        outputWav = wavFile;
        int bufSize = AudioRecord.getMinBufferSize(
                SAMPLE_RATE, CHANNEL_CONFIG, AUDIO_FORMAT);
        recorder = new AudioRecord(
                MediaRecorder.AudioSource.MIC,
                SAMPLE_RATE, CHANNEL_CONFIG, AUDIO_FORMAT, bufSize);
    }

    public void start() {
        recorder.startRecording();
        isRecording = true;
        recordingThread = new Thread(this::writeWavFile);
        recordingThread.start();
    }

    public void stop() {
        isRecording = false;
        recorder.stop();
        recorder.release();
        recordingThread = null;
    }

    private void writeWavFile() {
        try (FileOutputStream fos = new FileOutputStream(outputWav);
             BufferedOutputStream bos = new BufferedOutputStream(fos)) {
            // reserve WAV header space
            bos.write(new byte[44]);

            byte[] buffer = new byte[4096];
            int totalPcmLen = 0;
            while (isRecording) {
                int read = recorder.read(buffer, 0, buffer.length);
                if (read > 0) {
                    bos.write(buffer, 0, read);
                    totalPcmLen += read;
                }
            }

            // rewrite WAV header
            bos.flush();
            try (RandomAccessFile raf = new RandomAccessFile(outputWav, "rw")) {
                // ChunkSize = 36 + data size
                raf.seek(4);
                raf.writeInt(Integer.reverseBytes(36 + totalPcmLen));
                // Subchunk2Size = data size
                raf.seek(40);
                raf.writeInt(Integer.reverseBytes(totalPcmLen));
                // SampleRate, ByteRate, BlockAlign, BitsPerSample
                raf.seek(24);
                raf.writeInt(Integer.reverseBytes(SAMPLE_RATE));
                raf.writeInt(Integer.reverseBytes(SAMPLE_RATE * 2)); // byteRate = sr * bytesPerSample
                raf.writeShort(Short.reverseBytes((short)2));        // blockAlign = channels*bytesPerSample
                raf.writeShort(Short.reverseBytes((short)16));       // bitsPerSample
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
