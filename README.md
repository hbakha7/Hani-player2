# 103 IPTV Player

Android project for playback of streams/playlists you are authorized to access.

## Included
- M3U/M3U8 URL import and basic EXTINF/group-title parsing.
- Xtream Codes live-stream login (`player_api.php` + live stream URL format).
- Video playback with AndroidX Media3 ExoPlayer.
- Basic group filtering.
- FG Code information: FG is proprietary; integration needs the provider's documented API/protocol.

## Build the APK from an Android phone using GitHub
1. Create/sign in to GitHub and create a repository named `103-iptv-player`.
2. Extract this ZIP and upload the *contents* to the repository root (including the hidden `.github` folder).
3. Open the repository's **Actions** tab.
4. Select **Build 103 IPTV Player APK** and tap **Run workflow**. A push to `main` also starts a build.
5. Open the completed run, scroll to **Artifacts**, and download `103-iptv-player-apk`.
6. Extract the downloaded artifact ZIP and install `app-debug.apk`. Android may ask permission to install unknown apps.

## Notes
- This is a debug APK, not a Play Store release build.
- Some providers require custom headers/tokens or block third-party apps.
- Never share your Xtream credentials. Use only services and streams you are authorized to access.
