package org.matrix.androidsdk.crypto.verification

import android.content.Context
import org.matrix.androidsdk.R

object VerificationEmoji {

    data class EmojiRepresentation(val emoji: String, val name: String)

    fun getEmojiForCode(code: Int, context: Context?): EmojiRepresentation? {
        when (code) {
            0 -> return EmojiRepresentation("🐶", context?.getString(R.string.verification_emoji_dog)
                    ?: "")
            1 -> return EmojiRepresentation("🐱", context?.getString(R.string.verification_emoji_cat)
                    ?: "")
            2 -> return EmojiRepresentation("🦁", context?.getString(R.string.verification_emoji_lion)
                    ?: "")
            3 -> return EmojiRepresentation("🐎", context?.getString(R.string.verification_emoji_horse)
                    ?: "")
            4 -> return EmojiRepresentation("🦄", context?.getString(R.string.verification_emoji_unicorn)
                    ?: "")
            5 -> return EmojiRepresentation("🐷", context?.getString(R.string.verification_emoji_pig)
                    ?: "")
            6 -> return EmojiRepresentation("🐘", context?.getString(R.string.verification_emoji_elephant)
                    ?: "")
            7 -> return EmojiRepresentation("🐰", context?.getString(R.string.verification_emoji_rabbit)
                    ?: "")
            8 -> return EmojiRepresentation("🐼", context?.getString(R.string.verification_emoji_panda)
                    ?: "")
            9 -> return EmojiRepresentation("🐓", context?.getString(R.string.verification_emoji_rooster)
                    ?: "")
            10 -> return EmojiRepresentation("🐧", context?.getString(R.string.verification_emoji_penguin)
                    ?: "")
            11 -> return EmojiRepresentation("🐢", context?.getString(R.string.verification_emoji_turtle)
                    ?: "")
            12 -> return EmojiRepresentation("🐟", context?.getString(R.string.verification_emoji_fish)
                    ?: "")
            13 -> return EmojiRepresentation("🐙", context?.getString(R.string.verification_emoji_octopus)
                    ?: "")
            14 -> return EmojiRepresentation("🦋", context?.getString(R.string.verification_emoji_butterfly)
                    ?: "")
            15 -> return EmojiRepresentation("🌷", context?.getString(R.string.verification_emoji_flower)
                    ?: "")
            16 -> return EmojiRepresentation("🌳", context?.getString(R.string.verification_emoji_tree)
                    ?: "")
            17 -> return EmojiRepresentation("🌵", context?.getString(R.string.verification_emoji_cactus)
                    ?: "")
            18 -> return EmojiRepresentation("🍄", context?.getString(R.string.verification_emoji_mushroom)
                    ?: "")
            19 -> return EmojiRepresentation("🌏", context?.getString(R.string.verification_emoji_globe)
                    ?: "")
            20 -> return EmojiRepresentation("🌙", context?.getString(R.string.verification_emoji_moon)
                    ?: "")
            21 -> return EmojiRepresentation("☁", context?.getString(R.string.verification_emoji_cloud)
                    ?: "")
            22 -> return EmojiRepresentation("🔥", context?.getString(R.string.verification_emoji_fire)
                    ?: "")
            23 -> return EmojiRepresentation("🍌", context?.getString(R.string.verification_emoji_banana)
                    ?: "")
            24 -> return EmojiRepresentation("🍎", context?.getString(R.string.verification_emoji_apple)
                    ?: "")
            25 -> return EmojiRepresentation("🍓", context?.getString(R.string.verification_emoji_strawberry)
                    ?: "")
            26 -> return EmojiRepresentation("🌽", context?.getString(R.string.verification_emoji_corn)
                    ?: "")
            27 -> return EmojiRepresentation("🍕", context?.getString(R.string.verification_emoji_pizza)
                    ?: "")
            28 -> return EmojiRepresentation("🎂", context?.getString(R.string.verification_emoji_cake)
                    ?: "")
            29 -> return EmojiRepresentation("❤", context?.getString(R.string.verification_emoji_heart)
                    ?: "")
            30 -> return EmojiRepresentation("☺", context?.getString(R.string.verification_emoji_smiley)
                    ?: "")
            31 -> return EmojiRepresentation("🤖", context?.getString(R.string.verification_emoji_robot)
                    ?: "")
            32 -> return EmojiRepresentation("🎩", context?.getString(R.string.verification_emoji_hat)
                    ?: "")
            33 -> return EmojiRepresentation("👓", context?.getString(R.string.verification_emoji_glasses)
                    ?: "")
            34 -> return EmojiRepresentation("🔧", context?.getString(R.string.verification_emoji_wrench)
                    ?: "")
            35 -> return EmojiRepresentation("🎅", context?.getString(R.string.verification_emoji_santa)
                    ?: "")
            36 -> return EmojiRepresentation("👍", context?.getString(R.string.verification_emoji_thumbsup)
                    ?: "")
            37 -> return EmojiRepresentation("☂", context?.getString(R.string.verification_emoji_umbrella)
                    ?: "")
            38 -> return EmojiRepresentation("⌛", context?.getString(R.string.verification_emoji_hourglass)
                    ?: "")
            39 -> return EmojiRepresentation("⏰", context?.getString(R.string.verification_emoji_clock)
                    ?: "")
            40 -> return EmojiRepresentation("🎁", context?.getString(R.string.verification_emoji_gift)
                    ?: "")
            41 -> return EmojiRepresentation("💡", context?.getString(R.string.verification_emoji_lightbulb)
                    ?: "")
            42 -> return EmojiRepresentation("📕", context?.getString(R.string.verification_emoji_book)
                    ?: "")
            43 -> return EmojiRepresentation("✏", context?.getString(R.string.verification_emoji_pencil)
                    ?: "")
            44 -> return EmojiRepresentation("📎", context?.getString(R.string.verification_emoji_paperclip)
                    ?: "")
            45 -> return EmojiRepresentation("✂", context?.getString(R.string.verification_emoji_scissors)
                    ?: "")
            46 -> return EmojiRepresentation("🔒", context?.getString(R.string.verification_emoji_lock)
                    ?: "")
            47 -> return EmojiRepresentation("🔑", context?.getString(R.string.verification_emoji_key)
                    ?: "")
            48 -> return EmojiRepresentation("🔨", context?.getString(R.string.verification_emoji_hammer)
                    ?: "")
            49 -> return EmojiRepresentation("☎", context?.getString(R.string.verification_emoji_telephone)
                    ?: "")
            50 -> return EmojiRepresentation("🏁", context?.getString(R.string.verification_emoji_flag)
                    ?: "")
            51 -> return EmojiRepresentation("🚂", context?.getString(R.string.verification_emoji_train)
                    ?: "")
            52 -> return EmojiRepresentation("🚲", context?.getString(R.string.verification_emoji_bicycle)
                    ?: "")
            53 -> return EmojiRepresentation("✈", context?.getString(R.string.verification_emoji_airplane)
                    ?: "")
            54 -> return EmojiRepresentation("🚀", context?.getString(R.string.verification_emoji_rocket)
                    ?: "")
            55 -> return EmojiRepresentation("🏆", context?.getString(R.string.verification_emoji_trophy)
                    ?: "")
            56 -> return EmojiRepresentation("⚽", context?.getString(R.string.verification_emoji_ball)
                    ?: "")
            57 -> return EmojiRepresentation("🎸", context?.getString(R.string.verification_emoji_guitar)
                    ?: "")
            58 -> return EmojiRepresentation("🎺", context?.getString(R.string.verification_emoji_trumpet)
                    ?: "")
            59 -> return EmojiRepresentation("🔔", context?.getString(R.string.verification_emoji_bell)
                    ?: "")
            60 -> return EmojiRepresentation("⚓", context?.getString(R.string.verification_emoji_anchor)
                    ?: "")
            61 -> return EmojiRepresentation("🎧", context?.getString(R.string.verification_emoji_headphone)
                    ?: "")
            62 -> return EmojiRepresentation("📁", context?.getString(R.string.verification_emoji_folder)
                    ?: "")
            63 -> return EmojiRepresentation("📌", context?.getString(R.string.verification_emoji_pin)
                    ?: "")
            else -> return null
        }

    }

    /**
     * 🐶 Dog
    🐱 Cat
    🦁 Lion
    🐎 Horse
    🦄 Unicorn
    🐷 Pig
    🐘 Elephant
    🐰 Rabbit
    🐼 Panda
    🐓 Rooster
    🐧 Penguin
    🐢 Turtle
    🐟 Fish
    🐙 Octopus
    🦋 Butterfly
    🌷 Flower
    🌳 Tree
    🌵 Cactus
    🍄 Mushroom
    🌏 Globe
    🌙 Moon
    ☁ Cloud
    🔥 Fire
    🍌 Banana
    🍎 Apple
    🍓 Strawberry
    🌽 Corn
    🍕 Pizza
    🎂 Cake
    ❤ Heart
    ☺ Smiley
    🤖 Robot
    🎩 Hat
    👓 Glasses
    🔧 Wrench
    🎅 Santa
    👍 Thumbs Up
    ☂ Umbrella
    ⌛ Hourglass
    ⏰ Clock
    🎁 Gift
    💡 Light Bulb
    📕 Book
    ✏ Pencil
    📎 Paperclip
    ✂ Scissors
    🔒 Lock
    🔑 Key
    🔨 Hammer
    ☎ Telephone
    🏁 Flag
    🚂 Train
    🚲 Bicycle
    ✈ Airplane
    🚀 Rocket
    🏆 Trophy
    ⚽ Ball
    🎸 Guitar
    🎺 Trumpet
    🔔 Bell
    ⚓ Anchor
    🎧 Headphone
    📁 Folder
    📌 Pin
     */

}