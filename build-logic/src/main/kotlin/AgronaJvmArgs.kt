/** JVM flags every process using Agrona 2.x needs: it accesses jdk.internal.misc.Unsafe directly. */
object AgronaJvmArgs {
    val ALL = listOf("--add-exports", "java.base/jdk.internal.misc=ALL-UNNAMED")
}
