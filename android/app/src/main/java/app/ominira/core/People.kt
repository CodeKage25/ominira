package app.ominira.core

// The Gateway's seeded customers, for a demo phone that changes hands. PIN 2 0 7 5 for all.
// token null: the build's own demo token (demo-customer). lang null: keep the language chosen.
data class DemoPerson(val key: String, val firstName: String, val token: String?, val lang: Lang?)

object DemoPeople {
    val all: List<DemoPerson> = listOf(
        DemoPerson("ronke", "Ronke", null, null),
        DemoPerson("tolu", "Tolu", "demo-tolu", Lang.Yo),
        DemoPerson("mama", "Mama", "demo-mama", Lang.Yo),
        DemoPerson("musa", "Musa", "demo-musa", Lang.Ha),
        DemoPerson("aisha", "Aisha", "demo-aisha", Lang.Ha),
        DemoPerson("emeka", "Emeka", "demo-emeka", Lang.Ig),
        DemoPerson("chika", "Chika", "demo-chika", Lang.Ig),
    )

    fun byKey(key: String?): DemoPerson? = all.firstOrNull { it.key == key }
}
