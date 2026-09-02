package com.example.kbawelfaremessenger

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.CheckBox
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView


class ContactAdapter(
    private var contacts: List<Contact>,
    private val onSelectionChanged: () -> Unit
) : RecyclerView.Adapter<ContactAdapter.ContactViewHolder>() {


    /*
     * Store selection by phone number.
     *
     * This is important because the RecyclerView list
     * can be filtered by search.
     */
    private val selectedPhones =
        LinkedHashSet<String>()


    inner class ContactViewHolder(
        itemView: View
    ) : RecyclerView.ViewHolder(itemView) {

        val checkbox: CheckBox =
            itemView.findViewById(
                R.id.chkContact
            )

        val txtName: TextView =
            itemView.findViewById(
                R.id.txtContactName
            )

        val txtPhone: TextView =
            itemView.findViewById(
                R.id.txtContactPhone
            )

        val txtFields: TextView =
            itemView.findViewById(
                R.id.txtContactFields
            )
    }


    override fun onCreateViewHolder(
        parent: ViewGroup,
        viewType: Int
    ): ContactViewHolder {

        val view =
            LayoutInflater
                .from(parent.context)
                .inflate(
                    R.layout.item_contact,
                    parent,
                    false
                )


        return ContactViewHolder(
            view
        )
    }


    override fun onBindViewHolder(
        holder: ContactViewHolder,
        position: Int
    ) {

        val contact =
            contacts[position]


        /*
         * Important:
         * Remove listener before setting checked state.
         * Otherwise RecyclerView can trigger the listener
         * while recycling rows.
         */
        holder.checkbox.setOnCheckedChangeListener(
            null
        )


        holder.checkbox.isChecked =
            selectedPhones.contains(
                contact.phone
            )


        holder.txtName.text =
            contact.name.ifBlank {
                "Unnamed"
            }


        holder.txtPhone.text =
            contact.phone


        /*
         * Display all other CSV fields.
         *
         * OriginalName and MobileNumber are omitted
         * because they are already displayed above.
         */
        val otherFields =
            contact.fields
                .filter { (key, _) ->

                    !key.equals(
                        "OriginalName",
                        ignoreCase = true
                    ) &&

                    !key.equals(
                        "Original Name",
                        ignoreCase = true
                    ) &&

                    !key.equals(
                        "Name",
                        ignoreCase = true
                    ) &&

                    !key.equals(
                        "MobileNumber",
                        ignoreCase = true
                    ) &&

                    !key.equals(
                        "Mobile Number",
                        ignoreCase = true
                    ) &&

                    !key.equals(
                        "Mobile",
                        ignoreCase = true
                    ) &&

                    !key.equals(
                        "Phone",
                        ignoreCase = true
                    ) &&

                    !key.equals(
                        "PhoneNumber",
                        ignoreCase = true
                    )
                }
                .filter {
                    it.value.isNotBlank()
                }


        if (otherFields.isEmpty()) {

            holder.txtFields.visibility =
                View.GONE

            holder.txtFields.text =
                ""

        } else {

            holder.txtFields.visibility =
                View.VISIBLE


            holder.txtFields.text =
                otherFields
                    .entries
                    .joinToString(
                        separator = "\n"
                    ) {
                        "${it.key}: ${it.value}"
                    }
        }


        holder.checkbox.setOnCheckedChangeListener {
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


        /*
         * Allow tapping the row itself to toggle
         * the checkbox.
         */
        holder.itemView.setOnClickListener {

            holder.checkbox.isChecked =
                !holder.checkbox.isChecked
        }
    }


    override fun getItemCount(): Int =
        contacts.size


    fun setContacts(
        newContacts: List<Contact>
    ) {

        contacts =
            newContacts


        notifyDataSetChanged()
    }


    fun selectAll() {

        contacts.forEach {

            selectedPhones.add(
                it.phone
            )
        }


        notifyDataSetChanged()

        onSelectionChanged()
    }


    fun unselectAll() {

        selectedPhones.clear()

        notifyDataSetChanged()

        onSelectionChanged()
    }


    fun clearSelection() {

        selectedPhones.clear()

        notifyDataSetChanged()

        onSelectionChanged()
    }


    fun getSelectedCount(): Int =
        selectedPhones.size


    fun getSelectedContacts():
            List<Contact> {

        /*
         * Return contacts in the original CSV order.
         *
         * This ensures the name and number always belong
         * to the same Contact object.
         */
        return contacts.filter {

            selectedPhones.contains(
                it.phone
            )
        }
    }
}
