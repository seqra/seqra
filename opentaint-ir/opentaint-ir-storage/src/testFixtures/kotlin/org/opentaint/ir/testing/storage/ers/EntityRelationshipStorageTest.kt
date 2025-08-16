package org.opentaint.ir.testing.storage.ers

import org.opentaint.ir.api.storage.ers.ERSConflictingTransactionException
import org.opentaint.ir.api.storage.ers.ERSNonExistingEntityException
import org.opentaint.ir.api.storage.ers.ERSTransactionFinishedException
import org.opentaint.ir.api.storage.ers.EmptyErsSettings
import org.opentaint.ir.api.storage.ers.EntityIterable
import org.opentaint.ir.api.storage.ers.EntityRelationshipStorageSPI
import org.opentaint.ir.api.storage.ers.ErsSettings
import org.opentaint.ir.api.storage.ers.FindOption
import org.opentaint.ir.api.storage.ers.Transaction
import org.opentaint.ir.api.storage.ers.blobOf
import org.opentaint.ir.api.storage.ers.compressed
import org.opentaint.ir.api.storage.ers.links
import org.opentaint.ir.api.storage.ers.nonSearchable
import org.opentaint.ir.api.storage.ers.propertyOf
import org.opentaint.ir.api.jvm.storage.ers.typed.ErsSearchability
import org.opentaint.ir.api.jvm.storage.ers.typed.ErsType
import org.opentaint.ir.api.jvm.storage.ers.typed.all
import org.opentaint.ir.api.jvm.storage.ers.typed.find
import org.opentaint.ir.api.jvm.storage.ers.typed.getEntityOrNull
import org.opentaint.ir.api.jvm.storage.ers.typed.link
import org.opentaint.ir.api.jvm.storage.ers.typed.newEntity
import org.opentaint.ir.api.jvm.storage.ers.typed.property
import org.opentaint.ir.impl.storage.ers.kv.KV_ERS_SPI
import org.opentaint.ir.impl.storage.ers.ram.RAM_ERS_SPI
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Assumptions
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

abstract class EntityRelationshipStorageTest {

    open val ersSettings: ErsSettings get() = EmptyErsSettings
    abstract val ersId: String

    private val ersSpi by lazy(LazyThreadSafetyMode.NONE) {
        EntityRelationshipStorageSPI.getProvider(ersId)
    }
    protected lateinit var txn: Transaction

    @Test
    fun createEntityLoadEntity() {
        val user = txn.newEntity("User")
        val id = user.id
        assertEquals(user, txn.getEntityOrNull(id))
    }

    @Test
    fun stringProperties() {
        val user = txn.newEntity("User")
        user["login"] = "testuser"
        user["password"] = "!@#$%^&*()"
        val found = txn.find("User", "login", "testuser")
        assertFalse(found.isEmpty)
        assertEquals(1L, found.size)
        assertTrue(user in found)

        val login: String? by propertyOf(user)
        assertEquals("testuser", login)

        val noSuchProperty: String? by propertyOf(user)
        assertNull(noSuchProperty)

        // TODO: resolve this
        if (ersSpi.id == KV_ERS_SPI || ersSpi.id == RAM_ERS_SPI) {
            txn.getPropertyNames("User").toList().let { props ->
                assertEquals(2, props.size)
                assertEquals("login", props[0])
                assertEquals("password", props[1])
            }
        }

        user.deleteProperty("login")
        val deletedLogin: String? by propertyOf(user, "login")
        assertNull(deletedLogin)

        user.delete()
        val passwordOfDeleted: String? by propertyOf(user, "password")
        assertThrows<ERSNonExistingEntityException> {
            @Suppress("UNUSED_EXPRESSION")
            passwordOfDeleted
        }
    }

    @Test
    fun integerProperties() {
        val user1 = txn.newEntity("User").also { user ->
            user["seed"] = 2808.compressed
            user["login"] = "user1"
        }
        val user2 = txn.newEntity("User").also { user ->
            user["seed"] = 2808.compressed
            user["login"] = "user2"
        }

        // TODO: resolve this
        if (ersSpi.id == KV_ERS_SPI || ersSpi.id == RAM_ERS_SPI) {
            txn.getPropertyNames("User").toList().let { props ->
                assertEquals(2, props.size)
                assertEquals("login", props[0])
                assertEquals("seed", props[1])
            }
        }

        val found = txn.find("User", "seed", 2808.compressed)
        assertFalse(found.isEmpty)
        assertEquals(2L, found.size)
        assertTrue(user1 in found)
        assertTrue(user2 in found)

        val seed1: Int? by propertyOf(user1, "seed", compressed = true)
        assertEquals(2808, seed1)
        val seed2: Int? by propertyOf(user2, "seed", compressed = true)
        assertEquals(2808, seed2)
    }

    @Test
    fun longProperties() {
        val user1 = txn.newEntity("User").also { user ->
            user["seed"] = 2808L
            user["login"] = "user1"
        }
        val user2 = txn.newEntity("User").also { user ->
            user["seed"] = 2808L
            user["login"] = "user2"
        }
        val found = txn.find("User", "seed", 2808L)
        assertFalse(found.isEmpty)
        assertEquals(2L, found.size)
        assertTrue(user1 in found)
        assertTrue(user2 in found)

        val seed1: Long? by propertyOf(user1, "seed")
        assertEquals(2808L, seed1)
        val seed2: Long? by propertyOf(user2, "seed")
        assertEquals(2808L, seed2)
    }

    @Test
    fun byteArrayProperties() {
        val bytes = byteArrayOf(0, 1, 2, 3)
        val user = txn.newEntity("User")
        user["bytes"] = bytes

        val savedBytes: ByteArray? by propertyOf(user, "bytes")
        assertNotNull(savedBytes)
        assertArrayEquals(bytes, savedBytes)
    }

    @Test
    fun booleanProperties() {
        val user = txn.newEntity("User")
        user["banned"] = true

        val banned: Boolean? by propertyOf(user)
        assertNotNull(banned)
        assertTrue(banned!!)
    }

    @Test
    fun blobs() {
        val user = txn.newEntity("User")
        val png = byteArrayOf(1, 2, 3, 4)

        user["avatar"] = png.nonSearchable

        // there can be a property with the same name
        user["avatar"] = 1L.compressed

        val avatar by blobOf<ByteArray>(user)
        assertNotNull(avatar)
        assertArrayEquals(png, avatar)

        user.deleteBlob("avatar")
        assertNull(user.getBlob("avatar"))

        assertEquals(1L, user.getCompressed("avatar"))
    }

    @Test
    fun findLt() {
        // TODO: resolve this
        Assumptions.assumeTrue(ersSpi.id == KV_ERS_SPI || ersSpi.id == RAM_ERS_SPI)
        repeat(100) { i ->
            txn.newEntity("Endpoint").also { endpoint ->
                endpoint["url"] = "https://localhost/endpoint".nonSearchable
                endpoint["port"] = i
            }
        }
        txn.commit()
        txn.ers.transactional(readonly = true) { txn ->
            assertTrue(txn.findLt("Endpoint", "port", 0).isEmpty)
            assertTrue(equals(0, 49L downTo 0L, txn.findLt("Endpoint", "port", 50)))
            assertTrue(equals(0, 98L downTo 0L, txn.findLt("Endpoint", "port", 99)))
            assertTrue(equals(0, 99L downTo 0L, txn.findLt("Endpoint", "port", 100)))

            assertTrue(equals(0, 0L..0L, txn.findEqOrLt("Endpoint", "port", 0)))
            assertTrue(txn.findEqOrLt("Endpoint", "port", -1).isEmpty)
            assertTrue(equals(0, 50L downTo 0L, txn.find("Endpoint", "port", 50, FindOption.EqOrLt)))
            assertTrue(equals(0, 99L downTo 0L, txn.findEqOrLt("Endpoint", "port", 100)))
        }
    }

    @Test
    fun findGt() {
        // TODO: resolve this
        Assumptions.assumeTrue(ersSpi.id == KV_ERS_SPI || ersSpi.id == RAM_ERS_SPI)
        repeat(100) { i ->
            txn.newEntity("Endpoint").also { endpoint ->
                endpoint["url"] = "https://localhost/endpoint".nonSearchable
                endpoint["port"] = i
            }
        }
        txn.commit()
        txn.ers.transactional(readonly = true) { txn ->
            assertTrue(equals(0, 0L..99L, txn.findGt("Endpoint", "port", -1)))
            assertTrue(equals(0, 1L..99L, txn.findGt("Endpoint", "port", 0)))
            assertTrue(equals(0, 11L..99L, txn.findGt("Endpoint", "port", 10)))

            assertTrue(equals(0, 0L..99L, txn.findEqOrGt("Endpoint", "port", 0)))
            assertTrue(equals(0, 0L..99L, txn.findEqOrGt("Endpoint", "port", -1)))
            assertTrue(equals(0, 10L..99L, txn.find("Endpoint", "port", 10, FindOption.EqOtGt)))
            assertEquals(0L, txn.findEqOrGt("Endpoint", "port", 100).size)
        }
    }

    @Test
    fun txnIsolation() {
        val user = txn.newEntity("User")
        user["login"] = "testuser"
        try {
            txn.ers.beginTransaction().use { anotherTxn ->
                assertNull(anotherTxn.getEntityOrNull(user.id))
                anotherTxn.abort()
            }
        } catch (_: ERSConflictingTransactionException) {
            // disallowing parallel read and writes is an allowed way of achieving isolation
        }
    }

    @Test
    fun parallelTxns() {
        val user = txn.newEntity("User")
        user["login"] = "testuser"

        assertThrows<ERSConflictingTransactionException> {
            txn.ers.beginTransaction().use { anotherTxn ->
                assertNull(anotherTxn.getEntityOrNull(user.id))
                assertNotEquals(user.id, anotherTxn.newEntity("User"))
            }
            txn.commit()
        }
        txn.abort()
    }

    @Test
    fun sequentialTxns() {
        var user = txn.newEntity("User")
        val id = user.id
        user["login"] = "testuser"
        txn.commit()

        txn.ers.beginTransaction().use { anotherTxn ->
            user = anotherTxn.getEntityOrNull(user.id)!!
            assertEquals("testuser", user.get<String>("login"))
            assertEquals(id, user.id)
        }
    }

    @Test
    fun thousandSequentialTxns() {
        val ers = txn.ers
        txn.abort()
        repeat(1000) { i ->
            ers.beginTransaction().use { txn ->
                val user = txn.newEntity("User")
                user["login"] = ("testuser" + (i % 10))
            }
        }

        ers.beginTransaction(readonly = true).use { txn ->
            assertEquals(1000L, txn.all("User").size)
            val iterable = txn.find("User", "login", "testuser1")
            assertTrue(iterable.isNotEmpty)
            assertFalse(iterable.isEmpty)
            assertEquals(100, iterable.size)
        }
    }

    @Test
    fun all() {
        repeat(100) {
            txn.newEntity("User")["name"] = "user$it"
        }

        assertFalse(txn.all("User").isEmpty)
        assertEquals(100, txn.all("User").size)
    }

    @Test
    fun delete() {
        all()
        txn.all("User").forEach { e ->
            if (e.id.instanceId % 10L == 0L) {
                e.delete()
                assertThrows<ERSNonExistingEntityException> {
                    e["name"] = "deleted user"
                }
                assertThrows<ERSNonExistingEntityException> {
                    e.get<String>("name")
                }
                assertThrows<ERSNonExistingEntityException> {
                    e.getLinks("x")
                }
                assertThrows<ERSNonExistingEntityException> {
                    e.addLink("x", e)
                }
                assertThrows<ERSNonExistingEntityException> {
                    e.setRawBlob("x", byteArrayOf())
                }
            }
        }
        txn.all("User").apply {
            assertFalse(isEmpty)
            assertEquals(90L, size)
        }
    }

    @Test
    fun entityIterableUnion() {
        val user1 = txn.newEntity("User").also { user ->
            user["login"] = "user1"
        }
        val user2 = txn.newEntity("User").also { user ->
            user["login"] = "user2"
        }
        val united = txn.find("User", "login", "user1") +
                txn.find("User", "login", "user2")
        assertFalse(united.isEmpty)
        assertEquals(2L, united.size)
        assertTrue(user1 in united)
        assertTrue(user2 in united)
    }

    @Test
    fun entityIterableIntersect() {
        txn.newEntity("User").also { user ->
            user["login"] = "user1"
        }
        txn.newEntity("User").also { user ->
            user["login"] = "user2"
        }
        val intersected = txn.find("User", "login", "user1") *
                txn.find("User", "login", "user2")
        assertTrue(intersected.isEmpty)
    }

    @Test
    fun crossTransactionEntityIterableIntersect() {
        val iterable1 = txn.all("User")
        txn.ers.beginTransaction(readonly = true).use { txn2 ->
            val iterable2 = txn2.all("User")
            assertThrows<IllegalArgumentException> {
                iterable1 * iterable2
            }
        }
    }

    @Test
    fun entityIterableMinus() {
        val user1 = txn.newEntity("User").also { user ->
            user["login"] = "user1"
        }
        val user2 = txn.newEntity("User").also { user ->
            user["login"] = "user2"
        }
        val united = txn.find("User", "login", "user1") +
                txn.find("User", "login", "user2")
        assertFalse(united.isEmpty)
        assertEquals(2L, united.size)
        assertTrue(user1 in united)
        assertTrue(user2 in united)
        val minus = united - txn.find("User", "login", "user2")
        assertFalse(minus.isEmpty)
        assertEquals(1L, minus.size)
        assertTrue(user1 in minus)
        assertTrue(user2 !in minus)
    }

    @Test
    fun addLink() {
        val user = txn.newEntity("User")
        val userProfiles = links(user, "userProfile")
        repeat(200) {
            userProfiles += txn.newEntity("UserProfile")
        }
        val userProfile = txn.newEntity("UserProfile").also {
            userProfiles += it
        }
        repeat(200) {
            userProfiles += txn.newEntity("UserProfile")
        }

        assertTrue(userProfile in userProfiles)
        assertFalse(user.addLink("userProfile", userProfile))
        assertTrue(userProfile in userProfiles)
    }

    @Test
    fun deleteLink() {
        addLink()
        val user = txn.all("User").first()
        val userProfile = txn.all("UserProfile").first()
        val userProfiles = links(user, "userProfile")
        assertTrue(userProfile in userProfiles)
        userProfiles -= userProfile
        assertTrue(userProfile !in userProfiles)
        assertFalse(user.deleteLink("userProfile", userProfile))
    }

    @Test
    fun useTransactionAfterCommit() {
        txn.commit()
        assertThrows<ERSTransactionFinishedException> {
            txn.all("User")
        }
    }

    @Test
    fun useIterableAfterAbort() {
        val iterable = txn.all("User")
        txn.abort()
        assertThrows<ERSTransactionFinishedException> {
            iterable.iterator()
        }
    }

    @Test
    fun useEntityAfterAbort() {
        val entity = txn.newEntity("User")
        txn.abort()
        assertThrows<ERSTransactionFinishedException> {
            entity.get<String>("x")
        }
    }

    @Test
    fun useLinkIterableAfterCommit() {
        val linkIterable = txn.newEntity("User").getLinks("x")
        txn.abort()
        assertThrows<ERSTransactionFinishedException> {
            linkIterable.iterator()
        }
    }

    object UserType : ErsType {
        val login by property(String::class)
        val password by property(String::class)
        val avatar by property(ByteArray::class, searchability = ErsSearchability.NonSearchable)
        val profile by link(UserProfileType)
    }

    object UserProfileType : ErsType

    @Nested
    inner class TypedErsTest {

        @Test
        fun createEntityLoadEntity() {
            val user = txn.newEntity(UserType)
            val id = user.id
            assertEquals(user, txn.getEntityOrNull(id))
        }

        @Test
        fun stringProperties() {
            val user = txn.newEntity(UserType)
            user[UserType.login] = "testuser"
            user[UserType.password] = "!@#$%^&*()"
            val found = txn.find(UserType.login, "testuser")
            assertFalse(found.isEmpty)
            assertEquals(1L, found.size)
            assertTrue(user in found)

            assertEquals("testuser", user[UserType.login])

            val nonExistingProp by UserType.property(String::class)
            assertNull(user[nonExistingProp])

            user[UserType.login] = null
            assertNull(user[UserType.login])

            user.delete()
            assertThrows<ERSNonExistingEntityException> {
                user[UserType.password]
            }
        }

        @Test
        fun blobs() {
            val user = txn.newEntity(UserType)
            val png = byteArrayOf(1, 2, 3, 4)

            user[UserType.avatar] = png

            val avatar = user[UserType.avatar]
            assertNotNull(avatar)
            assertArrayEquals(png, avatar)

            user[UserType.avatar] = null
            assertNull(user[UserType.avatar])
        }

        @Test
        fun all() {
            repeat(100) {
                txn.newEntity(UserType)[UserType.login] = "user$it"
            }

            assertFalse(txn.all(UserType).isEmpty)
            assertEquals(100, txn.all(UserType).size)
        }

        @Test
        fun entityIterableSubtract() {
            val user1 = txn.newEntity(UserType).also { user ->
                user[UserType.login] = "user1"
                user[UserType.password] = "123"
            }
            val user2 = txn.newEntity(UserType).also { user ->
                user[UserType.login] = "user2"
                user[UserType.password] = "123"
            }
            val user3 = txn.newEntity(UserType).also { user ->
                user[UserType.password] = "123"
            }
            val subtracted = txn.find(UserType.password, "123") - txn.find(UserType.login, "user2")
            assertFalse(subtracted.isEmpty)
            assertEquals(2L, subtracted.size)
            assertTrue(user1 in subtracted)
            assertFalse(user2 in subtracted)
            assertTrue(user3 in subtracted)
        }

        @Test
        fun addLink() {
            val user = txn.newEntity(UserType)
            val userProfile = txn.newEntity(UserProfileType).also {
                user.addLink(UserType.profile, it)
            }
            assertTrue(userProfile in user[UserType.profile])
            assertFalse(user.addLink(UserType.profile, userProfile))
            assertTrue(userProfile in user[UserType.profile])
        }

        @Test
        fun deleteLink() {
            addLink()
            val user = txn.all(UserType).first()
            val userProfile = txn.all(UserProfileType).first()
            assertTrue(userProfile in user[UserType.profile])
            user.deleteLink(UserType.profile, userProfile)
            assertTrue(userProfile !in user[UserType.profile])
            assertFalse(user.deleteLink(UserType.profile, userProfile))
        }
    }

    @BeforeEach
    fun setUp() {
        txn = ersSpi.newStorage(
            persistenceLocation = null,
            settings = ersSettings
        ).beginTransaction()
    }

    @AfterEach
    fun tearDown() {
        txn.close()
        txn.ers.close()
    }

    private fun equals(typeId: Int, ids: LongProgression, entities: EntityIterable): Boolean {
        val idsIt = ids.iterator()
        val entitiesIt = entities.iterator()
        while (idsIt.hasNext() && entitiesIt.hasNext()) {
            val e = entitiesIt.next()
            if (e.id.typeId != typeId) return false
            if (e.id.instanceId != idsIt.next()) return false
        }
        return !idsIt.hasNext() && !entitiesIt.hasNext()
    }
}