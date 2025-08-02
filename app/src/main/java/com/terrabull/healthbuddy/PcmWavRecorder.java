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
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

public class PcmWavRecorder {
    private static final int SAMPLE_RATE = 16000;
    private static final int CHANNEL_CONFIG = AudioFormat.CHANNEL_IN_MONO;
    private static final int AUDIO_FORMAT = AudioFormat.ENCODING_PCM_16BIT;

    private AudioRecord recorder;
    private Thread recordingThread;
    private volatile boolean isRecording = false;
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
        // stop capturing
        isRecording = false;
        recorder.stop();
        // wait for the thread to finish writing
        if (recordingThread != null) {
            try {
                recordingThread.join();
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            recordingThread = null;
        }
        // release recorder
        recorder.release();
    }

    private void writeWavFile() {
        int totalPcmLen = 0;
        byte[] buffer = new byte[4096];

        try (FileOutputStream fos = new FileOutputStream(outputWav);
             BufferedOutputStream bos = new BufferedOutputStream(fos)) {

            // 1) Write placeholder header (44 bytes)
            bos.write(new byte[44]);

            // 2) Stream PCM data
            while (isRecording) {
                int read = recorder.read(buffer, 0, buffer.length);
                if (read > 0) {
                    bos.write(buffer, 0, read);
                    totalPcmLen += read;
                }
            }
            bos.flush();

            // 3) Back-fill RIFF header with proper sizes and format
            try (RandomAccessFile raf = new RandomAccessFile(outputWav, "rw")) {
                // Prepare a proper header in little-endian
                ByteBuffer header = ByteBuffer.allocate(44)
                        .order(ByteOrder.LITTLE_ENDIAN);
                long totalDataLen = totalPcmLen + 36;
                long byteRate = SAMPLE_RATE * 2; // channels(1) * bytesPerSample(2)

                // ChunkID "RIFF"
                header.put("RIFF".getBytes());
                header.putInt((int) totalDataLen);             // ChunkSize
                header.put("WAVE".getBytes());                  // Format
                header.put("fmt ".getBytes());                  // Subchunk1ID
                header.putInt(16);                              // Subchunk1Size (PCM)
                header.putShort((short) 1);                     // AudioFormat = PCM
                header.putShort((short) 1);                     // NumChannels = mono
                header.putInt(SAMPLE_RATE);                     // SampleRate
                header.putInt((int) byteRate);                  // ByteRate
                header.putShort((short) 2);                     // BlockAlign
                header.putShort((short) 16);                    // BitsPerSample
                header.put("data".getBytes());                  // Subchunk2ID
                header.putInt(totalPcmLen);                     // Subchunk2Size

                // Write header at the file start
                raf.seek(0);
                raf.write(header.array());
            }

        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
