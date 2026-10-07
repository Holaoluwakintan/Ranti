package com.holaoluwakintan.ranti.core

enum class Tone(val label: String, val emoji: String) {
    WARM("Warm", "🤗"), FUNNY("Funny", "😄"), PRAYERFUL("Prayerful", "🙏"), SHORT("Short", "⚡");
    companion object { fun from(s: String?) = entries.firstOrNull { it.name == s } ?: WARM }
}

/** Offline message templates. {name} = first name, {age} = ordinal age line or empty. */
object Messages {

    private val birthday: Map<Tone, List<String>> = mapOf(
        Tone.WARM to listOf(
            "Happy birthday, {name}! 🎉 I'm so grateful for you. May this new year{age} bring you joy, peace and every good thing your heart has been waiting for. Enjoy your day!",
            "{name}, happy birthday! ❤️ You make life brighter for everyone around you. Wishing you a year full of laughter, good health and big wins.",
            "Happy birthday to one of my favourite people! 🎂 {name}, thank you for being you. Have the most beautiful day.",
            "Cheers to you, {name}! 🥂 Another year older, wiser and more amazing. I'm celebrating you today and always."
        ),
        Tone.FUNNY to listOf(
            "Happy birthday, {name}! 🎂 Don't worry about getting older, you're still younger than you'll be next year 😄 Enjoy the cake (and send my share).",
            "{name}! Happy birthday 🎉 They say age is just a number… in your case a very big number 😂 Love you, have a great one!",
            "Happy birthday, {name}! 🥳 I was going to buy you something nice, then I remembered I'm the gift 😎 Enjoy your day!",
            "Oya {name}, it's your day! 🎉 Eat, dance and don't count the candles, the fire service is on standby 😂 Happy birthday!"
        ),
        Tone.PRAYERFUL to listOf(
            "Happy birthday, {name}! 🙏 May God's favour follow you all through this new year{age}. May you never lack, may your joy be full, and may His peace guard your heart.",
            "{name}, happy birthday! 🎂 I thank God for your life. This year, may doors open for you that no man can shut. Long life and prosperity in Jesus' name.",
            "Happy birthday, {name}! May the Lord bless you and keep you, make His face shine upon you, and give you peace (Numbers 6:24-26). Have a blessed day 🙏",
            "Celebrating you today, {name}! 🎉 May this new year be your year of answered prayers, divine health and overflowing grace. God bless you."
        ),
        Tone.SHORT to listOf(
            "Happy birthday, {name}! 🎉",
            "HBD {name}! Have an amazing day 🎂",
            "Happy birthday {name} ❤️ Enjoy your day!",
            "Cheers to you, {name}! Happy birthday 🥳"
        ),
    )

    private val celebration: Map<Tone, List<String>> = mapOf(
        Tone.WARM to listOf(
            "Happy {occasion}, {name}! ❤️ Thinking of you today and wishing you so much joy. Celebrate well!",
            "{name}, congratulations on your {occasion}! 🎉 You deserve every bit of today's happiness."
        ),
        Tone.FUNNY to listOf(
            "Happy {occasion}, {name}! 🎉 May the celebrations be loud and the jollof be plenty 😄",
            "{name}! It's your {occasion} 🥳 I'm officially reminding you to have too much fun today."
        ),
        Tone.PRAYERFUL to listOf(
            "Happy {occasion}, {name}! 🙏 May God bless this day and everything it represents, and may His grace keep you always.",
            "{name}, on your {occasion}, I pray that God's love, peace and favour rest upon you. Congratulations! 🙏"
        ),
        Tone.SHORT to listOf(
            "Happy {occasion}, {name}! 🎉",
            "Congrats on your {occasion}, {name}! ❤️"
        ),
    )

    fun options(type: OccasionType, tone: Tone): List<String> =
        if (type == OccasionType.BIRTHDAY) birthday.getValue(tone) else celebration.getValue(tone)

    fun render(template: String, firstName: String, type: OccasionType, customLabel: String, years: Int?): String {
        val occasion = when (type) {
            OccasionType.BIRTHDAY -> "birthday"
            OccasionType.ANNIVERSARY -> "anniversary"
            OccasionType.WEDDING -> "wedding day"
            OccasionType.EVENT -> customLabel.ifBlank { "big day" }
            OccasionType.CUSTOM -> customLabel.ifBlank { "special day" }
        }
        val ageText = if (years != null && type == OccasionType.BIRTHDAY) ", your ${DateMath.ordinal(years)}," else ""
        return template.replace("{name}", firstName.ifBlank { "friend" })
            .replace("{age}", ageText)
            .replace("{occasion}", occasion)
            // "this new year, your 29th." not "this new year, your 29th,."
            .replace(",.", ".").replace(",!", "!")
    }

    fun subject(type: OccasionType, firstName: String, customLabel: String = ""): String = when (type) {
        OccasionType.BIRTHDAY -> "Happy birthday, $firstName! 🎂"
        OccasionType.ANNIVERSARY -> "Happy anniversary, $firstName! ❤️"
        OccasionType.WEDDING -> "Congratulations, $firstName! 💍"
        else -> "Thinking of you today, $firstName 🎉"
    }

    fun draft(type: OccasionType, tone: Tone, index: Int, firstName: String, customLabel: String = "", years: Int? = null): String {
        val list = options(type, tone)
        val t = list[((index % list.size) + list.size) % list.size]
        return render(t, firstName, type, customLabel, years)
    }
}

/** What each rung of the ladder asks the user to do. */
object Prompts {
    fun title(stage: Stage, firstName: String, what: String): String = when (stage) {
        Stage.WEEK -> "7 days to $firstName's $what"
        Stage.FIVE -> "5 days to $firstName's $what"
        Stage.ONE -> "Tomorrow is $firstName's $what"
        Stage.DAY -> if (what == "birthday") "🎂 $firstName's birthday today" else "Today is $firstName's $what 🎉"
    }

    /** v0.2: the friends-link notification. */
    fun friendsTitle(names: List<String>): String = when (names.size) {
        0 -> ""
        1 -> "🎂 ${names[0]} added their birthday"
        else -> "🎂 ${names.size} friends added their birthdays"
    }

    fun friendsBody(names: List<String>): String = when {
        names.isEmpty() -> ""
        names.size == 1 -> "Ranti will remind you before their day."
        names.size <= 3 -> names.joinToString(", ") + ". Ranti will remind you before each day."
        else -> names.take(3).joinToString(", ") + " and ${names.size - 3} more. Ranti will remind you before each day."
    }

    fun body(stage: Stage, firstName: String): String = when (stage) {
        Stage.WEEK -> "🎁 Pick a gift idea now, while there's still time to get it."
        Stage.FIVE -> "✍️ Write your message today, so it's ready and from the heart."
        Stage.ONE -> "📞 Plan the call: what time will you ring $firstName tomorrow?"
        Stage.DAY -> "Tap for a ready wish: WhatsApp, SMS or email in one tap."
    }

    fun job(stage: Stage): String = when (stage) {
        Stage.WEEK -> "Pick a gift"
        Stage.FIVE -> "Write your message"
        Stage.ONE -> "Plan the call"
        Stage.DAY -> "Send your wishes"
    }

    val nightlyTitle = "Is there someone's birthday you'd like to remember?"
    private val nightlyBodies = listOf(
        "Tap to add them in 10 seconds. Ranti will remind you 7, 5 and 1 day before.",
        "A friend, a cousin, someone from church? Add one name tonight.",
        "Think of one person you'd hate to forget. Add them now.",
        "Weddings and anniversaries count too. Tap to add.",
    )
    fun nightlyBody(seed: Int) = nightlyBodies[((seed % nightlyBodies.size) + nightlyBodies.size) % nightlyBodies.size]
}

/** v0.2: the birthday link and its share text. */
object BirthdayLink {
    const val BASE = "https://ranti-ng.vercel.app"
    fun url(code: String) = "$BASE/b/$code"
    fun shareText(code: String, name: String): String =
        "🎂 When's your birthday? Pop it in here so I never forget it 👇\n" + url(code)
    private const val ALPHABET = "abcdefghijkmnpqrstuvwxyz23456789"
    /** 10 characters from an unambiguous alphabet (no 0/o, 1/l). */
    fun newCode(rnd: java.util.Random): String = (1..10).map { ALPHABET[rnd.nextInt(ALPHABET.length)] }.joinToString("")
    fun newKey(rnd: java.util.Random): String {
        val b = ByteArray(32); rnd.nextBytes(b)
        return java.util.Base64.getUrlEncoder().withoutPadding().encodeToString(b)
    }
}

object Phone {
    /** Digits for wa.me: Nigerian local numbers (0803…) become 234803…; '+' and spaces are dropped. */
    fun forWhatsApp(raw: String): String {
        PhoneNorm.international(raw)?.let { return it }
        return raw.filter { it.isDigit() }
    }
}
