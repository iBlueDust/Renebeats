//
// By adrielcafe on Github
// Modified for custom bitrate support
//

package com.yearzero.renebeats;

import android.content.Context;
import android.media.MediaMetadataRetriever;
import android.media.MediaPlayer;
import android.net.Uri;
import android.util.Log;

import androidx.annotation.Nullable;

import com.github.hiteshsondhi88.libffmpeg.FFmpeg;
import com.github.hiteshsondhi88.libffmpeg.FFmpegExecuteResponseHandler;
import com.github.hiteshsondhi88.libffmpeg.FFmpegLoadBinaryResponseHandler;
import com.github.hiteshsondhi88.libffmpeg.exceptions.FFmpegCommandAlreadyRunningException;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;

import cafe.adriel.androidaudioconverter.callback.ILoadCallback;
import cafe.adriel.androidaudioconverter.model.AudioFormat;

//TODO: Add mp4 support (and video support actually)

public class AndroidAudioConverter {
    public static final String TAG = "AndroidAudioConverter";

    public interface IConvertCallback {
        void onSuccess(File var1);
        void onProgress(long size, int progress, int total);
        void onFailure(Exception var1);
    }

    private static boolean loaded;
    private Context context;
    private File audioFile;
    private AudioFormat format;
    private short bitRate;
    private IConvertCallback callback;
    private boolean mono, normalize;
    private Integer sampleRate, startMs, end;
    private float maxdB;
    private ProcessSpeed speed;
    private int time, total;

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

//    public AndroidAudioConverter setProgressEnable(boolean progress) {
//        this.progress = progress;
//        return this;
//    }

    public AndroidAudioConverter setFormat(AudioFormat format) {
        this.format = format;
        return this;
    }

    public AndroidAudioConverter setBitrate(short bitRate) {
        if (bitRate > 0) this.bitRate = bitRate;
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

    public AndroidAudioConverter setProcessSpeed(ProcessSpeed speed) {
        this.speed = speed;
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
        boolean b = instance == null || (instance.isFFmpegCommandRunning() && instance.killRunningProcesses());
        instance = null;
        return b;
    }

    public AndroidAudioConverter setCallback(IConvertCallback callback) {
        this.callback = callback;
        return this;
    }

    @Nullable
    public File convert() {
        if (!isLoaded()) {
            this.callback.onFailure(new Exception("FFmpeg not loaded"));
        } else if (this.audioFile != null && this.audioFile.exists()) {
            if (!this.audioFile.canRead())
                this.callback.onFailure(new IOException("Can't read the file. Missing permission?"));
            else {
                if (instance == null) instance = FFmpeg.getInstance(context);

                //region get audio file length
                MediaMetadataRetriever mmr = new MediaMetadataRetriever();
                mmr.setDataSource(context, Uri.fromFile(audioFile));
                String durationStr = mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION);
                try {
                    total = Integer.parseInt(durationStr);
                } catch (NumberFormatException e) {
                    Log.e(TAG, "NumberFormatException when retrieving length of audio file. Process will still continue but progress tracking will not work.");
                }
                //endregion
                final File convertedFile = getConvertedFile(this.audioFile, this.format);

                if (normalize) {
                    callback.onProgress(0L, 0, 0);
                    try {
                        instance.execute(new String[]{"-i", audioFile.getAbsolutePath(), "-af", "volumedetect", "-vn", "-sn", "-dn", "-f", "null", "/dev/null"}, new FFmpegExecuteResponseHandler() {
                            @Override
                            public void onSuccess(String message) {
                                postExcecute(convertedFile);
                            }

                            @Override
                            public void onProgress(String message) {
                                /*if (progress && message.startsWith("size=N/A")) {
                                    SimpleDateFormat sdf = new SimpleDateFormat("HH:mm:ss.SSS", Locale.ENGLISH);
                                    sdf.setTimeZone(TimeZone.getTimeZone("UTC"));
                                    Date date;
                                    try {
                                        date = sdf.parse(message.replaceFirst(".*?time=([0-9:.]+).*", "$1") + '0');
                                        total = (int) date.getTime();
                                    } catch (ParseException e) {
                                        Log.w(TAG, "Normalize size search ParseException 'time='");
                                    }
                                } else */if (message.startsWith("[Parsed")) {
                                    try {
                                        maxdB = Float.parseFloat(message.replaceFirst("\\[Parsed_volumedetect_0 @ [0-9a-fx]+] max_volume: (-?\\d+\\.\\d+) dB", "$1"));
                                    } catch (NumberFormatException ignored) {}
                                }
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
                } else postExcecute(convertedFile);
                return convertedFile;
            }
        } else if (callback != null) callback.onFailure(new FileNotFoundException());
        return null;
    }

    private void postExcecute(File convertedFile) {
        ArrayList<String> commandBuilder = new ArrayList<>(Arrays.asList("-y", "-i", this.audioFile.getPath()));

        if (speed != null)
            commandBuilder.addAll(Arrays.asList("-preset", speed.getValue()));

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
            if (instance != null) instance.execute(cmd, new FFmpegExecuteResponseHandler() {
                public void onStart() {
                }

                public void onProgress(String message) {
                    if (!message.startsWith("size=")) return;
                    SimpleDateFormat sdf = new SimpleDateFormat("HH:mm:ss.SSS", Locale.ENGLISH);
                    sdf.setTimeZone(TimeZone.getTimeZone("UTC"));
                    Date date;
                    try {
                        date = sdf.parse(message.replaceFirst(".*?time=([0-9:.]+).*", "$1") + '0');
                        time = (int) date.getTime();
                    } catch (ParseException e) {
                        Log.w(TAG, "onProgress ParseException 'time='");
                    }
                    callback.onProgress(convertedFile.length(), time, total);
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

