package com.pokewidgets.app

/**
 * Outbound links, in one place so a dead invite is one edit rather than a search.
 *
 * One invite is used across the app, the landing page and the README, deliberately. Discord
 * counts uses per code, so separate codes per surface would have said where people came from —
 * that was offered and declined as not worth the setup for a first beta. If the question ever
 * becomes interesting, the fix is three codes in three places, not anything in the app.
 *
 * Blank means the invite does not exist yet, and the UI hides the button entirely rather than
 * shipping one that lands on a Discord error page.
 */
object Links {

    /** Never-expiring, unlimited-use invite to the PokeWidget beta server. */
    const val DISCORD_INVITE = "https://discord.gg/QFuHPZJRqM"

    val hasDiscord: Boolean get() = DISCORD_INVITE.isNotBlank()
}
