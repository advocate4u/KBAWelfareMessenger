from pathlib import Path

MAIN = Path("app/src/main/java/com/example/kbawelfaremessenger/MainActivity.kt")
VERSION = Path("app/src/main/java/com/example/kbawelfaremessenger/AppVersionTextView.kt")

text = MAIN.read_text(encoding="utf-8-sig")
text = text.replace(
    "is EditText -> view.hintTextColor = ColorStateList.valueOf(secondary)",
    "is EditText -> view.setHintTextColor(ColorStateList.valueOf(secondary))",
)

# The release build previously contained a malformed escaped validation block.
# Normalize that block and enforce BOTH licensed SIM numbers.
start = text.find("        val actualSimPhone = normalizePhone(rawSimPhone)")
end_marker = "        val smsManager ="
if start < 0:
    raise SystemExit("Could not locate SMS phone validation start")
end = text.find(end_marker, start)
if end < 0:
    raise SystemExit("Could not locate SMS manager after phone validation")

replacement = '''        val actualSimPhone = normalizePhone(rawSimPhone)

        val licensedPhones = listOfNotNull(
            license.phone,
            license.secondaryPhone
        ).map(::normalizePhone).filter { it.isNotBlank() }

        // validatePhone=false explicitly permits SMS from any active SIM.
        // When validation is enabled, either licensed SIM may be selected.
        if (license.options.validatePhone && actualSimPhone !in licensedPhones) {
            AppLogger.warning(this, "LICENSE", "Selected SMS SIM is not licensed.")
            throw SmsLicenseException(
                "License/SIM mismatch.\\n\\n" +
                        "Licensed phones: " + licensedPhones.joinToString(", ").ifBlank { "Unknown" } + "\\n" +
                        "Selected SMS SIM: " + actualSimPhone.ifBlank { "Unknown" } + "\\n\\n" +
                        "SMS sending is blocked."
            )
        }

'''
text = text[:start] + replacement + text[end:]
MAIN.write_text(text, encoding="utf-8")

version_text = VERSION.read_text(encoding="utf-8-sig")
version_text = version_text.replace(
    'text = "MyAdv v${BuildConfig.VERSION_NAME}"',
    'text = "MyAdv v${context.packageManager.getPackageInfo(context.packageName, 0).versionName}"',
)
VERSION.write_text(version_text, encoding="utf-8")

print("Release source repair completed successfully; multi-SIM license validation enforced")
