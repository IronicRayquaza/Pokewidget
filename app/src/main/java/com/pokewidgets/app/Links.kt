package com.pokewidgets.app

/**
 * Outbound links, in one place so a dead invite is one edit rather than a search.
 *
 * Each surface gets its **own** Discord invite code — this app, the landing page, the README —
 * because Discord counts uses per code. That is the whole of the channel attribution for this
 * beta, and it costs nothing to set up: create three invites, never-expiring, unlimited uses.
 * Only the app's code belongs here.
 *
 * Blank means the invite does not exist yet, and the UI hides the button entirely rather than
 * shipping one that lands on a Discord error page.
 */
object Links {

    /** Never-expiring, unlimited-use invite, created specifically for the in-app button. */
    const val DISCORD_INVITE = ""

    val hasDiscord: Boolean get() = DISCORD_INVITE.isNotBlank()
}
