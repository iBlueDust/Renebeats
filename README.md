<div align="center">
	<img src="docs/images/icon_round.svg" width="144" height="144"></img>
	
# Renebeats

</div>

Renebeats is an app that simplifies your process of stockpiling songs as offline-ready files.
Directly search, download, and process audio from YT without hassle.

**Features:**

- Search YT and download directly in the app
- Conversion to many possible formats
- Works while the app is in the background
- A download and conversion queue to let you download many songs at once
- Automatic metadata retrieval and parsing (e.g. title, author, year)
- Metadata injection (for supported files, including MP3)

## Background

This Android Native app begin development in early 2018 as a side project to learn more about the
Android Native development process while in middle school. Though the app worked well for years
after, I have refreshed it to work with the breaking changes of the newer Android updates and
refactored some code.

The `revival` branch holds the refactored code whereas the `master` branch holds the original code
up to `v1.0.3` in 2019.

## Releases

See [Releases](https://github.com/iBlueDust/Renebeats/releases)

## Development

How to start developing this repository:

1.  `git clone https://github.com/iBlueDust/Renebeats`

2.  Install Android Studio
    _ The `revival` branch has been tested to work in
    [Android Studio 2022.1.1 Electric Eel Patch 1](https://developer.android.com/studio/archive).
    _ The `master` branch has been tested to work in
    [Android Studio 3.6.1](https://developer.android.com/studio/archive).

3.  Install the [Lombok](https://plugins.jetbrains.com/plugin/6317-lombok) plugin.

4.  Get a [YT API key](https://blog.hubspot.com/website/how-to-get-youtube-api-key) and place it in
    a new file called `secret.properties` in the project root folder in the following format:

        ```properties
        YouTubeAPIKey_Debug="Insert your key here"
        YouTubeAPIKey_Release="Insert your key here"
        ```

5.  Start experimenting!

## License

This app uses the [`FFmpegKit`](https://github.com/arthenica/ffmpeg-kit) library that is licensed under `LGPL v3.0`.
Hence, Renebeats is also under the `LGPL v3.0` license.
