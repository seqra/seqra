## Typed API for Pluggable Entity Relationship Storage

The typed ERS API mirrors the [regular Pluggable Entity Relationship Storage API](../readme.md) functionality 
while ensuring type-safety by allowing users to declare entity types and their properties.

- [Getting started](#getting-started)
- [Declaring Entity Types](#declaring-entity-types)
- [Creating and Using Entities](#creating-and-using-entities)
- [Queries](#queries)

## Getting Started

To start interacting with the storage via the typed API, you need to:
1. Obtain an `EntityRelationshipStorage` instance via one of the `EntityRelationshipStorageSPI` implementations.
2. Start a transaction as described [here](../readme.md#transactions).

## Declaring Entity Types

To declare an entity type, you need to create an `object` that inherits from `ErsType`:
```kotlin
object UserProfileType : ErsType
```

Inside the entity type, you can declare which properties and links it has:
```kotlin
object UserType : ErsType {
    val login by property(String::class)
    val password by property(String::class)
    val avatar by property(ByteArray::class, searchability = ErsSearchability.NonSearchable)
    val profile by link(UserProfileType)
}
```

You need to specify a type for every property.

By default, only `String`, `Int`, `Long`, `Boolean`, raw `ByteArray`, and `Enum` property types are supported,
but you can support custom types by implementing the `Binding` interface and decorating
the `EntityRelationshipStorage.getBinding()` method.

## Creating and Using Entities

Given you already have a [transaction](../readme.md#transactions) `txn`, you can create and persist a new entity like this:
```kotlin
val user = txn.newEntity(UserType)
```

Next, you can:
1. Retrieve the entity ID:
    ```kotlin
    val id = user.id
    ```
2. Set and get entity properties:
   ```kotlin
   user[UserType.login] = "testuser"
   val login = user[UserType.login]
   ```
3. Delete an entity:
   ```kotlin
   user.delete()
   ```
4. Add, get, and delete links:
   ```kotlin
   val userProfile = txn.newEntity(UserProfileType)
   user.addLink(UserType.profile, userProfile)
   val profilesIterator = user[UserType.profile]
   user.deleteLink(UserType.profile, userProfile)
   ```

## Queries

Currently, there are two basic queries available on the instance of `Transaction`:

1. Enumerate all users:
    ```kotlin
    txn.all(UserType).forEach { user ->
        // ...
    }
    ```
2. Enumerate all users having password "42":
    ```kotlin
    txn.find(UserType.password, "42").forEach { user ->
        // ...
    }
    ```
