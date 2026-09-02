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

        holder.checkBox.setOnCheckedChangeListener(
            null
        )

        holder.checkBox.isChecked =
            selectedPhones.contains(
                contact.phone
            )

        holder.txtOtherFields.text =
            buildOtherFields(contact)

        if (holder.txtOtherFields.text.isBlank()) {
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
                    Color.rgb(245, 124, 0)
                )
            }

            SmsStatus.SENT -> {

                holder.txtStatus.text =
                    "✓ SENT"

                holder.txtStatus.setTextColor(
                    Color.rgb(46, 125, 50)
                )
            }

            SmsStatus.FAILED -> {

                holder.txtStatus.text =
                    "✕ FAILED"

                holder.txtStatus.setTextColor(
                    Color.rgb(198, 40, 40)
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
                "m.no."
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
            .joinToString("  |  ")
    }

    override fun getItemCount(): Int =
        filteredContacts.size

    fun filter(
        query: String
    ) {

        val q =
            query.trim().lowercase()

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

                    contact.fields.any { (key, value) ->

                        key.lowercase()
                            .contains(q) ||

                                value.lowercase()
                                    .contains(q)
                    }
                }
            }

        notifyDataSetChanged()
    }

    fun replaceContacts(
        contacts: List<Contact>
    ) {

        allContacts = contacts

        selectedPhones.retainAll(
            contacts.map {
                it.phone
            }.toSet()
        )

        filteredContacts =
            contacts.toList()

        notifyDataSetChanged()

        onSelectionChanged()
    }

    fun selectAll(
        contacts: List<Contact>
    ) {

        selectedPhones.clear()

        selectedPhones.addAll(
            contacts.map {
                it.phone
            }
        )

        notifyDataSetChanged()

        onSelectionChanged()
    }

    fun unselectAll() {

        selectedPhones.clear()

        notifyDataSetChanged()

        onSelectionChanged()
    }

    fun getSelectedPhones(): Set<String> =
        selectedPhones.toSet()

    fun notifyContactStatusChanged(
        phone: String
    ) {

        val index =
            filteredContacts.indexOfFirst {
                it.phone == phone
            }

        if (index >= 0) {
            notifyItemChanged(index)
        }
    }
}
