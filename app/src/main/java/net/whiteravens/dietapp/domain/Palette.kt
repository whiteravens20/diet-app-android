// Copyright (C) 2026 White Ravens. AGPL-3.0-only with an additional term; see LICENSE and NOTICE.

package net.whiteravens.dietapp.domain

/**
 * The colour schemes the web offers an account (`palette` of `/users/me`), each drawn light or dark
 * as the phone is set. [wire] is the name the API gives it.
 */
enum class Palette(val wire: String) {
    DEFAULT("default"),
    OCEAN("ocean"),
    FOREST("forest"),
    SUNSET("sunset"),
    MONO("mono"),
    ROSE("rose"),
    ;

    companion object {
        /** The scheme the API names [wire], or null for one this version does not know. */
        fun of(wire: String?): Palette? = entries.firstOrNull { it.wire == wire }
    }
}
