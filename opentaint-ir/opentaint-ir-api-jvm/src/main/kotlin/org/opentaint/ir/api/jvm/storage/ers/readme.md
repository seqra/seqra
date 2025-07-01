## Pluggable Entity Relationship Storage API

Pluggable Entity Relationship Storage API (ERS API) is designed to access data as entities with attributes and links.
It is similar to [ER model](https://en.wikipedia.org/wiki/Entity%E2%80%93relationship_model), but it differs in that
ERS API lets you make your model dynamic. In addition, ERS API lets you describe several domains of data (probably
not linked or linked weakly) in a single storage with transactional access to data of several domains.

Implementations of the API are being provided using a Service Provider Interface, `EntityRelationshipStorageSPI`.
Any particular ERS storage is an implementation of `EntityRelationshipStorage`. It can be created by particular Service
Provider, an implementation of `EntityRelationshipStorageSPI`.

- [Transactions](#transactions)
- [Entities](#entities)
- [Properties](#properties)
- [Blobs](#blobs)
- [Links](#links)
- [Queries](#queries)

## Transactions

Use a **transactions** to create, modify, read and query data. Transactions are quite similar to those on the
Pluggable Key/Value Storage API layer.

To create a transaction for given `EntityRelationshipStorage`, use `beginTransaction()`:

```kotlin
val txn = storage.beginTransaction()
```

To create read-only transaction for given `EntityRelationshipStorage`, use the same method with explicitly specified
parameter:

```kotlin
val txn = storage.beginTransaction(readonly = true)
```

Any transaction should be finished, meaning that it is either aborted or committed. To finish a read-only transaction,
abort it. Transaction is `Closeable`, so it can be just closed. Closing read-only transaction aborts it, closing
read-write one commits it. Commit of a transaction can result in a `ERSConflictingTransactionException` thrown which
means that another transaction was committed after that one was created and prior to attempt to commit that one.

Transactions can be created and closed implicitly by use of the `EntityRelationshipStorage.transactional()`:

```kotlin
storage.transactional { txn ->
    // do smth in read-write transaction
}
```

For read-only operations:

```kotlin
storage.transactional(readonly = true) { txn ->
    // do smth in read-only transaction
}
```

If a transactional action is such that it can be repeated after a `ERSConflictingTransactionException` is thrown,
then use `EntityRelationshipStorage.transactionalOptimistic()`:

```kotlin
storage.transactionalOptimistic(attempts = 5) {
    // do smth
}
```

In `EntityRelationshipStorage.transactionalOptimistic()`, read-write transaction is being created and commited
implicitly. If a `ERSConflictingTransactionException` is thrown, the action would be repeatedly executed in a new
transaction. The number of attempts to repeat an action can be specified explicitly, by default it's 5.
After this number of unsuccessful optimistic attempts `EntityRelationshipStorage.transactionalOptimistic()` would
finally fail with `ERSConflictingTransactionException` thrown.

## Entities

**Entities** can have properties and blobs, and can be linked. Each property, blob, or link is identified by its name.
Given we have already a transaction `txn`, let's create a user:

```kotlin
val user: Entity = txn.newEntity("User")
```

At that point an entity has no properties, blobs and links. It has only its type (`"User"`) and unique ID, described
by the `EntityId` class:

```kotlin
val id: EntityId = user.id
```

`EntityId` encapsulates `typeId` (having a bijection with type name) and `instanceId`, a unique number of entity of
its type.

Entity ID can be used to load an entity in any transaction:

```kotlin
val user: Entity? = txn.getEntityOrNull(id)
```

The method returns `null`, if there is no entity with specified `id` in the database snapshot held by the transaction.
Alternatively, it is possible to get entity for given type and instance ID:

```kotlin
val user: Entity? = txn.getEntityOrNull("User", 0L)
```

To delete an entity `e`, use `Entity.delete()` method:

```kotlin
e.delete()
```

## Properties

Let's create user with specific `loginName` and `email`, the properties of type `String`:

```kotlin
val user = txn.newEntity("User").also { user ->
    user["loginName"] = loginName
    user["email"] = email
}
```

To get these properties:

```kotlin
val loginName: String? = user["loginName"]
val email: String? = user["email"]
```

Or, alternatively:

```kotlin
val loginName: String? by propertyOf(user)
val email: String? by propertyOf(user)
```

The following property declarations are semantically equal:

```kotlin
val loginName: String? by propertyOf(user)
val loginName by propertyOf<String>(user)
val login: String? by propertyOf(user, name = "loginName")
val login by propertyOf<String>(user, name = "loginName")
```

By default, this notation means:

- property values are stored with no compression;
- property values are indexed under the hood so that entities can be found by property value (see [Queries](#queries)).

There is a limited set of classes which objects can be used as a property value. By default, only `String`, `Int`,
`Long`, `Boolean`, raw `ByteArray`, and `Enum` property values are supported. For each class, a `Binding` implementation
does a transform from/to objects/ByteArrays. Each `EntityRelationshipStorage` is a `BindingProvider` implementor,
so it looks quite easy to implement a decorator storage with custom bindings supported. In addition, there is a
possibility to set/get raw property value as `ByteArray`:

```kotlin
user.setRawProperty("avatar", byteArrayOf(0, 1, 2))
val avatar: ByteArray? = user.getRawProperty("avatar")
```

To set a compressed property value:

```kotlin
user["age"] = 42.compressed
```

To get a compressed property value use several alternative, but essentially equal methods:

```kotlin
val age: Int? = user.getCompressed("age")
val age = user.getCompressed<Int>("age")
val age: Int? by propertyOf(user, compressed = true)
```

Compression is being supported only for non-negative property values of type `Int` and `Long`. It makes sense to
compress property values, if they are close to `0` or `0L`, and it doesn't make sense to compress _random_ values.
Compression of property values reduces database size and thus improves vertical scalability.

To delete a property, use `deleteProperty()` method:

```kotlin
user.deleteProperty("tempProp")
```

## Blobs

Properties are searchable, unless they are marked as `nonSearchable`. Non-searchable properties are actually blobs.
Blobs do not have value indices, so they cannot be used to find entities by blob value.
Examples of setting blob values:

```kotlin
user["bio"] = "My heart's in the Highlands, my heart is not here".nonSearchable
user["ndfl"] = 18.compressed.nonSearchable
user.setRawBlob("avatar", byteArrayOf(0, 1, 2))
```

Getting blobs:

```kotlin
val bio = user.getBlob<String>("bio")
val ndfl: Int? = user.getCompressedBlob("ndfl")
val avatar = user.getRawBlob("avatar")
```

The following blob declarations are semantically equal:

```kotlin
val bio: String? = user.getBlob("bio")
val bio = user.getBlob<String>("bio")
val bio: String? by blobOf(user)
val bio by blobOf<String>(user)
val myBio: String? by blobOf(user, name = "bio")
val myBio by blobOf<String>(user, name = "bio")
```

To delete a blob, use `deleteBlob()` method:

```kotlin
user.deleteBlob("tempBlob")
```

From the performance point of view, it is preferable to use blobs rather than properties, unless searching for entities
by property value is necessary.

## Links

Let's define an authentication module and link user with it, if the user can be authenticated using its method:

```kotlin
val user = txn.newEntity("User").also { user ->
    user["loginName"] = loginName
    user["email"] = email
}
val authModules = links(user, "authModules")
val ldap = txn.newEntity("AuthModule").also { authModule ->
    authModule["type"] = "ldap"
    authModule["url"] = "https://..."
}
authModules += ldap
```

So `user` is now linked with `ldap` which could mean she/he can be authenticated using LDAP.

Alternative way to add a link:

```kotlin
user.addLink("authModule", ldap)
```

If `ldap` authentication module is no longer a valid auth method for user, unlink it:

```kotlin
// authModules is defined as above 
authModules -= ldap
```

Or, alternatively and more explicitly:

```kotlin
user.deleteLink("authModule", ldap)
```

To get all auth modules, use `Entity.getLinks()` method:

```kotlin
user.getLinks("authModule").forEach {
    // ...
}
```

Or just iterate over `authModules` as it is defined above:

```kotlin
authModules.forEach {
    // ...
}
```

If an entity can be linked with another one (and only one!) by a specified link, use `Entity.getLink()` method to get
a linked entity:

```kotlin
val userProfile: Entity = user.getLink("userProfile")
```

It would throw `NoSuchElementException`, if there is no such link, or `IllegalArgumentException`, if this entity is
linked with more than one entity by specified link name.

## Queries

There are the following basic queries available on the instance of `Transaction`:

- `Transaction.all()` gets all entities of specified type;
- `Transaction.find()` gets entities of specified type with specified property equal to specified value;
- `Transaction.findLt()` gets entities of specified type with specified property less than specified value;
- `Transaction.findOrLt()` gets entities of specified type with specified property equal to or less than specified
  value;
- `Transaction.findGt()` gets entities of specified type with specified property greater than specified value;
- `Transaction.findOrGt()` gets entities of specified type with specified property equal to or greater than specified
  value.

Enumerate all users:

```kotlin
txn.all("User").forEach { user ->
    // ...
}
```

Enumerate all users having age 42:

```kotlin
txn.find("User", "age", 42.compressed).forEach { user ->
    // ...
}
```

Enumerate all users having age equal to or less than 42:

```kotlin
txn.findEqOrLt("User", "age", 42.compressed).forEach { user ->
    // ...
}
```

Queries return instances of `EntityIterable`. `EntityIterable` is `Iterable<Entity>`. In addition, there are tree binary
operations on instances of `EntityIterable`: _intersect_, _union_ and _minus_.

To get users with age `42` _and_ having e-mail `user@cia.gov`:

```kotlin
val users = txn.find("User", "age", 42.compressed) * txn.find("User", "e-mail", "user@cia.gov") 
```

To get users with age equal to or greater than `42` _or_ having e-mail `user@cia.gov`:

```kotlin
val users = txn.findEqOrGt("User", "age", 42.compressed) + txn.find("User", "e-mail", "user@cia.gov") 
```

To get users with age greater than `42` _not_ having e-mail `user@cia.gov`:

```kotlin
val users = txn.findGt("User", "age", 42.compressed) - txn.find("User", "e-mail", "user@cia.gov") 
```