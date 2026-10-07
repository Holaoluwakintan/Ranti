package com.holaoluwakintan.ranti.system

import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import com.holaoluwakintan.ranti.core.Phone

object Share {
    /** Opens WhatsApp with the text ready (to the person's number if we have it). Falls back to the share sheet. */
    fun whatsApp(ctx: Context, text: String, phone: String) {
        val digits = Phone.forWhatsApp(phone)
        val url = "https://wa.me/" + digits + "?text=" + Uri.encode(text)
        val attempts = listOf("com.whatsapp", "com.whatsapp.w4b", null)
        for (pkg in attempts) {
            try {
                val i = Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                if (pkg != null) i.setPackage(pkg)
                ctx.startActivity(i)
                return
            } catch (_: ActivityNotFoundException) {
            } catch (_: SecurityException) {
            }
        }
        shareSheet(ctx, text)
    }

    /** Opens the SMS app with the message ready (to the person's number if we have it). */
    fun sms(ctx: Context, text: String, phone: String) {
        val to = phone.filter { it.isDigit() || it == '+' }
        val i = Intent(Intent.ACTION_SENDTO, Uri.parse("smsto:" + to)).putExtra("sms_body", text).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        try { ctx.startActivity(i) } catch (_: Exception) { shareSheet(ctx, text) }
    }

    /** Opens the email app with subject and message ready. */
    fun email(ctx: Context, text: String, to: String, subject: String) {
        val uri = Uri.parse("mailto:" + Uri.encode(to.trim()) + "?subject=" + Uri.encode(subject) + "&body=" + Uri.encode(text))
        val i = Intent(Intent.ACTION_SENDTO, uri).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        try { ctx.startActivity(i) } catch (_: Exception) {
            try {
                ctx.startActivity(Intent.createChooser(
                    Intent(Intent.ACTION_SEND).setType("message/rfc822").putExtra(Intent.EXTRA_EMAIL, arrayOf(to)).putExtra(Intent.EXTRA_SUBJECT, subject).putExtra(Intent.EXTRA_TEXT, text),
                    "Send email…").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            } catch (_: Exception) { copy(ctx, text) }
        }
    }

    fun shareSheet(ctx: Context, text: String) {
        val send = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, text)
        try {
            ctx.startActivity(Intent.createChooser(send, "Send with…").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        } catch (_: Exception) {
            copy(ctx, text)
        }
    }

    fun copy(ctx: Context, text: String) {
        val cm = ctx.getSystemService(ClipboardManager::class.java)
        cm?.setPrimaryClip(ClipData.newPlainText("Ranti message", text))
        Toast.makeText(ctx, "Copied", Toast.LENGTH_SHORT).show()
    }

    fun dial(ctx: Context, phone: String) {
        try {
            ctx.startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:" + phone.filter { it.isDigit() || it == '+' })).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        } catch (_: Exception) {
            Toast.makeText(ctx, "No phone app found", Toast.LENGTH_SHORT).show()
        }
    }
}
