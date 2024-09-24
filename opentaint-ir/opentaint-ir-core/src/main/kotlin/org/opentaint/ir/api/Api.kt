package org.opentaint.ir.api

import org.objectweb.asm.tree.ClassNode
import org.objectweb.asm.tree.MethodNode
import org.opentaint.ir.impl.signature.FieldResolution
import org.opentaint.ir.impl.signature.MethodResolution
import org.opentaint.ir.impl.signature.TypeResolution
import java.io.Closeable
import java.io.File
import java.io.InputStream

/**
 * represents structure which has access modifiers like field, class, method
 */
interface Accessible {
    /** byte-code access value */
    suspend fun access(): Int

}

/**
 * Represents information about JVM Class loaded from some `location`
 */
interface ClassId : Accessible {

    /** `location `*/
    val location: ByteCodeLocation?

    /** full class name */
    val name: String

    /** simple class name */
    val simpleName: String

    suspend fun byteCode(): ClassNode?

    suspend fun innerClasses(): List<ClassId>

    suspend fun outerClass(): ClassId?

    suspend fun isAnonymous(): Boolean

    suspend fun signature(): TypeResolution

    suspend fun outerMethod(): MethodId?

    /** list of methods */
    suspend fun methods(): List<MethodId>

    /** superclass for this class.*/
    suspend fun superclass(): ClassId?

    /** list of implemented interfaces for this class. Interfaces are not inherited from super classes. */
    suspend fun interfaces(): List<ClassId>

    /** list of annotations. Annotations are not inherited from super classes. */
    suspend fun annotations(): List<ClassId>

    /** list of fields. Fields are not inherited from super classes. */
    suspend fun fields(): List<FieldId>

}

/**
 * Immutable structure represented a file system location of bytecode such as `jar` or build folder.
 *
 * Each location has `unique id` which may be used as unique identifier of location.
 * Calculation of such `id` may include file system operation and may be expensive.
 * For optimization this class uses `cachedId` calculated at the time when instance of this
 * class is created and `currentId` i.e id calculated the same way as when this method
 *
 */
interface ByteCodeLocation {

    /**
     * unmodifiable id for this `location` instance.
     * getter for this field should omit any expensive operations
     */
    val id: String

    val scope: LocationScope

    /**
     * this operation may involve file-system operations and may be expensive
     *
     * @returns true if file-system has changes not reflected in current `location`
     */
    fun isChanged(): Boolean

    /**
     * @return new refreshed version of this `location`
     */
    fun createRefreshed(): ByteCodeLocation

    /**
     * resolve byte-code based on class name
     *
     * @param classFullName full name of the class to be resolved
     * @return input stream with byte-code or null if class is not found in this location
     */
    suspend fun resolve(classFullName: String): InputStream?

    /**
     * loader for loading all classes from this location
     */
    suspend fun loader(): ByteCodeLoader?
}

enum class LocationScope {
    RUNTIME,
    APP
}

/**
 * Loader for byte-code location
 */
interface ByteCodeLoader {

    /** byte-code location for this loader */
    val location: ByteCodeLocation

    /**
     * map with all classed to be loaded: class full name -> stream with byte code.
     * Null stream for some class means that class byte-code will be loaded lazily or in a
     * background if this class will be returned by `asyncResources` method
     *
     * @return map className -> byte-code stream
     */
    suspend fun allResources(): ClassLoadingContainer

    /**
     * map with resources: class full name -> stream with byte code.
     * If stream is null that means that class byte-code will be loaded lazy or in a
     * background if this class will be returned by `asyncResources` method
     *
     * @return map className -> byte-code stream
     */
    suspend fun asyncResources(): suspend () -> ClassLoadingContainer?
}

/**
 * Classes container
 */
interface ClassLoadingContainer : Closeable {

    /** map name -> resources */
    val classesToLoad: Map<String, InputStream?>

    override fun close() {
    }
}

/**
 * represents jvm class method
 */
interface MethodId : Accessible {
    /** method name */
    val name: String

    /** reference to class */
    val classId: ClassId

    suspend fun signature(): MethodResolution

    /**
     * @return return type of the method or null in case of void methods
     */
    suspend fun returnType(): ClassId?

    /**
     * @return method parameters
     */
    suspend fun parameters(): List<ClassId>

    /**
     * list of annotations for this methods
     */
    suspend fun annotations(): List<ClassId>

    /**
     * method description
     */
    suspend fun description(): String

    /**
     * Return ASM method node with byte-code instructions. If byte-code location is differs from
     * file system then method will return null.
     *
     * @return ASM method node
     */
    suspend fun readBody(): MethodNode?

}

/**
 * represents class field
 */
interface FieldId : Accessible {

    /** field name */
    val name: String

    /** class of this field */
    val classId: ClassId

    suspend fun signature(): FieldResolution

    /** field type */
    suspend fun type(): ClassId?

    /**
     * list of annotations for this field
     */
    suspend fun annotations(): List<ClassId>

}

/**
 * Represents classpath i.e number of locations of byte code.
 *
 * Classpath **must be** closed when it's not needed anymore.
 * This will release references from database to possibly outdated libraries
 */
interface ClasspathSet : Closeable {

    /** locations of this classpath */
    val locations: List<ByteCodeLocation>

    /** reference to database */
    val db: CompilationDatabase

    /**
     * in case if some locations are outdated it's required to call this method which will create new instance
     * with refreshed locations. Old instance will be automatically closed.
     *
     * @return new instance with refreshed locations
     */
    suspend fun refreshed(closeOld: Boolean = true): ClasspathSet

    /**
     *  @param name full name of the class
     *
     * @return classId or null if there is no such class found in locations
     */
    suspend fun findClassOrNull(name: String): ClassId?

    /**
     * @param name full name of the class
     * @param allHierarchy search will return all subclasses through all hierarchy
     * @return list of direct subclasses if they are exists in classpath or empty list
     */
    suspend fun findSubClasses(name: String, allHierarchy: Boolean = false): List<ClassId>

    /**
     * @param classId classId of super class
     * @param allHierarchy search will return all subclasses through all hierarchy
     * @return list of direct subclasses if they are exists in classpath or empty list
     */
    suspend fun findSubClasses(classId: ClassId, allHierarchy: Boolean = false): List<ClassId>

    /**
     * query index by specified term
     *
     * @param key index key
     * @param term term for index
     */
    suspend fun <T> query(key: String, term: String): List<T>

    /**
     * query index by specified term
     *
     * @param key index key
     * @param location to limit location
     * @param term term for index
     */
    suspend fun <T> query(key: String, location: ByteCodeLocation, term: String): List<T>

}


/**
 * field usage mode
 */
enum class FieldUsageMode {

    /** search for reads */
    READ,

    /** search for writes */
    WRITE
}


/**
 * Compilation database for
 *
 * `close` method should be called when database is not needed anymore
 */
interface CompilationDatabase : Closeable {

    /**
     * create classpath instance
     *
     * @param dirOrJars list of byte-code resources to be processed and included in classpath
     * @return new classpath instance associated with specified byte-code locations
     */
    suspend fun classpathSet(dirOrJars: List<File>): ClasspathSet

    /**
     * process and index single byte-code resource
     * @param dirOrJar build folder or jar file
     * @return current database instance
     */
    suspend fun load(dirOrJar: File): CompilationDatabase

    /**
     * process and index byte-code resources
     * @param dirOrJars list of build folder or jar file
     * @return current database instance
     */
    suspend fun load(dirOrJars: List<File>): CompilationDatabase

    /**
     * explicitly refreshes the state of resources from file-system.
     * That means that any new classpath created after refresh is done will
     * reference fresh byte-code from file-system. While any classpath created
     * before `refresh` will still reference byte-code which is outdated
     * according to file-system
     */
    suspend fun refresh()

    /**
     * watch file system for changes and refreshes the state of database in case loaded resources and resources from
     * file systems are different.
     *
     * @return current database instance
     */
    fun watchFileSystemChanges(): CompilationDatabase

    /**
     * await background jobs
     */
    suspend fun awaitBackgroundJobs()
}