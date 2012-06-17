/*
 * Created: 11.06.2012
 *
 * Copyright (C) 2012 Victor Antonovich (v.antonovich@gmail.com)
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA  02111-1307, USA.
 */
package su.comp.bk.arch.io;

import su.comp.bk.arch.Computer;
import su.comp.bk.arch.cpu.Cpu;
import su.comp.bk.arch.cpu.opcode.BaseOpcode;
import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioTrack;
import android.os.Bundle;
import android.util.Log;

/**
 * Audio output (one bit PCM, bit 6 in SEL1 register).
 */
public class AudioOutput implements Device, Runnable {

    private static final String TAG = AudioOutput.class.getName();

    // Audio output bit
    public final static int OUTPUT_BIT = (1 << 6);

    private final static int[] ADDRESSES = { Cpu.REG_SEL1 };

    private final static int OUTPUT_SAMPLE_RATE = 22050;

    private static final long NANOSECS_IN_SECOND = 1000000000L;

    private final Computer computer;

    private final AudioTrack player;

    // Audio samples buffer
    private final short[] samplesBuffer;

    private short lastSampleValue = Short.MIN_VALUE;
    private long lastSampleTimestamp;

    // PCM timestamps circular buffer, one per output state change
    private final long[] pcmTimestamps;
    // PCM timestamps circular buffer put index
    private int putPcmTimestampIndex = 0;
    // PCM timestamps circular buffer get index
    private int getPcmTimestampIndex = 0;
    // PCM timestamps circular buffer current capacity
    private int pcmTimestampsCapacity;

    private Thread audioOutputThread;

    private boolean isRunning;

    private int lastOutputState;

    public AudioOutput(Computer computer) {
        this.computer = computer;
        int minBufferSize = AudioTrack.getMinBufferSize(OUTPUT_SAMPLE_RATE,
                AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT);
        if (minBufferSize <= 0) {
            throw new IllegalStateException("Invalid minimum audio buffer size: " + minBufferSize);
        }
        player = new AudioTrack(AudioManager.STREAM_MUSIC, OUTPUT_SAMPLE_RATE,
                AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT,
                minBufferSize, AudioTrack.MODE_STREAM);
        samplesBuffer = new short[minBufferSize / 4]; // two bytes per sample
        int pcmTimestampsBufferSize = (int) (samplesBuffer.length * computer.getClockFrequency()
                * 1000L / (OUTPUT_SAMPLE_RATE * BaseOpcode.getBaseExecutionTime()));
        pcmTimestamps = new long[pcmTimestampsBufferSize];
        pcmTimestampsCapacity = pcmTimestamps.length;
        Log.d(TAG, "created audio output, player buffer size: " + minBufferSize +
                ", PCM buffer size: " + pcmTimestampsCapacity);
    }

    @Override
    public int[] getAddresses() {
        return ADDRESSES;
    }

    @Override
    public void init(long cpuTime) {
        lastSampleTimestamp = cpuTime;
    }

    public void start() {
        Log.d(TAG, "starting audio output");
        isRunning = true;
        audioOutputThread = new Thread(this, "AudioOutputThread");
        audioOutputThread.start();
    }

    public void stop() {
        Log.d(TAG, "stopping audio output");
        isRunning = false;
        player.stop();
        while (audioOutputThread.isAlive()) {
            try {
                audioOutputThread.join();
            } catch (InterruptedException e) {
            }
        }
    }

    public void pause() {
        Log.d(TAG, "pausing audio output");
        player.pause();
    }

    public void resume() {
        Log.d(TAG, "resuming audio output");
        player.play();
    }

    public void release() {
        Log.d(TAG, "releasing audio output");
        player.release();
    }

    @Override
    public void saveState(Bundle outState) {
        // Do nothing
    }

    @Override
    public void restoreState(Bundle inState) {
        // Do nothing
    }

    @Override
    public int read(long cpuTime, int address) {
        return 0;
    }

    @Override
    public void write(long cpuTime, boolean isByteMode, int address, int value) {
        int outputState = value & OUTPUT_BIT;
        if ((outputState ^ lastOutputState) != 0) {
            putPcmTimestamp(cpuTime);
        }
        lastOutputState = outputState;
    }

    private synchronized void putPcmTimestamp(long pcmTimestamp) {
        if (pcmTimestampsCapacity > 0) {
            pcmTimestamps[putPcmTimestampIndex++] = pcmTimestamp;
            putPcmTimestampIndex %= pcmTimestamps.length;
            pcmTimestampsCapacity--;
        } else {
            Log.w(TAG, "PCM buffer overflow!");
        }
    }

    private synchronized void removePcmTimestamp() {
        if (pcmTimestampsCapacity < pcmTimestamps.length) {
            getPcmTimestampIndex = ++getPcmTimestampIndex % pcmTimestamps.length;
            pcmTimestampsCapacity++;
        } else {
            Log.w(TAG, "PCM buffer underflow!");
        }
    }

    private synchronized long peekPcmTimestamp() {
        long pcmTimestamp = -1L;
        if (pcmTimestampsCapacity < pcmTimestamps.length) {
            pcmTimestamp = pcmTimestamps[getPcmTimestampIndex];
        }
        return pcmTimestamp;
    }

    @Override
    public void run() {
        Log.d(TAG, "audio output started");
        while (isRunning) {
            int sampleIndex = 0;
            long lastPcmTimestamp = lastSampleTimestamp;
            long pcmTimestamp = peekPcmTimestamp();
            while (sampleIndex < samplesBuffer.length) {
                short sampleValue = lastSampleValue;
                int numSamples = samplesBuffer.length - sampleIndex;
                if (pcmTimestamp >= 0 && pcmTimestamp < lastSampleTimestamp) {
                    removePcmTimestamp();
                    numSamples = Math.min((int) (computer.getCpuTimeNanos(pcmTimestamp -
                            lastPcmTimestamp) * OUTPUT_SAMPLE_RATE / NANOSECS_IN_SECOND),
                            numSamples);
                    lastPcmTimestamp = pcmTimestamp;
                    pcmTimestamp = peekPcmTimestamp();
                    lastSampleValue = (sampleValue > 0) ? Short.MIN_VALUE : Short.MAX_VALUE;
                }
                while (numSamples-- > 0) {
                    samplesBuffer[sampleIndex++] = sampleValue;
                }
            }
            lastSampleTimestamp += computer.getNanosCpuTime(samplesBuffer.length
                    * NANOSECS_IN_SECOND / OUTPUT_SAMPLE_RATE);
            player.write(samplesBuffer, 0, samplesBuffer.length);
        }
        Log.d(TAG, "audio output stopped");
    }

}