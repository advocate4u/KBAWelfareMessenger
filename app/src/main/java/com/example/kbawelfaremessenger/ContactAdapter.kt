package com.example.kbawelfaremessenger

import android.graphics.Typeface
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.CheckBox
import android.widget.LinearLayout
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

class ContactAdapter(
    private var contacts: List<Contact>,
    private val onSelectionChanged: () -> Unit
) : RecyclerView.Adapter<ContactAdapter.ContactViewHolder>() {

    private val selectedPhones = mutableSetOf<String>()

    class ContactViewHolder(view: View) :
        RecyclerView.ViewHolder(view) {

        val checkBox: CheckBox =
            view.findViewById(android.R.id.checkbox)

        val name: TextView =
            view.findViewById(android.R.id.text1)

        val phone: TextView =
            view.findViewById(android.R.id.text2)
    }

    override fun onCreateViewHolder(
        parent: ViewGroup,
        viewType: Int
    ): ContactViewHolder {

        val container =
            LinearLayout(parent.context).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(8, 6, 8, 6)
                setBackgroundColor(0xFFFFFFFF.toInt())
            }

        val checkBox =
            CheckBox(parent.context).apply {
                id = android.R.id.checkbox
                textSize = 15f
                setTypeface(null, Typeface.BOLD)
            }

        val name =
            TextView(parent.context).apply {
                id = android.R.id.text1
                textSize = 15f
                setTypeface(null, Typeface.BOLD)
                setTextColor(0xFF163968.toInt())
            }

        val phone =
            TextView(parent.context).apply {
                id = android.R.id.text2
                textSize = 13f
                setTextColor(0xFF687586.toInt())
            }

        val textContainer =
            LinearLayout(parent.context).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(4, 0, 4, 0)
            }

        textContainer.addView(name)
        textContainer.addView(phone)

        val row =
            LinearLayout(parent.context).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = android.view.Gravity.CENTER_VERTICAL
            }

        row.addView(
            checkBox,
            LinearLayout.LayoutParams(
                48,
                48
            )
        )

        row.addView(
            textContainer,
            LinearLayout.LayoutParams(
                0,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                1f
            )
        )

        container.addView(
            row,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        )

        return ContactViewHolder(container)
    }

    override fun onBindViewHolder(
        holder: ContactViewHolder,
        position: Int
    ) {

        val contact = contacts[position]

        holder.name.text =
            contact.name.ifBlank {
                "Unknown Name"
            }

        holder.phone.text =
            contact.phone

        holder.checkBox.setOnCheckedChangeListener(null)

        holder.checkBox.isChecked =
            selectedPhones.contains(contact.phone)

        holder.checkBox.setOnCheckedChangeListener { _, checked ->

            if (checked) {
                selectedPhones.add(contact.phone)
            } else {
                selectedPhones.remove(contact.phone)
            }

            onSelectionChanged()
        }

        holder.itemView.setOnClickListener {
            holder.checkBox.isChecked =
                !holder.checkBox.isChecked
        }
    }

    override fun getItemCount(): Int =
        contacts.size

    fun setContacts(newContacts: List<Contact>) {

        contacts = newContacts

        notifyDataSetChanged()
    }

    fun selectAll() {

        contacts.forEach {
            selectedPhones.add(it.phone)
        }

        notifyDataSetChanged()
        onSelectionChanged()
    }

    fun unselectAll() {

        selectedPhones.clear()

        notifyDataSetChanged()
        onSelectionChanged()
    }

    fun getSelectedContacts(): List<Contact> {

        return contacts.filter {
            selectedPhones.contains(it.phone)
        }
    }

    fun getSelectedCount(): Int =
        selectedPhones.size

    fun clearSelection() {

        selectedPhones.clear()

        notifyDataSetChanged()
        onSelectionChanged()
    }
}
