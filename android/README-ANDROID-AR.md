# تطبيق «تواصل» — مشروع Android أصلي حقيقي (Kotlin + Jetpack Compose + Firebase)

هذا المجلد هو تطبيق Android أصلي كامل (Native Android — وليس غلاف HTML/WebView) مبني بـ **Kotlin** و**Jetpack Compose (Material 3)** ومربوط مباشرة بنفس مشروع وقاعدة بيانات Firebase الخاصة بالتطبيق:
- **Firebase Project ID:** `stalwart-overview-sklwk`
- **Firestore Database ID:** `ai-studio-f0a6dc55-c580-4b93-91e9-381782a539f0`
- **Package Name:** `com.tawasul.app`
- **Google OAuth Web Client ID:** `743734969863-bck4iloasoa4bu33e40hm292ckhje64b.apps.googleusercontent.com`

## خطوات استخراج ملف APK الحقيقي

### الطريقة 1: البناء السحابي عبر GitHub Actions (لا تحتاج إلى كمبيوتر أو Android Studio)
1. فك ضغط الحزمة `tawasul-android-project.zip` وارفع محتويات مجلد `android/` إلى مستودع على GitHub.
2. انتقل إلى تبويب **Actions** وشغّل سير العمل `Build Tawasul Android APK` (تم تضمينه في `.github/workflows/build-apk.yml`).
3. بعد انتهاء البناء (حوالي 3 دقائق)، نزّل الملف الناتج `tawasul-app-debug-apk` الذي يحتوي على **`app-debug.apk`**.
4. *(لتفعيل Google Sign-In في الـ APK)*: من Firebase Console للمشروع `stalwart-overview-sklwk` أضف تطبيق Android بمعرف الحزمة `com.tawasul.app` وبصمة SHA-1، ثم نزّل `google-services.json` وأضفه في `app/google-services.json` أو في Secret باسم `GOOGLE_SERVICES_JSON` داخل GitHub.

### الطريقة 2: عبر Android Studio
1. افتح مجلد `android/` في Android Studio (Ladybug أو أحدث) مع JDK 17.
2. ضع ملف `google-services.json` في `android/app/google-services.json`.
3. اختر **Build > Build Bundle(s) / APK(s) > Build APK(s)**.
