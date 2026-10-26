# 👑️ Chess for Android

> A free, open-source chess app — no ads, just chess.

## ✨ Features

|            | Details                                                                                      |
|-------------------|----------------------------------------------------------------------------------------------|
| 👑| Standard chess, Chess 960, Duck chess                                       |
| 🌐| Play online on [Lichess](https://lichess.org) and [FICS](https://freechess.org)              |
| 🧩| Lichess puzzles. Mate in two collection                                                      |
| 📶| Cast the board to your Chromecast device                                                     |
| 📖| ECO opening explorer                                                                         |
| 🗄| Local game database with search                                                              |
| ♿| Screen reader support via TalkBack                                                           |
| 🛠️| Manual board setup mode                                                                      |
| 🖥️| Supports the **Open Exchange Protocol (OEX)**, letting you plug in third-party chess engines |

---

## 🎬 Demo

[https://youtu.be/oFvqyaLGvq0](https://youtu.be/oFvqyaLGvq0)


## 📦 Install

| Store | Link |
|---|---|
| F-Droid | [https://f-droid.org/packages/jwtc.android.chess/](https://f-droid.org/packages/jwtc.android.chess/) |
| Google Play | [https://play.google.com/store/apps/details?id=jwtc.android.chess](https://play.google.com/store/apps/details?id=jwtc.android.chess) |


## 📌 Roadmap

- Address open items in the [feature request list](../../issues)
- Improve translation coverage and consistency
- Better screen reader interaction
- **Contributions welcome** — feel free to open a PR or issue


## ☕ Support

If you enjoy the app, consider buying me a coffee!

[https://paypal.me/jcaroluschess](https://paypal.me/jcaroluschess)


## Linting and formatting

Run from the repository root:

```sh
./scripts/lint.sh
./scripts/format.sh
```

`lint.sh` checks without editing sources and runs every check even if another fails:

- Android Lint defaults for FOSS and Play Store debug variants (errors fail; warnings are reported).
- Java formatting using google-java-format's AOSP style: four-space block indentation and opening braces on the same line.
- Checkstyle's `NeedBraces` rule for Java `if`, `else`, `for`, `while`, and `do` bodies, including single statements.
- Rust formatting using rustfmt defaults, plus default Clippy rules for the entire workspace including test targets. Clippy warnings are not promoted to errors.

All Java sources under `app/src`, including FICS, flavors, and tests, are checked.
Formatting and brace violations fail the command. Android reports are in
`app/build/reports/`; brace reports are in `build/reports/checkstyle/`.

`format.sh` rewrites Java and Rust formatting. It does not insert missing Java
braces; fix those reported by `checkJavaBraces` manually. Existing violations
are reported without a baseline or exclusions.

Use the same JDK and Android SDK required to build the app, plus Rust with
`rustup component add rustfmt clippy`. Initial runs need network access to
download Gradle tooling. Both scripts support `CARGO=/path/to/cargo`.

## 📄 License

MIT — see [License.md](License.md) for details.
