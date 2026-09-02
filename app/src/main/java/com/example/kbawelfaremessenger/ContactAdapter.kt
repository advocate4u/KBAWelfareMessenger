package com.example.kbawelfaremessenger

import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.CheckBox
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

class ContactAdapter(
    private var allContacts: List<Contact>,
    private val onSelectionChanged: () -> Unit
) : RecyclerView.Adapter<ContactAdapter.ContactViewHolder>() {

    private var filteredContacts =
        allContacts.toList()

    private val selectedPhones =
        LinkedHashSet<String>()

    class ContactViewHolder(
        itemView: View
    ) : RecyclerView.ViewHolder(itemView) {

        val checkBox: CheckBox =
            itemView.findViewById(R.id.chkContact)

        val txtName: TextView =
            itemView.findViewById(R.id.txtContactName)

        val txtPhone: TextView =
            itemView.findViewById(R.id.txtContactPhone)

        val txtOtherFields: TextView =
            itemView.findViewById(R.id.txtOtherFields)

        val txtStatus: TextView =
            itemView.findViewById(R.id.txtContactStatus)
    }

    override fun onCreateViewHolder(
        parent: ViewGroup,
        viewType: Int
    ): ContactViewHolder {

        val view =
            LayoutInflater.from(
                parent.context
            ).inflate(
                R.layout.item_contact,
                parent,
                false
            )

        return ContactViewHolder(view)
    }

    override fun onBindViewHolder(
        holder: ContactViewHolder,
        position: Int
    ) {

        val contact =
            filteredContacts[position]

        holder.txtName.text =
            contact.name

        holder.txtPhone.text =
            contact.phone

        // Important:
        // Remove previous listener before setting the checkbox.
        holder.checkBox.setOnCheckedChangeListener(
            null
        )

        holder.checkBox.isChecked =
            selectedPhones.contains(
                contact.phone
            )

        holder.txtOtherFields.text =
            buildOtherFields(contact)

        if (
            holder.txtOtherFields.text
                .toString()
                .isBlank()
        ) {

            holder.txtOtherFields.visibility =
                View.GONE

        } else {

            holder.txtOtherFields.visibility =
                View.VISIBLE
        }

        when (contact.smsStatus) {

            SmsStatus.NONE -> {

                holder.txtStatus.text =
                    "Not sent"

                holder.txtStatus.setTextColor(
                    Color.DKGRAY
                )
            }

            SmsStatus.SENDING -> {

                holder.txtStatus.text =
                    "⏳ SENDING"

                holder.txtStatus.setTextColor(
                    Color.rgb(
                        245,
                        124,
                        0
                    )
                )
            }

            SmsStatus.SENT -> {

                holder.txtStatus.text =
                    "✓ SENT"

                holder.txtStatus.setTextColor(
                    Color.rgb(
                        46,
                        125,
                        50
                    )
                )
            }

            SmsStatus.FAILED -> {

                holder.txtStatus.text =
                    "✕ FAILED"

                holder.txtStatus.setTextColor(
                    Color.rgb(
                        198,
                        40,
                        40
                    )
                )
            }
        }

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

        holder.itemView.setOnClickListener {

            holder.checkBox.performClick()
        }
    }

    private fun buildOtherFields(
        contact: Contact
    ): String {

        val ignored =
            setOf(
                "name",
                "original name",
                "given name",
                "phone 1 - value",
                "mobile",
                "mobile number",
                "phone",
                "phone number",
                "m.no.",
                "m.no",
                "mobile no",
                "mobile no.",
                "contact",
                "contact number"
            )

        return contact.fields
            .filter { (key, value) ->

                value.isNotBlank() &&
                        !ignored.contains(
                            key.trim()
                                .lowercase()
                        )
            }
            .map { (key, value) ->

                "$key: $value"
            }
            .joinToString(
                "  |  "
            )
    }

    override fun getItemCount(): Int =
        filteredContacts.size

    /**
     * Search/filter contacts.
     *
     * Selection is NOT cleared when filtering.
     * Therefore selections remain when the search is cleared.
     */
    fun filter(
        query: String
    ) {

        val q =
            query.trim()
                .lowercase()

        filteredContacts =
            if (q.isEmpty()) {

                allContacts.toList()

            } else {

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
            }

        notifyDataSetChanged()

        onSelectionChanged()
    }

    /**
     * Replace the complete CSV contact list.
     */
    fun replaceContacts(
        contacts: List<Contact>
    ) {

        allContacts =
            contacts

        // Remove selections for contacts
        // which no longer exist.
        selectedPhones.retainAll(
            contacts
                .map {
                    it.phone
                }
                .toSet()
        )

        filteredContacts =
            contacts.toList()

        notifyDataSetChanged()

        onSelectionChanged()
    }

    /**
     * SELECT ALL
     *
     * IMPORTANT:
     * This selects only the currently visible/filtered
     * contacts.
     *
     * If no search is active:
     *     selects all contacts.
     *
     * If search is active:
     *     selects only search results.
     */
    fun selectAll() {

        selectedPhones.addAll(
            filteredContacts.map {
                it.phone
            }
        )

        notifyDataSetChanged()

        onSelectionChanged()
    }

    /**
     * Backward-compatible version.
     *
     * If MainActivity still calls:
     *
     * adapter.selectAll(contacts)
     *
     * we intentionally ignore the supplied full list
     * and select only the currently visible list.
     */
    fun selectAll(
        contacts: List<Contact>
    ) {

        selectAll()
    }

    /**
     * UNSELECT ALL
     *
     * Only currently visible/filtered contacts
     * are unselected.
     *
     * Contacts hidden by search remain selected.
     */
    fun unselectAll() {

        val visiblePhones =
            filteredContacts.map {
                it.phone
            }.toSet()

        selectedPhones.removeAll(
            visiblePhones
        )

        notifyDataSetChanged()

        onSelectionChanged()
    }

    /**
     * SELECT RANGE
     *
     * fromPosition and toPosition are 1-based positions
     * in the currently displayed list.
     *
     * Example:
     *
     * 10 to 25
     *
     * selects displayed contacts 10 through 25.
     */
    fun selectRange(
        fromPosition: Int,
        toPosition: Int
    ): Boolean {

        if (filteredContacts.isEmpty()) {
            return false
        }

        if (fromPosition < 1 ||
            toPosition < 1
        ) {
            return false
        }

        if (fromPosition > filteredContacts.size ||
            toPosition > filteredContacts.size
        ) {
            return false
        }

        val start =
            minOf(
                fromPosition,
                toPosition
            ) - 1

        val end =
            maxOf(
                fromPosition,
                toPosition
            ) - 1

        for (
            index in start..end
        ) {

            selectedPhones.add(
                filteredContacts[index].phone
            )
        }

        notifyDataSetChanged()

        onSelectionChanged()

        return true
    }

    /**
     * UNSELECT RANGE
     *
     * Removes selection only from the specified
     * range in the currently displayed list.
     */
    fun unselectRange(
        fromPosition: Int,
        toPosition: Int
    ): Boolean {

        if (filteredContacts.isEmpty()) {
            return false
        }

        if (fromPosition < 1 ||
            toPosition < 1
        ) {
            return false
        }

        if (fromPosition > filteredContacts.size ||
            toPosition > filteredContacts.size
        ) {
            return false
        }

        val start =
            minOf(
                fromPosition,
                toPosition
            ) - 1

        val end =
            maxOf(
                fromPosition,
                toPosition
            ) - 1

        for (
            index in start..end
        ) {

            selectedPhones.remove(
                filteredContacts[index].phone
            )
        }

        notifyDataSetChanged()

        onSelectionChanged()

        return true
    }

    /**
     * Returns the currently selected phone numbers.
     */
    fun getSelectedPhones(): Set<String> =
        selectedPhones.toSet()

    /**
     * Returns the currently visible/filtered contacts.
     */
    fun getVisibleContacts(): List<Contact> =
        filteredContacts.toList()

    /**
     * Returns number of currently visible contacts.
     */
    fun getVisibleCount(): Int =
        filteredContacts.size

    /**
     * Returns whether a phone number is selected.
     */
    fun isSelected(
        phone: String
    ): Boolean =
        selectedPhones.contains(phone)

    /**
     * Refresh one contact's SMS status.
     */
    fun notifyContactStatusChanged(
        phone: String
    ) {

        val index =
            filteredContacts.indexOfFirst {
                it.phone == phone
            }

        if (index >= 0) {

            notifyItemChanged(
                index
            )
        }
    }
}
