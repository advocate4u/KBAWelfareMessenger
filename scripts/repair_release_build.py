from pathlib import Path

MAIN = Path("app/src/main/java/com/example/kbawelfaremessenger/MainActivity.kt")
VERSION = Path("app/src/main/java/com/example/kbawelfaremessenger/AppVersionTextView.kt")

text = MAIN.read_text(encoding="utf-8-sig")
text = text.replace(
    "is EditText -> view.hintTextColor = ColorStateList.valueOf(secondary)",
    "is EditText -> view.setHintTextColor(ColorStateList.valueOf(secondary))",
)

old = r'''        val actualSimPhone = normalizePhone(rawSimPhone)\n\n        // validatePhone=false explicitly permits SMS from any active SIM.\n        if (license.options.validatePhone && actualSimPhone != licensedPhone) {\n            AppLogger.warning(this, "LICENSE", "Licensed phone does not match selected SMS SIM.")\n            throw SmsLicenseException(\n                "License/SIM mismatch.\\n\\n" +\n                        "Licensed phone: $licensedPhone\\n" +\n                        "Selected SMS SIM: " + actualSimPhone.ifBlank { "Unknown" } + "\\n\\n" +\n                        "SMS sending is blocked."\n            )\n        }\n\n        val smsManager ='''

new = '''        val actualSimPhone = normalizePhone(rawSimPhone)

        // validatePhone=false explicitly permits SMS from any active SIM.
        if (license.options.validatePhone && actualSimPhone != licensedPhone) {
            AppLogger.warning(this, "LICENSE", "Licensed phone does not match selected SMS SIM.")
            throw SmsLicenseException(
                "License/SIM mismatch.\\n\\n" +
                        "Licensed phone: $licensedPhone\\n" +
                        "Selected SMS SIM: " + actualSimPhone.ifBlank { "Unknown" } + "\\n\\n" +
                        "SMS sending is blocked."
            )
        }

        val smsManager ='''

if old not in text:
    raise SystemExit("Expected malformed SMS validation block was not found")
text = text.replace(old, new, 1)
MAIN.write_text(text, encoding="utf-8")

version_text = VERSION.read_text(encoding="utf-8-sig")
version_text = version_text.replace(
    'text = "MyAdv v${BuildConfig.VERSION_NAME}"',
    'text = "MyAdv v${context.packageManager.getPackageInfo(context.packageName, 0).versionName}"',
)
VERSION.write_text(version_text, encoding="utf-8")

print("Release source repair completed successfully")
