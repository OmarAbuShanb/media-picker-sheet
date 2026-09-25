# MediaPickerSheet

[![Platform](https://img.shields.io/badge/Platform-Android-green.svg)](https://developer.android.com)
[![Min SDK](https://img.shields.io/badge/Min%20SDK-21-brightgreen.svg)](https://developer.android.com)
[![Language](https://img.shields.io/badge/Language-Kotlin-orange.svg)](https://kotlinlang.org/)
[![License](https://img.shields.io/badge/License-Apache_2.0-blue.svg)](LICENSE)

A modern, high-performance, and customizable Media Picker Bottom Sheet library for Android. Built with Kotlin, Material Design 3, Coroutines, and Coil.

---

## Demo

<!-- You can drag and drop your demo GIF or images here on GitHub -->


---

## Features

- 🖼️ **Image & Video Support**: Effortlessly display and select photos and videos from external storage.
- 📁 **Folder Categorization**: Automatically groups media into folders (e.g. All Media, Images, Videos, and specific device folders) with a smooth animated dropdown selector.
- 🎯 **Multi-Selection**: Native multi-selection support powered by `androidx.recyclerview:recyclerview-selection`.
- ⚡ **High Performance Loading**: Uses Coil image loader with memory/disk caching, optimized item cache size, and custom grid spacing.
- 🎨 **Material 3 Bottom Sheet**: Smooth bottom sheet dialog with dynamic top-corner rounding on drag/slide.
- 🛡️ **Scoped Storage & Permissions Ready**: Built-in runtime permission handling supporting Android 13+ (`READ_MEDIA_IMAGES`, `READ_MEDIA_VIDEO`) and legacy versions (`READ_EXTERNAL_STORAGE`).

---

## Tech Stack & Compatibility

- **Minimum SDK**: 21 (Android 5.0 Lollipop)
- **Compile SDK**: 36
- **Language**: Kotlin
- **UI Components**: Material Components (BottomSheetDialogFragment), RecyclerView, ViewBinding
- **Image Loader**: Coil (`2.7.0`)
- **Architecture**: Coroutines & Flow, LiveData, ViewModel

---

## Installation

Add the dependency to your app's `build.gradle.kts`:

```kotlin
dependencies {
    implementation(project(":media_picker_sheet"))
}
```

> 💡 **Note on Permissions**: All required storage permissions (`READ_MEDIA_IMAGES`, `READ_MEDIA_VIDEO`, and `READ_EXTERNAL_STORAGE`) are already declared in the library's `AndroidManifest.xml` and will be automatically merged into your app. No manual manifest configuration is needed.

---

## Usage

Showing the `MediaPickerBottomSheet` in your `Activity` or `Fragment` is simple:

```kotlin
import dev.anonymous.media_picker_library.ui.view.MediaPickerBottomSheet

// Show the Media Picker Bottom Sheet
MediaPickerBottomSheet().show(
    supportFragmentManager,
    "MediaPickerBottomSheet"
)
```

The picker automatically handles media loading and runtime permission requests on display.

---

## Sample App

Check out the `:sample` module in this repository to see a complete working example.

---

## License

This project is licensed under the Apache License 2.0 - see the [LICENSE](LICENSE) file for details.
