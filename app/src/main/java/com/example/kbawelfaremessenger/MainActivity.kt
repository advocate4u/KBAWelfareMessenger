package com.example.kbawelfaremessenger

import android.Manifest
import android.app.Activity
import android.app.AlertDialog
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.content.res.ColorStateList
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.OpenableColumns
import android.telephony.SmsManager
import android.widget.*
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.widget.addTextChangedListener
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import java.io.BufferedReader
import java.io.InputStreamReader
import java.util.Locale

enum class SmsStatus {
    NONE,
    SENDING,
    SENT,
    FAILED
}

data class Contact(
    val name: String,
    val phone: String,
    val fields: Map<String, String>,
    val row: Int,
    var smsStatus: SmsStatus = SmsStatus.NONE
)

data class SmsProgress(
    val contact: Contact,
    val totalParts: Int,
    var completedParts: Int = 0,
    var failed: Boolean = false,
    var errorCode: Int = -1
)

data class SmsResult(
    val contact: Contact,
    val status: String,
    val detail: String
)

class MainActivity : AppCompatActivity() {

    companion object {
        private const val SMS_SENT_ACTION =
            "com.example.kbawelfaremessenger.SMS_SENT"

        private const val EXTRA_REQUEST_ID = "request_id"
        private const val EXTRA_PHONE = "phone"

        private const val SEND_DELAY_MS = 500L
    }

    private lateinit var edtMessage: EditText
    private lateinit var edtSearch: EditText
    private lateinit var txtStatus: TextView
    private lateinit var txtStats: TextView
    private lateinit var txtSelected: TextView
    private lateinit var recyclerContacts: RecyclerView

    private lateinit var btnUpload: Button
    private lateinit var btnSelectAll: Button
    private lateinit var btnUnselectAll: Button
    private lateinit var btnPreview: Button
    private lateinit var btnTestSms: Button
    private lateinit var btnSendSms: Button
    private lateinit var btnWhatsApp: Button
    private lateinit var btnReset: Button
    private lateinit var btnClearData: Button

    private lateinit var txtFile: TextView

    private lateinit var adapter: ContactAdapter

    private val contacts = mutableListOf<Contact>()

    private val handler = Handler(Looper.getMainLooper())

    private var requestIdCounter = 1000

    private val requestToPhone = mutableMapOf<Int, String>()
    private val pendingSms = mutableMapOf<String, SmsProgress>()

    private var smsOperationActive = false
    private var sendQueue: List<Contact> = emptyList()
    private var queueIndex = 0

    private val operationResults = linkedMapOf<String, SmsResult>()

    private var smsReceiverRegistered = false

    private val defaultMessage = """R/m {{name}} ji,

Kindly support & vote for Mohit Arora (Ch.547) for Treasurer, DBA Karnal election. Your blessings mean a lot.

Thank you- Mohit Arora, 9518804747"""

    private val pickCsvLauncher =
        registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            uri?.let {
                loadCsv(it)
            }
        }

    private val smsPermissionLauncher =
        registerForActivityResult(
            ActivityResultContracts.RequestPermission()
        ) { granted ->
            if (granted) {
                startSmsConfirmation()
            } else {
                showAlert(
                    "SMS Permission",
                    "SMS permission is required to send messages."
                )
            }
        }

    private val smsSentReceiver = object : BroadcastReceiver() {

        override fun onReceive(context: Context, intent: Intent) {

            if (intent.action != SMS_SENT_ACTION) return

            val requestId =
                intent.getIntExtra(EXTRA_REQUEST_ID, -1)

            val phone =
                requestToPhone.remove(requestId)
                    ?: return

            val progress =
                pendingSms[phone]
                    ?: return

            val resultCode = resultCode

            if (resultCode == Activity.RESULT_OK) {

                progress.completedParts++

            } else {

                progress.failed = true
                progress.errorCode = resultCode
                progress.completedParts++
            }

            if (progress.completedParts >= progress.totalParts) {

                pendingSms.remove(phone)

                if (progress.failed) {

                    completeSms(
                        progress.contact,
                        false,
                        smsErrorMessage(progress.errorCode)
                    )

                } else {

                    completeSms(
                        progress.contact,
                        true,
                        "SMS submitted successfully to Android SMS service."
                    )
                }
            }

            updateCounts()
            checkSmsOperationFinished()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContentView(R.layout.activity_main)

        initialiseViews()
        setupRecycler()
        setupButtons()
        setupMessage()
        setupSearch()
        setupSmsReceiver()
        setupButtonColors()

        updateCounts()
    }

    private fun initialiseViews() {

        edtMessage = findViewById(R.id.edtMessage)
        edtSearch = findViewById(R.id.edtSearch)

        txtStatus = findViewById(R.id.txtStatus)
        txtStats = findViewById(R.id.txtStats)
        txtSelected = findViewById(R.id.txtSelected)

        recyclerContacts = findViewById(R.id.recyclerContacts)

        btnUpload = findViewById(R.id.btnUpload)
        btnSelectAll = findViewById(R.id.btnSelectAll)
        btnUnselectAll = findViewById(R.id.btnUnselectAll)
        btnPreview = findViewById(R.id.btnPreview)
        btnTestSms = findViewById(R.id.btnTestSms)
        btnSendSms = findViewById(R.id.btnSendSms)
        btnWhatsApp = findViewById(R.id.btnWhatsApp)
        btnReset = findViewById(R.id.btnReset)
        btnClearData = findViewById(R.id.btnClearData)

        txtFile = findViewById(R.id.txtFile)

        // Remove old Draft/Schedule controls if they still exist
        hideOldButton("btnSaveDraft")
        hideOldButton("btnSchedule")
        hideOldButton("btnCancelSchedule")
    }

    private fun setupRecycler() {

        adapter = ContactAdapter(
            contacts
        ) {
            updateCounts()
        }

        recyclerContacts.layoutManager =
            LinearLayoutManager(this)

        recyclerContacts.adapter = adapter
    }

    private fun setupButtons() {

        btnUpload.setOnClickListener {
            pickCsvLauncher.launch(
                arrayOf(
                    "text/csv",
                    "text/comma-separated-values",
                    "application/csv",
                    "text/plain",
                    "*/*"
                )
            )
        }

        btnSelectAll.setOnClickListener {

            if (contacts.isEmpty()) {
                showAlert(
                    "Select All",
                    "Please upload a CSV file first."
                )
                return@setOnClickListener
            }

            adapter.selectAll(contacts)
            updateCounts()
        }

        btnUnselectAll.setOnClickListener {

            adapter.unselectAll()
            updateCounts()
        }

        btnPreview.setOnClickListener {
            showPreview()
        }

        btnTestSms.setOnClickListener {
            testSms()
        }

        btnSendSms.setOnClickListener {

            if (smsOperationActive) {

                Toast.makeText(
                    this,
                    "SMS sending is already in progress.",
                    Toast.LENGTH_SHORT
                ).show()

                return@setOnClickListener
            }

            val message =
                edtMessage.text.toString().trim()

            if (message.isEmpty()) {

                showAlert(
                    "Message Required",
                    "Please enter a message first."
                )

                return@setOnClickListener
            }

            val selected = selectedContacts()

            if (selected.isEmpty()) {

                showAlert(
                    "No Contacts Selected",
                    "Please select at least one contact."
                )

                return@setOnClickListener
            }

            checkSmsPermissionAndStart()
        }

        btnWhatsApp.setOnClickListener {
            sendWhatsApp()
        }

        btnReset.setOnClickListener {

            adapter.unselectAll()

            contacts.forEach {
                it.smsStatus = SmsStatus.NONE
            }

            updateCounts()

            adapter.notifyDataSetChanged()

            txtStatus.text =
                "Selection and SMS status reset."
        }

        btnClearData.setOnClickListener {

            if (smsOperationActive) {

                Toast.makeText(
                    this,
                    "Please wait until SMS sending is completed.",
                    Toast.LENGTH_SHORT
                ).show()

                return@setOnClickListener
            }

            contacts.clear()

            adapter.replaceContacts(emptyList())

            txtFile.text = "No CSV selected"

            updateCounts()

            txtStatus.text = "All contact data cleared."
        }
    }

    private fun setupMessage() {

        if (edtMessage.text.toString().trim().isEmpty()) {
            edtMessage.setText(defaultMessage)
        }
    }

    private fun setupSearch() {

        edtSearch.addTextChangedListener {

            adapter.filter(
                it?.toString().orEmpty()
            )
        }
    }

    private fun setupSmsReceiver() {

        val filter =
            IntentFilter(SMS_SENT_ACTION)

        if (android.os.Build.VERSION.SDK_INT >= 33) {

            registerReceiver(
                smsSentReceiver,
                filter,
                Context.RECEIVER_NOT_EXPORTED
            )

        } else {

            registerReceiver(
                smsSentReceiver,
                filter
            )
        }

        smsReceiverRegistered = true
    }

    private fun setupButtonColors() {

        setButtonColor(btnUpload, "#1976D2")
        setButtonColor(btnSelectAll, "#455A64")
        setButtonColor(btnUnselectAll, "#757575")
        setButtonColor(btnPreview, "#7B1FA2")
        setButtonColor(btnTestSms, "#F9A825")
        setButtonColor(btnSendSms, "#2E7D32")
        setButtonColor(btnWhatsApp, "#00897B")
        setButtonColor(btnReset, "#EF6C00")
        setButtonColor(btnClearData, "#C62828")
    }

    private fun setButtonColor(
        button: Button,
        color: String
    ) {

        button.backgroundTintList =
            ColorStateList.valueOf(
                Color.parseColor(color)
            )
    }

    private fun hideOldButton(name: String) {

        val id =
            resources.getIdentifier(
                name,
                "id",
                packageName
            )

        if (id != 0) {

            findViewById<android.view.View>(id)
                ?.visibility = android.view.View.GONE
        }
    }

    private fun loadCsv(uri: Uri) {

        try {

            val inputStream =
                contentResolver.openInputStream(uri)
                    ?: throw Exception("Unable to open CSV.")

            val reader =
                BufferedReader(
                    InputStreamReader(inputStream, Charsets.UTF_8)
                )

            val lines =
                reader.readLines()

            reader.close()

            if (lines.isEmpty()) {

                showAlert(
                    "CSV Error",
                    "The selected CSV file is empty."
                )

                return
            }

            val headers =
                parseCsvLine(
                    lines[0]
                ).mapIndexed { index, value ->

                    if (index == 0) {
                        value.trim().removePrefix("\uFEFF")
                    } else {
                        value.trim()
                    }
                }

            val nameIndex =
                findHeaderIndex(
                    headers,
                    listOf(
                        "Name",
                        "Original Name",
                        "Given Name"
                    )
                )

            val phoneIndex =
                findHeaderIndex(
                    headers,
                    listOf(
                        "Phone 1 - Value",
                        "Mobile",
                        "Mobile Number",
                        "Phone",
                        "Phone Number",
                        "M.No."
                    )
                )

            if (phoneIndex == -1) {

                showAlert(
                    "CSV Error",
                    "Could not find a mobile/phone column in the CSV."
                )

                return
            }

            contacts.clear()

            val usedPhones =
                mutableSetOf<String>()

            for (lineIndex in 1 until lines.size) {

                val line = lines[lineIndex]

                if (line.isBlank()) continue

                val values =
                    parseCsvLine(line)

                val rawPhone =
                    values.getOrNull(phoneIndex)
                        ?.trim()
                        .orEmpty()

                val phone =
                    normalizePhone(rawPhone)

                if (phone.isEmpty()) continue

                if (!usedPhones.add(phone)) {
                    continue
                }

                val rawName =
                    if (nameIndex >= 0) {
                        values.getOrNull(nameIndex)
                            ?.trim()
                            .orEmpty()
                    } else {
                        ""
                    }

                val displayName =
                    cleanName(rawName)
                        .ifBlank {
                            "Contact ${contacts.size + 1}"
                        }

                val fields =
                    linkedMapOf<String, String>()

                headers.forEachIndexed { index, header ->

                    if (header.isNotBlank()) {

                        fields[header] =
                            values.getOrNull(index)
                                ?.trim()
                                .orEmpty()
                    }
                }

                contacts.add(
                    Contact(
                        name = displayName,
                        phone = phone,
                        fields = fields,
                        row = lineIndex
                    )
                )
            }

            adapter.replaceContacts(contacts)

            txtFile.text =
                getFileName(uri)

            txtStatus.text =
                "${contacts.size} contacts loaded successfully."

            updateCounts()

        } catch (e: Exception) {

            showAlert(
                "CSV Error",
                e.message ?: "Unable to read CSV file."
            )
        }
    }

    private fun findHeaderIndex(
        headers: List<String>,
        possibleNames: List<String>
    ): Int {

        return headers.indexOfFirst { header ->

            possibleNames.any {

                header.equals(
                    it,
                    ignoreCase = true
                )
            }
        }
    }

    private fun parseCsvLine(
        line: String
    ): List<String> {

        val result = mutableListOf<String>()

        var current = StringBuilder()
        var insideQuotes = false

        var index = 0

        while (index < line.length) {

            val char = line[index]

            when {

                char == '"' -> {

                    if (
                        insideQuotes &&
                        index + 1 < line.length &&
                        line[index + 1] == '"'
                    ) {

                        current.append('"')
                        index++

                    } else {

                        insideQuotes =
                            !insideQuotes
                    }
                }

                char == ',' && !insideQuotes -> {

                    result.add(
                        current.toString()
                    )

                    current = StringBuilder()
                }

                else -> {
                    current.append(char)
                }
            }

            index++
        }

        result.add(
            current.toString()
        )

        return result
    }

    private fun cleanName(
        value: String
    ): String {

        return value
            .trim()
            .removePrefix("KBA ")
            .removePrefix("KNL ")
            .trim()
    }

    private fun normalizePhone(
        value: String
    ): String {

        var raw =
            value
                .trim()
                .replace(".0", "")

        // If several values are present, try each one.
        val candidates =
            raw.split(
                Regex("[,;/\\s]+")
            )

        for (candidate in candidates) {

            var number =
                candidate
                    .replace(Regex("[^0-9+]"), "")

            if (number.startsWith("+91")) {
                number = number.substring(3)
            } else if (number.startsWith("91") && number.length == 12) {
                number = number.substring(2)
            }

            number =
                number.filter {
                    it.isDigit()
                }

            if (number.length == 10) {
                return "91$number"
            }
        }

        return ""
    }

    private fun selectedContacts(): List<Contact> {

        val selectedPhones =
            adapter.getSelectedPhones()

        return contacts.filter {
            selectedPhones.contains(it.phone)
        }
    }

    private fun personaliseMessage(
        contact: Contact
    ): String {

        var message =
            edtMessage.text.toString()

        val replacements =
            linkedMapOf<String, String>()

        replacements["{{name}}"] =
            contact.name

        replacements["{{Name}}"] =
            contact.name

        replacements["{{MobileNumber}}"] =
            contact.phone

        replacements["{{Mobile}}"] =
            contact.phone

        replacements["{{Phone}}"] =
            contact.phone

        contact.fields.forEach { (key, value) ->

            replacements["{{$key}}"] =
                value
        }

        replacements.forEach { (key, value) ->

            message =
                message.replace(
                    key,
                    value,
                    ignoreCase = true
                )
        }

        return message
    }

    private fun showPreview() {

        val selected =
            selectedContacts()

        if (selected.isEmpty()) {

            showAlert(
                "Preview",
                "No contacts are selected."
            )

            return
        }

        val builder =
            StringBuilder()

        builder.append(
            "Selected contacts: ${selected.size}\n\n"
        )

        selected.forEachIndexed { index, contact ->

            builder.append(
                "${index + 1}. ${contact.name}\n"
            )

            builder.append(
                "Mobile: ${contact.phone}\n"
            )

            builder.append(
                "Status: ${statusText(contact.smsStatus)}\n"
            )

            builder.append(
                "Message:\n"
            )

            builder.append(
                personaliseMessage(contact)
            )

            builder.append(
                "\n\n------------------------------\n\n"
            )
        }

        val textView =
            TextView(this).apply {

                text = builder.toString()

                textSize = 15f

                setPadding(
                    35,
                    20,
                    35,
                    20
                )
            }

        val scroll =
            ScrollView(this).apply {

                addView(textView)
            }

        AlertDialog.Builder(this)
            .setTitle(
                "Message Preview"
            )
            .setView(scroll)
            .setPositiveButton(
                "Close",
                null
            )
            .show()
    }

    private fun checkSmsPermissionAndStart() {

        if (
            ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.SEND_SMS
            ) != PackageManager.PERMISSION_GRANTED
        ) {

            smsPermissionLauncher.launch(
                Manifest.permission.SEND_SMS
            )

        } else {

            startSmsConfirmation()
        }
    }

    private fun startSmsConfirmation() {

        val selected =
            selectedContacts()

        val alreadySent =
            selected.count {
                it.smsStatus == SmsStatus.SENT
            }

        val willSend =
            selected.count {
                it.smsStatus != SmsStatus.SENT
            }

        val message =
            """
Selected: ${selected.size}
Already sent: $alreadySent
Will send now: $willSend

Each selected mobile number will receive its own personalized SMS.

Do you want to continue?
            """.trimIndent()

        AlertDialog.Builder(this)
            .setTitle("Confirm SMS Sending")
            .setMessage(message)
            .setNegativeButton(
                "Cancel",
                null
            )
            .setPositiveButton(
                "Send SMS"
            ) { _, _ ->

                startSmsOperation(selected)
            }
            .show()
    }

    private fun startSmsOperation(
        selected: List<Contact>
    ) {

        if (smsOperationActive) return

        if (selected.isEmpty()) return

        operationResults.clear()

        // Already sent contacts are skipped.
        selected.forEach { contact ->

            if (contact.smsStatus == SmsStatus.SENT) {

                operationResults[contact.phone] =
                    SmsResult(
                        contact,
                        "SKIPPED",
                        "Already sent earlier in this session."
                    )
            }
        }

        sendQueue =
            selected.filter {
                it.smsStatus != SmsStatus.SENT
            }

        if (sendQueue.isEmpty()) {

            showSmsResultAlert(selected)

            return
        }

        smsOperationActive = true

        queueIndex = 0

        pendingSms.clear()
        requestToPhone.clear()

        btnSendSms.isEnabled = false

        txtStatus.text =
            "Starting SMS sending for ${sendQueue.size} contacts..."

        updateCounts()

        dispatchNextSms()
    }

    private fun dispatchNextSms() {

        if (!smsOperationActive) return

        if (queueIndex >= sendQueue.size) {

            checkSmsOperationFinished()

            return
        }

        val contact =
            sendQueue[queueIndex]

        queueIndex++

        contact.smsStatus =
            SmsStatus.SENDING

        adapter.notifyContactStatusChanged(
            contact.phone
        )

        updateCounts()

        val message =
            personaliseMessage(contact)

        try {

            sendSmsForContact(
                contact,
                message
            )

        } catch (e: Exception) {

            completeSms(
                contact,
                false,
                e.message ?: "SMS sending error."
            )
        }

        handler.postDelayed(
            {
                dispatchNextSms()
            },
            SEND_DELAY_MS
        )
    }

    private fun sendSmsForContact(
        contact: Contact,
        message: String
    ) {

        val smsManager =
            SmsManager.getDefault()

        val parts =
            smsManager.divideMessage(message)

        val progress =
            SmsProgress(
                contact = contact,
                totalParts = parts.size
            )

        pendingSms[contact.phone] =
            progress

        val sentIntents =
            ArrayList<PendingIntent>()

        parts.forEach {

            val requestId =
                requestIdCounter++

            requestToPhone[requestId] =
                contact.phone

            val intent =
                Intent(SMS_SENT_ACTION).apply {

                    setPackage(packageName)

                    putExtra(
                        EXTRA_REQUEST_ID,
                        requestId
                    )

                    putExtra(
                        EXTRA_PHONE,
                        contact.phone
                    )
                }

            val pendingIntent =
                PendingIntent.getBroadcast(
                    this,
                    requestId,
                    intent,
                    PendingIntent.FLAG_UPDATE_CURRENT or
                            PendingIntent.FLAG_IMMUTABLE
                )

            sentIntents.add(
                pendingIntent
            )
        }

        if (parts.size == 1) {

            smsManager.sendTextMessage(
                contact.phone,
                null,
                message,
                sentIntents[0],
                null
            )

        } else {

            smsManager.sendMultipartTextMessage(
                contact.phone,
                null,
                parts,
                sentIntents,
                null
            )
        }
    }

    private fun completeSms(
        contact: Contact,
        success: Boolean,
        detail: String
    ) {

        if (
            operationResults.containsKey(
                contact.phone
            )
        ) {
            return
        }

        contact.smsStatus =
            if (success) {
                SmsStatus.SENT
            } else {
                SmsStatus.FAILED
            }

        operationResults[contact.phone] =
            SmsResult(
                contact = contact,
                status =
                    if (success) "SENT"
                    else "FAILED",
                detail = detail
            )

        adapter.notifyContactStatusChanged(
            contact.phone
        )

        updateCounts()

        txtStatus.text =
            "SMS progress: " +
                    "${sentCount()} sent, " +
                    "${failedCount()} failed."
    }

    private fun checkSmsOperationFinished() {

        if (!smsOperationActive) return

        if (
            queueIndex >= sendQueue.size &&
            pendingSms.isEmpty() &&
            operationResults.size >=
            selectedOperationCount()
        ) {

            smsOperationActive = false

            btnSendSms.isEnabled = true

            updateCounts()

            txtStatus.text =
                "SMS sending completed."

            showSmsResultAlert(
                selectedContactsForResult()
            )
        }
    }

    private fun selectedOperationCount(): Int {

        return operationResults.size +
                pendingSms.size +
                sendQueue.size -
                queueIndex
    }

    private fun selectedContactsForResult(): List<Contact> {

        val resultPhones =
            operationResults.keys

        return contacts.filter {
            resultPhones.contains(it.phone)
        }
    }

    private fun showSmsResultAlert(
        contactsForResult: List<Contact>
    ) {

        val results =
            contactsForResult.mapNotNull {
                operationResults[it.phone]
            }

        val sent =
            results.count {
                it.status == "SENT"
            }

        val failed =
            results.count {
                it.status == "FAILED"
            }

        val skipped =
            results.count {
                it.status == "SKIPPED"
            }

        val text =
            StringBuilder()

        text.append(
            "SMS Sending Result\n\n"
        )

        text.append(
            "Selected: ${results.size}\n"
        )

        text.append(
            "Sent: $sent\n"
        )

        text.append(
            "Failed: $failed\n"
        )

        text.append(
            "Skipped: $skipped\n\n"
        )

        text.append(
            "Individual Details:\n\n"
        )

        results.forEachIndexed { index, result ->

            val symbol =
                when (result.status) {
                    "SENT" -> "✓"
                    "FAILED" -> "✕"
                    else -> "↷"
                }

            text.append(
                "$symbol ${index + 1}. " +
                        "${result.contact.name}\n"
            )

            text.append(
                "   ${result.contact.phone}\n"
            )

            text.append(
                "   ${result.status}: " +
                        "${result.detail}\n\n"
            )
        }

        val textView =
            TextView(this).apply {

                text = text.toString()

                textSize = 15f

                setPadding(
                    35,
                    20,
                    35,
                    20
                )
            }

        val scroll =
            ScrollView(this).apply {

                addView(textView)
            }

        AlertDialog.Builder(this)
            .setTitle(
                "SMS Send Result"
            )
            .setView(scroll)
            .setPositiveButton(
                "OK",
                null
            )
            .show()
    }

    private fun testSms() {

        val selected =
            selectedContacts()

        if (selected.isEmpty()) {

            showAlert(
                "Test SMS",
                "Select at least one contact."
            )

            return
        }

        val contact =
            selected.first()

        val message =
            personaliseMessage(contact)

        AlertDialog.Builder(this)
            .setTitle("Test SMS")
            .setMessage(
                "A test SMS will be sent to:\n\n" +
                        "${contact.name}\n" +
                        "${contact.phone}\n\n" +
                        message
            )
            .setNegativeButton(
                "Cancel",
                null
            )
            .setPositiveButton(
                "Send Test"
            ) { _, _ ->

                if (
                    ContextCompat.checkSelfPermission(
                        this,
                        Manifest.permission.SEND_SMS
                    ) != PackageManager.PERMISSION_GRANTED
                ) {

                    smsPermissionLauncher.launch(
                        Manifest.permission.SEND_SMS
                    )

                    return@setPositiveButton
                }

                try {

                    val smsManager =
                        SmsManager.getDefault()

                    smsManager.sendTextMessage(
                        contact.phone,
                        null,
                        message,
                        null,
                        null
                    )

                    showAlert(
                        "Test SMS",
                        "SMS request submitted to Android SMS service.\n\n" +
                                "Name: ${contact.name}\n" +
                                "Number: ${contact.phone}"
                    )

                } catch (e: Exception) {

                    showAlert(
                        "Test SMS Failed",
                        e.message ?: "Unable to send SMS."
                    )
                }
            }
            .show()
    }

    private fun sendWhatsApp() {

        val selected =
            selectedContacts()

        if (selected.isEmpty()) {

            showAlert(
                "WhatsApp",
                "Please select at least one contact."
            )

            return
        }

        val contact =
            selected.first()

        val message =
            personaliseMessage(contact)

        try {

            val uri =
                Uri.parse(
                    "https://wa.me/${contact.phone}" +
                            "?text=${Uri.encode(message)}"
                )

            val intent =
                Intent(
                    Intent.ACTION_VIEW,
                    uri
                )

            startActivity(intent)

            showAlert(
                "WhatsApp",
                "WhatsApp opened for:\n\n" +
                        "${contact.name}\n" +
                        contact.phone +
                        "\n\n" +
                        "WhatsApp does not allow this app to silently bulk-send messages to all selected contacts."
            )

        } catch (e: Exception) {

            showAlert(
                "WhatsApp",
                "Unable to open WhatsApp."
            )
        }
    }

    private fun updateCounts() {

        val selected =
            adapter.getSelectedPhones().size

        val sent =
            contacts.count {
                it.smsStatus == SmsStatus.SENT
            }

        val failed =
            contacts.count {
                it.smsStatus == SmsStatus.FAILED
            }

        val sending =
            contacts.count {
                it.smsStatus == SmsStatus.SENDING
            }

        txtSelected.text =
            "Selected: $selected"

        txtStats.text =
            "Total: ${contacts.size}  |  " +
                    "Sent: $sent  |  " +
                    "Failed: $failed  |  " +
                    "Pending/Sending: $sending"
    }

    private fun sentCount(): Int {

        return contacts.count {
            it.smsStatus == SmsStatus.SENT
        }
    }

    private fun failedCount(): Int {

        return contacts.count {
            it.smsStatus == SmsStatus.FAILED
        }
    }

    private fun statusText(
        status: SmsStatus
    ): String {

        return when (status) {

            SmsStatus.NONE ->
                "Not sent"

            SmsStatus.SENDING ->
                "Sending..."

            SmsStatus.SENT ->
                "SENT"

            SmsStatus.FAILED ->
                "FAILED"
        }
    }

    private fun smsErrorMessage(
        code: Int
    ): String {

        return when (code) {

            SmsManager.RESULT_ERROR_GENERIC_FAILURE ->
                "Generic SMS failure."

            SmsManager.RESULT_ERROR_RADIO_OFF ->
                "Mobile radio is off."

            SmsManager.RESULT_ERROR_NULL_PDU ->
                "Invalid SMS data."

            SmsManager.RESULT_ERROR_NO_SERVICE ->
                "No mobile service."

            SmsManager.RESULT_ERROR_LIMIT_EXCEEDED ->
                "SMS limit exceeded."

            SmsManager.RESULT_ERROR_FDN_CHECK_FAILURE ->
                "Fixed Dialing Number check failed."

            SmsManager.RESULT_ERROR_SHORT_CODE_NOT_ALLOWED ->
                "Short code SMS not allowed."

            SmsManager.RESULT_ERROR_SHORT_CODE_NEVER_ALLOWED ->
                "Short code SMS is not allowed."

            else ->
                "SMS service returned error code $code."
        }
    }

    private fun getFileName(
        uri: Uri
    ): String {

        var result: String? = null

        contentResolver.query(
            uri,
            null,
            null,
            null,
            null
        )?.use { cursor ->

            val index =
                cursor.getColumnIndex(
                    OpenableColumns.DISPLAY_NAME
                )

            if (cursor.moveToFirst() && index >= 0) {
                result =
                    cursor.getString(index)
            }
        }

        return result ?: "CSV file"
    }

    private fun showAlert(
        title: String,
        message: String
    ) {

        AlertDialog.Builder(this)
            .setTitle(title)
            .setMessage(message)
            .setPositiveButton(
                "OK",
                null
            )
            .show()
    }

    override fun onDestroy() {

        handler.removeCallbacksAndMessages(null)

        if (smsReceiverRegistered) {

            unregisterReceiver(
                smsSentReceiver
            )

            smsReceiverRegistered = false
        }

        super.onDestroy()
    }
}
