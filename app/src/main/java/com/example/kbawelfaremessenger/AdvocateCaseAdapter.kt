package com.example.kbawelfaremessenger

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

class AdvocateCaseAdapter(
    private var items: List<AdvocateCase>,
    private val onEdit: (AdvocateCase) -> Unit,
    private val onDelete: (AdvocateCase) -> Unit
) : RecyclerView.Adapter<AdvocateCaseAdapter.CaseViewHolder>() {

    fun submitList(newItems: List<AdvocateCase>) {
        items = newItems
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): CaseViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_advocate_case, parent, false)
        return CaseViewHolder(view)
    }

    override fun onBindViewHolder(holder: CaseViewHolder, position: Int) {
        holder.bind(items[position])
    }

    override fun getItemCount(): Int = items.size

    inner class CaseViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        private val txtCase = view.findViewById<TextView>(R.id.txtCase)
        private val txtClient = view.findViewById<TextView>(R.id.txtClient)
        private val txtCourt = view.findViewById<TextView>(R.id.txtCourt)
        private val txtNextDate = view.findViewById<TextView>(R.id.txtNextDate)
        private val txtFee = view.findViewById<TextView>(R.id.txtFee)
        private val txtUpdate = view.findViewById<TextView>(R.id.txtUpdate)
        private val btnEdit = view.findViewById<Button>(R.id.btnEditCase)
        private val btnDelete = view.findViewById<Button>(R.id.btnDeleteCase)

        fun bind(item: AdvocateCase) {
            txtCase.text = "Case: ${item.caseNumber}"
            txtClient.text = "Client: ${item.clientName}" +
                if (item.clientPhone.isBlank()) "" else " - ${item.clientPhone}"
            txtCourt.text = "Court: ${item.courtName.ifBlank { "Not specified" }}"
            txtNextDate.text = "Next Date: ${item.nextDate.ifBlank { "Not set" }}"
            txtFee.text = "Fee: ${money(item.totalFee)} | Received: ${money(item.amountReceived)} | Balance: ${money(item.balance)}"
            txtUpdate.text = "Update: ${item.newUpdate.ifBlank { item.currentUpdate.ifBlank { "No update" } }}"
            btnEdit.setOnClickListener { onEdit(item) }
            btnDelete.setOnClickListener { onDelete(item) }
            itemView.setOnClickListener { onEdit(item) }
        }

        private fun money(value: Double): String =
            if (value == value.toLong().toDouble()) value.toLong().toString()
            else String.format("%.2f", value)
    }
}
