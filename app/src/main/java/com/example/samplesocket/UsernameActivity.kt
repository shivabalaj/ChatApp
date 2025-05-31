package com.example.samplesocket

import android.content.Intent
import android.os.Bundle
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.example.samplesocket.databinding.ActivityUsernameBinding

class UsernameActivity : AppCompatActivity() {
    private lateinit var binding: ActivityUsernameBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityUsernameBinding.inflate(layoutInflater)
        setContentView(binding.root)

        showUsernameDialog()
    }

    private fun showUsernameDialog() {
        val input = EditText(this).apply {
            hint = "Enter username"
        }

        AlertDialog.Builder(this).apply {
            setTitle("Join Chat")
            setView(input)
            setPositiveButton("Join") { _, _ ->
                val username = input.text.toString().trim()
                if (username.isNotEmpty()) {
                    startChatActivity(username)
                } else {
                    showToast("Username cannot be empty")
                    showUsernameDialog()
                }
            }
            setCancelable(false)
        }.create().show()
    }

    private fun startChatActivity(username: String) {
        val intent = Intent(this, MainActivity::class.java).apply {
            putExtra("username", username)
        }
        startActivity(intent)
        finish()
    }

    private fun showToast(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }
}