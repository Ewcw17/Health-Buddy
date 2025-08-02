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
    private static final int SAMPLE_RATE     = 16_000;
    private static final int CHANNEL_CONFIG  = AudioFormat.CHANNEL_IN_MONO;
    private static final int AUDIO_FORMAT    = AudioFormat.ENCODING_PCM_16BIT;

    private AudioRecord recorder;
    private Thread recordingThread;
    private volatile boolean isRecording = false;
    private final File outputWav;

    @RequiresPermission(Manifest.permission.RECORD_AUDIO)
    public PcmWavRecorder(File wavFile) {
        this.outputWav = wavFile;
        initRecorder();              // initial construction
    }

    /** Create a fresh AudioRecord instance with a valid native handle. */
    @RequiresPermission(Manifest.permission.RECORD_AUDIO)
    private void initRecorder() {
        int bufSize = AudioRecord.getMinBufferSize(
                SAMPLE_RATE, CHANNEL_CONFIG, AUDIO_FORMAT);

        recorder = new AudioRecord(
                MediaRecorder.AudioSource.MIC,
                SAMPLE_RATE, CHANNEL_CONFIG, AUDIO_FORMAT, bufSize);
    }

    /** Start a new recording session. Can be invoked repeatedly after stop(). */
    @RequiresPermission(Manifest.permission.RECORD_AUDIO)
    public void start() {
        // Re‑create if released or never initialised
        if (recorder == null ||
                recorder.getState() == AudioRecord.STATE_UNINITIALIZED) {
            initRecorder();
        }

        recorder.startRecording();
        isRecording = true;
        recordingThread = new Thread(this::writeWavFile, "pcm-writer");
        recordingThread.start();
    }

    /** Stop the current recording and release native resources. */
    public void stop() {
        if (!isRecording) return;

        isRecording = false;
        recorder.stop();

        if (recordingThread != null) {
            try {
                recordingThread.join();
            } catch (InterruptedException ignored) {}
            recordingThread = null;
        }

        recorder.release();   // frees the native AudioRecord object
        recorder = null;      // mark pointer as invalid for next start()
    }

    // ───────────────────────── Internal helpers ─────────────────────────
    private void writeWavFile() {
        int totalPcmLen = 0;
        byte[] buffer   = new byte[4096];

        try (FileOutputStream fos = new FileOutputStream(outputWav);
             BufferedOutputStream bos = new BufferedOutputStream(fos)) {

            // 1) Write placeholder 44‑byte RIFF header
            bos.write(new byte[44]);

            // 2) Stream PCM while recording flag is true
            while (isRecording) {
                int read = recorder.read(buffer, 0, buffer.length);
                if (read > 0) {
                    bos.write(buffer, 0, read);
                    totalPcmLen += read;
                }
            }
            bos.flush();

            // 3) Back‑fill RIFF header with actual sizes
            try (RandomAccessFile raf = new RandomAccessFile(outputWav, "rw")) {
                ByteBuffer header = ByteBuffer.allocate(44)
                        .order(ByteOrder.LITTLE_ENDIAN);

                long totalDataLen = totalPcmLen + 36;
                long byteRate     = SAMPLE_RATE * 2; // mono * 16‑bit

                header.put("RIFF".getBytes());
                header.putInt((int) totalDataLen);
                header.put("WAVE".getBytes());
                header.put("fmt ".getBytes());
                header.putInt(16);              // PCM header size
                header.putShort((short) 1);     // PCM format
                header.putShort((short) 1);     // mono
                header.putInt(SAMPLE_RATE);
                header.putInt((int) byteRate);
                header.putShort((short) 2);     // BlockAlign
                header.putShort((short) 16);    // BitsPerSample
                header.put("data".getBytes());
                header.putInt(totalPcmLen);

                raf.seek(0);
                raf.write(header.array());
            }

        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
