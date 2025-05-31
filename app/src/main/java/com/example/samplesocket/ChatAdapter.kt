package com.example.samplesocket

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.example.samplesocket.databinding.ItemMessageBinding

class ChatAdapter(private val messages: List<ChatMessage>) :
    RecyclerView.Adapter<ChatAdapter.ViewHolder>() {

    inner class ViewHolder(val binding: ItemMessageBinding) :
        RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemMessageBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val message = messages[position]

        with(holder.binding) {
            if (message.isOwn) {
                messageContainer.setBackgroundResource(R.drawable.bubble_outgoing)
                tvMessage.setTextColor(ContextCompat.getColor(holder.itemView.context, android.R.color.white))
                tvTimestamp.text = "${message.timestamp} ${
                    if (message.isDelivered) "✓✓" else "✓"
                }"
            } else {
                messageContainer.setBackgroundResource(R.drawable.bubble_incoming)
                tvMessage.setTextColor(ContextCompat.getColor(holder.itemView.context, android.R.color.black))
                tvTimestamp.text = message.timestamp
            }

            tvMessage.text = message.message
            tvSender.text = if (message.isOwn) "You" else message.sender

            tvSender.visibility = if (position > 0 &&
                messages[position - 1].sender == message.sender) {
                View.GONE
            } else {
                View.VISIBLE
            }
        }
    }

    override fun getItemCount() = messages.size
}