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
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.OpenableColumns
import android.telephony.SmsManager
import android.text.Editable
import android.text.TextWatcher
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import java.io.BufferedReader
import java.io.InputStreamReader

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

        private const val EXTRA_REQUEST_ID =
            "request_id"

        private const val SEND_DELAY_MS =
            500L
    }

    private lateinit var edtMessage: EditText
    private lateinit var edtSearch: EditText
    private lateinit var edtRangeFrom: EditText
    private lateinit var edtRangeTo: EditText

    private lateinit var txtStatus: TextView
    private lateinit var txtStats: TextView
    private lateinit var txtSelected: TextView
    private lateinit var txtFile: TextView

    private lateinit var recyclerContacts: RecyclerView

    private lateinit var btnUpload: Button
    private lateinit var btnSelectAll: Button
    private lateinit var btnUnselectAll: Button
    private lateinit var btnSelectRange: Button
    private lateinit var btnUnselectRange: Button
    private lateinit var btnPreview: Button
    private lateinit var btnTestSms: Button
    private lateinit var btnSendSms: Button
    private lateinit var btnWhatsApp: Button
    private lateinit var btnReset: Button
    private lateinit var btnClearData: Button

    private lateinit var adapter: ContactAdapter

    private val contacts =
        mutableListOf<Contact>()

    private val handler =
        Handler(Looper.getMainLooper())

    private val requestToPhone =
        mutableMapOf<Int, String>()

    private val pendingSms =
        mutableMapOf<String, SmsProgress>()

    private val operationResults =
        linkedMapOf<String, SmsResult>()

    private var requestIdCounter = 1000

    private var smsOperationActive = false

    private var sendQueue =
        emptyList<Contact>()

    private var queueIndex = 0

    private var smsReceiverRegistered = false

    private val defaultMessage =
        """R/m {{name}} ji,

Kindly support & vote for Mohit Arora (Ch.547) for Treasurer, DBA Karnal election. Your blessings mean a lot.

Thank you- Mohit Arora, 9518804747"""

    private val pickCsvLauncher =
        registerForActivityResult(
            ActivityResultContracts.OpenDocument()
        ) { uri ->

            if (uri != null) {
                loadCsv(uri)
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

    private val smsSentReceiver =
        object : BroadcastReceiver() {

            override fun onReceive(
                context: Context?,
                intent: Intent?
            ) {

                if (
                    intent?.action !=
                    SMS_SENT_ACTION
                ) {
                    return
                }

                val requestId =
                    intent.getIntExtra(
                        EXTRA_REQUEST_ID,
                        -1
                    )

                if (requestId == -1) {
                    return
                }

                val phone =
                    requestToPhone.remove(
                        requestId
                    )
                        ?: return

                val progress =
                    pendingSms[phone]
                        ?: return

                val resultCode =
                    getResultCode()

                if (
                    resultCode ==
                    Activity.RESULT_OK
                ) {

                    progress.completedParts++

                } else {

                    progress.failed = true

                    progress.errorCode =
                        resultCode

                    progress.completedParts++
                }

                if (
                    progress.completedParts >=
                    progress.totalParts
                ) {

                    pendingSms.remove(
                        phone
                    )

                    if (progress.failed) {

                        completeSms(
                            progress.contact,
                            false,
                            smsErrorMessage(
                                progress.errorCode
                            )
                        )

                    } else {

                        completeSms(
                            progress.contact,
                            true,
                            "SMS submitted successfully."
                        )
                    }
                }

                updateCounts()
                checkSmsOperationFinished()
            }
        }

    override fun onCreate(
        savedInstanceState: Bundle?
    ) {

        super.onCreate(
            savedInstanceState
        )

        setContentView(
            R.layout.activity_main
        )

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

        edtMessage =
            findViewById(R.id.edtMessage)

        edtSearch =
            findViewById(R.id.edtSearch)

        edtRangeFrom =
            findViewById(R.id.edtRangeFrom)

        edtRangeTo =
            findViewById(R.id.edtRangeTo)

        txtStatus =
            findViewById(R.id.txtStatus)

        txtStats =
            findViewById(R.id.txtStats)

        txtSelected =
            findViewById(R.id.txtSelected)

        txtFile =
            findViewById(R.id.txtFile)

        recyclerContacts =
            findViewById(R.id.recyclerContacts)

        btnUpload =
            findViewById(R.id.btnUpload)

        btnSelectAll =
            findViewById(R.id.btnSelectAll)

        btnUnselectAll =
            findViewById(R.id.btnUnselectAll)

        btnSelectRange =
            findViewById(R.id.btnSelectRange)

        btnUnselectRange =
            findViewById(R.id.btnUnselectRange)

        btnPreview =
            findViewById(R.id.btnPreview)

        btnTestSms =
            findViewById(R.id.btnTestSms)

        btnSendSms =
            findViewById(R.id.btnSendSms)

        btnWhatsApp =
            findViewById(R.id.btnWhatsApp)

        btnReset =
            findViewById(R.id.btnReset)

        btnClearData =
            findViewById(R.id.btnClearData)
    }

    private fun setupRecycler() {

        adapter =
            ContactAdapter(
                contacts
            ) {
                updateCounts()
            }

        recyclerContacts.layoutManager =
            LinearLayoutManager(this)

        recyclerContacts.adapter =
            adapter
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

            if (
                adapter.getVisibleCount() == 0
            ) {

                showAlert(
                    "Select All",
                    "No contacts match the current search."
                )

                return@setOnClickListener
            }

            adapter.selectAll()

            updateCounts()
        }

        btnUnselectAll.setOnClickListener {

            if (contacts.isEmpty()) {
                return@setOnClickListener
            }

            adapter.unselectAll()

            updateCounts()
        }

        btnSelectRange.setOnClickListener {

            selectRange(true)
        }

        btnUnselectRange.setOnClickListener {

            selectRange(false)
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
                edtMessage.text
                    .toString()
                    .trim()

            if (message.isEmpty()) {

                showAlert(
                    "Message Required",
                    "Please enter a message first."
                )

                return@setOnClickListener
            }

            if (
                selectedContacts().isEmpty()
            ) {

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

            if (smsOperationActive) {

                Toast.makeText(
                    this,
                    "Please wait until SMS sending is completed.",
                    Toast.LENGTH_SHORT
                ).show()

                return@setOnClickListener
            }

            adapter.unselectAll()

            contacts.forEach {
                it.smsStatus =
                    SmsStatus.NONE
            }

            adapter.notifyDataSetChanged()

            updateCounts()

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

            adapter.replaceContacts(
                emptyList()
            )

            txtFile.text =
                "No CSV selected"

            txtStatus.text =
                "All contact data cleared."

            updateCounts()
        }
    }

    private fun selectRange(
        select: Boolean
    ) {

        if (contacts.isEmpty()) {

            showAlert(
                "Range Selection",
                "Please upload a CSV file first."
            )

            return
        }

        val from =
            edtRangeFrom.text
                .toString()
                .trim()
                .toIntOrNull()

        val to =
            edtRangeTo.text
                .toString()
                .trim()
                .toIntOrNull()

        if (
            from == null ||
            to == null
        ) {

            showAlert(
                "Range Selection",
                "Please enter valid From and To numbers."
            )

            return
        }

        if (
            from < 1 ||
            to < 1
        ) {

            showAlert(
                "Range Selection",
                "Range numbers must be greater than zero."
            )

            return
        }

        val visibleCount =
            adapter.getVisibleCount()

        if (visibleCount == 0) {

            showAlert(
                "Range Selection",
                "No contacts are currently displayed."
            )

            return
        }

        val start =
            minOf(from, to)

        val end =
            maxOf(from, to)

        if (
            start > visibleCount ||
            end > visibleCount
        ) {

            showAlert(
                "Range Selection",
                "Invalid range.\n\n" +
                        "Currently displayed contacts: $visibleCount\n" +
                        "Valid range: 1 to $visibleCount"
            )

            return
        }

        if (select) {

            adapter.selectRange(
                from,
                to
            )

            txtStatus.text =
                "Range $start-$end selected."

        } else {

            adapter.unselectRange(
                from,
                to
            )

            txtStatus.text =
                "Range $start-$end unselected."
        }

        updateCounts()
    }

    private fun setupMessage() {

        if (
            edtMessage.text
                .toString()
                .trim()
                .isEmpty()
        ) {

            edtMessage.setText(
                defaultMessage
            )
        }
    }

    private fun setupSearch() {

        edtSearch.addTextChangedListener(
            object : TextWatcher {

                override fun beforeTextChanged(
                    s: CharSequence?,
                    start: Int,
                    count: Int,
                    after: Int
                ) {}

                override fun onTextChanged(
                    s: CharSequence?,
                    start: Int,
                    before: Int,
                    count: Int
                ) {

                    adapter.filter(
                        s?.toString().orEmpty()
                    )
                }

                override fun afterTextChanged(
                    s: Editable?
                ) {}
            }
        )
    }

    private fun setupSmsReceiver() {

        val filter =
            IntentFilter(
                SMS_SENT_ACTION
            )

        if (
            Build.VERSION.SDK_INT >=
            Build.VERSION_CODES.TIRAMISU
        ) {

            registerReceiver(
                smsSentReceiver,
                filter,
                Context.RECEIVER_NOT_EXPORTED
            )

        } else {

            @Suppress(
                "UnspecifiedRegisterReceiverFlag"
            )

            registerReceiver(
                smsSentReceiver,
                filter
            )
        }

        smsReceiverRegistered = true
    }

    private fun setupButtonColors() {

        setButtonColor(
            btnUpload,
            "#1976D2"
        )

        setButtonColor(
            btnSelectAll,
            "#455A64"
        )

        setButtonColor(
            btnUnselectAll,
            "#757575"
        )

        setButtonColor(
            btnSelectRange,
            "#5E35B1"
        )

        setButtonColor(
            btnUnselectRange,
            "#8E24AA"
        )

        setButtonColor(
            btnPreview,
            "#7B1FA2"
        )

        setButtonColor(
            btnTestSms,
            "#F9A825"
        )

        setButtonColor(
            btnSendSms,
            "#2E7D32"
        )

        setButtonColor(
            btnWhatsApp,
            "#00897B"
        )

        setButtonColor(
            btnReset,
            "#EF6C00"
        )

        setButtonColor(
            btnClearData,
            "#C62828"
        )
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

    private fun loadCsv(
        uri: Uri
    ) {

        try {

            val inputStream =
                contentResolver.openInputStream(uri)
                    ?: throw Exception(
                        "Unable to open CSV file."
                    )

            val reader =
                BufferedReader(
                    InputStreamReader(
                        inputStream,
                        Charsets.UTF_8
                    )
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
                ).mapIndexed {
                        index,
                        value ->

                    if (index == 0) {

                        value
                            .trim()
                            .removePrefix("\uFEFF")

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

            var phoneIndex =
                findHeaderIndex(
                    headers,
                    listOf(
                        "Phone 1 - Value",
                        "Mobile",
                        "Mobile Number",
                        "Phone",
                        "Phone Number",
                        "M.No.",
                        "M.No",
                        "M No",
                        "Mobile No",
                        "Mobile No.",
                        "Contact",
                        "Contact Number"
                    )
                )

            if (phoneIndex == -1) {

                val maxColumns =
                    headers.size

                for (
                    columnIndex in
                    0 until maxColumns
                ) {

                    var phoneMatches = 0
                    var rowsChecked = 0

                    for (
                        lineIndex in
                        1 until minOf(
                            lines.size,
                            21
                        )
                    ) {

                        val values =
                            parseCsvLine(
                                lines[lineIndex]
                            )

                        val value =
                            values
                                .getOrNull(
                                    columnIndex
                                )
                                ?.trim()
                                .orEmpty()

                        if (value.isNotEmpty()) {

                            rowsChecked++

                            if (
                                normalizePhone(
                                    value
                                ).isNotEmpty()
                            ) {
                                phoneMatches++
                            }
                        }
                    }

                    if (
                        rowsChecked > 0 &&
                        phoneMatches >= 2
                    ) {

                        phoneIndex =
                            columnIndex

                        break
                    }
                }
            }

            if (phoneIndex == -1) {

                showAlert(
                    "CSV Error",
                    "Could not find a mobile/phone column.\n\n" +
                            "CSV headers found:\n\n" +
                            headers.joinToString("\n")
                )

                return
            }

            contacts.clear()

            val usedPhones =
                mutableSetOf<String>()

            for (
                lineIndex in
                1 until lines.size
            ) {

                val line =
                    lines[lineIndex]

                if (line.isBlank()) {
                    continue
                }

                val values =
                    parseCsvLine(line)

                val rawPhone =
                    values
                        .getOrNull(phoneIndex)
                        ?.trim()
                        .orEmpty()

                val phone =
                    normalizePhone(rawPhone)

                if (phone.isEmpty()) {
                    continue
                }

                if (!usedPhones.add(phone)) {
                    continue
                }

                val rawName =
                    if (nameIndex >= 0) {

                        values
                            .getOrNull(nameIndex)
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

                headers.forEachIndexed {
                        index,
                        header ->

                    if (header.isNotBlank()) {

                        fields[header] =
                            values
                                .getOrNull(index)
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

            adapter.replaceContacts(
                contacts
            )

            txtFile.text =
                getFileName(uri)

            txtStatus.text =
                "${contacts.size} contacts loaded successfully."

            updateCounts()

        } catch (e: Exception) {

            showAlert(
                "CSV Error",
                e.message
                    ?: "Unable to read CSV file."
            )
        }
    }

    private fun parseCsvLine(
        line: String
    ): List<String> {

        val result =
            mutableListOf<String>()

        var current =
            StringBuilder()

        var insideQuotes =
            false

        var index = 0

        while (index < line.length) {

            val char =
                line[index]

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

                char == ',' &&
                        !insideQuotes -> {

                    result.add(
                        current.toString()
                    )

                    current =
                        StringBuilder()
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

    private fun findHeaderIndex(
        headers: List<String>,
        possibleNames: List<String>
    ): Int {

        val exactIndex =
            headers.indexOfFirst { header ->

                possibleNames.any { name ->

                    header.trim().equals(
                        name.trim(),
                        ignoreCase = true
                    )
                }
            }

        if (exactIndex >= 0) {
            return exactIndex
        }

        return headers.indexOfFirst { header ->

            val normalized =
                header
                    .trim()
                    .lowercase()
                    .replace(
                        Regex("[^a-z0-9]"),
                        ""
                    )

            normalized == "mobile" ||
                    normalized == "mobilenumber" ||
                    normalized == "mobileno" ||
                    normalized == "mno" ||
                    normalized == "mnumber" ||
                    normalized == "phone" ||
                    normalized == "phonenumber" ||
                    normalized == "phone1value" ||
                    normalized == "contact" ||
                    normalized == "contactnumber"
        }
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

        val candidates =
            value
                .trim()
                .replace(".0", "")
                .split(
                    Regex("[,;/\\s]+")
                )

        for (
            candidate in candidates
        ) {

            var number =
                candidate.replace(
                    Regex("[^0-9+]"),
                    ""
                )

            if (
                number.startsWith("+91")
            ) {

                number =
                    number.substring(3)

            } else if (
                number.startsWith("91") &&
                number.length == 12
            ) {

                number =
                    number.substring(2)
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

    private fun selectedContacts():
            List<Contact> {

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

        contact.fields.forEach {
                (key, value) ->

            replacements["{{$key}}"] =
                value
        }

        replacements.forEach {
                (key, value) ->

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

        selected.forEachIndexed {
                index,
                contact ->

            builder.append(
                "${index + 1}. ${contact.name}\n"
            )

            builder.append(
                "Mobile: ${contact.phone}\n"
            )

            builder.append(
                "Status: ${statusText(contact.smsStatus)}\n\n"
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

                text =
                    builder.toString()

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
            .setTitle("Message Preview")
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
                it.smsStatus ==
                        SmsStatus.SENT
            }

        val willSend =
            selected.count {
                it.smsStatus !=
                        SmsStatus.SENT
            }

        val confirmation =
            """
Selected: ${selected.size}

Already sent: $alreadySent

Will send now: $willSend

Each selected contact will receive one personalized SMS.

Contacts already marked SENT will be skipped automatically.

Do you want to continue?
            """.trimIndent()

        AlertDialog.Builder(this)
            .setTitle(
                "Confirm SMS Sending"
            )
            .setMessage(
                confirmation
            )
            .setNegativeButton(
                "Cancel",
                null
            )
            .setPositiveButton(
                "Send SMS"
            ) { _, _ ->

                startSmsOperation(
                    selected
                )
            }
            .show()
    }

    private fun startSmsOperation(
        selected: List<Contact>
    ) {

        if (smsOperationActive) {
            return
        }

        if (selected.isEmpty()) {
            return
        }

        operationResults.clear()

        selected.forEach { contact ->

            if (
                contact.smsStatus ==
                SmsStatus.SENT
            ) {

                operationResults[
                    contact.phone
                ] =
                    SmsResult(
                        contact,
                        "SKIPPED",
                        "Already sent earlier."
                    )
            }
        }

        sendQueue =
            selected.filter {
                it.smsStatus !=
                        SmsStatus.SENT
            }

        if (sendQueue.isEmpty()) {

            txtStatus.text =
                "All selected contacts were already sent."

            showSmsResultAlert(selected)

            return
        }

        smsOperationActive = true
        queueIndex = 0

        pendingSms.clear()
        requestToPhone.clear()

        btnSendSms.isEnabled = false

        txtStatus.text =
            "Sending SMS to ${sendQueue.size} contacts..."

        updateCounts()

        dispatchNextSms()
    }

    private fun dispatchNextSms() {

        if (!smsOperationActive) {
            return
        }

        if (
            queueIndex >=
            sendQueue.size
        ) {

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
                e.message
                    ?: "SMS sending error."
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
            smsManager.divideMessage(
                message
            )

        if (parts.isEmpty()) {

            throw IllegalArgumentException(
                "Message is empty."
            )
        }

        val progress =
            SmsProgress(
                contact,
                parts.size
            )

        pendingSms[
            contact.phone
        ] = progress

        val sentIntents =
            ArrayList<PendingIntent>()

        parts.forEach {

            val requestId =
                requestIdCounter++

            requestToPhone[
                requestId
            ] =
                contact.phone

            val intent =
                Intent(
                    SMS_SENT_ACTION
                ).apply {

                    setPackage(
                        packageName
                    )

                    putExtra(
                        EXTRA_REQUEST_ID,
                        requestId
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

        operationResults[
            contact.phone
        ] =
            SmsResult(
                contact,
                if (success) "SENT" else "FAILED",
                detail
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

        if (!smsOperationActive) {
            return
        }

        if (
            queueIndex >=
            sendQueue.size &&
            pendingSms.isEmpty()
        ) {

            smsOperationActive = false

            btnSendSms.isEnabled = true

            updateCounts()

            txtStatus.text =
                "SMS sending completed."

            showSmsResultAlert(
                operationResults.values
                    .map { it.contact }
            )
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

        val builder =
            StringBuilder()

        builder.append(
            "Selected: ${results.size}\n"
        )

        builder.append(
            "Sent: $sent\n"
        )

        builder.append(
            "Failed: $failed\n"
        )

        builder.append(
            "Skipped: $skipped\n\n"
        )

        builder.append(
            "Individual Details\n"
        )

        builder.append(
            "============================\n\n"
        )

        results.forEachIndexed {
                index,
                result ->

            val symbol =
                when (result.status) {
                    "SENT" -> "✓"
                    "FAILED" -> "✕"
                    else -> "↷"
                }

            builder.append(
                "$symbol ${index + 1}. " +
                        "${result.contact.name}\n"
            )

            builder.append(
                "   Mobile: " +
                        "${result.contact.phone}\n"
            )

            builder.append(
                "   Status: " +
                        "${result.status}\n"
            )

            builder.append(
                "   ${result.detail}\n\n"
            )
        }

        val textView =
            TextView(this).apply {

                text =
                    builder.toString()

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
            .setTitle("SMS Send Result")
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
                "Test SMS will be sent to:\n\n" +
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
                    ) !=
                    PackageManager.PERMISSION_GRANTED
                ) {

                    Toast.makeText(
                        this,
                        "Please grant SMS permission and tap Test SMS again.",
                        Toast.LENGTH_LONG
                    ).show()

                    smsPermissionLauncher.launch(
                        Manifest.permission.SEND_SMS
                    )

                    return@setPositiveButton
                }

                try {

                    SmsManager.getDefault()
                        .sendTextMessage(
                            contact.phone,
                            null,
                            message,
                            null,
                            null
                        )

                    showAlert(
                        "Test SMS",
                        "SMS request submitted.\n\n" +
                                "Name: ${contact.name}\n" +
                                "Number: ${contact.phone}"
                    )

                } catch (e: Exception) {

                    showAlert(
                        "Test SMS Failed",
                        e.message
                            ?: "Unable to send SMS."
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
                    "https://wa.me/" +
                            contact.phone +
                            "?text=" +
                            Uri.encode(message)
                )

            startActivity(
                Intent(
                    Intent.ACTION_VIEW,
                    uri
                )
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
            adapter
                .getSelectedPhones()
                .size

        val sent =
            contacts.count {
                it.smsStatus ==
                        SmsStatus.SENT
            }

        val failed =
            contacts.count {
                it.smsStatus ==
                        SmsStatus.FAILED
            }

        val sending =
            contacts.count {
                it.smsStatus ==
                        SmsStatus.SENDING
            }

        txtSelected.text =
            "Selected: $selected"

        txtStats.text =
            "Total: ${contacts.size}  |  " +
                    "Sent: $sent  |  " +
                    "Failed: $failed  |  " +
                    "Pending/Sending: $sending"
    }

    private fun sentCount(): Int =
        contacts.count {
            it.smsStatus ==
                    SmsStatus.SENT
        }

    private fun failedCount(): Int =
        contacts.count {
            it.smsStatus ==
                    SmsStatus.FAILED
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

            if (
                cursor.moveToFirst() &&
                index >= 0
            ) {

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

        handler.removeCallbacksAndMessages(
            null
        )

        if (smsReceiverRegistered) {

            unregisterReceiver(
                smsSentReceiver
            )

            smsReceiverRegistered =
                false
        }

        super.onDestroy()
    }

    // =========================================================
    // ContactAdapter
    // =========================================================

    private class ContactAdapter(
        private val allContacts: MutableList<Contact>,
        private val onSelectionChanged: () -> Unit
    ) :
        RecyclerView.Adapter<ContactAdapter.ContactViewHolder>() {

        private val visibleContacts =
            mutableListOf<Contact>()

        private val selectedPhones =
            mutableSetOf<String>()

        init {
            visibleContacts.addAll(
                allContacts
            )
        }

        class ContactViewHolder(
            val root: LinearLayout
        ) : RecyclerView.ViewHolder(root) {

            val checkBox: CheckBox =
                CheckBox(root.context)

            val nameText: TextView =
                TextView(root.context)

            val phoneText: TextView =
                TextView(root.context)

            val statusText: TextView =
                TextView(root.context)

            init {

                root.orientation =
                    LinearLayout.HORIZONTAL

                root.gravity =
                    Gravity.CENTER_VERTICAL

                root.setPadding(
                    8,
                    8,
                    8,
                    8
                )

                root.addView(
                    checkBox,
                    LinearLayout.LayoutParams(
                        48,
                        48
                    )
                )

                val info =
                    LinearLayout(
                        root.context
                    ).apply {

                        orientation =
                            LinearLayout.VERTICAL

                        setPadding(
                            8,
                            4,
                            8,
                            4
                        )
                    }

                info.addView(
                    nameText,
                    LinearLayout.LayoutParams(
                        -1,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                    )
                )

                info.addView(
                    phoneText,
                    LinearLayout.LayoutParams(
                        -1,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                    )
                )

                info.addView(
                    statusText,
                    LinearLayout.LayoutParams(
                        -1,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                    )
                )

                root.addView(
                    info,
                    LinearLayout.LayoutParams(
                        0,
                        -2,
                        1f
                    )
                )
            }
        }

        override fun onCreateViewHolder(
            parent: android.view.ViewGroup,
            viewType: Int
        ): ContactViewHolder {

            val root =
                LinearLayout(
                    parent.context
                )

            return ContactViewHolder(
                root
            )
        }

        override fun getItemCount(): Int =
            visibleContacts.size

        override fun onBindViewHolder(
            holder: ContactViewHolder,
            position: Int
        ) {

            val contact =
                visibleContacts[position]

            holder.nameText.text =
                "${position + 1}. ${contact.name}"

            holder.nameText.textSize =
                16f

            holder.nameText.setTypeface(
                null,
                android.graphics.Typeface.BOLD
            )

            holder.phoneText.text =
                contact.phone

            holder.phoneText.textSize =
                14f

            holder.statusText.text =
                when (contact.smsStatus) {

                    SmsStatus.NONE ->
                        "Not sent"

                    SmsStatus.SENDING ->
                        "Sending..."

                    SmsStatus.SENT ->
                        "✓ SENT"

                    SmsStatus.FAILED ->
                        "✕ FAILED"
                }

            holder.statusText.textSize =
                13f

            holder.checkBox.setOnCheckedChangeListener(
                null
            )

            holder.checkBox.isChecked =
                selectedPhones.contains(
                    contact.phone
                )

            holder.checkBox.setOnCheckedChangeListener {
                    _, checked ->

                    if (checked) {

                        selectedPhones.add(
                            contact.phone
                        )

                    } else {

                        selectedPhones.remove(
                            contact.phone
                        )
                    }

                    onSelectionChanged()
                }

            holder.root.setOnClickListener {

                holder.checkBox.isChecked =
                    !holder.checkBox.isChecked
            }
        }

        fun replaceContacts(
            contacts: List<Contact>
        ) {

            visibleContacts.clear()

            visibleContacts.addAll(
                contacts
            )

            selectedPhones.clear()

            notifyDataSetChanged()
        }

        fun filter(
            query: String
        ) {

            val q =
                query.trim()
                    .lowercase()

            visibleContacts.clear()

            if (q.isEmpty()) {

                visibleContacts.addAll(
                    allContacts
                )

            } else {

                visibleContacts.addAll(
                    allContacts.filter {

                        it.name
                            .lowercase()
                            .contains(q) ||

                                it.phone
                                    .contains(q)
                    }
                )
            }

            notifyDataSetChanged()
        }

        fun selectAll() {

            visibleContacts.forEach {

                selectedPhones.add(
                    it.phone
                )
            }

            notifyDataSetChanged()

            onSelectionChanged()
        }

        fun unselectAll() {

            visibleContacts.forEach {

                selectedPhones.remove(
                    it.phone
                )
            }

            notifyDataSetChanged()

            onSelectionChanged()
        }

        fun selectRange(
            from: Int,
            to: Int
        ) {

            val start =
                minOf(from, to) - 1

            val end =
                maxOf(from, to)

            for (
                index in start until end
            ) {

                visibleContacts
                    .getOrNull(index)
                    ?.let {

                        selectedPhones.add(
                            it.phone
                        )
                    }
            }

            notifyDataSetChanged()

            onSelectionChanged()
        }

        fun unselectRange(
            from: Int,
            to: Int
        ) {

            val start =
                minOf(from, to) - 1

            val end =
                maxOf(from, to)

            for (
                index in start until end
            ) {

                visibleContacts
                    .getOrNull(index)
                    ?.let {

                        selectedPhones.remove(
                            it.phone
                        )
                    }
            }

            notifyDataSetChanged()

            onSelectionChanged()
        }

        fun getVisibleCount(): Int =
            visibleContacts.size

        fun getSelectedPhones():
                Set<String> =
            selectedPhones.toSet()

        fun notifyContactStatusChanged(
            phone: String
        ) {

            val index =
                visibleContacts.indexOfFirst {
                    it.phone == phone
                }

            if (index >= 0) {
                notifyItemChanged(index)
            }
        }
    }
}
