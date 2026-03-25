package com.example.cryingbabyanalyzerapp;
/*
 * 울음 후보 감지되면 5초짜리 16kHz mono PCM16 wav 생성
 * 서버로 보낼 파일 만들기
 * */
import android.content.Context;
import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaRecorder;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;

public class WavRecorder {

    public static File recordFiveSeconds(Context context) throws Exception {
        int sampleRate = 16000;
        int channelConfig = AudioFormat.CHANNEL_IN_MONO;
        int audioFormat = AudioFormat.ENCODING_PCM_16BIT;

        int minBufferSize = AudioRecord.getMinBufferSize(sampleRate, channelConfig, audioFormat);
        AudioRecord audioRecord = new AudioRecord(
                MediaRecorder.AudioSource.MIC,
                sampleRate,
                channelConfig,
                audioFormat,
                minBufferSize * 2
        );

        byte[] buffer = new byte[minBufferSize];
        ByteArrayOutputStream pcmStream = new ByteArrayOutputStream();

        audioRecord.startRecording();
        long endAt = System.currentTimeMillis() + 5000L;

        while (System.currentTimeMillis() < endAt) {
            int read = audioRecord.read(buffer, 0, buffer.length);
            if (read > 0) {
                pcmStream.write(buffer, 0, read);
            }
        }

        audioRecord.stop();
        audioRecord.release();

        byte[] pcmData = pcmStream.toByteArray();
        File wavFile = new File(context.getCacheDir(), "cry_" + System.currentTimeMillis() + ".wav");
        writeWavFile(wavFile, pcmData, sampleRate, 1, 16);
        return wavFile;
    }

    private static void writeWavFile(File file, byte[] pcmData, int sampleRate,
                                     int channels, int bitsPerSample) throws IOException {
        try (FileOutputStream fos = new FileOutputStream(file)) {
            int byteRate = sampleRate * channels * bitsPerSample / 8;
            int totalDataLen = pcmData.length + 36;

            fos.write(new byte[] {'R','I','F','F'});
            writeInt(fos, totalDataLen);
            fos.write(new byte[] {'W','A','V','E'});
            fos.write(new byte[] {'f','m','t',' '});
            writeInt(fos, 16);
            writeShort(fos, (short) 1);
            writeShort(fos, (short) channels);
            writeInt(fos, sampleRate);
            writeInt(fos, byteRate);
            writeShort(fos, (short) (channels * bitsPerSample / 8));
            writeShort(fos, (short) bitsPerSample);
            fos.write(new byte[] {'d','a','t','a'});
            writeInt(fos, pcmData.length);
            fos.write(pcmData);
        }
    }

    private static void writeInt(FileOutputStream fos, int value) throws IOException {
        fos.write(value & 0xff);
        fos.write((value >> 8) & 0xff);
        fos.write((value >> 16) & 0xff);
        fos.write((value >> 24) & 0xff);
    }

    private static void writeShort(FileOutputStream fos, short value) throws IOException {
        fos.write(value & 0xff);
        fos.write((value >> 8) & 0xff);
    }
}
