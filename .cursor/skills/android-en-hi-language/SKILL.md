---
name: android-en-hi-language
description: >-
  Implements per-app English/Hindi language support for Android Jetpack Compose
  apps using AppCompat locales, string resources, smooth cream language switch
  (no black flash), login language control, and profile language sheet. Use when
  the user asks for language choice, i18n, Hindi/English, AppLanguage,
  LanguagePicker, locales_config, or to copy Emaan Wallpapers language UX into
  another app (e.g. ApniPost).
---

# Android EN/HI Per-App Language (Emaan pattern)

Implement the **same language system as Emaan Wallpapers** in the current Android project. Adapt package names to this app. Do not invent a different architecture.

## Goal

- User can pick **English** or **Hindi**
- Choice is **per-app** (survives logout; not stored in session/DataStore)
- Switch is **instant and smooth** — never a black screen
- Login has an attractive language control; Profile/settings has a language sheet
- All user-visible UI text goes through `stringResource` + `values` / `values-hi`
- Hard unit tests for locale helpers must pass

## Non-negotiable rules

1. **MainActivity must extend `AppCompatActivity`** (not ComponentActivity alone).
2. Dependency: `androidx.appcompat:appcompat:1.7.0` (or newer 1.7.x).
3. **Never** call `resources.updateConfiguration(...)` — causes black flashes.
4. **Never** override Compose `LocalContext` with `createConfigurationContext` — crashes `rememberLauncherForActivityResult` (`No ActivityResultRegistryOwner`).
5. **Never** `Activity.recreate()` for language change.
6. Language cover overlay color must be the app **light window background** (cream/off-white), **never black**.
7. Same-language tap must no-op (`shouldApplyLanguage`).
8. Only `en` and `hi` are supported; anything else normalizes to `en`.
9. Do not store language in logout-cleared prefs/DataStore — use `AppCompatDelegate.setApplicationLocales`.
10. Manifest activity: `android:configChanges="locale|layoutDirection"`.
11. Do not break existing features (payments, auth, navigation, gallery pickers).

## Implementation checklist (do in order)

### 1) Gradle

In `app/build.gradle.kts` / `build.gradle`:

```kotlin
implementation("androidx.appcompat:appcompat:1.7.0")
```

### 2) Locales config

Create `app/src/main/res/xml/locales_config.xml`:

```xml
<?xml version="1.0" encoding="utf-8"?>
<locale-config xmlns:android="http://schemas.android.com/apk/res/android">
    <locale android:name="en" />
    <locale android:name="hi" />
</locale-config>
```

In `AndroidManifest.xml` `<application>`:

```xml
android:localeConfig="@xml/locales_config"
```

On launcher `MainActivity`:

```xml
android:configChanges="locale|layoutDirection"
```

Theme must be AppCompat/Material3 light with non-black window background:

```xml
<item name="android:windowBackground">@color/window_background</item>
<item name="android:colorBackground">@color/window_background</item>
```

Add light `@color/window_background` (e.g. `#F5F8F7`). Optional: zero-duration locale activity anims so any config change never flashes black.

### 3) Core API — `i18n/AppLanguage.kt`

Package: `<app>.i18n`

```kotlin
object AppLanguage {
    const val ENGLISH = "en"
    const val HINDI = "hi"

    private var tagState by mutableStateOf(readAppliedTag())

    fun apply(tag: String) {
        val locale = normalizeLanguageTag(tag)
        if (!shouldApplyLanguage(tagState, locale)) return
        tagState = locale
        Locale.setDefault(Locale.forLanguageTag(locale))
        AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags(locale))
    }

    fun currentTag(): String = tagState
}
```

Helpers (testable):

```kotlin
internal fun normalizeLanguageTag(tag: String?): String =
    if (tag.equals(AppLanguage.HINDI, ignoreCase = true)) AppLanguage.HINDI else AppLanguage.ENGLISH

internal fun shouldApplyLanguage(current: String, requested: String): Boolean =
    normalizeLanguageTag(current) != normalizeLanguageTag(requested)
```

### 4) Smooth switch helpers

`i18n/LanguageSwitchOverlay.kt`:

- `shouldShowLanguageSwitchCover(isFirstComposition, previousTag, newTag)` — false on first composition / null previous / same language; true only on real en↔hi change
- `LANGUAGE_COVER_HOLD_MS = 48L`
- `LANGUAGE_COVER_FADE_MS = 160`

`i18n/LocalizedContext.kt`:

- `localizedContextForTag(base, tag)` via `createConfigurationContext`
- **Use only** to build `LocalConfiguration` — do **not** provide as `LocalContext`

### 5) UI — `ui/language/LanguagePicker.kt`

#### `LanguageChangeFade`

Wrap the whole app content in MainActivity:

```kotlin
setContent {
    AppTheme {
        LanguageChangeFade {
            AppNavGraph() // or root composable
        }
    }
}
```

Behavior:

- Read `AppLanguage.currentTag()`
- `CompositionLocalProvider(LocalConfiguration provides localizedConfig)` only
- Keep Activity as `LocalContext`
- Root `Box` with app background color always
- On real language change: snap cream/light cover to alpha 1 → hold → fade to 0
- Never black cover

#### `LoginLanguageButton`

- Compact EN | हिं chip on login (high z-index, visible above hero)
- Calls `AppLanguage.apply(ENGLISH|HINDI)`

#### `LanguagePickerSheet`

- Modal bottom sheet from Profile / settings
- Title + subtitle from strings
- Two rows: English / हिंदी with check on selected
- Apply then dismiss

### 6) Wire entry points

- **Login screen**: show `LoginLanguageButton` (top corner)
- **Profile / settings**: row “Language” → opens `LanguagePickerSheet`
- **MainActivity**: window background = light color drawable; `LanguageChangeFade` wraps nav

### 7) Strings

Create / update:

- `app/src/main/res/values/strings.xml` — English (default)
- `app/src/main/res/values-hi/strings.xml` — Hindi

Minimum language keys:

```xml
<string name="language_title">Language</string>
<string name="language_subtitle">Choose the language for the whole app</string>
<string name="language_english">English</string>
<string name="language_hindi">हिंदी</string>
```

Hindi file:

```xml
<string name="language_title">भाषा</string>
<string name="language_subtitle">पूरे ऐप की भाषा चुनें</string>
<string name="language_english">English</string>
<string name="language_hindi">हिंदी</string>
```

Then migrate **all user-visible hardcoded UI text** to `stringResource(R.string.*)` with matching `values-hi` entries (nav, auth, empty states, dialogs, profile, errors). Keep brand names / proper nouns as-is when appropriate.

### 8) Dynamic / server labels (optional but recommended)

If categories/content have English + Hindi from backend:

```kotlin
fun localizedLabel(english: String, hindi: String?, language: String?): String {
    return if (normalizeLanguageTag(language) == AppLanguage.HINDI) {
        hindi?.trim()?.takeIf { it.isNotEmpty() } ?: english
    } else english
}
```

Use `AppLanguage.currentTag()` when displaying.

### 9) Unit tests (must pass)

Add JVM unit tests:

1. `normalizeLanguageTag`: `hi`/`HI` → hi; `en`/`fr`/`null`/`hi-IN` → en (match Emaan: only exact `hi`)
2. `shouldApplyLanguage`: same language false; en↔hi true
3. `shouldShowLanguageSwitchCover`: first composition false; null previous false; real change true
4. Cover timings in sane ranges (hold 16–120ms, fade 80–300ms)
5. `mustKeepActivityLocalContext(true) == true` regression for Profile crash
6. `localizedLabel` en/hi/fallback

Run:

```bash
./gradlew :app:testDebugUnitTest --tests "*.i18n.*"
```

Fix until green.

## Acceptance criteria

- [ ] EN↔HI on login and profile updates UI without activity recreate
- [ ] No black screen on language change
- [ ] Logout does not reset language
- [ ] Profile gallery / any `rememberLauncherForActivityResult` still works
- [ ] Phone language outside en/hi still shows English until user picks Hindi
- [ ] Unit tests pass

## What NOT to do

- Do not use a third-party i18n library
- Do not put language in Supabase user row as the source of truth for UI locale
- Do not recreate Activity or use NavHost transitions that break ActivityResult owners
- Do not copy Emaan package names — use this app’s package
- Do not commit secrets

## Reference architecture (Emaan Wallpapers)

Proven files to mirror (adapt packages):

- `i18n/AppLanguage.kt`
- `i18n/LanguageSwitchOverlay.kt`
- `i18n/LocalizedContext.kt`
- `i18n/LocalizedLabel.kt`
- `ui/language/LanguagePicker.kt` (`LanguageChangeFade`, `LoginLanguageButton`, `LanguagePickerSheet`)
- `res/xml/locales_config.xml`
- `res/values/strings.xml` + `res/values-hi/strings.xml`
- Unit tests under `src/test/.../i18n/`

When the user says “add language like Emaan / ApniPost language”, implement this skill fully end-to-end, then run unit tests.
