//
// By adrielcafe on Github
// Modified for custom bitrate support
//

package com.yearzero.renebeats;

import android.content.Context;
import android.media.MediaMetadataRetriever;
import android.net.Uri;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.arthenica.ffmpegkit.FFmpegKit;
import com.arthenica.ffmpegkit.FFmpegSessionCompleteCallback;
import com.arthenica.ffmpegkit.SessionState;
import com.yearzero.renebeats.preferences.enums.ProcessSpeed;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;

import lombok.Setter;
import lombok.experimental.Accessors;

//TODO: Add mp4 support (and video support actually)

@Accessors(chain = true)
public class AndroidAudioConverter {
	public static final String TAG = "AndroidAudioConverter";

	public interface IConvertCallback {
		void onSuccess(File var1);
		void onProgress(long size, int progress, int total);
		void onFailure(Exception var1);
	}

	private final Context context;
	private File audioFile;
	@Setter
	private AudioFormat format;
	private short bitRate;
	@Setter
	private IConvertCallback callback;
	@Setter
	private boolean mono, normalize;
	@Setter
	private Integer sampleRate;
	private Integer startMs, endMs;
	private float maxdB;
	@Setter
	private ProcessSpeed speed;
	private int time, total;

	public AndroidAudioConverter(Context context) {
		this.bitRate = 128;
		this.context = context;
	}

	public AndroidAudioConverter setFile(File originalFile) {
		this.audioFile = originalFile;
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

	public AndroidAudioConverter setTrim(Integer startMs, Integer endMs) {
		this.startMs = startMs;
		this.endMs = endMs;
		return this;
	}

	public void killProcess() {
		FFmpegKit.cancel();
	}

	@Nullable
	public File convert() {
		if (this.audioFile == null || !this.audioFile.exists()) {
			if (callback != null) callback.onFailure(new FileNotFoundException());
			return null;
		}

		if (!this.audioFile.canRead()) {
			this.callback.onFailure(new IOException("Can't read the file. Missing permission?"));
			return null;
		}

		Integer duration = fetchAudioDuration(Uri.fromFile(audioFile));
		if (duration == null)
			Log.e(TAG, "Could not retrieve track duration. Process will still continue without progress reports.");
		else
			total = duration;

		final File convertedFile = getConvertedFile(this.audioFile, this.format);

		if (!normalize) {
			postExecute(convertedFile);
			return convertedFile;
		}

		callback.onProgress(0L, 0, 0);

		fetchMaxAmplitude(audioFile, session -> { postExecute(audioFile); });
		return convertedFile;
	}

	private void fetchMaxAmplitude(File audioFile, @NonNull FFmpegSessionCompleteCallback callback) {
		String args = String.format(
				Commons.getLocale(),
				"-i \"%s\" -af volumedetect -vn -sn -f null /dev/null",
				audioFile.getAbsolutePath()
		);

		FFmpegKit.executeAsync(args, session -> {
			String message = session.getAllLogsAsString();

			if (session.getState() == SessionState.FAILED && this.callback != null) {
				this.callback.onFailure(new RuntimeException(message));
				return;
			}

			try {
				maxdB = Float.parseFloat(
						message.replaceFirst(
								"\\[Parsed_volumedetect_0 @ 0x[\\da-fA-F]+] max_volume: (-?[\\d.]+) dB",
								"$1"
						)
				);
			} catch (NumberFormatException ignored) {}

			callback.apply(session);
		});
	}

	private String swapFilenameExtension(String original, String newExtension) {
		String name = original.replaceFirst("\\.[A-Za-z\\d]+$", "");
		return name + '.' + newExtension;
	}

	private void postExecute(File source) {
		String extension = format.name().toLowerCase();
		File destination = new File(swapFilenameExtension(source.getAbsolutePath(), extension));
		String command = generateFFmpegCommand(source, destination);

		SimpleDateFormat sdf = new SimpleDateFormat("HH:mm:ss.SSS", Locale.ENGLISH);
		sdf.setTimeZone(TimeZone.getTimeZone("UTC"));

		FFmpegKit.executeAsync(command, session -> {
			if (session.getState() == SessionState.FAILED) {
				String message = session.getAllLogsAsString();
				Log.e(TAG, message);
				callback.onFailure(new IOException(message));
				return;
			}

			callback.onSuccess(destination);
		}, log -> {
			String message = log.getMessage();

			if (!message.startsWith("size="))
				return;

			Date date;
			try {
				date = sdf.parse(message.replaceFirst(".*?time=([0-9:.]+).*", "$1") + '0');
				time = (int) date.getTime();
			} catch (ParseException | NullPointerException e) {
				Log.w(TAG, "onProgress ParseException 'time='");
			}
			callback.onProgress(source.length(), time, total);
		}, statistics -> {});
	}

	@NonNull
	private String generateFFmpegCommand(File source, File destination) {
		StringBuilder commandBuilder = new StringBuilder(
				String.format("-y -i \"%s\" ", source.getPath())
		);

		if (speed != null) {
			commandBuilder.append("-preset ");
			commandBuilder.append(speed.getValue());
			commandBuilder.append(' ');
		}

		if (sampleRate != null && sampleRate != 0) {
			commandBuilder.append("-ar ");
			commandBuilder.append(this.sampleRate.toString());
			commandBuilder.append(' ');
		}

		if (bitRate > 0) {
			commandBuilder.append("-ab ");
			commandBuilder.append(this.bitRate);
			commandBuilder.append(' ');
		}

		if (mono) commandBuilder.append("-ac 1 ");

		if (startMs != null || endMs != null) {
			commandBuilder.append("-ss ");
			commandBuilder.append(startMs == null ? 0 : startMs);
			commandBuilder.append(' ');

			if (endMs != null) {
				commandBuilder.append("-to ");
				commandBuilder.append(endMs);
				commandBuilder.append(' ');
			}
		}

		if (normalize && maxdB < 0f) {
			commandBuilder.append("-af volume=");
			commandBuilder.append(-maxdB);
			commandBuilder.append("dB ");
		}

		commandBuilder.append('"');
		commandBuilder.append(destination.getPath());
		commandBuilder.append('"');
		return commandBuilder.toString();
	}

	private static File getConvertedFile(File originalFile, AudioFormat format) {
		String[] f = originalFile.getPath().split("\\.");
		String filePath = originalFile.getPath().replace(f[f.length - 1], format.toString());
		return new File(filePath);
	}

	private Integer fetchAudioDuration(Uri file) {
		try (MediaMetadataRetriever mmr = new MediaMetadataRetriever()) {
			mmr.setDataSource(context, file);
			String durationStr = mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION);
			return Integer.parseInt(durationStr);
		} catch (NumberFormatException | IOException e) {
			return null;
		}
	}

}

