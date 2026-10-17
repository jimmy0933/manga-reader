![Android CI Build](https://github.com/jimmy0933/manga-reader/actions/workflows/android-build.yml/badge.svg)

# Manga Reader (漫畫閱讀與離線下載 Android App)

本專案為使用 **Kotlin + Jetpack Compose + Room + WorkManager** 開發的 Android 漫畫閱讀器，專門支援漫畫人（manhuaren.com）漫畫解析、章節離線快取下載與流暢閱讀。

---

## 核心功能特色

1. **漫畫人線上解析與搜尋**：
   - 支援直接輸入或貼上漫畫人網址（例如 `https://www.manhuaren.com/manhua-wangzhetianxia/`）解析加入書架。
   - 內建搜尋功能與內建 WebView 瀏覽器。
2. **完整章節目錄與自動同步**：
   - 自動相容單行本（卷）與單話連載目錄結構。
   - 進入漫畫詳情頁時自動背景檢查並同步最新話數。
3. **整部漫畫離線下載**：
   - 支援背景整部批次下載所有章節漫畫圖片。
   - 離線快取無網路狀態下隨時流暢閱讀。
4. **沉浸式漫畫閱讀器**：
   - **上下滑動翻頁模式**（Vertical Pager）：原生流暢垂直翻頁。
   - **螢幕點擊翻頁**：點擊上半部上一頁、點擊下半部下一頁、點擊中央呼叫/隱藏控制列。
   - **連續條漫模式**（Vertical Scroll）與**左右翻頁模式**（Horizontal Pager）。
   - 沉浸全螢幕閱讀，自動隱藏系統上下黑條。
   - 記住閱讀進度與章節位置。

---

## 專案架構

- `app/src/main/java/com/mangareader/app/data/local/`: Room Database、DAO、實體資料庫模型（`MangaEntity`, `MangaChapterEntity`）。
- `app/src/main/java/com/mangareader/app/parser/`: `ManhuarenParser`（Jsoup 漫畫人網頁與圖片資料解析）。
- `app/src/main/java/com/mangareader/app/data/repository/`: `MangaRepository` 資料管理與下載排程。
- `app/src/main/java/com/mangareader/app/ui/`:
  - `MangaShelfScreen`: 漫畫書架首頁、最新話顯示、整部下載進度條。
  - `MangaDetailScreen`: 漫畫詳情、章節目錄、自動同步最新話數。
  - `MangaReaderScreen`: 漫畫閱讀器（上下滑動翻頁、點擊區域翻頁、雙指縮放）。
  - `MangaWebBrowserScreen`: 內建瀏覽器。

---

## 如何建置與執行

1. 使用 **Android Studio** 開啟目錄：
   ```
   D:\git\manga-reader
   ```
2. 等待 Gradle Sync 完成。
3. 點擊 **Run** 或執行 `./gradlew assembleRelease` 即可編譯安裝。

## 如何下載安裝檔 (APK)

您可以透過 GitHub Actions 自動建置的 Artifact 下載最新的 APK：
1. 進入專案的 **[Actions](https://github.com/jimmy0933/manga-reader/actions)** 頁籤。
2. 點選最新的成功工作 (Workflow run)。
3. 捲動至最下方的 **Artifacts** 區塊，點選 `app-debug-...` 或 `app-release-unsigned-...` 進行下載。
4. 下載後解壓縮得到的 `.apk` 檔案即可傳至手機安裝。

**⚠️ 簽章注意事項 (Release APK)**：
目前的 Release APK 暫時使用 debug signing，不具備正式的上架簽章金鑰。請注意這並不代表應用程式具有正式簽章，僅供測試與開發環境安裝。
