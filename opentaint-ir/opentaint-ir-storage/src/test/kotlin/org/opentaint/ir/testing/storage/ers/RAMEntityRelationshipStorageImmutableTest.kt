package org.opentaint.ir.testing.storage.ers

import org.opentaint.ir.api.storage.ers.EmptyErsSettings
import org.opentaint.ir.api.storage.ers.EntityRelationshipStorage
import org.opentaint.ir.api.storage.ers.EntityRelationshipStorageSPI
import org.opentaint.ir.api.storage.ers.compressed
import org.opentaint.ir.api.storage.ers.nonSearchable
import org.opentaint.ir.impl.storage.ers.ram.RAM_ERS_SPI
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class RAMEntityRelationshipStorageImmutableTest {

    private val ersSpi by lazy(LazyThreadSafetyMode.NONE) {
        EntityRelationshipStorageSPI.getProvider(RAM_ERS_SPI)
    }

    private lateinit var readonlyStorage: EntityRelationshipStorage

    @Test
    fun `get all users`() {
        readonlyStorage.transactional { txn ->
            assertEquals(100L, txn.all("User").size)
            txn.all("User").forEachIndexed { i, user ->
                assertEquals("login$i", user.get<String>("login"))
                assertEquals("password$i", user.get<String>("password"))
                assertEquals("!@#$%^&$i", user.getBlob<String>("avatar"))
            }
        }
    }

    @Test
    fun `get all users in a group`() {
        readonlyStorage.transactional { txn ->
            val groupUsers = txn.all("UserGroup").first().getLinks("user").toList()
            assertEquals(100, groupUsers.size)
            assertEquals(100L, txn.all("User").size)
            txn.all("User").forEachIndexed { i, user ->
                assertEquals(groupUsers[i], user)
            }
        }
    }

    @Test
    fun `find users by property`() {
        readonlyStorage.transactional { txn ->
            repeat(100) { i ->
                (txn.find("User", "login", "login$i") * txn.find("User", "password", "password$i")).first()
                    .let { user ->
                        assertEquals("login$i", user.get<String>("login"))
                        assertEquals("password$i", user.get<String>("password"))
                        assertEquals("!@#$%^&$i", user.getBlob<String>("avatar"))
                    }
            }
        }
    }

    @Test
    fun `find users by property in range`() {
        readonlyStorage.transactional { txn ->
            assertEquals(100L, txn.findEqOrGt("User", "age", 20.compressed).size)
            assertEquals(90L, txn.findGt("User", "age", 20.compressed).size)
            assertEquals(40L, txn.findGt("User", "age", 25.compressed).size)
            assertEquals(0L, txn.findEqOrGt("User", "age", 30.compressed).size)
            assertEquals(0L, txn.findGt("User", "age", 29.compressed).size)
            assertEquals(0L, txn.findLt("User", "age", 20.compressed).size)
            assertEquals(10L, txn.findEqOrLt("User", "age", 20.compressed).size)
            assertEquals(20L, txn.findEqOrLt("User", "age", 21.compressed).size)
            assertEquals(10L, txn.findLt("User", "age", 21.compressed).size)
            assertEquals(20L, txn.findLt("User", "age", 22.compressed).size)

            assertEquals(100L, txn.findEqOrGt("User", "height", 100.compressed).size)
            assertEquals(100L, txn.findEqOrLt("User", "height", 180.compressed).size)
            assertEquals(90L, txn.findLt("User", "height", 180.compressed).size)

            repeat(100) { i ->
                assertEquals(100L - i, txn.findEqOrGt("User", "dozen", i * 12).size)
                assertEquals(99L - i, txn.findGt("User", "dozen", i * 12).size)
                assertEquals(99L - i, txn.findEqOrGt("User", "dozen", i * 12 + 1).size)
                assertEquals(99L - i, txn.findGt("User", "dozen", i * 12 + 1).size)
                assertEquals(i.toLong(), txn.findLt("User", "dozen", i * 12).size)
                assertEquals(i.toLong() + 1, txn.findEqOrLt("User", "dozen", i * 12).size)
                assertEquals(i.toLong() + 1, txn.findLt("User", "dozen", i * 12 + 1).size)
                assertEquals(i.toLong() + 1, txn.findEqOrLt("User", "dozen", i * 12 + 1).size)
            }
        }
    }

    @Test
    fun `entity metadata`() {
        readonlyStorage.transactional { txn ->
            txn.getPropertyNames("User").toList().let { props ->
                assertEquals(5, props.size)
                assertEquals("age", props[0])
                assertEquals("dozen", props[1])
                assertEquals("height", props[2])
                assertEquals("login", props[3])
                assertEquals("password", props[4])
            }
            txn.getBlobNames("User").toList().let { blobs ->
                assertEquals(1, blobs.size)
                assertEquals("avatar", blobs[0])
            }
            txn.getLinkNames("UserGroup").toList().let { links ->
                assertEquals(1, links.size)
                assertEquals("user", links[0])
            }
        }
    }

    @BeforeEach
    fun setUp() {
        readonlyStorage = ersSpi.newStorage(persistenceLocation = null, settings = EmptyErsSettings).let { rwStorage ->
            // populate some data
            rwStorage.transactional { txn ->
                repeat(100) { i ->
                    val user = txn.newEntity("User")
                    user["login"] = "login$i"
                    user["password"] = "password$i"
                    user["avatar"] = "!@#$%^&$i".nonSearchable
                    user["age"] = (20 + (i / 10)).compressed
                    user["height"] = (180 - (i / 10)).compressed
                    user["dozen"] = i * 12
                }
                val userGroup = txn.newEntity("UserGroup")
                txn.all("User").forEach { user ->
                    userGroup.addLink("user", user)
                }
            }
            rwStorage.asReadonly
        }
    }
}