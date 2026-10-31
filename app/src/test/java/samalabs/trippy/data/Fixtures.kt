package samalabs.trippy.data

object Fixtures {
    fun read(name: String): String =
        requireNotNull(javaClass.classLoader!!.getResource("fixtures/$name")) { "missing fixture $name" }.readText()
}
