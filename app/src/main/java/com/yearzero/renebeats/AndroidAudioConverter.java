//
// By adrielcafe on Github
// Modified for custom bitrate support
//

package com.yearzero.renebeats;

import android.content.Context;
import android.media.MediaPlayer;
import android.net.Uri;
import android.util.Log;

import com.github.hiteshsondhi88.libffmpeg.FFmpeg;
import com.github.hiteshsondhi88.libffmpeg.FFmpegExecuteResponseHandler;
import com.github.hiteshsondhi88.libffmpeg.FFmpegLoadBinaryResponseHandler;
import com.github.hiteshsondhi88.libffmpeg.exceptions.FFmpegCommandAlreadyRunningException;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;

import cafe.adriel.androidaudioconverter.callback.ILoadCallback;
import cafe.adriel.androidaudioconverter.model.AudioFormat;

public class AndroidAudioConverter {
    public static final String TAG = "ModdedAudioConverter";

    public interface IConvertCallback {
        void onSuccess(File var1);
        void onProgress(long current);
        void onFailure(Exception var1);
    }

    private static boolean loaded;
    private Context context;
    private File audioFile;
    private AudioFormat format;
    private short bitRate;
    private IConvertCallback callback;
    private boolean mono, fast, normalize;
    private Integer sampleRate, startMs, end;
    private float maxdB;

    private FFmpeg instance;

    private AndroidAudioConverter(Context context) {
        this.bitRate = 128;
        this.context = context;
    }

    public static boolean isLoaded() {
        return loaded;
    }

    public static void load(Context context, final ILoadCallback callback) {
        try {
            FFmpeg.getInstance(context).loadBinary(new FFmpegLoadBinaryResponseHandler() {
                public void onStart() { }

                public void onSuccess() {
                    AndroidAudioConverter.loaded = true;
                    callback.onSuccess();
                }

                public void onFailure() {
                    AndroidAudioConverter.loaded = false;
                    callback.onFailure(new Exception("Failed to loaded FFmpeg lib"));
                }

                public void onFinish() { }
            });
        } catch (Exception var3) {
            loaded = false;
            callback.onFailure(var3);
        }

    }

    public static AndroidAudioConverter with(Context context) {
        return new AndroidAudioConverter(context);
    }

    public AndroidAudioConverter setFile(File originalFile) {
        this.audioFile = originalFile;
        return this;
    }

    public AndroidAudioConverter setFormat(AudioFormat format) {
        this.format = format;
        return this;
    }

    public AndroidAudioConverter setBitrate(short bitRate) {
        if (Arrays.asList(Commons.Pref.BITRATES).contains(bitRate))
            this.bitRate = bitRate;
        else {
            Log.e(TAG, "Indicated sample rate is not supported, audio is converting with original sample rate");
            this.bitRate = 0;
        }

        return this;
    }

    public AndroidAudioConverter setSampleRate(Integer sampleRate) {
        if (Arrays.asList(8000, 11025, 16000, 22050, 32000, 44100, 48000, 88200, 96000, 76400, 192000, 352800, 384000).contains(sampleRate)) {
            this.sampleRate = sampleRate;
        } else {
            Log.e(TAG, "Indicated sample rate is not supported, audio is converting with original sample rate");
            this.sampleRate = 0;
        }

        return this;
    }

    public AndroidAudioConverter setMono(boolean mono) {
        this.mono = mono;
        return this;
    }

    public AndroidAudioConverter setFast(boolean fast) {
        this.fast = fast;
        return this;
    }

    public AndroidAudioConverter setTrim(Integer startMs, Integer endMs) {
        this.startMs = startMs;
        this.end = endMs;
        return this;
    }

    public AndroidAudioConverter setNormalize(boolean normalize) {
        this.normalize = normalize;
        return this;
    }

    public boolean killProcess() {
        return (instance == null || instance.isFFmpegCommandRunning()) || instance.killRunningProcesses();
    }

    public AndroidAudioConverter setCallback(IConvertCallback callback) {
        this.callback = callback;
        return this;
    }

    public void convert() {
        if (!isLoaded()) {
            this.callback.onFailure(new Exception("FFmpeg not loaded"));
        } else if (this.audioFile != null && this.audioFile.exists()) {
            if (!this.audioFile.canRead())
                this.callback.onFailure(new IOException("Can't read the file. Missing permission?"));
            else {
                if (instance == null) instance = FFmpeg.getInstance(context);
                if (normalize) {
                    try {
                        instance.execute(new String[]{"-i", audioFile.getAbsolutePath(), "-af", "volumedetect", "-vn", "-sn", "-dn", "-f", "null", "/dev/null"}, new FFmpegExecuteResponseHandler() {
                            @Override
                            public void onSuccess(String message) {
                                postexcecute();
                            }

                            @Override
                            public void onProgress(String message) {
                                if (!message.startsWith("[Parsed_volumedetect_0")) return;

                                try {
                                    maxdB = Float.parseFloat(message.replaceFirst("\\[Parsed_volumedetect_0 @ [0-9a-fx]+] max_volume: (-?\\d+\\.\\d+) dB", "$1"));
                                } catch (NumberFormatException ignored) {}
                            }

                            @Override
                            public void onFailure(String message) {
                                if (callback != null)
                                    callback.onFailure(new RuntimeException(message));
                            }

                            @Override
                            public void onStart() {
                            }

                            @Override
                            public void onFinish() {
                            }
                        });
                    } catch (FFmpegCommandAlreadyRunningException e) {
                        e.printStackTrace();
                    }
                } else postexcecute();
            }
        } else if (callback != null) callback.onFailure(new FileNotFoundException());
    }

    private void postexcecute() {
        final File convertedFile = getConvertedFile(this.audioFile, this.format);
        ArrayList<String> commandBuilder = new ArrayList<>(Arrays.asList("-y", "-i", this.audioFile.getPath()));

        if (fast)
            commandBuilder.addAll(Arrays.asList("-preset", "ultrafast"));

        if (sampleRate != null && sampleRate != 0)
            commandBuilder.addAll(Arrays.asList("-ar", this.sampleRate.toString()));

        if (bitRate > 0)
            commandBuilder.addAll(Arrays.asList("-ab", String.valueOf(this.bitRate)));

        if (mono) commandBuilder.addAll(Arrays.asList("-ac", "1"));

        if (!(startMs == null && end == null)) {
            commandBuilder.add("-ss");
            if (startMs == null)
                commandBuilder.addAll(Arrays.asList("0", "-to"));
            else {
                if (end != null && startMs >= end) {
                    Log.w(TAG, "start is >= end. Resetting start...");
                    startMs = 0;
                }
                commandBuilder.addAll(Arrays.asList(String.valueOf(startMs), "-to"));
            }

            int d = MediaPlayer.create(context, Uri.parse(audioFile.getAbsolutePath())).getDuration();
            if (end == null) {
                commandBuilder.add(String.valueOf(d));
            } else if (end > d) {
                commandBuilder.add(String.valueOf(d));
                Log.w(TAG, "end exceeds actual audio file duration. Clipping to real duration.");
            } else {
                commandBuilder.add(String.valueOf(end));
            }
        }

        if (normalize && maxdB < 0f) commandBuilder.addAll(Arrays.asList("-af", "volume=" + -maxdB + "dB"));
        commandBuilder.add(convertedFile.getPath());
        String[] cmd = commandBuilder.toArray(new String[0]);

        try {
            instance.execute(cmd, new FFmpegExecuteResponseHandler() {
                public void onStart() {
                }

                public void onProgress(String message) {
                    callback.onProgress(convertedFile.length());
                }

                public void onSuccess(String message) {
                    AndroidAudioConverter.this.callback.onSuccess(convertedFile);
                }

                public void onFailure(String message) {
                    Log.e(TAG, message);
                    AndroidAudioConverter.this.callback.onFailure(new IOException(message));
                }

                public void onFinish() {
                }
            });
        } catch (Exception var5) {
            if (callback != null) callback.onFailure(var5);
        }
    }

    private static File getConvertedFile(File originalFile, AudioFormat format) {
        String[] f = originalFile.getPath().split("\\.");
        String filePath = originalFile.getPath().replace(f[f.length - 1], format.getFormat());
        return new File(filePath);
    }

}

