package com.example.samplesocket

import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.util.Log
import android.widget.ArrayAdapter
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.isVisible
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.samplesocket.databinding.ActivityMainBinding
import io.socket.client.IO
import io.socket.client.Socket
import io.socket.emitter.Emitter
import org.json.JSONArray
import org.json.JSONObject
import java.net.URISyntaxException
import java.text.SimpleDateFormat
import java.util.*
import java.util.Timer

class MainActivity : AppCompatActivity() {
    private lateinit var binding: ActivityMainBinding
    private lateinit var mSocket: Socket
    private lateinit var username: String
    private var recipient: String = ""
    private val messageList = mutableListOf<ChatMessage>()
    private lateinit var adapter: ChatAdapter
    private val onlineUsers = mutableListOf<String>()
    private lateinit var onlineUsersAdapter: ArrayAdapter<String>

    // Socket listeners defined first
    private val onConnect = Emitter.Listener {
        runOnUiThread {
            showToast("Connected to server")
            Log.d("SocketIO", "Connected")
            if (username.isNotEmpty()) {
                mSocket.emit("register", username)
                mSocket.emit("get online users")
            }
        }
    }

    private val onDisconnect = Emitter.Listener {
        runOnUiThread {
            showToast("Disconnected from server")
            Log.d("SocketIO", "Disconnected")
        }
    }

    private val onConnectError = Emitter.Listener { args ->
        runOnUiThread {
            val error = args[0] as? Exception
            showToast("Connection error: ${error?.message ?: "Unknown error"}")
            Log.e("SocketIO", "Connection error", error)
        }
    }

    private val onPrivateMessage = Emitter.Listener { args ->
        runOnUiThread {
            try {
                val data = args[0] as JSONObject
                Log.d("MESSAGE", "Received: ${data.toString()}")

                if (data.getString("from") == recipient) {
                    val message = ChatMessage(
                        sender = data.getString("from"),
                        message = data.getString("message"),
                        timestamp = data.optString("timestamp"),
                        isOwn = false,
                        isDelivered = true
                    )
                    messageList.add(message)
                    adapter.notifyItemInserted(messageList.size - 1)
                    binding.recyclerView.smoothScrollToPosition(messageList.size - 1)
                }
            } catch (e: Exception) {
                Log.e("SocketIO", "Error parsing message", e)
            }
        }
    }

    private val onTyping = Emitter.Listener { args ->
        runOnUiThread {
            try {
                val data = args[0] as JSONObject
                if (data.getString("from") == recipient) {
                    binding.tvTyping.text = "${data.getString("from")} is typing..."
                    binding.tvTyping.visibility = TextView.VISIBLE
                    binding.tvTyping.postDelayed({
                        binding.tvTyping.visibility = TextView.GONE
                    }, 2000)
                }
            } catch (e: Exception) {
                Log.e("SocketIO", "Error parsing typing event", e)
            }
        }
    }

    private val onUserAvailable = Emitter.Listener { args ->
        runOnUiThread {
            try {
                val data = args[0] as JSONObject
                val isAvailable = data.getBoolean("available")
                if (!isAvailable) {
                    showToast("User ${data.getString("username")} is not available")
                    binding.tvRecipient.isVisible = false
                    recipient = ""
                }
            } catch (e: Exception) {
                Log.e("SocketIO", "Error parsing user availability", e)
            }
        }
    }

    private val onOnlineUsers = Emitter.Listener { args ->
        runOnUiThread {
            try {
                val data = args[0] as JSONArray
                onlineUsers.clear()
                for (i in 0 until data.length()) {
                    val user = data.getString(i)
                    if (user != username) {
                        onlineUsers.add(user)
                    }
                }
                onlineUsersAdapter.notifyDataSetChanged()
                showOnlineUsersDialog()
            } catch (e: Exception) {
                Log.e("SocketIO", "Error parsing online users", e)
            }
        }
    }

    private val onMessageDelivered = Emitter.Listener { args ->
        runOnUiThread {
            try {
                val data = args[0] as JSONObject
                showToast("Message delivered to ${data.getString("to")}")
                messageList.lastOrNull()?.isDelivered = true
                adapter.notifyItemChanged(messageList.size - 1)
            } catch (e: Exception) {
                Log.e("SocketIO", "Error parsing delivery confirmation", e)
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        username = intent.getStringExtra("username") ?: run {
            finish()
            return
        }

        setupUI()
        setupSocket()
    }

    private fun setupSocket() {
        try {
            val opts = IO.Options().apply {
                transports = arrayOf("websocket")
                reconnection = true
                reconnectionAttempts = 5
                reconnectionDelay = 1000
                timeout = 20000
                forceNew = true
            }

            mSocket = IO.socket("http://192.168.0.109:3000", opts)

            mSocket.on(Socket.EVENT_CONNECT, onConnect)
            mSocket.on(Socket.EVENT_DISCONNECT, onDisconnect)
            mSocket.on(Socket.EVENT_CONNECT_ERROR, onConnectError)
            mSocket.on("private message", onPrivateMessage)
            mSocket.on("typing", onTyping)
            mSocket.on("user available", onUserAvailable)
            mSocket.on("online users", onOnlineUsers)
            mSocket.on("message delivered", onMessageDelivered)

            mSocket.connect()
        } catch (e: URISyntaxException) {
            showToast("Invalid server URL")
            Log.e("SocketIO", "URISyntaxException", e)
        } catch (e: Exception) {
            showToast("Connection failed: ${e.message}")
            Log.e("SocketIO", "Connection error", e)
        }
    }

    private fun setupUI() {
        binding.tvTitle.text = username
        adapter = ChatAdapter(messageList)

        binding.recyclerView.apply {
            layoutManager = LinearLayoutManager(this@MainActivity).apply {
                stackFromEnd = true
            }
            adapter = this@MainActivity.adapter
        }

        onlineUsersAdapter = ArrayAdapter(
            this,
            android.R.layout.simple_list_item_1,
            onlineUsers
        )

        binding.btnSend.setOnClickListener { sendMessage() }

        binding.btnSelectRecipient.setOnClickListener {
            if (::mSocket.isInitialized && mSocket.connected()) {
                mSocket.emit("get online users")
            } else {
                showToast("Not connected to server")
            }
        }

        binding.etMessage.addTextChangedListener(object : TextWatcher {
            private val typingDelay = 1000L
            private var typingTimer = Timer()

            override fun afterTextChanged(s: Editable?) {
                if (!s.isNullOrEmpty() && recipient.isNotEmpty()) {
                    typingTimer.cancel()
                    typingTimer = Timer()
                    typingTimer.schedule(object : TimerTask() {
                        override fun run() {
                            runOnUiThread {
                                if (::mSocket.isInitialized && mSocket.connected()) {
                                    mSocket.emit("typing", JSONObject().apply {
                                        put("from", username)
                                        put("to", recipient)
                                    })
                                }
                            }
                        }
                    }, typingDelay)
                } else if (recipient.isNotEmpty()) {
                    if (::mSocket.isInitialized && mSocket.connected()) {
                        mSocket.emit("stop typing", JSONObject().apply {
                            put("from", username)
                            put("to", recipient)
                        })
                    }
                }
            }

            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
        })
    }

    private fun showOnlineUsersDialog() {
        if (onlineUsers.isEmpty()) {
            showToast("No other users online")
            return
        }

        AlertDialog.Builder(this)
            .setTitle("Select recipient")
            .setAdapter(onlineUsersAdapter) { _, which ->
                recipient = onlineUsers[which]
                binding.tvRecipient.text = recipient
                binding.tvRecipient.isVisible = true
                checkUserAvailability(recipient)
                messageList.clear()
                adapter.notifyDataSetChanged()
            }
            .setNegativeButton("Cancel", null)
            .create()
            .show()
    }

    private fun checkUserAvailability(recipient: String) {
        if (::mSocket.isInitialized && mSocket.connected()) {
            mSocket.emit("check user", JSONObject().apply {
                put("username", recipient)
            })
        }
    }

    private fun sendMessage() {
        val message = binding.etMessage.text.toString().trim()
        if (message.isEmpty()) {
            showToast("Message cannot be empty")
            return
        }

        if (recipient.isEmpty()) {
            showToast("Please select a recipient first")
            return
        }

        if (!::mSocket.isInitialized || !mSocket.connected()) {
            showToast("Not connected to server")
            return
        }

        val timestamp = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date())
        val chatMessage = ChatMessage(
            sender = username,
            message = message,
            timestamp = timestamp,
            isOwn = true,
            isDelivered = false
        )

        messageList.add(chatMessage)
        adapter.notifyItemInserted(messageList.size - 1)
        binding.recyclerView.smoothScrollToPosition(messageList.size - 1)
        binding.etMessage.text.clear()

        try {
            val json = JSONObject().apply {
                put("from", username)
                put("to", recipient)
                put("message", message)
                put("timestamp", timestamp)
            }
            Log.d("MESSAGE", "Sending: $json")
            mSocket.emit("private message", json)
        } catch (e: Exception) {
            showToast("Failed to send message")
            Log.e("SocketIO", "Error sending message", e)
            messageList.removeLast()
            adapter.notifyItemRemoved(messageList.size)
        }
    }

    private fun showToast(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }

    override fun onDestroy() {
        super.onDestroy()
        if (::mSocket.isInitialized) {
            mSocket.disconnect()
            mSocket.off()
        }
    }
}