# KBA Welfare Messenger — CSV Edition

This is the first cloud-build version intended to be built from a phone using GitHub Actions.

## CSV format

Required headers:

OriginalName,MobileNumber

Example:

OriginalName,MobileNumber
Amit Kumar,"9876543210,9812345678"
Raj Kumar,9999999999

Multiple numbers are split into separate records. Supported separators include comma, semicolon, slash, pipe and line breaks. Duplicate numbers are removed.

## Build from a phone

1. Create a GitHub repository.
2. Upload all files/folders from this project, including `.github/workflows/build-apk.yml`.
3. Open the repository's Actions tab.
4. Select **Build KBA Welfare Messenger APK**.
5. Tap **Run workflow**.
6. Open the completed workflow run.
7. Under Artifacts, download **KBA-Welfare-Messenger-debug**.

GitHub Actions builds the APK on its cloud runner; the phone does not need Android SDK or Gradle installed.

## App functions

CSV import, search, From/To range, personalized message placeholders, preview, test SMS, SMS now, scheduled SMS, cancel scheduled SMS, WhatsApp next-contact, reset filters/actions, and clearing locally stored contacts.

SMS scheduling is best-effort and subject to Android/device/carrier background and SMS restrictions.
