package com.yearzero.renebeats.download;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.yearzero.renebeats.BuildConfig;
import com.yearzero.renebeats.preferences.Preferences;

import java.io.Serializable;
import java.util.Date;

import javax.annotation.ParametersAreNullableByDefault;

@ParametersAreNullableByDefault
public class HistoryLog implements Serializable {
	// Appcode (EA50) - "Class" (ClA5) - Class ID (415C_1066)
	private static long serialVersionUID = 0xEA50_C1A5_415C_1066L;

	//TODO: Merge with Download and use custom Serializer or Annotations?

	private String version;
	private int versionCode = 0;
	private boolean convert = false;
	private boolean normalize = false;
	private boolean overwrite = false;
	private short bitrate = 0;
	private int track = 0;
	private int year = 0;
	private int downloadId = 0;
	private long id = 0;
	private Integer start;
	private Integer end;
	private String album;
	private String artist;
	private String availableFormat;
	private String conv;
	private String down;
	private String format;
	private String mtdt;
	private String title;
	private String url;
	private String youtubeID;
	private String genres;
	private Date assigned;
	private Date completed;
	private String status_download;
	private String status_convert;
	private Boolean status_meta = null;
	private boolean invalid = false;
	private Exception exception;

	@Nullable
	public String getFilename(String sym_separator) {
		if (artist != null && title != null)
			return Preferences.getArtist_first() ?
					artist + ' ' + sym_separator + ' ' + title :
					title + ' ' + sym_separator + ' ' + artist;
		else if (artist != null) return artist;
		else if (title != null) return title;
		else return null;
	}

	public Date getDate() {
		return assigned;
	}

	public Status getStatus() {
		return new Status(Status.Download.fromValue(status_download), Status.Convert.fromValue(status_convert), status_meta);
	}

	public Download uncast() {
		Download d = new Download(new Query(youtubeID, title, artist, album, year, track, genres),
				bitrate,
				format == null ? Preferences.getFormat() : format,
				url,
				this.start,
				end,
				normalize,
				0L
		);
		d.setConvert(convert);
		d.setOverwrite(overwrite);

		d.setDownloadId(downloadId);
		d.setId(id);

		d.setAvailableFormat(availableFormat);
		d.setConv(conv);
		d.setDown(down);
		d.setMtdt(mtdt);

		d.setAssigned(assigned);
		d.setCompleteDate(completed);

		d.setStatus(getStatus());
		d.setException(exception);

		return d;
	}

	public static HistoryLog generate(@NonNull Download data) {
		HistoryLog log = cast(data);
		log.version = BuildConfig.VERSION_NAME;
		log.versionCode = BuildConfig.VERSION_CODE;
		return log;
	}

	private static HistoryLog cast(@NonNull Download data) {
		HistoryLog log = new HistoryLog();

		log.convert = data.isConvert();
		log.normalize = data.isNormalize();
		log.overwrite = data.getOverwrite();

		log.bitrate = data.getBitrate();

		log.track = data.getTrack();
		log.year = data.getYear();

		log.downloadId = data.getDownloadId();
		log.id = data.getId();

		log.start = data.getStart();
		log.end = data.getEnd();

		log.album = data.getAlbum();
		log.artist = data.getArtist();
		log.availableFormat = data.getAvailableFormat();
		log.conv = data.getConv();
		log.down = data.getDown();
		log.format = data.getFormat();
		log.mtdt = data.getMtdt();
		log.title = data.getTitle();
		log.url = data.getUrl();
		log.youtubeID = data.getYoutubeID();

		log.genres = data.getGenres();

		log.assigned = data.getAssigned();
		log.completed = data.getCompleteDate();

		log.status_download = data.getStatus().getDownload() == null ? null : data.getStatus().getDownload().getValue();
		log.status_convert = data.getStatus().getConvert() == null ? null : data.getStatus().getConvert().getValue();
		log.status_meta = data.getStatus().getMetadata();
		log.invalid = data.getStatus().isInvalid();

		log.exception = data.getException();

		return log;
	}

	@Override
	public boolean equals(@Nullable Object o) {
		if (this == o) return true;
		if (o instanceof Download) return ((Download) o).getId() == id;
		else if (o instanceof HistoryLog) return ((HistoryLog) o).id == id;
		else return false;
	}

	public String getVersion() {return this.version;}

	public int getVersionCode() {return this.versionCode;}

	public boolean isConvert() {return this.convert;}

	public boolean isNormalize() {return this.normalize;}

	public boolean isOverwrite() {return this.overwrite;}

	public short getBitrate() {return this.bitrate;}

	public int getTrack() {return this.track;}

	public int getYear() {return this.year;}

	public int getDownloadId() {return this.downloadId;}

	public long getId() {return this.id;}

	public Integer getStart() {return this.start;}

	public Integer getEnd() {return this.end;}

	public String getAlbum() {return this.album;}

	public String getArtist() {return this.artist;}

	public String getAvailableFormat() {return this.availableFormat;}

	public String getConv() {return this.conv;}

	public String getDown() {return this.down;}

	public String getFormat() {return this.format;}

	public String getMtdt() {return this.mtdt;}

	public String getTitle() {return this.title;}

	public String getUrl() {return this.url;}

	public String getYoutubeID() {return this.youtubeID;}

	public String getGenres() {return this.genres;}

	public Date getAssigned() {return this.assigned;}

	public Date getCompleted() {return this.completed;}

	public String getStatus_download() {return this.status_download;}

	public String getStatus_convert() {return this.status_convert;}

	public Boolean getStatus_meta() {return this.status_meta;}

	public boolean isInvalid() {return this.invalid;}

	public Exception getException() {return this.exception;}

	void setVersion(String version) {this.version = version; }

	void setVersionCode(int versionCode) {this.versionCode = versionCode; }

	void setConvert(boolean convert) {this.convert = convert; }

	void setNormalize(boolean normalize) {this.normalize = normalize; }

	void setOverwrite(boolean overwrite) {this.overwrite = overwrite; }

	void setBitrate(short bitrate) {this.bitrate = bitrate; }

	void setTrack(int track) {this.track = track; }

	void setYear(int year) {this.year = year; }

	void setDownloadId(int downloadId) {this.downloadId = downloadId; }

	void setId(long id) {this.id = id; }

	void setStart(Integer start) {this.start = start; }

	void setEnd(Integer end) {this.end = end; }

	void setAlbum(String album) {this.album = album; }

	void setArtist(String artist) {this.artist = artist; }

	void setAvailableFormat(String availableFormat) {this.availableFormat = availableFormat; }

	void setConv(String conv) {this.conv = conv; }

	void setDown(String down) {this.down = down; }

	void setFormat(String format) {this.format = format; }

	void setMtdt(String mtdt) {this.mtdt = mtdt; }

	void setTitle(String title) {this.title = title; }

	void setUrl(String url) {this.url = url; }

	void setYoutubeID(String youtubeID) {this.youtubeID = youtubeID; }

	void setGenres(String genres) {this.genres = genres; }

	void setAssigned(Date assigned) {this.assigned = assigned; }

	void setCompleted(Date completed) {this.completed = completed; }

	void setStatus_download(String status_download) {this.status_download = status_download; }

	void setStatus_convert(String status_convert) {this.status_convert = status_convert; }

	void setStatus_meta(Boolean status_meta) {this.status_meta = status_meta; }

	void setInvalid(boolean invalid) {this.invalid = invalid; }

	void setException(Exception exception) {this.exception = exception; }
}