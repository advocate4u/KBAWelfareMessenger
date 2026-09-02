package com.example.kbawelfaremessenger

import android.Manifest
import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.provider.OpenableColumns
import android.telephony.SmsManager
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.Worker
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import java.io.BufferedReader
import java.io.InputStreamReader
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import java.util.concurrent.TimeUnit


data class Contact(
    val name: String,
    val phone: String,
    val row: Int
)


class MainActivity : AppCompatActivity() {

    private val contacts = mutableListOf<Contact>()

    private lateinit var adapter: ContactAdapter

    private lateinit var message: EditText
    private lateinit var search: EditText
    private lateinit var status: TextView
    private lateinit var stats: TextView
    private lateinit var selectedText: TextView

    private var whatsappIndex = 0

    private val picker =
        registerForActivityResult(
            ActivityResultContracts.OpenDocument()
        ) { uri ->
            uri?.let {
                loadCsv(it)
            }
        }

    private var pendingSmsAction: (() -> Unit)? = null

    private val smsPermission =
        registerForActivityResult(
            ActivityResultContracts.RequestPermission()
        ) { granted ->

            if (granted) {
                pendingSmsAction?.invoke()
            } else {
                status.text =
                    "SMS permission was not granted."
            }

            pendingSmsAction = null
        }


    override fun onCreate(
        savedInstanceState: Bundle?
    ) {
        super.onCreate(savedInstanceState)

        setContentView(
            R.layout.activity_main
        )

        message =
            findViewById(
                R.id.edtMessage
            )

        search =
            findViewById(
                R.id.edtSearch
            )

        status =
            findViewById(
                R.id.txtStatus
            )

        stats =
            findViewById(
                R.id.txtStats
            )

        selectedText =
            findViewById(
                R.id.txtSelected
            )


        val recycler =
            findViewById<RecyclerView>(
                R.id.recyclerContacts
            )

        recycler.layoutManager =
            LinearLayoutManager(this)


        adapter =
            ContactAdapter(
                emptyList()
            ) {
                updateSelectedCount()
            }


        recycler.adapter = adapter


        message.setText(
            getPreferences(0)
                .getString(
                    "draft",
                    ""
                )
        )


        restoreContacts()


        findViewById<Button>(
            R.id.btnUpload
        ).setOnClickListener {

            picker.launch(
                arrayOf(
                    "text/csv",
                    "text/comma-separated-values",
                    "*/*"
                )
            )
        }


        findViewById<Button>(
            R.id.btnSelectAll
        ).setOnClickListener {

            adapter.selectAll()

            status.text =
                "All visible contacts selected."
        }


        findViewById<Button>(
            R.id.btnUnselectAll
        ).setOnClickListener {

            adapter.unselectAll()

            whatsappIndex = 0

            status.text =
                "All contacts unselected."
        }


        search.addTextChangedListener(
            object : android.text.TextWatcher {

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
                    refreshList()
                }

                override fun afterTextChanged(
                    s: android.text.Editable?
                ) {
                }
            }
        )


        findViewById<Button>(
            R.id.btnSaveDraft
        ).setOnClickListener {

            getPreferences(0)
                .edit()
                .putString(
                    "draft",
                    message.text.toString()
                )
                .apply()

            toast(
                "Draft saved"
            )
        }


        findViewById<Button>(
            R.id.btnPreview
        ).setOnClickListener {
            preview()
        }


        findViewById<Button>(
            R.id.btnTestSms
        ).setOnClickListener {

            ensureSmsPermission {
                testSms()
            }
        }


        findViewById<Button>(
            R.id.btnSendSms
        ).setOnClickListener {

            ensureSmsPermission {
                sendNow()
            }
        }


        findViewById<Button>(
            R.id.btnSchedule
        ).setOnClickListener {
            schedule()
        }


        findViewById<Button>(
            R.id.btnCancelSchedule
        ).setOnClickListener {

            WorkManager
                .getInstance(this)
                .cancelUniqueWork(
                    "kba_sms_schedule"
                )

            status.text =
                "Scheduled SMS cancelled."
        }


        findViewById<Button>(
            R.id.btnWhatsApp
        ).setOnClickListener {
            nextWhatsApp()
        }


        findViewById<Button>(
            R.id.btnReset
        ).setOnClickListener {

            search.setText("")

            adapter.clearSelection()

            whatsappIndex = 0

            status.text =
                "Selection and search reset."
        }


        findViewById<Button>(
            R.id.btnClearData
        ).setOnClickListener {

            showClearContactsDialog()
        }
    }


    private fun showClearContactsDialog() {

        AlertDialog.Builder(this)
            .setTitle(
                "Remove contacts?"
            )
            .setMessage(
                "This will remove the stored CSV contacts."
            )
            .setNegativeButton(
                "Cancel",
                null
            )
            .setPositiveButton(
                "Remove"
            ) { _, _ ->

                contacts.clear()

                getSharedPreferences(
                    "contacts",
                    0
                )
                    .edit()
                    .clear()
                    .apply()

                adapter.setContacts(
                    emptyList()
                )

                adapter.clearSelection()

                findViewById<TextView>(
                    R.id.txtFile
                ).text =
                    "No CSV selected"

                updateStats()
                updateSelectedCount()

                whatsappIndex = 0

                status.text =
                    "Contacts removed."
            }
            .show()
    }


    private fun ensureSmsPermission(
        action: () -> Unit
    ) {

        if (
            ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.SEND_SMS
            ) != PackageManager.PERMISSION_GRANTED
        ) {

            pendingSmsAction = action

            smsPermission.launch(
                Manifest.permission.SEND_SMS
            )

        } else {

            action()
        }
    }


    private fun toast(
        text: String
    ) {

        Toast.makeText(
            this,
            text,
            Toast.LENGTH_SHORT
        ).show()
    }


    private fun normalize(
        value: String
    ): String {

        var s =
            value
                .trim()
                .replace(
                    Regex("[^0-9+]"),
                    ""
                )

        if (s.startsWith("+")) {
            s = s.drop(1)
        }

        if (s.startsWith("00")) {
            s = s.drop(2)
        }

        if (
            s.matches(
                Regex("[6-9][0-9]{9}")
            )
        ) {
            s = "91$s"
        }

        return s
    }


    private fun splitNumbers(
        value: String
    ): List<String> {

        return value
            .split(
                Regex(
                    "[,;|/\\n]+|\\s{2,}"
                )
            )
            .map(::normalize)
            .filter {
                it.length >= 10
            }
            .distinct()
    }


    private fun findHeader(
        headers: List<String>,
        vararg names: String
    ): Int {

        val normalized =
            headers.map {
                it.trim()
                    .lowercase(
                        Locale.ROOT
                    )
            }

        for (name in names) {

            val index =
                normalized.indexOf(
                    name.lowercase(
                        Locale.ROOT
                    )
                )

            if (index >= 0) {
                return index
            }
        }

        return -1
    }


    private fun addContact(
        name: String,
        rawPhones: String,
        row: Int,
        seen: MutableSet<String>
    ) {

        for (
            phone in splitNumbers(
                rawPhones
            )
        ) {

            if (seen.add(phone)) {

                contacts.add(
                    Contact(
                        name.trim(),
                        phone,
                        row
                    )
                )
            }
        }
    }


    private fun loadCsv(
        uri: Uri
    ) {

        try {

            contacts.clear()

            val displayName =
                contentResolver
                    .query(
                        uri,
                        null,
                        null,
                        null,
                        null
                    )
                    ?.use { q ->

                        val index =
                            q.getColumnIndex(
                                OpenableColumns.DISPLAY_NAME
                            )

                        if (
                            q.moveToFirst() &&
                            index >= 0
                        ) {

                            q.getString(index)

                        } else {

                            "Selected CSV"
                        }
                    }
                    ?: "Selected CSV"


            findViewById<TextView>(
                R.id.txtFile
            ).text =
                displayName


            contentResolver
                .openInputStream(uri)
                ?.use { input ->

                    val lines =
                        BufferedReader(
                            InputStreamReader(
                                input
                            )
                        ).readLines()


                    require(
                        lines.isNotEmpty()
                    ) {
                        "CSV is empty."
                    }


                    val headers =
                        parseCsvLine(
                            lines.first()
                        )


                    val nameIndex =
                        findHeader(
                            headers,
                            "OriginalName",
                            "Original Name",
                            "Name"
                        )


                    val phoneIndex =
                        findHeader(
                            headers,
                            "MobileNumber",
                            "Mobile Number",
                            "Mobile",
                            "M.No."
                        )


                    require(
                        nameIndex >= 0 &&
                        phoneIndex >= 0
                    ) {

                        "Required columns OriginalName and MobileNumber were not found."
                    }


                    val seen =
                        hashSetOf<String>()


                    lines
                        .drop(1)
                        .forEachIndexed {
                                index,
                                line ->

                            if (
                                line.isBlank()
                            ) {
                                return@forEachIndexed
                            }


                            val row =
                                parseCsvLine(
                                    line
                                )


                            addContact(
                                row.getOrNull(
                                    nameIndex
                                ).orEmpty(),

                                row.getOrNull(
                                    phoneIndex
                                ).orEmpty(),

                                index + 2,

                                seen
                            )
                        }
                }


            saveContacts()

            updateStats()

            adapter.setContacts(
                contacts.toList()
            )

            adapter.clearSelection()

            whatsappIndex = 0

            status.text =
                "Loaded ${contacts.size} individual numbers. Select contacts below."


        } catch (e: Exception) {

            status.text =
                "CSV error: ${e.message}"

            toast(
                e.message
                    ?: "Could not read CSV"
            )
        }
    }


    private fun parseCsvLine(
        line: String
    ): List<String> {

        val result =
            mutableListOf<String>()

        val buffer =
            StringBuilder()

        var quoted = false
        var i = 0


        while (
            i < line.length
        ) {

            val c =
                line[i]


            when {

                c == '"' -> {

                    if (
                        quoted &&
                        i + 1 < line.length &&
                        line[i + 1] == '"'
                    ) {

                        buffer.append('"')
                        i++

                    } else {

                        quoted =
                            !quoted
                    }
                }


                c == ',' &&
                !quoted -> {

                    result.add(
                        buffer.toString()
                    )

                    buffer.setLength(0)
                }


                else -> {

                    buffer.append(c)
                }
            }

            i++
        }


        result.add(
            buffer.toString()
        )

        return result
    }


    private fun saveContacts() {

        val prefs =
            getSharedPreferences(
                "contacts",
                0
            )


        val edit =
            prefs.edit()
                .clear()


        edit.putInt(
            "count",
            contacts.size
        )


        contacts.forEachIndexed {
                index,
                contact ->

            edit.putString(
                "name_$index",
                contact.name
            )

            edit.putString(
                "phone_$index",
                contact.phone
            )

            edit.putInt(
                "row_$index",
                contact.row
            )
        }


        edit.apply()
    }


    private fun restoreContacts() {

        val prefs =
            getSharedPreferences(
                "contacts",
                0
            )


        val count =
            prefs.getInt(
                "count",
                0
            )


        repeat(count) {
            index ->

            contacts.add(
                Contact(
                    prefs.getString(
                        "name_$index",
                        ""
                    ) ?: "",

                    prefs.getString(
                        "phone_$index",
                        ""
                    ) ?: "",

                    prefs.getInt(
                        "row_$index",
                        0
                    )
                )
            )
        }


        updateStats()

        adapter.setContacts(
            contacts.toList()
        )

        updateSelectedCount()
    }


    private fun updateStats() {

        stats.text =
            "People: ${
                contacts
                    .map {
                        it.name
                    }
                    .distinct()
                    .size
            } | Individual numbers: ${
                contacts.size
            }"
    }


    private fun updateSelectedCount() {

        selectedText.text =
            "Selected: ${
                adapter.getSelectedCount()
            } / ${contacts.size}"
    }


    private fun refreshList() {

        val query =
            search.text
                .toString()
                .trim()
                .lowercase(
                    Locale.ROOT
                )


        val filtered =
            if (query.isBlank()) {

                contacts

            } else {

                contacts.filter {

                    it.name
                        .lowercase(
                            Locale.ROOT
                        )
                        .contains(query) ||

                    it.phone.contains(
                        query
                    )
                }
            }


        adapter.setContacts(
            filtered
        )

        updateSelectedCount()
    }


    private fun selectedContacts():
            List<Contact> {

        return adapter.getSelectedContacts()
    }


    private fun personalize(
        contact: Contact
    ): String {

        return message
            .text
            .toString()
            .replace(
                "{{Name}}",
                contact.name
            )
            .replace(
                "{{MobileNumber}}",
                contact.phone
            )
    }


    private fun preview() {

        val list =
            selectedContacts()
                .take(5)


        if (list.isEmpty()) {

            toast(
                "Select at least one contact."
            )

            return
        }


        AlertDialog.Builder(this)
            .setTitle(
                "Preview — First 5 Selected"
            )
            .setMessage(

                list.mapIndexed {
                        index,
                        contact ->

                    "${index + 1}. ${contact.name} — ${contact.phone}\n\n" +
                    personalize(contact)

                }.joinToString(
                    "\n\n"
                )
            )
            .setPositiveButton(
                "OK",
                null
            )
            .show()
    }


    private fun testSms() {

        val contact =
            selectedContacts()
                .firstOrNull()


        if (contact == null) {

            toast(
                "Select at least one contact."
            )

            return
        }


        if (
            message.text
                .toString()
                .isBlank()
        ) {

            toast(
                "Enter a message."
            )

            return
        }


        try {

            SmsManager
                .getDefault()
                .sendTextMessage(
                    contact.phone,
                    null,
                    personalize(contact),
                    null,
                    null
                )


            status.text =
                "Test SMS requested for ${contact.name} (${contact.phone})"

        } catch (e: Exception) {

            status.text =
                "Test SMS failed: ${e.message}"
        }
    }


    private fun sendNow() {

        val list =
            selectedContacts()


        if (list.isEmpty()) {

            toast(
                "Select at least one contact."
            )

            return
        }


        if (
            message.text
                .toString()
                .isBlank()
        ) {

            toast(
                "Enter a message."
            )

            return
        }


        Thread {

            var sent = 0


            for (contact in list) {

                try {

                    SmsManager
                        .getDefault()
                        .sendTextMessage(
                            contact.phone,
                            null,
                            personalize(contact),
                            null,
                            null
                        )


                    sent++


                    runOnUiThread {

                        status.text =
                            "SMS requested: $sent / ${list.size}"
                    }


                    Thread.sleep(700)

                } catch (_: Exception) {

                    // Continue with the next contact.
                }
            }


            runOnUiThread {

                status.text =
                    "SMS run finished: $sent / ${list.size}"
            }

        }.start()
    }


    private fun schedule() {

        val list =
            selectedContacts()


        if (list.isEmpty()) {

            toast(
                "Select at least one contact."
            )

            return
        }


        if (
            message.text
                .toString()
                .isBlank()
        ) {

            toast(
                "Enter a message."
            )

            return
        }


        val calendar =
            Calendar.getInstance()


        DatePickerDialog(
            this,

            { _, year, month, day ->

                TimePickerDialog(

                    this,

                    { _, hour, minute ->

                        calendar.set(
                            year,
                            month,
                            day,
                            hour,
                            minute,
                            0
                        )


                        calendar.set(
                            Calendar.MILLISECOND,
                            0
                        )


                        val delay =
                            calendar.timeInMillis -
                            System.currentTimeMillis()


                        if (delay <= 0) {

                            toast(
                                "Choose a future time."
                            )

                            return@TimePickerDialog
                        }


                        val packed =
                            list.joinToString(
                                "\u0001"
                            ) {

                                "${it.name}\u0002${it.phone}"
                            }


                        val data =
                            workDataOf(

                                "message" to
                                    message.text
                                        .toString(),

                                "contacts" to
                                    packed
                            )


                        val request =
                            OneTimeWorkRequestBuilder<SmsWorker>()
                                .setInitialDelay(
                                    delay,
                                    TimeUnit.MILLISECONDS
                                )
                                .setInputData(data)
                                .build()


                        WorkManager
                            .getInstance(this)
                            .enqueueUniqueWork(

                                "kba_sms_schedule",

                                ExistingWorkPolicy.REPLACE,

                                request
                            )


                        status.text =
                            "SMS scheduled for ${
                                SimpleDateFormat(
                                    "dd MMM yyyy, hh:mm a",
                                    Locale.getDefault()
                                ).format(
                                    calendar.time
                                )
                            } (${list.size} numbers)"

                    },

                    calendar.get(
                        Calendar.HOUR_OF_DAY
                    ),

                    calendar.get(
                        Calendar.MINUTE
                    ),

                    false

                ).show()

            },

            calendar.get(
                Calendar.YEAR
            ),

            calendar.get(
                Calendar.MONTH
            ),

            calendar.get(
                Calendar.DAY_OF_MONTH
            )

        ).show()
    }


    private fun nextWhatsApp() {

        val list =
            selectedContacts()


        if (list.isEmpty()) {

            toast(
                "Select at least one contact."
            )

            return
        }


        if (
            whatsappIndex >= list.size
        ) {

            whatsappIndex = 0
        }


        val contact =
            list[whatsappIndex++]


        status.text =
            "WhatsApp ${whatsappIndex}/${list.size}: ${contact.name}"


        try {

            startActivity(
                Intent(
                    Intent.ACTION_VIEW,

                    Uri.parse(
                        "https://wa.me/${contact.phone}?text=${
                            Uri.encode(
                                personalize(contact)
                            )
                        }"
                    )
                )
            )

        } catch (e: Exception) {

            toast(
                "Could not open WhatsApp."
            )
        }
    }
}


class SmsWorker(
    context: Context,
    params: WorkerParameters
) : Worker(
    context,
    params
) {

    override fun doWork(): Result {

        val template =
            inputData.getString(
                "message"
            ) ?: return Result.failure()


        val raw =
            inputData.getString(
                "contacts"
            ) ?: return Result.failure()


        var sent = 0


        return try {

            for (
                item in raw.split(
                    "\u0001"
                )
            ) {

                val parts =
                    item.split(
                        "\u0002",
                        limit = 2
                    )


                if (
                    parts.size < 2
                ) {
                    continue
                }


                val text =
                    template
                        .replace(
                            "{{Name}}",
                            parts[0]
                        )
                        .replace(
                            "{{MobileNumber}}",
                            parts[1]
                        )


                SmsManager
                    .getDefault()
                    .sendTextMessage(
                        parts[1],
                        null,
                        text,
                        null,
                        null
                    )


                sent++


                Thread.sleep(700)
            }


            Result.success(
                workDataOf(
                    "requested" to sent
                )
            )

        } catch (e: Exception) {

            if (sent > 0) {

                Result.failure(
                    workDataOf(
                        "requested" to sent,
                        "error" to (
                            e.message
                                ?: "SMS error"
                        )
                    )
                )

            } else {

                Result.retry()
            }
        }
    }
}
