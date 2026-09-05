from pathlib import Path
import re

MAIN = Path("app/src/main/java/com/example/kbawelfaremessenger/MainActivity.kt")
VERSION = Path("app/src/main/java/com/example/kbawelfaremessenger/AppVersionTextView.kt")

text = MAIN.read_text(encoding="utf-8-sig")
text = text.replace(
    "is EditText -> view.hintTextColor = ColorStateList.valueOf(secondary)",
    "is EditText -> view.setHintTextColor(ColorStateList.valueOf(secondary))",
)

# Enforce multi-SIM license validation in the release source. A license may
# authorize both the primary phone and an optional secondary phone.
pattern = re.compile(
    r'(?P<indent>\\s*)if \(license\\.options\\.validatePhone && actualSimPhone != licensedPhone\) \{.*?\\n(?P=indent)\}\\n\\n(?P=indent)val smsManager =',
    re.DOTALL,
)
replacement = '''\\g<indent>val licensedPhones = listOfNotNull(\n\\g<indent>    license.phone,\n\\g<indent>    license.secondaryPhone\n\\g<indent>).map(::normalizePhone).filter { it.isNotBlank() }\n\n\\g<indent>// validatePhone=false explicitly permits SMS from any active SIM.\n\\g<indent>// When validation is enabled, either licensed SIM may be selected.\n\\g<indent>if (license.options.validatePhone && actualSimPhone !in licensedPhones) {\n\\g<indent>    AppLogger.warning(this, "LICENSE", "Selected SMS SIM is not licensed.")\n\\g<indent>    throw SmsLicenseException(\n\\g<indent>        "License/SIM mismatch.\\n\\n" +\n\\g<indent>                "Licensed phones: " + licensedPhones.joinToString(", ").ifBlank { "Unknown" } + "\\n" +\n\\g<indent>                "Selected SMS SIM: " + actualSimPhone.ifBlank { "Unknown" } + "\\n\\n" +\n\\g<indent>                "SMS sending is blocked."\n\\g<indent>    )\n\\g<indent>}\n\n\\g<indent>val smsManager ='''
text, count = pattern.subn(replacement, text, count=1)
if count != 1:
    raise SystemExit("Could not locate SMS license validation block")
MAIN.write_text(text, encoding="utf-8")

version_text = VERSION.read_text(encoding="utf-8-sig")
version_text = version_text.replace(
    'text = "MyAdv v${BuildConfig.VERSION_NAME}"',
    'text = "MyAdv v${context.packageManager.getPackageInfo(context.packageName, 0).versionName}"',
)
VERSION.write_text(version_text, encoding="utf-8")

print("Release source repair completed successfully; multi-SIM license validation enforced")
