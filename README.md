# android-chess :: Chess game for Android


## Objectives
- User friendly
- Free
- NO ads
- Published on the Google Play store https://play.google.com/store/apps/details?id=jwtc.android.chess


## Special features
- Chess 960
- Setup board
- Play online on the Free Internet Chess Server (FICS)
- Cast the board via Chromecast


## Roadmap
- Take items from the feature-request list (GitHub issue list)
- Translation improvements (some translations are old or inconsistent).
- Add a "start from ECO opening" option to explore/choose an opening from the ECO opening database
- Contributions are welcome


### Native build:

`native/project/jni/$ <ndk>/ndk-build`

e.g. `$ANDROID_SDK_ROOT/ndk/22.1.7171670/ndk-build` (or add to PATH)

Copy the generated `native/project/libs/*` to ` app/src/main/jniLibs/`


### UCI engines
- support for UCI engines was dropped due to `security-related restrictions on W^X violations`


### License
- MIT licensed (see License.md)