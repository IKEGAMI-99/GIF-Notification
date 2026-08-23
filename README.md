# GIF Notification

GIPHYからGIFを検索し、選んだGIFをAndroidの通知センターに表示するAndroid 12+向けアプリです。

## 主な機能

- GIPHY Search / Trending API
- GIFアニメーションのグリッド表示
- お気に入りと履歴
- GIFをローカルキャッシュへ保存
- `Notification.BigPictureStyle` + `Icon.createWithContentUri()` によるアニメーション画像通知
- 通知を同じIDで更新してGIFを差し替え
- Android 13+ 通知権限対応

## 使い方

1. GIPHY DevelopersでAPIキーを取得
2. アプリ起動後、APIキー欄へ入力して保存
3. GIFを検索
4. GIFをタップ
5. 「通知に表示」を押す
6. 通知シェードを展開してGIFを見る

APIキーはリポジトリには保存せず、端末内のSharedPreferencesに保存します。

## 技術仕様

- Android 12 / API 31 以上
- Kotlin (AGP 9 built-in) + Compose Compiler 2.3.21
- Jetpack Compose / Material 3
- AGP 9.3.1
- compileSdk 37 / targetSdk 36
- Gradle 9.5

Android 12以降はBigPictureStyle通知でアニメーション画像を扱えます。ただし通知カードの外枠・余白・最大サイズなどはSystem UIが決定するため、完全なフルブリード表示は端末によって異なります。

## ビルド

GitHub Actionsの `Android Debug APK` ワークフローでDebug APKを生成します。

ローカルでは:

```bash
gradle :app:assembleDebug
```
