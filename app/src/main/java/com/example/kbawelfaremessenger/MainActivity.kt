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
import android.graphics.Typeface
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.OpenableColumns
import android.provider.Settings
import android.telephony.SmsManager
import android.telephony.SubscriptionManager
import android.text.Editable
import android.text.TextWatcher
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
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

        private const val PREF_PERMISSION_STATE =
            "sms_permission_state"

        private const val KEY_PERMISSION_REQUESTED =
            "permission_request_attempted"
    }

    // ---------------------------------------------------------
    // Views
    // ---------------------------------------------------------

    private lateinit var btnSettings: Button

    private lateinit var txtCsvHeader: TextView
    private lateinit var txtRangeHeader: TextView
    private lateinit var txtMessageHeader: TextView
    private lateinit var txtSendHeader: TextView
    private lateinit var txtOtherHeader: TextView

    private lateinit var layoutCsvSection: LinearLayout
    private lateinit var layoutRangeSection: LinearLayout
    private lateinit var layoutMessageSection: LinearLayout
    private lateinit var layoutSendSection: LinearLayout
    private lateinit var layoutOtherSection: LinearLayout

    private lateinit var edtMessage: EditText
    private lateinit var edtSearch: EditText
    private lateinit var edtRangeFrom: EditText
    private lateinit var edtRangeTo: EditText

    private lateinit var txtStatus: TextView
    private lateinit var txtStats: TextView
    private lateinit var txtSelected: TextView
    private lateinit var txtFile: TextView
    private lateinit var txtSendCount: TextView

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

    // ---------------------------------------------------------
    // Data / State
    // ---------------------------------------------------------

    private val contacts = mutableListOf<Contact>()

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

    private lateinit var appSettings: AppSettings

    /*
     * If SMS permissions are requested, this stores the action
     * that should continue after all required permissions are granted.
     */
    private var pendingSmsPermissionAction:
            (() -> Unit)? = null

    // ---------------------------------------------------------
    // CSV picker
    // ---------------------------------------------------------

    private val pickCsvLauncher =
        registerForActivityResult(
            ActivityResultContracts.OpenDocument()
        ) { uri ->

            if (uri != null) {
                loadCsv(uri)
            }
        }

    // =========================================================
    // SMS + PHONE PERMISSIONS
    // =========================================================

    /**
     * IMPORTANT:
     *
     * Do not check the RequestMultiplePermissions callback map
     * directly for permissions that were not requested.
     *
     * Android can return only the permissions that were actually
     * requested, and Android may group phone-related permissions.
     *
     * Therefore the callback always checks the REAL current
     * permission state through hasSmsPermissions().
     */
    private val smsPermissionLauncher =
        registerForActivityResult(
            ActivityResultContracts.RequestMultiplePermissions()
        ) { _ ->

            if (hasSmsPermissions()) {

                markSmsPermissionRequestCompleted()

                val action =
                    pendingSmsPermissionAction

                pendingSmsPermissionAction =
                    null

                if (action != null) {
                    action()
                } else {
                    startSmsAfterPermission()
                }

                return@registerForActivityResult
            }

            /*
             * At this point Android has returned from the permission
             * request but one or more required permissions are still
             * missing.
             */
            val missingPermissions =
                getMissingSmsPermissions()

            pendingSmsPermissionAction =
                null

            val permanentlyDenied =
                hasPermanentlyDeniedSmsPermission(
                    missingPermissions
                )

            if (permanentlyDenied) {

                showPermissionSettingsDialog()

            } else {

                showPermissionRequiredDialog(
                    missingPermissions
                )
            }
        }

    // ---------------------------------------------------------
    // SMS sent receiver
    // ---------------------------------------------------------

    private val smsSentReceiver =
        object : BroadcastReceiver() {

            override fun onReceive(
                context: Context?,
                intent: Intent?
            ) {

                if (intent?.action != SMS_SENT_ACTION) {
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
                    ) ?: return

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

                    pendingSms.remove(phone)

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

    // ---------------------------------------------------------
    // Lifecycle
    // ---------------------------------------------------------

    override fun onCreate(
        savedInstanceState: Bundle?
    ) {

        super.onCreate(
            savedInstanceState
        )

        setContentView(
            R.layout.activity_main
        )

        appSettings =
            AppSettingsManager.load(this)

        initialiseViews()
        setupRecycler()
        setupButtons()
        setupMessage()
        setupSearch()
        setupCollapsibleSections()
        setupSmsReceiver()
        setupButtonColors()

        updateCounts()
    }

    override fun onResume() {

        super.onResume()

        appSettings =
            AppSettingsManager.load(this)

        applyMessageSetting()

        updateCounts()

        /*
         * If the user went to Android Settings and enabled the
         * required permissions, allow the pending action to continue.
         */
        if (
            pendingSmsPermissionAction != null &&
            hasSmsPermissions()
        ) {

            val action =
                pendingSmsPermissionAction

            pendingSmsPermissionAction =
                null

            action?.invoke()
        }

        AppLogger.info(
            this,
            "MAIN",
            "Main screen resumed."
        )
    }

    // ---------------------------------------------------------
    // View initialization
    // ---------------------------------------------------------

    private fun initialiseViews() {

        btnSettings =
            findViewById(R.id.btnSettings)

        txtCsvHeader =
            findViewById(R.id.txtCsvHeader)

        txtRangeHeader =
            findViewById(R.id.txtRangeHeader)

        txtMessageHeader =
            findViewById(R.id.txtMessageHeader)

        txtSendHeader =
            findViewById(R.id.txtSendHeader)

        txtOtherHeader =
            findViewById(R.id.txtOtherHeader)

        layoutCsvSection =
            findViewById(R.id.layoutCsvSection)

        layoutRangeSection =
            findViewById(R.id.layoutRangeSection)

        layoutMessageSection =
            findViewById(R.id.layoutMessageSection)

        layoutSendSection =
            findViewById(R.id.layoutSendSection)

        layoutOtherSection =
            findViewById(R.id.layoutOtherSection)

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

        txtSendCount =
            findViewById(R.id.txtSendCount)

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

    // ---------------------------------------------------------
    // RecyclerView
    // ---------------------------------------------------------

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

        recyclerContacts.isNestedScrollingEnabled =
            false

        recyclerContacts.setHasFixedSize(
            false
        )

        recyclerContacts.overScrollMode =
            View.OVER_SCROLL_IF_CONTENT_SCROLLS
    }

    // ---------------------------------------------------------
    // Buttons
    // ---------------------------------------------------------

    private fun setupButtons() {

        btnSettings.setOnClickListener {

            AppLogger.info(
                this,
                "SETTINGS",
                "Settings screen opened."
            )

            startActivity(
                Intent(
                    this,
                    SettingsActivity::class.java
                )
            )
        }

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
                getCurrentMessage().trim()

            if (message.isEmpty()) {

                showAlert(
                    "Message Required",
                    "Please enter a message first."
                )

                return@setOnClickListener
            }

            if (selectedContacts().isEmpty()) {

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

            AppLogger.info(
                this,
                "MAIN",
                "Selection and SMS status reset."
            )
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

            AppLogger.info(
                this,
                "CSV",
                "All contact data cleared."
            )
        }
    }

    // ---------------------------------------------------------
    // Collapsible sections
    // ---------------------------------------------------------

    private fun setupCollapsibleSections() {

        setupCollapsibleSection(
            txtCsvHeader,
            layoutCsvSection,
            true
        )

        setupCollapsibleSection(
            txtRangeHeader,
            layoutRangeSection,
            false
        )

        setupCollapsibleSection(
            txtMessageHeader,
            layoutMessageSection,
            true
        )

        setupCollapsibleSection(
            txtSendHeader,
            layoutSendSection,
            true
        )

        setupCollapsibleSection(
            txtOtherHeader,
            layoutOtherSection,
            false
        )
    }

    private fun setupCollapsibleSection(
        header: TextView,
        section: View,
        initiallyExpanded: Boolean
    ) {

        setSectionState(
            header,
            section,
            initiallyExpanded
        )

        header.setOnClickListener {

            val expanded =
                section.visibility ==
                        View.VISIBLE

            setSectionState(
                header,
                section,
                !expanded
            )
        }
    }

    private fun setSectionState(
        header: TextView,
        section: View,
        expanded: Boolean
    ) {

        section.visibility =
            if (expanded) {
                View.VISIBLE
            } else {
                View.GONE
            }

        val originalText =
            header.text.toString()

        val cleanText =
            originalText
                .removePrefix("▼")
                .removePrefix("▶")
                .trim()

        header.text =
            if (expanded) {
                "▼  $cleanText"
            } else {
                "▶  $cleanText"
            }
    }

    // ---------------------------------------------------------
    // Range selection
    // ---------------------------------------------------------

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

        if (from == null || to == null) {

            showAlert(
                "Range Selection",
                "Please enter valid From and To numbers."
            )

            return
        }

        if (from < 1 || to < 1) {

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

    // ---------------------------------------------------------
    // Message settings
    // ---------------------------------------------------------

    private fun setupMessage() {

        if (appSettings.editMessageOnScreen) {

            if (
                edtMessage.text
                    .toString()
                    .trim()
                    .isEmpty()
            ) {

                edtMessage.setText(
                    appSettings.defaultMessage
                )
            }
        }

        applyMessageSetting()
    }

    private fun applyMessageSetting() {

        if (!::edtMessage.isInitialized) {
            return
        }

        if (appSettings.editMessageOnScreen) {

            txtMessageHeader.visibility =
                View.VISIBLE

            layoutMessageSection.visibility =
                View.VISIBLE

            if (
                edtMessage.text
                    .toString()
                    .trim()
                    .isEmpty()
            ) {

                edtMessage.setText(
                    appSettings.defaultMessage
                )
            }

        } else {

            txtMessageHeader.visibility =
                View.GONE

            layoutMessageSection.visibility =
                View.GONE
        }
    }

    private fun getCurrentMessage(): String {

        return if (
            appSettings.editMessageOnScreen
        ) {

            edtMessage.text.toString()

        } else {

            appSettings.defaultMessage
        }
    }

    // ---------------------------------------------------------
    // Search
    // ---------------------------------------------------------

    private fun setupSearch() {

        edtSearch.addTextChangedListener(
            object : TextWatcher {

                override fun beforeTextChanged(
                    s: CharSequence?,
                    start: Int,
                    count: Int,
                    after: Int
                ) {
                }

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
                ) {
                }
            }
        )
    }

    // ---------------------------------------------------------
    // SMS receiver
    // ---------------------------------------------------------

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

            @Suppress("UnspecifiedRegisterReceiverFlag")
            registerReceiver(
                smsSentReceiver,
                filter
            )
        }

        smsReceiverRegistered = true
    }

    // ---------------------------------------------------------
    // Button colors
    // ---------------------------------------------------------

    private fun setupButtonColors() {

        setButtonColor(btnUpload, "#315A7D")
        setButtonColor(btnSelectAll, "#526777")
        setButtonColor(btnUnselectAll, "#71808C")
        setButtonColor(btnSelectRange, "#4F6280")
        setButtonColor(btnUnselectRange, "#68798F")
        setButtonColor(btnPreview, "#596B7A")
        setButtonColor(btnTestSms, "#9A7B32")
        setButtonColor(btnSendSms, "#34704A")
        setButtonColor(btnWhatsApp, "#36796D")
        setButtonColor(btnReset, "#9A6840")
        setButtonColor(btnClearData, "#9A4C4C")
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

    // ---------------------------------------------------------
    // CSV loading
    // ---------------------------------------------------------

    private fun loadCsv(uri: Uri) {

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
                parseCsvLine(lines[0])
                    .mapIndexed { index, value ->

                        if (index == 0) {

                            value
                                .trim()
                                .removePrefix("\uFEFF")

                        } else {

                            value.trim()
                        }
                    }

            var nameIndex =
                findConfiguredHeaderIndex(
                    headers,
                    appSettings.nameColumn
                )

            if (nameIndex == -1) {

                nameIndex =
                    findHeaderIndex(
                        headers,
                        listOf(
                            "Name",
                            "Original Name",
                            "Given Name"
                        )
                    )
            }

            var phoneIndex =
                findConfiguredHeaderIndex(
                    headers,
                    appSettings.phoneColumn
                )

            if (phoneIndex == -1) {

                phoneIndex =
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
            }

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

                AppLogger.error(
                    this,
                    "CSV",
                    "Could not find a mobile/phone column."
                )

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
                    normalizePhone(
                        rawPhone
                    )

                if (phone.isEmpty()) {

                    if (
                        appSettings.skipInvalidNumbers
                    ) {
                        continue
                    }

                    continue
                }

                if (
                    appSettings.removeDuplicates &&
                    !usedPhones.add(phone)
                ) {
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
                    cleanName(
                        rawName
                    ).ifBlank {
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

            AppLogger.success(
                this,
                "CSV",
                "CSV loaded successfully. Contacts: ${contacts.size}"
            )

        } catch (e: Exception) {

            AppLogger.error(
                this,
                "CSV",
                "CSV loading failed: ${e.message}"
            )

            showAlert(
                "CSV Error",
                e.message
                    ?: "Unable to read CSV file."
            )
        }
    }

    private fun findConfiguredHeaderIndex(
        headers: List<String>,
        configuredName: String
    ): Int {

        if (configuredName.isBlank()) {
            return -1
        }

        return headers.indexOfFirst {

            it.trim().equals(
                configuredName.trim(),
                ignoreCase = true
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

    // ---------------------------------------------------------
    // Phone normalization
    // ---------------------------------------------------------

    private fun normalizePhone(
        value: String
    ): String {

        val candidates =
            value
                .trim()
                .replace(
                    ".0",
                    ""
                )
                .split(
                    Regex("[,;/\\s]+")
                )

        val countryCode =
            appSettings.defaultCountryCode
                .trim()
                .removePrefix("+")
                .removePrefix("00")

        for (candidate in candidates) {

            var number =
                candidate.replace(
                    Regex("[^0-9+]"),
                    ""
                )

            if (number.startsWith("+")) {
                number =
                    number.substring(1)
            }

            if (number.startsWith("00")) {
                number =
                    number.substring(2)
            }

            if (
                number.startsWith(countryCode) &&
                number.length ==
                countryCode.length + 10
            ) {

                number =
                    number.substring(
                        countryCode.length
                    )
            }

            number =
                number.filter {
                    it.isDigit()
                }

            if (number.length == 10) {
                return countryCode + number
            }
        }

        return ""
    }

    // ---------------------------------------------------------
    // Contact selection
    // ---------------------------------------------------------

    private fun selectedContacts(): List<Contact> {

        val selectedPhones =
            adapter.getSelectedPhones()

        return contacts.filter {

            selectedPhones.contains(
                it.phone
            )
        }
    }

    // ---------------------------------------------------------
    // Message personalization
    // ---------------------------------------------------------

    private fun personaliseMessage(
        contact: Contact
    ): String {

        var message =
            getCurrentMessage()

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

    // ---------------------------------------------------------
    // Preview
    // ---------------------------------------------------------

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

                textSize =
                    15f

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

    // =========================================================
    // STRICT SMS LICENSE / SIM VERIFICATION
    // =========================================================

    private data class VerifiedSmsSubscription(
        val subscriptionId: Int,
        val phone: String,
        val smsManager: SmsManager
    )

    private class SmsLicenseException(
        message: String
    ) : Exception(message)

    /**
     * Checks that all permissions required for strict
     * SMS/SIM verification are granted.
     */
    private fun hasSmsPermissions(): Boolean {

        return ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.SEND_SMS
        ) == PackageManager.PERMISSION_GRANTED &&

                ContextCompat.checkSelfPermission(
                    this,
                    Manifest.permission.READ_PHONE_STATE
                ) == PackageManager.PERMISSION_GRANTED &&

                ContextCompat.checkSelfPermission(
                    this,
                    Manifest.permission.READ_PHONE_NUMBERS
                ) == PackageManager.PERMISSION_GRANTED
    }

    /**
     * Returns only permissions that are currently missing.
     */
    private fun getMissingSmsPermissions(): Array<String> {

        val missing =
            mutableListOf<String>()

        if (
            ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.SEND_SMS
            ) != PackageManager.PERMISSION_GRANTED
        ) {

            missing.add(
                Manifest.permission.SEND_SMS
            )
        }

        if (
            ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.READ_PHONE_STATE
            ) != PackageManager.PERMISSION_GRANTED
        ) {

            missing.add(
                Manifest.permission.READ_PHONE_STATE
            )
        }

        if (
            ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.READ_PHONE_NUMBERS
            ) != PackageManager.PERMISSION_GRANTED
        ) {

            missing.add(
                Manifest.permission.READ_PHONE_NUMBERS
            )
        }

        return missing.toTypedArray()
    }

    private fun permissionWasRequestedBefore(): Boolean {

        return getSharedPreferences(
            PREF_PERMISSION_STATE,
            MODE_PRIVATE
        ).getBoolean(
            KEY_PERMISSION_REQUESTED,
            false
        )
    }

    private fun markSmsPermissionRequestCompleted() {

        getSharedPreferences(
            PREF_PERMISSION_STATE,
            MODE_PRIVATE
        )
            .edit()
            .putBoolean(
                KEY_PERMISSION_REQUESTED,
                true
            )
            .apply()
    }

    /**
     * Detects the case where Android will no longer show the
     * permission dialog and the user must enable it manually
     * from App Settings.
     */
    private fun hasPermanentlyDeniedSmsPermission(
        missingPermissions: Array<String>
    ): Boolean {

        if (!permissionWasRequestedBefore()) {
            return false
        }

        return missingPermissions.any { permission ->

            !shouldShowRequestPermissionRationale(
                permission
            )
        }
    }

    /**
     * Shows the normal permission-required dialog.
     */
    private fun showPermissionRequiredDialog(
        missingPermissions: Array<String>
    ) {

        val message =
            buildString {

                append(
                    "The following permissions are required for secure SMS sending:\n\n"
                )

                if (
                    missingPermissions.contains(
                        Manifest.permission.SEND_SMS
                    )
                ) {

                    append(
                        "• Send SMS\n"
                    )
                }

                if (
                    missingPermissions.contains(
                        Manifest.permission.READ_PHONE_STATE
                    )
                ) {

                    append(
                        "• Read phone state\n"
                    )
                }

                if (
                    missingPermissions.contains(
                        Manifest.permission.READ_PHONE_NUMBERS
                    )
                ) {

                    append(
                        "• Read phone numbers\n"
                    )
                }

                append(
                    "\nSMS sending is blocked until all required permissions are granted."
                )
            }

        AlertDialog.Builder(this)
            .setTitle(
                "Permission Required"
            )
            .setMessage(
                message
            )
            .setPositiveButton(
                "Allow"
            ) { _, _ ->

                requestMissingSmsPermissions()
            }
            .setNegativeButton(
                "Cancel",
                null
            )
            .show()
    }

    /**
     * Handles permissions that Android will not ask for again.
     */
    private fun showPermissionSettingsDialog() {

        AlertDialog.Builder(this)
            .setTitle(
                "Permission Required"
            )
            .setMessage(
                "One or more permissions required for secure SMS sending have been denied permanently.\n\n" +
                        "Android will not show the permission popup again for these permissions.\n\n" +
                        "Please open App Settings and enable:\n" +
                        "• Send SMS\n" +
                        "• Phone permissions\n\n" +
                        "SMS sending remains blocked until all required permissions are enabled."
            )
            .setNegativeButton(
                "Cancel",
                null
            )
            .setPositiveButton(
                "Open Settings"
            ) { _, _ ->

                try {

                    val intent =
                        Intent(
                            Settings.ACTION_APPLICATION_DETAILS_SETTINGS
                        ).apply {

                            data =
                                Uri.parse(
                                    "package:$packageName"
                                )
                        }

                    startActivity(intent)

                } catch (e: Exception) {

                    showAlert(
                        "Settings",
                        "Unable to open Android App Settings."
                    )
                }
            }
            .show()
    }

    /**
     * Requests only the permissions that are actually missing.
     *
     * This is the method that starts the Android permission popup.
     */
    private fun requestMissingSmsPermissions() {

        val missingPermissions =
            getMissingSmsPermissions()

        if (missingPermissions.isEmpty()) {

            val action =
                pendingSmsPermissionAction

            pendingSmsPermissionAction =
                null

            if (action != null) {
                action()
            } else {
                startSmsAfterPermission()
            }

            return
        }

        /*
         * Record that Android has now been asked.
         * This lets us distinguish a future permanent denial
         * from the first permission request.
         */
        markSmsPermissionRequestCompleted()

        smsPermissionLauncher.launch(
            missingPermissions
        )
    }

    /**
     * Main SMS permission entry point.
     */
    private fun checkSmsPermissionAndStart() {

        if (hasSmsPermissions()) {

            startSmsAfterPermission()

            return
        }

        pendingSmsPermissionAction =
            {
                startSmsAfterPermission()
            }

        requestMissingSmsPermissions()
    }

    private fun startSmsAfterPermission() {

        val selected =
            selectedContacts()

        if (selected.isEmpty()) {

            showAlert(
                "No Contacts Selected",
                "Please select at least one contact."
            )

            return
        }

        /*
         * First strict license/SIM preflight.
         * No confirmation dialog is shown until the
         * licensed SIM has been verified.
         */
        try {

            getVerifiedSmsSubscription()

        } catch (e: SmsLicenseException) {

            AppLogger.warning(
                this,
                "LICENSE",
                "Bulk SMS blocked: ${e.message}"
            )

            showAlert(
                "SMS Not Allowed",
                e.message
                    ?: "License/SIM verification failed."
            )

            return
        }

        if (
            appSettings.confirmBeforeBulkSend
        ) {

            startSmsConfirmation()

        } else {

            startSmsOperation(
                selected
            )
        }
    }

    // =========================================================
    // STRICT LICENSE / SIM CHECK
    // =========================================================

    private fun getVerifiedSmsSubscription():
            VerifiedSmsSubscription {

        if (!hasSmsPermissions()) {

            throw SmsLicenseException(
                "Required SMS/SIM permissions are not granted."
            )
        }

        val license =
            LicenseManager.getValidLicense(this)
                ?: throw SmsLicenseException(
                    "No valid license is installed, or the license has expired."
                )

        val licensedPhone =
            normalizePhone(
                license.phone
            )

        if (licensedPhone.isEmpty()) {

            throw SmsLicenseException(
                "The installed license does not contain a valid phone number."
            )
        }

        val subscriptionId =
            SubscriptionManager
                .getDefaultSmsSubscriptionId()

        if (
            subscriptionId ==
            SubscriptionManager.INVALID_SUBSCRIPTION_ID
        ) {

            throw SmsLicenseException(
                "No default SMS SIM is selected on this phone."
            )
        }

        val subscriptionManager =
            getSystemService(
                SubscriptionManager::class.java
            )

        if (subscriptionManager == null) {

            throw SmsLicenseException(
                "Unable to access SIM subscription information."
            )
        }

        val subscriptionInfo =
            try {

                subscriptionManager
                    .getActiveSubscriptionInfo(
                        subscriptionId
                    )

            } catch (e: SecurityException) {

                throw SmsLicenseException(
                    "Android denied access to the SIM phone number."
                )
            }

        if (subscriptionInfo == null) {

            throw SmsLicenseException(
                "The selected SMS SIM is not active."
            )
        }

        val rawSimPhone =
            subscriptionInfo.number
                ?.trim()
                .orEmpty()

        if (rawSimPhone.isEmpty()) {

            throw SmsLicenseException(
                "Android could not verify the phone number of the selected SMS SIM.\n\n" +
                        "For security reasons SMS sending is blocked."
            )
        }

        val actualSimPhone =
            normalizePhone(
                rawSimPhone
            )

        if (actualSimPhone.isEmpty()) {

            throw SmsLicenseException(
                "The selected SMS SIM returned an invalid phone number.\n\n" +
                        "SMS sending is blocked."
            )
        }

        if (actualSimPhone != licensedPhone) {

            AppLogger.warning(
                this,
                "LICENSE",
                "Licensed phone does not match selected SMS SIM."
            )

            throw SmsLicenseException(
                "License/SIM mismatch.\n\n" +
                        "Licensed phone: $licensedPhone\n" +
                        "Selected SMS SIM: $actualSimPhone\n\n" +
                        "SMS sending is blocked."
            )
        }

        val smsManager =
            try {

                SmsManager.getSmsManagerForSubscriptionId(
                    subscriptionId
                )

            } catch (e: Exception) {

                throw SmsLicenseException(
                    "Unable to access the SMS service for the licensed SIM."
                )
            }

        AppLogger.info(
            this,
            "LICENSE",
            "License/SIM verification successful."
        )

        return VerifiedSmsSubscription(
            subscriptionId = subscriptionId,
            phone = actualSimPhone,
            smsManager = smsManager
        )
    }

    // ---------------------------------------------------------
    // SMS confirmation
    // ---------------------------------------------------------

    private fun startSmsConfirmation() {

        val selected =
            selectedContacts()

        val alreadySent =
            selected.count {
                it.smsStatus ==
                        SmsStatus.SENT
            }

        val willSend =
            if (
                appSettings.skipAlreadySent
            ) {

                selected.count {
                    it.smsStatus !=
                            SmsStatus.SENT
                }

            } else {

                selected.size
            }

        val skipped =
            if (
                appSettings.skipAlreadySent
            ) {

                alreadySent

            } else {

                0
            }

        val confirmation =
            """
Selected: ${selected.size}

Already sent: $alreadySent

Will send now: $willSend

Will be skipped: $skipped

Each selected contact will receive one personalized SMS.

${if (appSettings.skipAlreadySent)
                "Contacts already marked SENT will be skipped automatically."
            else
                "Contacts marked SENT will also be sent again."}

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

    // ---------------------------------------------------------
    // SMS operation
    // ---------------------------------------------------------

    private fun startSmsOperation(
        selected: List<Contact>
    ) {

        if (
            smsOperationActive ||
            selected.isEmpty()
        ) {
            return
        }

        /*
         * SECOND strict verification immediately before
         * starting the bulk operation.
         */
        try {

            getVerifiedSmsSubscription()

        } catch (e: SmsLicenseException) {

            AppLogger.warning(
                this,
                "LICENSE",
                "Bulk SMS operation blocked: ${e.message}"
            )

            showAlert(
                "SMS Not Allowed",
                e.message
                    ?: "License/SIM verification failed."
            )

            return
        }

        operationResults.clear()

        val queue =
            if (
                appSettings.skipAlreadySent
            ) {

                selected.filter {
                    it.smsStatus !=
                            SmsStatus.SENT
                }

            } else {

                selected
            }

        if (
            appSettings.skipAlreadySent
        ) {

            selected
                .filter {
                    it.smsStatus ==
                            SmsStatus.SENT
                }
                .forEach { contact ->

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
            queue

        if (sendQueue.isEmpty()) {

            txtStatus.text =
                "All selected contacts were already sent."

            updateSendCount()

            showSmsResultAlert(
                selected
            )

            return
        }

        smsOperationActive =
            true

        queueIndex =
            0

        pendingSms.clear()

        requestToPhone.clear()

        btnSendSms.isEnabled =
            false

        txtStatus.text =
            "Sending SMS to ${sendQueue.size} contacts..."

        updateCounts()

        updateSendCount()

        AppLogger.info(
            this,
            "SMS",
            "SMS operation started. Selected: ${selected.size}, queue: ${sendQueue.size}"
        )

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

        updateSendCount()

        val message =
            personaliseMessage(
                contact
            )

        try {

            sendSmsForContact(
                contact,
                message
            )

        } catch (e: SmsLicenseException) {

            contact.smsStatus =
                SmsStatus.NONE

            adapter.notifyContactStatusChanged(
                contact.phone
            )

            abortSmsOperation(
                e.message
                    ?: "License/SIM verification failed."
            )

            return

        } catch (e: Exception) {

            completeSms(
                contact,
                false,
                e.message
                    ?: "SMS sending error."
            )
        }

        if (!smsOperationActive) {
            return
        }

        handler.postDelayed(
            {
                dispatchNextSms()
            },
            appSettings.smsDelayMs
        )
    }

    private fun sendSmsForContact(
        contact: Contact,
        message: String
    ) {

        /*
         * CRITICAL SECURITY CHECK:
         *
         * Performed immediately before the actual SMS API call.
         */
        val verified =
            getVerifiedSmsSubscription()

        val smsManager =
            verified.smsManager

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

    private fun abortSmsOperation(
        reason: String
    ) {

        smsOperationActive =
            false

        btnSendSms.isEnabled =
            true

        handler.removeCallbacksAndMessages(
            null
        )

        txtStatus.text =
            "SMS sending stopped: $reason"

        updateCounts()
        updateSendCount()

        AppLogger.warning(
            this,
            "SMS",
            "SMS operation stopped because of license/SIM verification: $reason"
        )

        showAlert(
            "SMS Sending Stopped",
            reason
        )
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

        val status =
            if (success) {
                "SENT"
            } else {
                "FAILED"
            }

        operationResults[
            contact.phone
        ] =
            SmsResult(
                contact,
                status,
                detail
            )

        adapter.notifyContactStatusChanged(
            contact.phone
        )

        updateCounts()
        updateSendCount()

        txtStatus.text =
            "SMS progress: ${sentCount()} sent, ${failedCount()} failed."

        AppLogger.info(
            this,
            "SMS",
            "${contact.name} (${contact.phone}) - $status - $detail"
        )
    }

    private fun checkSmsOperationFinished() {

        if (!smsOperationActive) {
            return
        }

        if (
            queueIndex >= sendQueue.size &&
            pendingSms.isEmpty()
        ) {

            smsOperationActive =
                false

            btnSendSms.isEnabled =
                true

            updateCounts()
            updateSendCount()

            txtStatus.text =
                "SMS sending completed."

            AppLogger.success(
                this,
                "SMS",
                "SMS operation completed. Sent: ${sentCount()}, Failed: ${failedCount()}"
            )

            showSmsResultAlert(
                operationResults.values.map {
                    it.contact
                }
            )
        }
    }

    // ---------------------------------------------------------
    // SEND COUNT
    // ---------------------------------------------------------

    private fun updateSendCount() {

        if (!::txtSendCount.isInitialized) {
            return
        }

        val selected =
            adapter.getSelectedPhones().size

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

        txtSendCount.text =
            when {

                smsOperationActive ->
                    "Sent: $sent / $selected"

                selected > 0 ->
                    "Sent: $sent / $selected"

                else ->
                    "Sent: $sent"
            }

        if (
            failed > 0 ||
            sending > 0
        ) {

            txtSendCount.text =
                "Sent: $sent / $selected" +
                        "   Failed: $failed" +
                        "   Sending: $sending"
        }
    }

    // ---------------------------------------------------------
    // SMS result
    // ---------------------------------------------------------

    private fun showSmsResultAlert(
        contactsForResult: List<Contact>
    ) {

        val results =
            contactsForResult.mapNotNull {

                operationResults[
                    it.phone
                ]
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
                "$symbol ${index + 1}. ${result.contact.name}\n"
            )

            builder.append(
                "   Mobile: ${result.contact.phone}\n"
            )

            builder.append(
                "   Status: ${result.status}\n"
            )

            builder.append(
                "   ${result.detail}\n\n"
            )
        }

        val textView =
            TextView(this).apply {

                text =
                    builder.toString()

                textSize =
                    15f

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

    // =========================================================
    // TEST SMS
    // =========================================================

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

        /*
         * FIX:
         *
         * Permission must be checked BEFORE license/SIM
         * verification.
         *
         * Previously testSms() called getVerifiedSmsSubscription()
         * first, so Android's permission popup was never reached.
         */
        if (!hasSmsPermissions()) {

            pendingSmsPermissionAction =
                {
                    showTestSmsConfirmation(
                        contact
                    )
                }

            requestMissingSmsPermissions()

            return
        }

        showTestSmsConfirmation(
            contact
        )
    }

    private fun showTestSmsConfirmation(
        contact: Contact
    ) {

        val message =
            personaliseMessage(
                contact
            )

        /*
         * Verify BEFORE showing final Test SMS confirmation.
         */
        try {

            getVerifiedSmsSubscription()

        } catch (e: SmsLicenseException) {

            AppLogger.warning(
                this,
                "LICENSE",
                "Test SMS blocked: ${e.message}"
            )

            showAlert(
                "Test SMS Not Allowed",
                e.message
                    ?: "License/SIM verification failed."
            )

            return
        }

        AlertDialog.Builder(this)
            .setTitle(
                "Test SMS"
            )
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

                sendTestSmsAfterConfirmation(
                    contact,
                    message
                )
            }
            .show()
    }

    private fun sendTestSmsAfterConfirmation(
        contact: Contact,
        message: String
    ) {

        if (!hasSmsPermissions()) {

            pendingSmsPermissionAction =
                {
                    sendTestSmsNow(
                        contact,
                        message
                    )
                }

            requestMissingSmsPermissions()

            return
        }

        sendTestSmsNow(
            contact,
            message
        )
    }

    private fun sendTestSmsNow(
        contact: Contact,
        message: String
    ) {

        try {

            /*
             * FINAL strict verification immediately before
             * actual Test SMS API call.
             */
            val verified =
                getVerifiedSmsSubscription()

            verified.smsManager.sendTextMessage(
                contact.phone,
                null,
                message,
                null,
                null
            )

            AppLogger.success(
                this,
                "SMS",
                "Test SMS submitted using verified subscription ${verified.subscriptionId}."
            )

            showAlert(
                "Test SMS",
                "SMS request submitted.\n\n" +
                        "Name: ${contact.name}\n" +
                        "Number: ${contact.phone}"
            )

        } catch (e: SmsLicenseException) {

            AppLogger.warning(
                this,
                "LICENSE",
                "Test SMS blocked at final verification: ${e.message}"
            )

            showAlert(
                "Test SMS Not Allowed",
                e.message
                    ?: "License/SIM verification failed."
            )

        } catch (e: Exception) {

            AppLogger.error(
                this,
                "SMS",
                "Test SMS failed: ${e.message}"
            )

            showAlert(
                "Test SMS Failed",
                e.message
                    ?: "Unable to send SMS."
            )
        }
    }

    // ---------------------------------------------------------
    // WhatsApp
    // ---------------------------------------------------------

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
            personaliseMessage(
                contact
            )

        try {

            val uri =
                Uri.parse(
                    "https://wa.me/${contact.phone}" +
                            "?text=${Uri.encode(message)}"
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

    // ---------------------------------------------------------
    // Counts
    // ---------------------------------------------------------

    private fun updateCounts() {

        val selected =
            adapter.getSelectedPhones().size

        val total =
            contacts.size

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
            "Total: $total  |  " +
                    "Sent: $sent  |  " +
                    "Failed: $failed  |  " +
                    "Pending/Sending: $sending"

        updateSendCount()
    }

    private fun sentCount() =
        contacts.count {
            it.smsStatus ==
                    SmsStatus.SENT
        }

    private fun failedCount() =
        contacts.count {
            it.smsStatus ==
                    SmsStatus.FAILED
        }

    private fun statusText(
        status: SmsStatus
    ) =
        when (status) {

            SmsStatus.NONE ->
                "Not sent"

            SmsStatus.SENDING ->
                "Sending..."

            SmsStatus.SENT ->
                "SENT"

            SmsStatus.FAILED ->
                "FAILED"
        }

    // ---------------------------------------------------------
    // SMS error messages
    // ---------------------------------------------------------

    private fun smsErrorMessage(
        code: Int
    ) =
        when (code) {

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

    // ---------------------------------------------------------
    // File name
    // ---------------------------------------------------------

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

        return result
            ?: "CSV file"
    }

    // ---------------------------------------------------------
    // Alert
    // ---------------------------------------------------------

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

    // ---------------------------------------------------------
    // Destroy
    // ---------------------------------------------------------

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
    // CONTACT ADAPTER
    // =========================================================

    private class ContactAdapter(
        private val allContacts: MutableList<Contact>,
        private val onSelectionChanged: () -> Unit
    ) : RecyclerView.Adapter<ContactAdapter.ContactViewHolder>() {

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

            val checkBox =
                CheckBox(root.context)

            val info =
                LinearLayout(root.context)

            val nameText =
                TextView(root.context)

            val phoneText =
                TextView(root.context)

            val detailsText =
                TextView(root.context)

            val statusText =
                TextView(root.context)

            init {

                root.orientation =
                    LinearLayout.HORIZONTAL

                root.gravity =
                    Gravity.CENTER_VERTICAL

                root.setPadding(
                    4,
                    7,
                    6,
                    7
                )

                checkBox.apply {

                    minWidth = 0
                    minHeight = 0

                    setPadding(
                        0,
                        0,
                        0,
                        0
                    )

                    gravity =
                        Gravity.CENTER
                }

                val checkboxParams =
                    LinearLayout.LayoutParams(
                        dp(
                            root.context,
                            42
                        ),
                        dp(
                            root.context,
                            42
                        )
                    ).apply {

                        gravity =
                            Gravity.CENTER_VERTICAL

                        rightMargin =
                            dp(
                                root.context,
                                5
                            )
                    }

                root.addView(
                    checkBox,
                    checkboxParams
                )

                info.orientation =
                    LinearLayout.VERTICAL

                info.gravity =
                    Gravity.START

                info.setPadding(
                    2,
                    0,
                    4,
                    0
                )

                nameText.textSize =
                    16f

                nameText.setTypeface(
                    null,
                    Typeface.BOLD
                )

                phoneText.textSize =
                    14f

                detailsText.textSize =
                    13f

                detailsText.setTextColor(
                    Color.DKGRAY
                )

                statusText.textSize =
                    13f

                statusText.setTypeface(
                    null,
                    Typeface.BOLD
                )

                info.addView(
                    nameText,
                    LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT
                    )
                )

                info.addView(
                    phoneText,
                    LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT
                    )
                )

                info.addView(
                    detailsText,
                    LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT
                    )
                )

                info.addView(
                    statusText,
                    LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT
                    )
                )

                root.addView(
                    info,
                    LinearLayout.LayoutParams(
                        0,
                        ViewGroup.LayoutParams.WRAP_CONTENT,
                        1f
                    )
                )
            }
        }

        override fun onCreateViewHolder(
            parent: ViewGroup,
            viewType: Int
        ): ContactViewHolder {

            return ContactViewHolder(
                LinearLayout(
                    parent.context
                )
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

            holder.phoneText.text =
                "Mobile: ${contact.phone}"

            val details =
                contact.fields.entries
                    .filter { entry ->

                        entry.key.isNotBlank() &&
                                entry.value.isNotBlank()
                    }
                    .joinToString(
                        separator = "\n"
                    ) { entry ->

                        "${entry.key}: ${entry.value}"
                    }

            if (details.isBlank()) {

                holder.detailsText.visibility =
                    View.GONE

            } else {

                holder.detailsText.visibility =
                    View.VISIBLE

                holder.detailsText.text =
                    details
            }

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

            holder.checkBox.setOnCheckedChangeListener(
                null
            )

            holder.checkBox.isChecked =
                selectedPhones.contains(
                    contact.phone
                )

            holder.checkBox.setOnCheckedChangeListener {
                    _,
                    checked ->

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
                query.trim().lowercase()

            visibleContacts.clear()

            if (q.isEmpty()) {

                visibleContacts.addAll(
                    allContacts
                )

            } else {

                visibleContacts.addAll(
                    allContacts.filter { contact ->

                        contact.name
                            .lowercase()
                            .contains(q) ||

                                contact.phone
                                    .contains(q) ||

                                contact.fields.any {
                                    (key, value) ->

                                    key.lowercase()
                                        .contains(q) ||

                                            value.lowercase()
                                                .contains(q)
                                }
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

        fun getSelectedPhones(): Set<String> =
            selectedPhones.toSet()

        fun notifyContactStatusChanged(
            phone: String
        ) {

            val index =
                visibleContacts.indexOfFirst {
                    it.phone == phone
                }

            if (index >= 0) {

                notifyItemChanged(
                    index
                )
            }
        }

        companion object {

            private fun dp(
                context: Context,
                value: Int
            ): Int {

                return (
                    value *
                            context.resources
                                .displayMetrics
                                .density
                    ).toInt()
            }
        }
    }
}
