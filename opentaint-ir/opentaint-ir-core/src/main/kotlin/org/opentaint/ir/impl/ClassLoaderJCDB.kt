package org.opentaint.ir.impl

//
//class ClassLoaderJIRDB(private val classLoader: ClassLoader) : JIRDB {
//
//    override val globalIdStore = InMemeoryGlobalIdsStore() // let's use any
//
//    override suspend fun awaitBackgroundJobs() {
//    }
//
//    override suspend fun classpathSet(dirOrJars: List<File>) = ClassLoaderClasspath(this, classLoader)
//
//    override suspend fun load(dirOrJar: File): JIRDB = this
//
//    override suspend fun load(dirOrJars: List<File>): JIRDB = this
//
//    override suspend fun loadLocations(locations: List<ByteCodeLocation>): JIRDB = this
//
//    override suspend fun refresh() {
//    }
//
//    override fun watchFileSystemChanges() = this
//
//    override fun close() {
//    }
//}
//
//interface ClassLookupAware {
//
//    val classpath: ClasspathSet
//
//    fun findByJVMName(name: String): ClassId = runBlocking {
//        classpath.findClass(name.jirdbName())
//    }
//
//}
//
//
//class ClassLoaderClasspath(override val db: JIRDB, private val classLoader: ClassLoader) : ClasspathSet {
//
//    override val locations: List<ByteCodeLocation>
//        get() = emptyList()
//
//    override suspend fun findClassOrNull(name: String): ClassId? {
//        try {
//            if (name.endsWith("[]")) {
//                val targetName = name.removeSuffix("[]")
//                return findClassOrNull(targetName)?.let {
//                    ArrayClassIdImpl(it)
//                }
//            }
//
//            val predefined = PredefinedPrimitives.of(name, this)
//            if (predefined != null) {
//                return predefined
//            }
//            val jvmName = name.jvmName()
//            println("find by name: $jvmName")
//            val clazz = Class.forName(jvmName, true, classLoader)
//            return ReflectionBasedClassId(this, clazz)
//        } catch (e: ClassNotFoundException) {
//            return null
//        }
//    }
//
//    override suspend fun findSubClasses(name: String, allHierarchy: Boolean): List<ClassId> {
//        return emptyList()
//    }
//
//    override suspend fun findSubClasses(classId: ClassId, allHierarchy: Boolean): List<ClassId> {
//        return emptyList()
//    }
//
//    override suspend fun <T : Serializable> query(key: String, term: String): List<T> {
//        return emptyList()
//    }
//
//    override suspend fun <T : Serializable> query(key: String, location: ByteCodeLocation, term: String): List<T> {
//        return emptyList()
//    }
//
//    override suspend fun refreshed(closeOld: Boolean) = this
//
//    override fun close() {
//    }
//
//}
//
//
//private class ReflectionBasedClassId(
//    override val classpath: ClasspathSet,
//    private val clazz: Class<*>
//) : ClassId, ClassLookupAware {
//    companion object {
//
//        val method = Class::class.java.getDeclaredMethod("getGenericSignature0").also {
//            it.isAccessible = true
//        }
//    }
//
//
//    override val location: ByteCodeLocation?
//        get() = null
//    override val name: String
//        get() = clazz.canonicalName
//    override val simpleName: String
//        get() = clazz.simpleName
//
//    private val fields by lazy(LazyThreadSafetyMode.NONE) {
//        clazz.declaredFields.map {
//            ReflectionBasedField(this, it)
//        }
//    }
//
//    private val annotations by lazy(LazyThreadSafetyMode.NONE) {
//        clazz.annotations.map { findByJVMName(it.annotationClass.java.name) }
//    }
//
//    private val interfaces by lazy(LazyThreadSafetyMode.NONE) {
//        clazz.interfaces.map { findByJVMName(it.name) }
//    }
//
//    private val methods by lazy(LazyThreadSafetyMode.NONE) {
//        clazz.declaredMethods.map { ReflectionBasedMethodId(this, it) } +
//                clazz.declaredConstructors.map { ReflectionBasedConstructorId(this, it) }
//    }
//
//    private val innerClasses by lazy(LazyThreadSafetyMode.NONE) {
//        clazz.declaredClasses.map { findByJVMName(it.name) }
//    }
//
//    override suspend fun access() = clazz.modifiers
//
//    override suspend fun annotations() = annotations
//
//    override suspend fun byteCode(): ClassNode? = null
//
//    override suspend fun fields() = fields
//
//    override suspend fun innerClasses() = innerClasses
//
//    override suspend fun interfaces() = interfaces
//
//    override suspend fun isAnonymous() = clazz.isAnonymousClass
//
//    override suspend fun methods() = methods
//
//    override suspend fun outerClass(): ClassId? {
//        return clazz.enclosingClass?.let {
//            ReflectionBasedClassId(classpath, it)
//        }
//    }
//
//    override suspend fun outerMethod(): MethodId? {
//        return clazz.enclosingMethod?.let {
//            ReflectionBasedMethodId(this, it)
//        }
//    }
//
//    override suspend fun resolution(): TypeResolution {
//        return TypeSignature.of(method.invoke(clazz) as? String, classpath)
//    }
//
//    override suspend fun superclass(): ClassId? {
//        return clazz.superclass?.let {
//            ReflectionBasedClassId(classpath, it)
//        }
//    }
//
//    override fun equals(other: Any?): Boolean {
//        if (this === other) return true
//        if (javaClass != other?.javaClass) return false
//
//        other as ReflectionBasedClassId
//
//        if (clazz != other.clazz) return false
//
//        return true
//    }
//
//    override fun hashCode(): Int {
//        return clazz.hashCode()
//    }
//
//
//}
//
//
//private abstract class AbstractExecutableId(
//    override val classId: ReflectionBasedClassId,
//    private val executable: Executable
//) : MethodId, ClassLookupAware {
//    override val classpath: ClasspathSet
//        get() = classId.classpath
//
//    private val annotations by lazy(LazyThreadSafetyMode.NONE) {
//        executable.annotations.map { findByJVMName(it.annotationClass.java.name) }
//    }
//
//    private val parameters by lazy(LazyThreadSafetyMode.NONE) {
//        executable.parameters.map { findByJVMName(it.type.name) }
//    }
//
//    override suspend fun access() = executable.modifiers
//
//    override suspend fun annotations() = annotations
//
//    override suspend fun description() = executable.toString()
//
//    override suspend fun parameters() = parameters
//
//    override suspend fun readBody() = null
//    override fun equals(other: Any?): Boolean {
//        if (this === other) return true
//        if (javaClass != other?.javaClass) return false
//
//        other as AbstractExecutableId
//
//        if (classId != other.classId) return false
//        if (executable != other.executable) return false
//
//        return true
//    }
//
//    override fun hashCode(): Int {
//        var result = classId.hashCode()
//        result = 31 * result + executable.hashCode()
//        return result
//    }
//
//
//}
//
//
//private class ReflectionBasedMethodId(clazz: ReflectionBasedClassId, val method: Method) :
//    AbstractExecutableId(clazz, method) {
//
//    companion object {
//        private val signatureField = Method::class.java.getDeclaredField("signature").also {
//            it.setAccessible(true)
//        }
//    }
//
//    override val name: String
//        get() = method.name
//
//    override suspend fun returnType(): ClassId {
//        return findByJVMName(method.returnType.name)
//    }
//
//    override suspend fun resolution(): MethodResolution {
//        return MethodSignature.of(signatureField.get(method) as? String, classId.classpath)
//    }
//
//    override suspend fun signature(internalNames: Boolean): String {
//        TODO("Not yet implemented")
//    }
//}
//
//private class ReflectionBasedConstructorId(
//    override val classId: ReflectionBasedClassId,
//    constructor: Constructor<*>
//) : AbstractExecutableId(classId, constructor) {
//
//    override val name: String
//        get() = "<init>"
//
//    override suspend fun returnType() = classId
//
//    override suspend fun resolution() = Raw
//
//    override suspend fun signature(internalNames: Boolean): String {
//        TODO("Not yet implemented")
//    }
//
//}
//
//
//private class ReflectionBasedField(
//    override val classId: ReflectionBasedClassId,
//    private val field: Field
//) : FieldId, ClassLookupAware {
//
//    companion object {
//        private val signatureField = Field::class.java.getDeclaredField("signature").also {
//            it.setAccessible(true)
//        }
//    }
//
//    override val classpath: ClasspathSet
//        get() = classId.classpath
//
//    override suspend fun access() = field.modifiers
//
//    override val name: String
//        get() = this.field.name
//
//    override suspend fun annotations(): List<ClassId> {
//        return field.annotations.map { findByJVMName(it.annotationClass.java.name) }
//    }
//
//    override suspend fun resolution(): FieldResolution {
//        return FieldSignature.extract(signatureField.get(field) as? String, classId.classpath)
//    }
//
//    override suspend fun type(): ClassId {
//        return findByJVMName(field.type.name)
//    }
//
//    override fun equals(other: Any?): Boolean {
//        if (this === other) return true
//        if (javaClass != other?.javaClass) return false
//
//        other as ReflectionBasedField
//
//        if (classId != other.classId) return false
//        if (field != other.field) return false
//
//        return true
//    }
//
//    override fun hashCode(): Int {
//        var result = classId.hashCode()
//        result = 31 * result + field.hashCode()
//        return result
//    }
//
//}
