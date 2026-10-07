package com.holaoluwakintan.ranti.system

import android.content.Context
import android.net.Uri
import android.provider.ContactsContract
import android.provider.ContactsContract.CommonDataKinds.Event
import android.provider.ContactsContract.CommonDataKinds.Phone as CPhone
import android.provider.ContactsContract.CommonDataKinds.Email as CEmail
import com.holaoluwakintan.ranti.core.AskContacts
import com.holaoluwakintan.ranti.core.ContactDates
import com.holaoluwakintan.ranti.core.ContactEntry
import com.holaoluwakintan.ranti.core.ContactEvents
import com.holaoluwakintan.ranti.core.PhoneRow
import com.holaoluwakintan.ranti.core.OccasionType

data class FoundDate(
    val name: String,
    val phone: String,
    val type: OccasionType,
    val month: Int,
    val day: Int,
    val year: Int?,
    val email: String = "",
)

object ContactsImport {

    /** Reads birthdays and anniversaries saved in the phone's contacts (including synced Google contacts), with each contact's number and email. Needs READ_CONTACTS. */
    fun read(ctx: Context): List<FoundDate> {
        val cr = ctx.contentResolver
        val phones = HashMap<Long, String>()
        cr.query(
            CPhone.CONTENT_URI, arrayOf(CPhone.CONTACT_ID, CPhone.NUMBER, CPhone.IS_PRIMARY), null, null, null
        )?.use { c ->
            while (c.moveToNext()) {
                val id = c.getLong(0); val num = c.getString(1) ?: continue
                if (!phones.containsKey(id) || c.getInt(2) == 1) phones[id] = num
            }
        }
        val emails = HashMap<Long, String>()
        try {
            cr.query(
                CEmail.CONTENT_URI, arrayOf(CEmail.CONTACT_ID, CEmail.ADDRESS, CEmail.IS_PRIMARY), null, null, null
            )?.use { c ->
                while (c.moveToNext()) {
                    val id = c.getLong(0); val addr = c.getString(1)?.trim() ?: continue
                    if (addr.isEmpty()) continue
                    if (!emails.containsKey(id) || c.getInt(2) == 1) emails[id] = addr
                }
            }
        } catch (_: Exception) {}
        val out = ArrayList<FoundDate>()
        // v0.3: ask for every Event row (incl. Google-synced ones) and classify in code, so custom-labelled
        // "Birthday"/"Wedding" events and rows saved without a type are not dropped by the SQL filter.
        cr.query(
            ContactsContract.Data.CONTENT_URI,
            arrayOf(ContactsContract.Data.CONTACT_ID, ContactsContract.Data.DISPLAY_NAME, Event.START_DATE, Event.TYPE, Event.LABEL),
            ContactEvents.SELECTION,
            ContactEvents.ARGS,
            null
        )?.use { c ->
            while (c.moveToNext()) {
                val id = c.getLong(0)
                val name = c.getString(1)?.trim().orEmpty()
                val raw = c.getString(2) ?: continue
                val type = ContactEvents.classify(c.getString(3), c.getString(4)) ?: continue
                val parsed = ContactDates.parse(raw) ?: continue
                if (name.isEmpty()) continue
                out += FoundDate(name, phones[id].orEmpty(), type, parsed.month, parsed.day, parsed.year, emails[id].orEmpty())
            }
        }
        return out.distinctBy { "${it.name.lowercase()}|${it.type}|${it.month}|${it.day}" }
            .sortedBy { it.name.lowercase() }
    }

    /** v0.3: every contact with a phone number (one row per person), for "Ask for birthdays". Needs READ_CONTACTS. */
    fun readAll(ctx: Context, defaultCountry: String = "234"): List<ContactEntry> {
        val rows = ArrayList<PhoneRow>()
        ctx.contentResolver.query(
            CPhone.CONTENT_URI, arrayOf(CPhone.CONTACT_ID, CPhone.DISPLAY_NAME, CPhone.NUMBER, CPhone.IS_PRIMARY, CPhone.TYPE), null, null, null
        )?.use { c ->
            while (c.moveToNext()) {
                rows += PhoneRow(c.getLong(0), c.getString(1), c.getString(2), c.getInt(3) == 1, c.getInt(4) == CPhone.TYPE_MOBILE)
            }
        }
        return AskContacts.clean(rows, defaultCountry)
    }

    /** Reads name + number from a phone row picked with ACTION_PICK (no READ_CONTACTS needed). */
    fun readPicked(ctx: Context, uri: Uri): Pair<String, String>? = try {
        ctx.contentResolver.query(uri, arrayOf(CPhone.DISPLAY_NAME, CPhone.NUMBER), null, null, null)?.use { c ->
            if (c.moveToFirst()) (c.getString(0).orEmpty()) to (c.getString(1).orEmpty()) else null
        }
    } catch (_: Exception) { null }
}
